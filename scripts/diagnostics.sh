#!/usr/bin/env bash
# Dump Kubernetes diagnostics for the ERMS release (used by cd.yml and deploy-local.sh on failure).
#
# Usage: scripts/diagnostics.sh [--context rancher-desktop] [--namespace erms] [--release erms]
# Never fails: every command is best-effort so the original error stays visible.
set -uo pipefail

KUBE_CONTEXT="${KUBE_CONTEXT:-rancher-desktop}"
NAMESPACE="${NAMESPACE:-erms}"
RELEASE="${RELEASE:-erms}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --context) KUBE_CONTEXT="$2"; shift 2 ;;
    --namespace | -n) NAMESPACE="$2"; shift 2 ;;
    --release) RELEASE="$2"; shift 2 ;;
    -h | --help) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

k() { kubectl --context "$KUBE_CONTEXT" -n "$NAMESPACE" "$@"; }
section() { printf '\n===== %s =====\n' "$*"; }

section "helm status / history"
helm status "$RELEASE" -n "$NAMESPACE" --kube-context "$KUBE_CONTEXT" || true
helm history "$RELEASE" -n "$NAMESPACE" --kube-context "$KUBE_CONTEXT" --max 5 || true

section "resources"
k get pods,deploy,sts,svc,ingress,pvc,networkpolicy -o wide || true

section "events (most recent last)"
k get events --sort-by=.lastTimestamp | tail -n 60 || true

section "describe non-ready pods"
for pod in $(k get pods -o jsonpath='{range .items[*]}{.metadata.name}{" "}{end}' 2>/dev/null); do
  ready="$(k get pod "$pod" -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null)"
  phase="$(k get pod "$pod" -o jsonpath='{.status.phase}' 2>/dev/null)"
  if [[ "$ready" != "True" && "$phase" != "Succeeded" ]]; then
    section "describe pod/$pod"
    k describe pod "$pod" || true
  fi
done

for target in deploy/"$RELEASE"-backend deploy/"$RELEASE"-frontend sts/"$RELEASE"-mysql; do
  section "logs $target (all containers, last 200 lines)"
  k logs "$target" --all-containers --tail=200 --prefix || true
  section "previous logs $target"
  k logs "$target" --all-containers --tail=100 --prefix --previous 2>/dev/null || echo "(no previous container)"
done

section "helm test pod logs"
k logs "$RELEASE"-test-health --tail=100 2>/dev/null || echo "(no test pod)"

exit 0
