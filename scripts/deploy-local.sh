#!/usr/bin/env bash
# Build the ERMS images locally and deploy them to Rancher Desktop (no CI involved).
# docs/ARCHITECTURE.md §5.4, exposed as `make deploy-local`.
#
# Usage: scripts/deploy-local.sh [options]
#   --tag TAG        Image tag. Default: local-<short git sha> (+ -dirty-<epoch> with uncommitted changes)
#   --no-build       Do not build; the images must already be in the local docker store
#                    (e.g. pulled from GHCR: docker pull ghcr.io/manojis/erms-backend:<sha>)
#   --skip-test      Skip `helm test`
#   --skip-smoke     Skip scripts/smoke-test.sh
#   --context NAME   Kube context (default rancher-desktop)
#   --namespace NS   Namespace (default erms)
#   --timeout DUR    Helm wait timeout (default 10m)
#
# Environment: IMAGE_REGISTRY (default ghcr.io/manojis), BASE_URL (default http://erms.localhost)
#
# Rancher Desktop's moby engine shares its image store with k3s, so locally built images are
# used directly (imagePullPolicy IfNotPresent, no registry push needed).
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHART="$ROOT/deploy/helm/erms"

KUBE_CONTEXT="rancher-desktop"
NAMESPACE="erms"
RELEASE="erms"
TIMEOUT="10m"
TAG=""
BUILD=1
RUN_TEST=1
RUN_SMOKE=1
IMAGE_REGISTRY="${IMAGE_REGISTRY:-ghcr.io/manojis}"
BASE_URL="${BASE_URL:-http://erms.localhost}"

usage() { awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag) TAG="$2"; shift 2 ;;
    --no-build) BUILD=0; shift ;;
    --skip-test) RUN_TEST=0; shift ;;
    --skip-smoke) RUN_SMOKE=0; shift ;;
    --context) KUBE_CONTEXT="$2"; shift 2 ;;
    --namespace | -n) NAMESPACE="$2"; shift 2 ;;
    --timeout) TIMEOUT="$2"; shift 2 ;;
    -h | --help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

log() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
die() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

# Rancher Desktop puts docker/kubectl/helm in ~/.rd/bin.
if [[ -d "$HOME/.rd/bin" ]]; then
  PATH="$HOME/.rd/bin:$PATH"
fi

# ---- preflight -------------------------------------------------------------------------------
for bin in docker kubectl helm git; do
  command -v "$bin" >/dev/null 2>&1 || die "'$bin' not found in PATH"
done
docker info >/dev/null 2>&1 || die "docker is not reachable (is Rancher Desktop running?)"
kubectl --context "$KUBE_CONTEXT" get nodes >/dev/null 2>&1 ||
  die "kube context '$KUBE_CONTEXT' is not reachable (is Kubernetes enabled in Rancher Desktop?)"

# Helm 4 renamed --atomic to --rollback-on-failure; keep Helm 3 working too.
if helm version --short 2>/dev/null | grep -q '^v3\.'; then
  ROLLBACK_FLAG="--atomic"
else
  ROLLBACK_FLAG="--rollback-on-failure"
fi

if [[ -z "$TAG" ]]; then
  TAG="local-$(git -C "$ROOT" rev-parse --short HEAD)"
  if [[ -n "$(git -C "$ROOT" status --porcelain -- backend frontend)" ]]; then
    # A unique tag makes the pods roll even though the commit did not change.
    TAG="${TAG}-dirty-$(date +%s)"
  fi
fi
BACKEND_IMAGE="${IMAGE_REGISTRY}/erms-backend:${TAG}"
FRONTEND_IMAGE="${IMAGE_REGISTRY}/erms-frontend:${TAG}"

# ---- build -----------------------------------------------------------------------------------
if [[ "$BUILD" == "1" ]]; then
  log "Building $BACKEND_IMAGE"
  docker build -t "$BACKEND_IMAGE" "$ROOT/backend"
  log "Building $FRONTEND_IMAGE"
  docker build -t "$FRONTEND_IMAGE" "$ROOT/frontend"
else
  for img in "$BACKEND_IMAGE" "$FRONTEND_IMAGE"; do
    docker image inspect "$img" >/dev/null 2>&1 || die "image $img is not in the local docker store (docker pull it first)"
  done
fi

# ---- deploy ----------------------------------------------------------------------------------
on_failure() {
  echo
  log "Deployment failed; collecting diagnostics"
  "$ROOT/scripts/diagnostics.sh" --context "$KUBE_CONTEXT" --namespace "$NAMESPACE" --release "$RELEASE" || true
}
trap on_failure ERR

log "Ensuring namespace $NAMESPACE (Pod Security: restricted)"
kubectl --context "$KUBE_CONTEXT" get namespace "$NAMESPACE" >/dev/null 2>&1 ||
  kubectl --context "$KUBE_CONTEXT" create namespace "$NAMESPACE" >/dev/null
kubectl --context "$KUBE_CONTEXT" label namespace "$NAMESPACE" --overwrite \
  pod-security.kubernetes.io/enforce=restricted \
  pod-security.kubernetes.io/enforce-version=latest >/dev/null

log "helm upgrade --install $RELEASE (tag $TAG)"
helm upgrade --install "$RELEASE" "$CHART" \
  --kube-context "$KUBE_CONTEXT" -n "$NAMESPACE" \
  -f "$CHART/values-local.yaml" \
  --set imageRegistry="$IMAGE_REGISTRY" \
  --set backend.image.tag="$TAG" \
  --set frontend.image.tag="$TAG" \
  "$ROLLBACK_FLAG" --wait --timeout "$TIMEOUT"

if [[ "$RUN_TEST" == "1" ]]; then
  log "helm test $RELEASE"
  helm test "$RELEASE" -n "$NAMESPACE" --kube-context "$KUBE_CONTEXT" --logs --timeout 5m
fi

if [[ "$RUN_SMOKE" == "1" ]]; then
  log "Smoke test $BASE_URL"
  "$ROOT/scripts/smoke-test.sh" "$BASE_URL"
fi

trap - ERR
log "Deployed $RELEASE with tag $TAG -> $BASE_URL"
