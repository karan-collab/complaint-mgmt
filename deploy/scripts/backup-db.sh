#!/usr/bin/env bash
#
# Nightly database backup for one SocietyCare environment.
#
#   ./backup-db.sh prod
#   ./backup-db.sh beta
#
# Writes a compressed pg_dump to /opt/societycare/backups/<env>/ and, if
# RCLONE_REMOTE is set, copies it off the server. A backup that only exists on
# the machine it came from is not a backup: the accident that loses the database
# usually loses the disk too.
#
# Retention: every dump for 7 days, plus Sunday dumps for 4 weeks.
#
# The dump is written to a temporary name and moved into place only after it has
# been verified. A half-written file must never sit in the backup directory
# looking like something you could restore from.
set -euo pipefail

ENV_NAME="${1:?Usage: $0 <beta|prod>}"
BASE_DIR="${SOCIETYCARE_DIR:-/opt/societycare}"
COMPOSE_FILE="${BASE_DIR}/docker-compose.prod.yml"
ENV_FILE="${BASE_DIR}/${ENV_NAME}/.env"
BACKUP_DIR="${BASE_DIR}/backups/${ENV_NAME}"
# Overridable so this can be rehearsed against a stack that is not named
# societycare-<env> - the local Compose project, for instance.
PROJECT="${COMPOSE_PROJECT_NAME:-societycare-${ENV_NAME}}"

DAILY_KEEP_DAYS=7
WEEKLY_KEEP_DAYS=28

log() { printf '%s  %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*"; }
die() { log "ERROR: $*" >&2; exit 1; }

[[ -f "$COMPOSE_FILE" ]] || die "no compose file at $COMPOSE_FILE"
[[ -f "$ENV_FILE" ]]     || die "no env file at $ENV_FILE (is '$ENV_NAME' a real environment?)"

compose() {
  docker compose -p "$PROJECT" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

# Read the user out of the env file rather than sourcing it - no need to pull
# passwords into this shell's environment just to learn a username.
DB_USER="$(grep -E '^DB_USER=' "$ENV_FILE" | cut -d= -f2- || true)"
DB_USER="${DB_USER:-societycare}"

compose ps --status running --services 2>/dev/null | grep -qx postgres \
  || die "the postgres container for '$PROJECT' is not running - nothing to back up"

mkdir -p "$BACKUP_DIR"

STAMP="$(date -u '+%Y-%m-%d-%a-%H%M%S')"
FINAL="${BACKUP_DIR}/societycare-${ENV_NAME}-${STAMP}.sql.gz"
TMP="${FINAL}.partial"
trap 'rm -f "$TMP"' EXIT

log "dumping ${PROJECT} database as user ${DB_USER}"
compose exec -T postgres pg_dump -U "$DB_USER" --clean --if-exists societycare \
  | gzip -9 > "$TMP"

# Three checks, cheapest first. Each one has caught a real broken backup
# somewhere: an empty file, a truncated stream, and a dump of the wrong thing.
[[ -s "$TMP" ]]      || die "dump is empty"
gzip -t "$TMP"       || die "dump failed its gzip integrity check (truncated?)"
gunzip -c "$TMP" | grep -q 'CREATE TABLE public.t_complaint' \
  || die "dump does not contain the expected schema - refusing to keep it"

mv "$TMP" "$FINAL"
trap - EXIT
log "wrote $FINAL ($(du -h "$FINAL" | cut -f1))"

# ------------------------------------------------------------------ off-site

if [[ -n "${RCLONE_REMOTE:-}" ]]; then
  if command -v rclone >/dev/null 2>&1; then
    log "copying to ${RCLONE_REMOTE}/${ENV_NAME}/"
    rclone copy "$FINAL" "${RCLONE_REMOTE}/${ENV_NAME}/" --no-traverse
    log "off-site copy done"
  else
    log "WARNING: RCLONE_REMOTE is set but rclone is not installed - backup is local only"
  fi
else
  log "WARNING: RCLONE_REMOTE not set - this backup exists only on this server"
fi

# ----------------------------------------------------------------- retention

# Sunday dumps are the weekly ones and live four times as long. Matching on the
# day name in the filename keeps this readable and avoids depending on file
# mtimes, which a copy or restore can change.
before=$(find "$BACKUP_DIR" -name 'societycare-*.sql.gz' | wc -l | tr -d ' ')

find "$BACKUP_DIR" -name 'societycare-*.sql.gz' ! -name '*-Sun-*' \
  -mtime "+${DAILY_KEEP_DAYS}" -delete
find "$BACKUP_DIR" -name 'societycare-*-Sun-*.sql.gz' \
  -mtime "+${WEEKLY_KEEP_DAYS}" -delete

after=$(find "$BACKUP_DIR" -name 'societycare-*.sql.gz' | wc -l | tr -d ' ')
log "retention: kept ${after} dump(s), removed $((before - after))"
log "done"
