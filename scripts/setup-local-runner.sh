#!/usr/bin/env bash
# Install, control or remove the self-hosted GitHub Actions runner that deploys ERMS to Rancher Desktop.
# docs/ARCHITECTURE.md §5.5, docs/CICD.md "Self-hosted runner".
#
# Usage:
#   scripts/setup-local-runner.sh [--repo owner/name] [--name RUNNER_NAME] [--skip-hardening]
#   scripts/setup-local-runner.sh --start | --stop | --status
#   scripts/setup-local-runner.sh --uninstall [--repo owner/name]
#
# Run as your normal (admin) user; the script asks for your password (sudo) for the privileged steps.
# Isolation model:
#   - The runner runs as the hidden, unprivileged macOS user _ermsrunner (no login shell, no password,
#     not an admin), from a system LaunchDaemon. It cannot read your home directory.
#   - It has NO access to the Rancher Desktop docker socket. k3s pulls the (public) images from GHCR.
#   - Its kubeconfig belongs to the ServiceAccount erms/erms-deployer (deploy/runner/deployer-rbac.yaml):
#     namespace "erms" only, no cluster-scoped resources, no RBAC changes.
#   - Deployments wait for an approval from the repository owner (environment "local").
# --stop also disables the service, so it stays off across reboots until --start.
# Idempotent: re-running keeps an existing registration and refreshes tools, kubeconfig, service
# and repository hardening.
#
# Requires: macOS arm64, gh (authenticated, repo admin), kubectl + helm with the rancher-desktop context.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO=""
RUNNER_NAME=""
RUNNER_LABELS="rancher-desktop"
KUBE_CONTEXT="rancher-desktop"
NAMESPACE="erms"
SERVICE_ACCOUNT="erms-deployer"
ENVIRONMENT="local"
DEPLOY_BRANCH="main"

RUNNER_USER="_ermsrunner"
RUNNER_BASE="/opt/erms-runner"     # root:wheel 755
TOOLS_DIR="$RUNNER_BASE/bin"       # root:wheel 755 (kubectl, helm); the runner cannot modify them
RUNNER_HOME="$RUNNER_BASE/home"    # _ermsrunner 700 (kubeconfig, helm cache)
RUNNER_DIR="$RUNNER_BASE/runner"   # _ermsrunner 700 (runner, credentials, _work)
LOG_DIR="/Library/Logs/erms-runner"
LABEL="dev.erms.actions-runner"
PLIST="/Library/LaunchDaemons/${LABEL}.plist"

ACTION="install"
HARDEN=1

usage() { awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo) REPO="$2"; shift 2 ;;
    --name) RUNNER_NAME="$2"; shift 2 ;;
    --start) ACTION="start"; shift ;;
    --stop) ACTION="stop"; shift ;;
    --status) ACTION="status"; shift ;;
    --uninstall) ACTION="uninstall"; shift ;;
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

[[ "$(id -u)" != "0" ]] || die "run as your normal user, not root (the script uses sudo where needed)"
[[ "$(uname -s)" == "Darwin" ]] || die "this script supports macOS only"

# Run a command as the runner user, from a directory it can access.
as_runner() { (cd / && sudo -u "$RUNNER_USER" -H "$@"); }
user_exists() { dscl . -read "/Users/$RUNNER_USER" UniqueID >/dev/null 2>&1; }
service_loaded() { sudo launchctl print "system/$LABEL" >/dev/null 2>&1; }

start_service() {
  [[ -f "$PLIST" ]] || die "service not installed; run: make runner-setup"
  sudo launchctl enable "system/$LABEL"
  if service_loaded; then
    sudo launchctl kickstart "system/$LABEL"
  else
    sudo launchctl bootstrap system "$PLIST"
  fi
  ok "runner service started ($LABEL)"
}

stop_service() {
  if [[ -f "$PLIST" ]]; then
    sudo launchctl bootout "system/$LABEL" 2>/dev/null || true
    sudo launchctl disable "system/$LABEL"
    ok "runner service stopped and disabled ($LABEL); 'make runner-start' turns it back on"
  else
    warn "service not installed"
  fi
}

resolve_repo() {
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
}

runner_registered() {
  gh api "repos/${REPO}/actions/runners" --paginate --jq '.runners[].name' 2>/dev/null |
    grep -Fxq "$RUNNER_NAME"
}

# ---------------------------------------------------------------------------------------------
# start / stop / status
# ---------------------------------------------------------------------------------------------
case "$ACTION" in
  start) start_service; exit 0 ;;
  stop) stop_service; exit 0 ;;
  status)
    if [[ ! -f "$PLIST" ]]; then
      echo "service: not installed"
    elif service_loaded; then
      echo "service: loaded"
      sudo launchctl print "system/$LABEL" | grep -E '^\s*(state|pid|last exit code) =' || true
    else
      echo "service: stopped"
    fi
    resolve_repo
    gh api "repos/${REPO}/actions/runners" --paginate \
      --jq '.runners[] | "github: \(.name) \(.status)\(if .busy then " (busy)" else "" end)"' || true
    exit 0
    ;;
esac

# ---------------------------------------------------------------------------------------------
# Uninstall
# ---------------------------------------------------------------------------------------------
if [[ "$ACTION" == "uninstall" ]]; then
  resolve_repo
  log "Uninstalling runner '$RUNNER_NAME' for $REPO"
  sudo -v
  if [[ -f "$PLIST" ]]; then
    sudo launchctl bootout "system/$LABEL" 2>/dev/null || true
    sudo launchctl enable "system/$LABEL" 2>/dev/null || true
    sudo rm -f "$PLIST"
    ok "LaunchDaemon removed"
  fi
  if sudo test -f "$RUNNER_DIR/.runner"; then
    REMOVE_TOKEN="$(gh api -X POST "repos/${REPO}/actions/runners/remove-token" --jq .token)"
    # shellcheck disable=SC2016 # positional parameters of the inner bash
    as_runner /bin/bash -c 'cd "$1" && ./config.sh remove --token "$2"' _ "$RUNNER_DIR" "$REMOVE_TOKEN" ||
      warn "config.sh remove failed; delete the runner in Settings → Actions → Runners"
    unset REMOVE_TOKEN
    ok "runner unregistered from GitHub"
  fi
  sudo rm -rf "$RUNNER_BASE" "$LOG_DIR"
  ok "removed $RUNNER_BASE and $LOG_DIR"
  if user_exists; then
    sudo dscl . -delete "/Users/$RUNNER_USER"
    ok "macOS user $RUNNER_USER deleted"
  fi
  if dscl . -read "/Groups/$RUNNER_USER" >/dev/null 2>&1; then
    sudo dscl . -delete "/Groups/$RUNNER_USER"
  fi
  if kubectl --context "$KUBE_CONTEXT" get namespace "$NAMESPACE" >/dev/null 2>&1; then
    kubectl --context "$KUBE_CONTEXT" -n "$NAMESPACE" delete --ignore-not-found \
      rolebinding/"$SERVICE_ACCOUNT" role/"$SERVICE_ACCOUNT" secret/"$SERVICE_ACCOUNT"-token serviceaccount/"$SERVICE_ACCOUNT" >/dev/null
    ok "Kubernetes ServiceAccount $NAMESPACE/$SERVICE_ACCOUNT and its RBAC deleted (the app is untouched)"
  fi
  echo "The GitHub environment '$ENVIRONMENT' and repository settings were left unchanged."
  exit 0
fi

# ---------------------------------------------------------------------------------------------
# 1. Preflight
# ---------------------------------------------------------------------------------------------
log "Preflight checks"
resolve_repo
[[ "$(uname -m)" == "arm64" ]] || die "this script supports Apple silicon (arm64) only"
ok "macOS arm64; repository $REPO"
[[ "$(gh api "repos/${REPO}" --jq .permissions.admin)" == "true" ]] ||
  die "you need admin rights on $REPO to register runners and change settings"
for bin in kubectl helm curl shasum tar realpath; do
  command -v "$bin" >/dev/null 2>&1 || die "'$bin' not found in PATH (Rancher Desktop installs kubectl/helm in ~/.rd/bin)"
done
kubectl --context "$KUBE_CONTEXT" get nodes >/dev/null 2>&1 ||
  die "kubectl context '$KUBE_CONTEXT' is not reachable; start Rancher Desktop with Kubernetes enabled"
ok "kubectl context $KUBE_CONTEXT reachable"
log "sudo is needed to create the runner user, /opt/erms-runner and the LaunchDaemon"
sudo -v

# ---------------------------------------------------------------------------------------------
# 2. Unprivileged macOS user (hidden role account: no password, no shell, not an admin)
# ---------------------------------------------------------------------------------------------
if user_exists; then
  ok "user $RUNNER_USER exists (uid $(id -u "$RUNNER_USER"))"
else
  log "Creating hidden user $RUNNER_USER"
  RUNNER_ID=""
  for candidate in $(seq 499 -1 450); do
    if ! dscl . -search /Users UniqueID "$candidate" | grep -q . &&
      ! dscl . -search /Groups PrimaryGroupID "$candidate" | grep -q .; then
      RUNNER_ID="$candidate"
      break
    fi
  done
  [[ -n "$RUNNER_ID" ]] || die "no free uid/gid between 450 and 499"
  sudo dscl . -create "/Groups/$RUNNER_USER"
  sudo dscl . -create "/Groups/$RUNNER_USER" PrimaryGroupID "$RUNNER_ID"
  sudo dscl . -create "/Groups/$RUNNER_USER" RealName "ERMS Actions runner"
  sudo dscl . -create "/Groups/$RUNNER_USER" Password '*'
  sudo dscl . -create "/Users/$RUNNER_USER"
  sudo dscl . -create "/Users/$RUNNER_USER" UniqueID "$RUNNER_ID"
  sudo dscl . -create "/Users/$RUNNER_USER" PrimaryGroupID "$RUNNER_ID"
  sudo dscl . -create "/Users/$RUNNER_USER" RealName "ERMS Actions runner"
  sudo dscl . -create "/Users/$RUNNER_USER" NFSHomeDirectory "$RUNNER_HOME"
  sudo dscl . -create "/Users/$RUNNER_USER" UserShell /usr/bin/false
  sudo dscl . -create "/Users/$RUNNER_USER" Password '*'
  sudo dscl . -create "/Users/$RUNNER_USER" IsHidden 1
  ok "user $RUNNER_USER created (uid/gid $RUNNER_ID)"
fi
if dseditgroup -o checkmember -m "$RUNNER_USER" admin >/dev/null 2>&1; then
  die "$RUNNER_USER is a member of the admin group; remove it (sudo dseditgroup -o edit -d $RUNNER_USER -t user admin)"
fi

sudo install -d -o root -g wheel -m 755 "$RUNNER_BASE" "$TOOLS_DIR"
sudo install -d -o "$RUNNER_USER" -g "$RUNNER_USER" -m 700 "$RUNNER_HOME" "$RUNNER_HOME/.kube" "$RUNNER_DIR"
sudo install -d -o "$RUNNER_USER" -g "$RUNNER_USER" -m 750 "$LOG_DIR"
ok "directories under $RUNNER_BASE and $LOG_DIR"

# ---------------------------------------------------------------------------------------------
# 3. Tools: kubectl (matching the cluster, SHA-256 verified) and helm (from Rancher Desktop)
# ---------------------------------------------------------------------------------------------
log "Installing kubectl and helm into $TOOLS_DIR"
SERVER_VERSION="$(kubectl --context "$KUBE_CONTEXT" version -o json | grep -A8 '"serverVersion"' |
  grep -o '"gitVersion": *"v[0-9]*\.[0-9]*\.[0-9]*' | grep -o 'v[0-9.]*' | head -n1)"
[[ -n "$SERVER_VERSION" ]] || die "cannot determine the Kubernetes server version"
if [[ -x "$TOOLS_DIR/kubectl" ]] && "$TOOLS_DIR/kubectl" version --client 2>/dev/null | grep -q "$SERVER_VERSION\$"; then
  ok "kubectl $SERVER_VERSION already installed"
else
  TMP_DIR="$(mktemp -d)"
  KUBECTL_URL="https://dl.k8s.io/release/${SERVER_VERSION}/bin/darwin/arm64/kubectl"
  curl -fsSL --retry 3 -o "$TMP_DIR/kubectl" "$KUBECTL_URL"
  EXPECTED_SHA="$(curl -fsSL --retry 3 "${KUBECTL_URL}.sha256" | awk '{print $1}')"
  ACTUAL_SHA="$(shasum -a 256 "$TMP_DIR/kubectl" | awk '{print $1}')"
  [[ -n "$EXPECTED_SHA" && "$ACTUAL_SHA" == "$EXPECTED_SHA" ]] || die "kubectl SHA-256 mismatch ($ACTUAL_SHA != $EXPECTED_SHA)"
  sudo install -o root -g wheel -m 755 "$TMP_DIR/kubectl" "$TOOLS_DIR/kubectl"
  rm -rf "$TMP_DIR"
  ok "kubectl $SERVER_VERSION (SHA-256 verified)"
fi
HELM_SRC="$(realpath "$(command -v helm)")"
sudo install -o root -g wheel -m 755 "$HELM_SRC" "$TOOLS_DIR/helm"
ok "helm $("$TOOLS_DIR/helm" version --short) (copied from $HELM_SRC)"

# ---------------------------------------------------------------------------------------------
# 4. Namespace-scoped Kubernetes identity
# ---------------------------------------------------------------------------------------------
log "Applying namespace $NAMESPACE and ServiceAccount $SERVICE_ACCOUNT (deploy/runner/deployer-rbac.yaml)"
kubectl --context "$KUBE_CONTEXT" apply -f "$ROOT/deploy/runner/deployer-rbac.yaml" >/dev/null
SA_TOKEN=""
for _ in $(seq 1 30); do
  SA_TOKEN="$(kubectl --context "$KUBE_CONTEXT" -n "$NAMESPACE" get secret "$SERVICE_ACCOUNT-token" -o jsonpath='{.data.token}' 2>/dev/null || true)"
  [[ -n "$SA_TOKEN" ]] && break
  sleep 1
done
[[ -n "$SA_TOKEN" ]] || die "the token for $SERVICE_ACCOUNT was not issued"
API_SERVER="$(kubectl config view --raw --minify --context "$KUBE_CONTEXT" -o jsonpath='{.clusters[0].cluster.server}')"
CA_DATA="$(kubectl config view --raw --minify --context "$KUBE_CONTEXT" -o jsonpath='{.clusters[0].cluster.certificate-authority-data}')"
[[ -n "$API_SERVER" && -n "$CA_DATA" ]] || die "cannot read server/CA of context $KUBE_CONTEXT"
# The context keeps the name "rancher-desktop" so cd.yml works unchanged.
umask 077
TMP_KUBECONFIG="$(mktemp)"
cat >"$TMP_KUBECONFIG" <<EOF
apiVersion: v1
kind: Config
clusters:
  - name: $KUBE_CONTEXT
    cluster:
      server: $API_SERVER
      certificate-authority-data: $CA_DATA
users:
  - name: $SERVICE_ACCOUNT
    user:
      token: $(printf '%s' "$SA_TOKEN" | base64 -d)
contexts:
  - name: $KUBE_CONTEXT
    context: {cluster: $KUBE_CONTEXT, user: $SERVICE_ACCOUNT, namespace: $NAMESPACE}
current-context: $KUBE_CONTEXT
EOF
unset SA_TOKEN
sudo install -o "$RUNNER_USER" -g "$RUNNER_USER" -m 600 "$TMP_KUBECONFIG" "$RUNNER_HOME/.kube/config"
rm -f "$TMP_KUBECONFIG"
umask 022
can_i() { as_runner "$TOOLS_DIR/kubectl" --kubeconfig "$RUNNER_HOME/.kube/config" auth can-i "$@" 2>/dev/null || true; }
[[ "$(can_i create deployments -n "$NAMESPACE")" == "yes" ]] || die "the runner kubeconfig cannot deploy to $NAMESPACE"
[[ "$(can_i get nodes)" == "no" ]] || die "the runner kubeconfig unexpectedly has cluster-wide access"
[[ "$(can_i get pods -n kube-system)" == "no" ]] || die "the runner kubeconfig unexpectedly reaches kube-system"
ok "runner kubeconfig: namespace $NAMESPACE only (verified with 'kubectl auth can-i')"

# ---------------------------------------------------------------------------------------------
# 5. Download the latest runner (SHA-256 verified)
# ---------------------------------------------------------------------------------------------
if sudo test -x "$RUNNER_DIR/config.sh" && sudo test -x "$RUNNER_DIR/bin/Runner.Listener"; then
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
  TMP_DIR="$(mktemp -d)"
  curl -fsSL --retry 3 -o "$TMP_DIR/$ASSET" "$URL"
  ACTUAL_SHA="$(shasum -a 256 "$TMP_DIR/$ASSET" | awk '{print $1}')"
  [[ "$ACTUAL_SHA" == "$EXPECTED_SHA" ]] || die "SHA-256 mismatch for $ASSET: expected $EXPECTED_SHA, got $ACTUAL_SHA"
  ok "SHA-256 verified ($ACTUAL_SHA)"
  as_runner tar -xzf - -C "$RUNNER_DIR" <"$TMP_DIR/$ASSET"
  rm -rf "$TMP_DIR"
  ok "runner v${VERSION} extracted to $RUNNER_DIR (owned by $RUNNER_USER)"
fi

# ---------------------------------------------------------------------------------------------
# 6. Register (only if not already registered)
# ---------------------------------------------------------------------------------------------
if sudo test -f "$RUNNER_DIR/.runner" && runner_registered; then
  ok "runner '$RUNNER_NAME' already registered with $REPO"
else
  if [[ -f "$PLIST" ]]; then
    sudo launchctl bootout "system/$LABEL" 2>/dev/null || true
  fi
  if sudo test -f "$RUNNER_DIR/.runner"; then
    warn "stale local registration found (runner missing on GitHub); resetting local config"
    sudo rm -f "$RUNNER_DIR/.runner" "$RUNNER_DIR/.credentials" "$RUNNER_DIR/.credentials_rsaparams"
  fi
  log "Registering runner '$RUNNER_NAME' (labels: self-hosted, macOS, ARM64, $RUNNER_LABELS)"
  REG_TOKEN="$(gh api -X POST "repos/${REPO}/actions/runners/registration-token" --jq .token)"
  # shellcheck disable=SC2016 # positional parameters of the inner bash
  as_runner /bin/bash -c 'cd "$1" && ./config.sh --unattended --replace --url "$2" --token "$3" --name "$4" --labels "$5" --work _work' \
    _ "$RUNNER_DIR" "https://github.com/${REPO}" "$REG_TOKEN" "$RUNNER_NAME" "$RUNNER_LABELS"
  unset REG_TOKEN
  ok "registered"
fi

# ---------------------------------------------------------------------------------------------
# 7. Runner environment and LaunchDaemon (runs as $RUNNER_USER)
# ---------------------------------------------------------------------------------------------
printf '%s\n' "$TOOLS_DIR:/usr/bin:/bin:/usr/sbin:/sbin" | as_runner tee "$RUNNER_DIR/.path" >/dev/null
as_runner tee "$RUNNER_DIR/.env" >/dev/null <<EOF
LANG=en_US.UTF-8
HOME=$RUNNER_HOME
KUBECONFIG=$RUNNER_HOME/.kube/config
EOF
as_runner cp "$RUNNER_DIR/bin/runsvc.sh" "$RUNNER_DIR/runsvc.sh"
as_runner chmod 700 "$RUNNER_DIR/runsvc.sh"
ok "wrote .path, .env and runsvc.sh"

TMP_PLIST="$(mktemp)"
cat >"$TMP_PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
  <dict>
    <key>Label</key>
    <string>$LABEL</string>
    <key>ProgramArguments</key>
    <array>
      <string>$RUNNER_DIR/runsvc.sh</string>
    </array>
    <key>UserName</key>
    <string>$RUNNER_USER</string>
    <key>GroupName</key>
    <string>$RUNNER_USER</string>
    <key>WorkingDirectory</key>
    <string>$RUNNER_DIR</string>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <dict>
      <key>SuccessfulExit</key>
      <false/>
    </dict>
    <key>Umask</key>
    <integer>63</integer>
    <key>StandardOutPath</key>
    <string>$LOG_DIR/stdout.log</string>
    <key>StandardErrorPath</key>
    <string>$LOG_DIR/stderr.log</string>
    <key>EnvironmentVariables</key>
    <dict>
      <key>ACTIONS_RUNNER_SVC</key>
      <string>1</string>
      <key>HOME</key>
      <string>$RUNNER_HOME</string>
    </dict>
  </dict>
</plist>
EOF
plutil -lint "$TMP_PLIST" >/dev/null || die "generated plist is invalid"
if [[ -f "$PLIST" ]]; then
  sudo launchctl bootout "system/$LABEL" 2>/dev/null || true
fi
sudo install -o root -g wheel -m 644 "$TMP_PLIST" "$PLIST"
rm -f "$TMP_PLIST"
start_service

# ---------------------------------------------------------------------------------------------
# 8. Public-repository hardening
# ---------------------------------------------------------------------------------------------
if [[ "$HARDEN" == "1" ]]; then
  log "Applying repository hardening to $REPO"

  # (a) Environment "local": the repository owner must approve every deployment; main only.
  OWNER_LOGIN="${REPO%%/*}"
  OWNER_ID="$(gh api "users/${OWNER_LOGIN}" --jq .id)"
  if gh api -X PUT "repos/${REPO}/environments/${ENVIRONMENT}" --input - >/dev/null <<JSON
{"wait_timer": 0, "prevent_self_review": false,
 "reviewers": [{"type": "User", "id": ${OWNER_ID}}],
 "deployment_branch_policy": {"protected_branches": false, "custom_branch_policies": true}}
JSON
  then
    ok "environment '$ENVIRONMENT': required reviewer $OWNER_LOGIN"
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
  (workflow_run from a successful CI push on main of this repository, or workflow_dispatch),
  and every deployment waits for your approval in the '$ENVIRONMENT' environment.
EOF
fi

echo
log "Runner ready"
gh api "repos/${REPO}/actions/runners" --paginate \
  --jq ".runners[] | select(.name == \"${RUNNER_NAME}\") | \"  \\(.name): \\(.status) [\\([.labels[].name] | join(\", \"))]\"" || true
echo "  Status:     make runner-status"
echo "  Stop/start: make runner-stop / make runner-start"
echo "  Logs:       sudo tail -n 50 $LOG_DIR/stderr.log; sudo ls $RUNNER_DIR/_diag"
