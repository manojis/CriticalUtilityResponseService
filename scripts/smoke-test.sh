#!/usr/bin/env bash
# ERMS deployment smoke test (docs/ARCHITECTURE.md §5.4).
#
# Usage: scripts/smoke-test.sh [BASE_URL]
#   BASE_URL defaults to http://erms.localhost (Rancher Desktop ingress).
#   Use http://localhost:3000 for the docker-compose stack.
#
# Environment:
#   SMOKE_USER / SMOKE_PASSWORD  demo credentials (default alice / password; needs the "demo" profile)
#   SMOKE_TIMEOUT                seconds to keep retrying until the app answers (default 60)
#
# Checks:
#   1. GET /                     -> 200, HTML containing <div id="root">
#   2. POST /api/v1/auth/login   -> 200 + accessToken
#   3. GET /api/v1/me            -> 200, username == SMOKE_USER
#   4. GET /api/v1/esfs          -> 200, 15 items
#   5. GET /api/v1/me (no token) -> 401
#   6. Actuator is not reachable through the public entry point
set -euo pipefail

BASE_URL="${1:-${BASE_URL:-http://erms.localhost}}"
BASE_URL="${BASE_URL%/}"
SMOKE_USER="${SMOKE_USER:-alice}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-password}"
SMOKE_TIMEOUT="${SMOKE_TIMEOUT:-60}"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

pass() { printf '  \033[32mPASS\033[0m %s\n' "$*"; }
fail() {
  printf '  \033[31mFAIL\033[0m %s\n' "$*" >&2
  if [[ -s "$WORK/body" ]]; then
    echo "  --- last response body (truncated) ---" >&2
    head -c 600 "$WORK/body" >&2 || true
    echo >&2
  fi
  exit 1
}

command -v curl >/dev/null 2>&1 || fail "curl is required"

# json_get <file> <expression>   expression: "accessToken", "username" or "length"
json_get() {
  local file="$1" expr="$2"
  if command -v jq >/dev/null 2>&1; then
    case "$expr" in
      length) jq -r 'length' "$file" ;;
      *) jq -r --arg k "$expr" '.[$k] // empty' "$file" ;;
    esac
  elif command -v python3 >/dev/null 2>&1; then
    python3 -I -c '
import json, sys
data = json.load(open(sys.argv[1]))
key = sys.argv[2]
if key == "length":
    print(len(data))
else:
    value = data.get(key) if isinstance(data, dict) else None
    print("" if value is None else value)
' "$file" "$expr"
  else
    fail "jq or python3 is required to parse JSON"
  fi
}

# request <method> <path> [curl args...]  -> sets HTTP_CODE, body in $WORK/body
request() {
  local method="$1" path="$2"
  shift 2
  HTTP_CODE="$(curl -sS -o "$WORK/body" -w '%{http_code}' --max-time 10 \
    -X "$method" -H 'Accept: application/json, text/html' "$@" "${BASE_URL}${path}" 2>"$WORK/curl.err" || true)"
  [[ -n "$HTTP_CODE" ]] || HTTP_CODE="000"
}

echo "ERMS smoke test against ${BASE_URL}"

# 1. Wait for the SPA (exponential backoff, capped at 8 s per wait, SMOKE_TIMEOUT total).
deadline=$((SECONDS + SMOKE_TIMEOUT))
delay=1
while :; do
  request GET /
  if [[ "$HTTP_CODE" == "200" ]] && grep -q '<div id="root">' "$WORK/body"; then
    break
  fi
  if ((SECONDS >= deadline)); then
    fail "GET / -> HTTP ${HTTP_CODE} (expected 200 with <div id=\"root\">) after ${SMOKE_TIMEOUT}s $(cat "$WORK/curl.err" 2>/dev/null)"
  fi
  echo "  waiting for ${BASE_URL}/ (HTTP ${HTTP_CODE}), retry in ${delay}s"
  sleep "$delay"
  delay=$((delay * 2 > 8 ? 8 : delay * 2))
done
pass "GET / -> 200, SPA shell served"

# 2. Login (retried as well: the backend may still be warming up behind the ingress).
delay=1
while :; do
  request POST /api/v1/auth/login -H 'Content-Type: application/json' \
    --data "{\"username\":\"${SMOKE_USER}\",\"password\":\"${SMOKE_PASSWORD}\"}"
  if [[ "$HTTP_CODE" == "200" ]]; then
    break
  fi
  if [[ "$HTTP_CODE" == "401" || "$HTTP_CODE" == "400" ]] || ((SECONDS >= deadline)); then
    fail "POST /api/v1/auth/login -> HTTP ${HTTP_CODE} (expected 200; is the 'demo' profile active?)"
  fi
  echo "  waiting for the API (HTTP ${HTTP_CODE}), retry in ${delay}s"
  sleep "$delay"
  delay=$((delay * 2 > 8 ? 8 : delay * 2))
done
TOKEN="$(json_get "$WORK/body" accessToken)"
[[ -n "$TOKEN" ]] || fail "login response has no accessToken"
pass "POST /api/v1/auth/login as ${SMOKE_USER} -> 200 + accessToken"

# 3. /me with the token.
request GET /api/v1/me -H "Authorization: Bearer ${TOKEN}"
[[ "$HTTP_CODE" == "200" ]] || fail "GET /api/v1/me -> HTTP ${HTTP_CODE} (expected 200)"
ME="$(json_get "$WORK/body" username)"
[[ "$ME" == "$SMOKE_USER" ]] || fail "GET /api/v1/me -> username '${ME}' (expected '${SMOKE_USER}')"
pass "GET /api/v1/me -> 200, username=${ME}"

# 4. Reference data.
request GET /api/v1/esfs -H "Authorization: Bearer ${TOKEN}"
[[ "$HTTP_CODE" == "200" ]] || fail "GET /api/v1/esfs -> HTTP ${HTTP_CODE} (expected 200)"
COUNT="$(json_get "$WORK/body" length)"
[[ "$COUNT" == "15" ]] || fail "GET /api/v1/esfs -> ${COUNT} items (expected 15)"
pass "GET /api/v1/esfs -> 200, 15 ESFs"

# 5. Unauthenticated call.
request GET /api/v1/me
[[ "$HTTP_CODE" == "401" ]] || fail "GET /api/v1/me without token -> HTTP ${HTTP_CODE} (expected 401)"
pass "GET /api/v1/me without token -> 401"

# 6. The management port must not leak through the public entry point.
for path in /actuator/health /api/actuator/health; do
  request GET "$path"
  if [[ "$HTTP_CODE" == "200" ]] && grep -q '"status"' "$WORK/body" && grep -q '"UP"' "$WORK/body"; then
    fail "GET ${path} exposes actuator health publicly"
  fi
done
pass "actuator health is not exposed publicly"

echo "Smoke test passed."
