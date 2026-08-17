#!/usr/bin/env bash
#
# Restore a SocietyCare database from a dump produced by backup-db.sh.
#
#   ./restore-db.sh beta /opt/societycare/backups/prod/societycare-prod-2026-08-18-Tue-030000.sql.gz
#
# THIS REPLACES THE TARGET DATABASE. Everything currently in it is discarded.
#
# Restoring production's dump into beta is the recommended routine: it proves
# the backup is genuinely restorable, and it gives beta realistic data. Note
# that it also copies real resident names and phone numbers into the test
# environment - fine for a small society, but make it a deliberate choice.
#
# An untested backup is a guess. Run this on beta regularly so that the day you
# need it on production is not the first time it has ever been tried.
set -euo pipefail

ENV_NAME="${1:?Usage: $0 <beta|prod> <dump.sql.gz>}"
DUMP="${2:?Usage: $0 <beta|prod> <dump.sql.gz>}"
BASE_DIR="${SOCIETYCARE_DIR:-/opt/societycare}"
COMPOSE_FILE="${BASE_DIR}/docker-compose.prod.yml"
ENV_FILE="${BASE_DIR}/${ENV_NAME}/.env"
# Overridable for the same reason as in backup-db.sh: so a restore can be
# rehearsed against a stack that is not named societycare-<env>.
PROJECT="${COMPOSE_PROJECT_NAME:-societycare-${ENV_NAME}}"

log() { printf '%s  %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*"; }
die() { log "ERROR: $*" >&2; exit 1; }

[[ -f "$COMPOSE_FILE" ]] || die "no compose file at $COMPOSE_FILE"
[[ -f "$ENV_FILE" ]]     || die "no env file at $ENV_FILE"
[[ -f "$DUMP" ]]         || die "no dump file at $DUMP"
gzip -t "$DUMP"          || die "$DUMP fails its gzip check - do not restore from it"

compose() {
  docker compose -p "$PROJECT" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

DB_USER="$(grep -E '^DB_USER=' "$ENV_FILE" | cut -d= -f2- || true)"
DB_USER="${DB_USER:-societycare}"

# Restoring into production is occasionally the right thing to do and should not
# be impossible - but it should require you to say so out loud.
if [[ "$ENV_NAME" == "prod" && "${I_MEAN_IT:-}" != "yes" ]]; then
  die "refusing to overwrite production. Re-run with I_MEAN_IT=yes if that is really what you want."
fi

log "target      : ${PROJECT}"
log "dump        : ${DUMP} ($(du -h "$DUMP" | cut -f1))"
log "This will DISCARD the current contents of the ${ENV_NAME} database."
read -r -p "Type the environment name to confirm: " answer
[[ "$answer" == "$ENV_NAME" ]] || die "confirmation did not match - nothing was changed"

compose ps --status running --services 2>/dev/null | grep -qx postgres \
  || die "the postgres container for '$PROJECT' is not running"

# The api holds open connections and would fight the restore for locks, so it
# goes down first. The dump was taken with --clean --if-exists, so it drops and
# recreates each object itself.
log "stopping api"
compose stop api >/dev/null

log "restoring"
gunzip -c "$DUMP" | compose exec -T postgres psql -U "$DB_USER" -d societycare -v ON_ERROR_STOP=1 --quiet

log "starting api"
compose start api >/dev/null

log "waiting for the api to report healthy"
for _ in $(seq 1 30); do
  if compose ps --format '{{.Service}} {{.Status}}' | grep -q '^api .*healthy'; then
    log "restore complete and the api is healthy"
    exit 0
  fi
  sleep 2
done

die "the api did not become healthy after the restore - check: compose logs api"
