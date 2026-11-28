#!/usr/bin/env bash
# In-place MySQL upgrade test: seed a data volume on the first version with the real Flyway
# migrations + demo seed, then restart it on each following version on the same volume and verify
# the server upgraded, every table passes CHECK, and the data is unchanged.
#
#   scripts/mysql-upgrade-test.sh <from-tag> [<via-tag> ...] <to-tag>
#   e.g. 8.4.13 9.7.4.1 26.10.0.1
#
# MySQL only upgrades in place within a release lineage or from the immediately preceding LTS
# (8.4 LTS -> 9.7 LTS -> 26.x Innovation); a skipped LTS aborts at startup (docs/RUNBOOK.md).
# Used by the CI job "mysql-upgrade" (docs/CICD.md). Needs only docker.
set -euo pipefail

if [ $# -lt 2 ]; then
  echo "usage: $0 <from-tag> [<via-tag> ...] <to-tag>" >&2
  exit 2
fi
FROM_TAG=$1
shift
ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
DB_DIR="$ROOT_DIR/backend/src/main/resources/db"
NAME="erms-mysql-upgrade-$$"
VOLUME="$NAME-data"
ROOT_PW="upgrade-test-only"

log() { printf '\n==> %s\n' "$*"; }
cleanup() {
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  docker volume rm "$VOLUME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

sql() { docker exec -i -e MYSQL_PWD="$ROOT_PW" "$NAME" mysql -uroot --batch --skip-column-names "$@"; }

start() {
  docker run -d --name "$NAME" -e MYSQL_ROOT_PASSWORD="$ROOT_PW" -e MYSQL_DATABASE=erms \
    -v "$VOLUME":/var/lib/mysql "mysql:$1" >/dev/null
  for _ in $(seq 1 90); do
    # The entrypoint runs a temporary server on first init; wait for the real one (TCP).
    if docker exec -e MYSQL_PWD="$ROOT_PW" "$NAME" mysql -uroot -h127.0.0.1 -e 'SELECT 1' >/dev/null 2>&1; then
      return 0
    fi
    if [ "$(docker inspect -f '{{.State.Running}}' "$NAME")" != "true" ]; then
      docker logs "$NAME" >&2
      echo "::error::mysql:$1 exited during startup" >&2
      return 1
    fi
    sleep 2
  done
  docker logs "$NAME" >&2
  echo "::error::mysql:$1 did not become ready" >&2
  return 1
}

snapshot() {
  local tables
  tables=$(sql -e "SELECT table_name FROM information_schema.tables WHERE table_schema='erms' AND table_type='BASE TABLE' ORDER BY table_name")
  for t in $tables; do
    printf '%s rows=%s checksum=%s\n' "$t" \
      "$(sql erms -e "SELECT COUNT(*) FROM \`$t\`")" \
      "$(sql erms -e "CHECKSUM TABLE \`$t\`" | awk '{print $2}')"
  done
}

log "Seeding mysql:$FROM_TAG (V* migrations + demo seed)"
start "$FROM_TAG"
for f in "$DB_DIR"/migration/V*.sql "$DB_DIR"/seed/R__*.sql; do
  echo "  applying ${f#"$ROOT_DIR"/}"
  sql erms <"$f"
done
before=$(snapshot)
echo "$before"
[ -n "$before" ] || { echo "::error::no tables were created" >&2; exit 1; }

current=$FROM_TAG
for TO_TAG in "$@"; do
  log "Stopping mysql:$current cleanly"
  docker stop -t 60 "$NAME" >/dev/null
  docker rm "$NAME" >/dev/null

  log "Starting mysql:$TO_TAG on the same volume"
  start "$TO_TAG"
  version=$(sql -e 'SELECT VERSION()')
  echo "server version: $version"
  case "$TO_TAG" in
    "$version" | "$version".*) ;;
    *) echo "::error::expected server $TO_TAG, got $version" >&2; exit 1 ;;
  esac
  if docker logs "$NAME" 2>&1 | grep -E '\[ERROR\]'; then
    echo "::error::mysql:$TO_TAG logged errors while upgrading" >&2
    exit 1
  fi
  docker logs "$NAME" 2>&1 | grep -iE 'upgrad' || true

  log "Checking tables on $TO_TAG"
  # CHECK TABLE in SQL: mysqlcheck is no longer shipped in the 9.x+ images.
  tables=$(sql -e "SELECT GROUP_CONCAT(CONCAT('\`', table_schema, '\`.\`', table_name, '\`')) FROM information_schema.tables WHERE table_schema='erms' AND table_type='BASE TABLE'")
  check=$(sql -e "CHECK TABLE $tables")
  if echo "$check" | awk -F'\t' '$3 == "status" && $4 != "OK" {bad=1} $3 == "error" {bad=1} END {exit !bad}'; then
    echo "$check"
    echo "::error::CHECK TABLE reported problems" >&2
    exit 1
  fi
  echo "all tables OK"

  log "Comparing data on $TO_TAG"
  after=$(snapshot)
  if [ "$before" != "$after" ]; then
    diff <(echo "$before") <(echo "$after") || true
    echo "::error::data changed during the upgrade to $TO_TAG" >&2
    exit 1
  fi
  echo "data identical"
  current=$TO_TAG
done

log "Re-applying the repeatable demo seed (app write path on the upgraded server)"
sql erms <"$DB_DIR/seed/R__demo_seed.sql"

log "OK: mysql $FROM_TAG -> $(printf '%s -> ' "$@" | sed 's/ -> $//') upgraded in place with identical data"
