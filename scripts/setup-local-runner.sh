#!/usr/bin/env bash
# Install (or remove) the self-hosted GitHub Actions runner that deploys ERMS to Rancher Desktop.
# docs/ARCHITECTURE.md §5.5, docs/CICD.md "Self-hosted runner".
#
# Usage:
#   scripts/setup-local-runner.sh [--repo owner/name] [--name RUNNER_NAME] [--dir DIR] [--skip-hardening]
#   scripts/setup-local-runner.sh --uninstall [--repo owner/name] [--dir DIR]
#
# Defaults: repo from `gh repo view`, name "<LocalHostName>-rancher-desktop", dir ~/actions-runner/erms.
# Idempotent: re-running keeps an existing registration, rewrites .path/.env, (re)starts the
# LaunchAgent and re-applies the repository hardening.
#
# Requires: macOS arm64, gh (authenticated, repo admin), docker + kubectl + helm (Rancher Desktop).
set -euo pipefail

REPO=""
RUNNER_NAME=""
RUNNER_DIR="${HOME}/actions-runner/erms"
RUNNER_LABELS="rancher-desktop"
KUBE_CONTEXT="rancher-desktop"
ENVIRONMENT="local"
DEPLOY_BRANCH="main"
UNINSTALL=0
HARDEN=1

usage() { awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo) REPO="$2"; shift 2 ;;
    --name) RUNNER_NAME="$2"; shift 2 ;;
    --dir) RUNNER_DIR="$2"; shift 2 ;;
    --uninstall) UNINSTALL=1; shift ;;
    --skip-hardening) HARDEN=0; shift ;;
    -h | --help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

log() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
ok() { printf '  \033[32m✓\033[0m %s\n' "$*"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

if [[ -d "$HOME/.rd/bin" ]]; then
  PATH="$HOME/.rd/bin:$PATH"
fi

[[ "$(id -u)" != "0" ]] || die "run as your normal user, not root (the runner is a per-user LaunchAgent)"
command -v gh >/dev/null 2>&1 || die "gh (GitHub CLI) is required: brew install gh"
gh auth status >/dev/null 2>&1 || die "gh is not authenticated: run 'gh auth login'"

if [[ -z "$REPO" ]]; then
  REPO="$(gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null)" ||
    die "cannot determine the repository; pass --repo owner/name"
fi
[[ "$REPO" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || die "invalid --repo '$REPO'"

if [[ -z "$RUNNER_NAME" ]]; then
  RUNNER_NAME="$(scutil --get LocalHostName 2>/dev/null || hostname -s)-rancher-desktop"
fi

runner_registered() {
  gh api "repos/${REPO}/actions/runners" --paginate --jq '.runners[].name' 2>/dev/null |
    grep -Fxq "$RUNNER_NAME"
}

# svc.sh install writes the LaunchAgent plist path into .service.
service_installed() {
  [[ -f "$RUNNER_DIR/.service" ]] && [[ -f "$(cat "$RUNNER_DIR/.service")" ]]
}

# ---------------------------------------------------------------------------------------------
# Uninstall
# ---------------------------------------------------------------------------------------------
if [[ "$UNINSTALL" == "1" ]]; then
  log "Uninstalling runner '$RUNNER_NAME' for $REPO from $RUNNER_DIR"
  if service_installed; then
    (cd "$RUNNER_DIR" && ./svc.sh stop >/dev/null 2>&1 || true)
    (cd "$RUNNER_DIR" && ./svc.sh uninstall >/dev/null)
    ok "LaunchAgent stopped and removed"
  fi
  if [[ -f "$RUNNER_DIR/.runner" ]]; then
    REMOVE_TOKEN="$(gh api -X POST "repos/${REPO}/actions/runners/remove-token" --jq .token)"
    (cd "$RUNNER_DIR" && ./config.sh remove --token "$REMOVE_TOKEN")
    ok "runner unregistered from GitHub"
  else
    warn "no local runner configuration found in $RUNNER_DIR"
  fi
  echo
  echo "Runner files remain in $RUNNER_DIR (delete with: rm -rf \"$RUNNER_DIR\")."
  echo "The GitHub environment '$ENVIRONMENT' and repository settings were left unchanged."
  exit 0
fi

# ---------------------------------------------------------------------------------------------
# 1. Preflight
# ---------------------------------------------------------------------------------------------
log "Preflight checks"
[[ "$(uname -s)" == "Darwin" ]] || die "this script supports macOS only"
[[ "$(uname -m)" == "arm64" ]] || die "this script supports Apple silicon (arm64) only"
ok "macOS arm64"
ok "gh authenticated; repository $REPO"
[[ "$(gh api "repos/${REPO}" --jq .permissions.admin)" == "true" ]] ||
  die "you need admin rights on $REPO to register runners and change settings"
for bin in docker kubectl helm curl shasum tar; do
  command -v "$bin" >/dev/null 2>&1 || die "'$bin' not found in PATH (Rancher Desktop installs docker/kubectl/helm in ~/.rd/bin)"
done
docker info >/dev/null 2>&1 || die "docker is not reachable; start Rancher Desktop"
DOCKER_CTX="$(docker context show 2>/dev/null || echo unknown)"
if [[ "$DOCKER_CTX" != "rancher-desktop" ]]; then
  warn "docker context is '$DOCKER_CTX' (expected rancher-desktop). The CD job uses ~/.rd/docker.sock directly."
fi
ok "docker reachable (context $DOCKER_CTX)"
kubectl --context "$KUBE_CONTEXT" get nodes >/dev/null 2>&1 ||
  die "kubectl context '$KUBE_CONTEXT' is not reachable; enable Kubernetes in Rancher Desktop"
ok "kubectl context $KUBE_CONTEXT reachable"
ok "helm $(helm version --short 2>/dev/null)"
if command -v rdctl >/dev/null 2>&1; then
  if rdctl list-settings 2>/dev/null | tr -d ' \n' | grep -q '"kubernetes":{[^}]*"enabled":false'; then
    warn "rdctl reports Kubernetes disabled in Rancher Desktop settings"
  fi
fi

# ---------------------------------------------------------------------------------------------
# 2. Download the latest runner (SHA-256 verified)
# ---------------------------------------------------------------------------------------------
mkdir -p "$RUNNER_DIR"
if [[ -x "$RUNNER_DIR/config.sh" && -x "$RUNNER_DIR/bin/Runner.Listener" ]]; then
  ok "runner already present in $RUNNER_DIR (it self-updates); skipping download"
else
  log "Downloading the latest actions/runner (osx-arm64)"
  RELEASE_JSON="$(gh api repos/actions/runner/releases/latest)"
  VERSION="$(printf '%s' "$RELEASE_JSON" | grep -o '"tag_name": *"v[0-9][0-9.]*"' | head -n1 | grep -o '[0-9][0-9.]*')"
  [[ -n "$VERSION" ]] || die "could not determine the latest actions/runner version"
  ASSET="actions-runner-osx-arm64-${VERSION}.tar.gz"
  URL="https://github.com/actions/runner/releases/download/v${VERSION}/${ASSET}"
  EXPECTED_SHA="$(printf '%s' "$RELEASE_JSON" | grep -o 'BEGIN SHA osx-arm64 -->[0-9a-f]\{64\}' | grep -o '[0-9a-f]\{64\}' | head -n1 || true)"
  [[ -n "$EXPECTED_SHA" ]] || die "could not find the osx-arm64 SHA-256 in the release notes of v${VERSION}"
  # Cross-check with the asset digest GitHub computes on upload (when available).
  ASSET_DIGEST="$(gh api "repos/actions/runner/releases/tags/v${VERSION}" --jq ".assets[] | select(.name == \"${ASSET}\") | .digest // empty" | sed 's/^sha256://')"
  if [[ -n "$ASSET_DIGEST" && "$ASSET_DIGEST" != "$EXPECTED_SHA" ]]; then
    die "release-notes SHA ($EXPECTED_SHA) and asset digest ($ASSET_DIGEST) disagree"
  fi
  TMP_TAR="$(mktemp -d)/${ASSET}"
  curl -fsSL --retry 3 -o "$TMP_TAR" "$URL"
  ACTUAL_SHA="$(shasum -a 256 "$TMP_TAR" | awk '{print $1}')"
  [[ "$ACTUAL_SHA" == "$EXPECTED_SHA" ]] || die "SHA-256 mismatch for $ASSET: expected $EXPECTED_SHA, got $ACTUAL_SHA"
  ok "SHA-256 verified ($ACTUAL_SHA)"
  tar -xzf "$TMP_TAR" -C "$RUNNER_DIR"
  rm -rf "$(dirname "$TMP_TAR")"
  ok "runner v${VERSION} extracted to $RUNNER_DIR"
fi

# ---------------------------------------------------------------------------------------------
# 3. Register (only if not already registered)
# ---------------------------------------------------------------------------------------------
cd "$RUNNER_DIR"
if [[ -f .runner ]] && runner_registered; then
  ok "runner '$RUNNER_NAME' already registered with $REPO"
else
  if service_installed; then
    ./svc.sh stop >/dev/null 2>&1 || true
  fi
  if [[ -f .runner ]]; then
    warn "stale local registration found (runner missing on GitHub); resetting local config"
    rm -f .runner .credentials .credentials_rsaparams
  fi
  log "Registering runner '$RUNNER_NAME' (labels: self-hosted, macOS, ARM64, $RUNNER_LABELS)"
  REG_TOKEN="$(gh api -X POST "repos/${REPO}/actions/runners/registration-token" --jq .token)"
  ./config.sh --unattended --replace \
    --url "https://github.com/${REPO}" \
    --token "$REG_TOKEN" \
    --name "$RUNNER_NAME" \
    --labels "$RUNNER_LABELS" \
    --work _work
  unset REG_TOKEN
  ok "registered"
fi

# ---------------------------------------------------------------------------------------------
# 4. Environment for the launchd service
# ---------------------------------------------------------------------------------------------
printf '%s\n' "$HOME/.rd/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin" >.path
cat >.env <<EOF
LANG=en_US.UTF-8
DOCKER_CONFIG=$HOME/.docker
KUBECONFIG=$HOME/.kube/config
EOF
ok "wrote .path and .env"

# ---------------------------------------------------------------------------------------------
# 5. LaunchAgent (runs as the current user, so it can use ~/.rd/docker.sock and ~/.kube/config)
# ---------------------------------------------------------------------------------------------
if service_installed; then
  ./svc.sh stop >/dev/null 2>&1 || true
  ./svc.sh start
  ok "LaunchAgent restarted"
else
  ./svc.sh install
  ./svc.sh start
  ok "LaunchAgent installed and started"
fi

# ---------------------------------------------------------------------------------------------
# 6. Public-repository hardening
# ---------------------------------------------------------------------------------------------
if [[ "$HARDEN" == "1" ]]; then
  log "Applying repository hardening to $REPO"

  # (a) Environment "local": deployments only from main.
  if gh api -X PUT "repos/${REPO}/environments/${ENVIRONMENT}" --input - >/dev/null <<'JSON'
{"deployment_branch_policy": {"protected_branches": false, "custom_branch_policies": true}}
JSON
  then
    if gh api "repos/${REPO}/environments/${ENVIRONMENT}/deployment-branch-policies" \
      --jq '.branch_policies[] | select(.type == "branch" or .type == null) | .name' 2>/dev/null |
      grep -Fxq "$DEPLOY_BRANCH"; then
      ok "environment '$ENVIRONMENT' restricted to '$DEPLOY_BRANCH' (already)"
    else
      gh api -X POST "repos/${REPO}/environments/${ENVIRONMENT}/deployment-branch-policies" \
        -f name="$DEPLOY_BRANCH" -f type=branch >/dev/null
      ok "environment '$ENVIRONMENT' restricted to '$DEPLOY_BRANCH'"
    fi
  else
    warn "could not create/update environment '$ENVIRONMENT'"
  fi

  # (b) Fork pull requests from any external contributor need maintainer approval to run workflows.
  if gh api -X PUT "repos/${REPO}/actions/permissions/fork-pr-contributor-approval" \
    -f approval_policy=all_external_contributors >/dev/null 2>&1; then
    ok "fork-PR workflow approval: all external contributors"
  else
    warn "could not set fork-PR approval policy; set it in Settings → Actions → General"
  fi

  # (c) Read-only default GITHUB_TOKEN; workflows cannot approve pull requests.
  if gh api -X PUT "repos/${REPO}/actions/permissions/workflow" \
    -f default_workflow_permissions=read -F can_approve_pull_request_reviews=false >/dev/null 2>&1; then
    ok "default workflow token permissions: read; PR approval by workflows disabled"
  else
    warn "could not set default workflow permissions"
  fi

  # (d) Secret scanning + push protection (free for public repositories).
  if gh api -X PATCH "repos/${REPO}" --input - >/dev/null 2>&1 <<'JSON'
{"security_and_analysis": {"secret_scanning": {"status": "enabled"}, "secret_scanning_push_protection": {"status": "enabled"}}}
JSON
  then
    ok "secret scanning and push protection enabled"
  else
    warn "could not enable secret scanning / push protection"
  fi

  cat <<EOF

  REMINDER: $REPO is public. Self-hosted runners must NEVER be targeted by workflows triggered
  by pull_request / pull_request_target. Only .github/workflows/cd.yml uses this runner
  (workflow_run from a successful CI push on main of this repository, or workflow_dispatch).
EOF
fi

echo
log "Runner ready"
gh api "repos/${REPO}/actions/runners" --paginate \
  --jq ".runners[] | select(.name == \"${RUNNER_NAME}\") | \"  \\(.name): \\(.status) [\\([.labels[].name] | join(\", \"))]\"" || true
echo "  Service status: (cd \"$RUNNER_DIR\" && ./svc.sh status)"
echo "  Logs:           ~/Library/Logs/actions.runner.${REPO/\//-}.${RUNNER_NAME}/"
