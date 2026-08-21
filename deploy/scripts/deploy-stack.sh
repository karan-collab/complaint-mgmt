#!/usr/bin/env bash
#
# Bring one SocietyCare environment up on a given pair of images, and roll back
# if it does not come up healthy.
#
#   ./deploy-stack.sh beta ghcr.io/owner/societycare-api:abc1234 ghcr.io/owner/societycare-web:abc1234 8081 https://beta.complaintsmgmt.com
#
# Required in the environment (supplied by the deploy workflow):
#   POSTGRES_PASSWORD, DB_PASSWORD, JWT_SECRET
#
# This runs ON THE SERVER. Keeping the logic here rather than inside the
# workflow means beta and production cannot drift apart, and it can be run by
# hand when GitHub is not involved.
#
# Rollback works by keeping the previous .env: it holds the image tags that were
# running, so restoring it and starting again returns to the last known-good
# version. It does not undo database migrations - those only go forwards, which
# is exactly why changes go to beta first.
set -euo pipefail

ENV_NAME="${1:?Usage: $0 <beta|prod> <api-image> <web-image> <web-port> <public-origin>}"
API_IMAGE="${2:?api image required}"
WEB_IMAGE="${3:?web image required}"
WEB_PORT="${4:?web port required}"
PUBLIC_ORIGIN="${5:?public origin required, e.g. https://complaintsmgmt.com}"

: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set}"
: "${DB_PASSWORD:?DB_PASSWORD must be set}"
: "${JWT_SECRET:?JWT_SECRET must be set}"

BASE_DIR="${SOCIETYCARE_DIR:-/opt/societycare}"
COMPOSE_FILE="${BASE_DIR}/docker-compose.prod.yml"
ENV_DIR="${BASE_DIR}/${ENV_NAME}"
ENV_FILE="${ENV_DIR}/.env"
PREV_FILE="${ENV_DIR}/.env.previous"
PROJECT="societycare-${ENV_NAME}"

HEALTH_TIMEOUT_SECONDS=180

log() { printf '%s  %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*"; }
die() { log "ERROR: $*" >&2; exit 1; }

compose() {
  docker compose -p "$PROJECT" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

[[ -f "$COMPOSE_FILE" ]] || die "no compose file at $COMPOSE_FILE"
mkdir -p "$ENV_DIR"

# Keep whatever is currently running, so there is something to go back to.
if [[ -f "$ENV_FILE" ]]; then
  cp "$ENV_FILE" "$PREV_FILE"
  log "previous version recorded for rollback"
else
  log "first deploy of ${ENV_NAME} - no previous version to roll back to"
fi

write_env() {
  local api="$1" web="$2"
  umask 077                       # secrets: owner-readable only
  cat > "$ENV_FILE" <<EOF
# Written by deploy-stack.sh - do not edit by hand, the next deploy overwrites it.
API_IMAGE=${api}
WEB_IMAGE=${web}
WEB_PORT=${WEB_PORT}
DB_USER=societycare
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
DB_PASSWORD=${DB_PASSWORD}
JWT_SECRET=${JWT_SECRET}
# Browsers send an Origin header on every POST, INCLUDING same-origin ones.
# Spring's CORS filter therefore evaluates it even though nginx serves the UI
# and the API from one host - and an empty allow-list rejects everything with
# 403. Leaving this blank broke every login while curl, which sends no Origin
# header, kept returning 200.
#
# Note that blank is worse than absent: application.yml's ${VAR:default} only
# applies when the variable is UNSET, so an empty value silently wins.
APP_CORS_ALLOWED_ORIGINS=${PUBLIC_ORIGIN}
EOF
}

wait_for_health() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    if compose ps --format '{{.Service}} {{.Status}}' | grep -q '^api .*healthy'; then
      # The api reporting healthy is necessary but not sufficient - check the
      # whole path a visitor takes, through nginx.
      if curl -fsS -o /dev/null --max-time 5 "http://127.0.0.1:${WEB_PORT}/actuator/health"; then
        # And check it the way a BROWSER does. Browsers attach an Origin header
        # to every request; curl does not. A broken CORS allow-list answers 403
        # to the browser while plain curl still sees 200, so a check without
        # this header once passed a deploy in which no one could log in.
        local code
        code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 \
          -H "Origin: ${PUBLIC_ORIGIN}" \
          "http://127.0.0.1:${WEB_PORT}/api/v1/meta/categories")
        if [ "$code" = "200" ]; then
          return 0
        fi
        log "  api is up but returns HTTP ${code} to a request carrying Origin: ${PUBLIC_ORIGIN}"
        log "  (that is a CORS allow-list problem, not a startup problem)"
      fi
    fi
    sleep 5
  done
  return 1
}

log "deploying ${ENV_NAME}"
log "  api : ${API_IMAGE}"
log "  origin: ${PUBLIC_ORIGIN}"
log "  web : ${WEB_IMAGE}"
log "  port: ${WEB_PORT}"

write_env "$API_IMAGE" "$WEB_IMAGE"
compose pull --quiet
compose up -d

log "waiting up to ${HEALTH_TIMEOUT_SECONDS}s for the stack to report healthy"
if wait_for_health; then
  log "${ENV_NAME} is healthy on ${API_IMAGE##*:}"
  docker image prune -f >/dev/null 2>&1 || true
  exit 0
fi

# ------------------------------------------------------------------ rollback

log "FAILED: ${ENV_NAME} did not become healthy"
compose logs --tail 40 api || true

if [[ ! -f "$PREV_FILE" ]]; then
  die "no previous version to roll back to - the stack is left as-is for inspection"
fi

log "rolling back to the previous version"
cp "$PREV_FILE" "$ENV_FILE"
compose pull --quiet
compose up -d

if wait_for_health; then
  log "rolled back successfully - ${ENV_NAME} is healthy on the previous version"
else
  log "ROLLBACK ALSO FAILED - ${ENV_NAME} needs manual attention"
fi

# Either way the deploy did not succeed, so the workflow must go red.
exit 1
