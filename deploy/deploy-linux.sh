#!/bin/sh
set -eu

ROOT="${1:-$(pwd)}"
MODE="${2:-host}"
cd "$ROOT"

command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "curl is required" >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "docker compose plugin is required" >&2; exit 1; }
[ -f .env ] || { echo "copy .env.production.example to .env first" >&2; exit 1; }

set -- -f docker-compose.yml
case "$MODE" in
  host) ;;
  network) set -- "$@" -f deploy/docker-compose.onepanel-network.yml ;;
  *) echo "mode must be host or network" >&2; exit 1 ;;
esac

WEB_PORT="$(sed -n 's/^CATLINK_WEB_PORT=//p' .env | tail -n 1 | tr -d '\r')"
WEB_PORT="${WEB_PORT:-58888}"
case "$WEB_PORT" in *[!0-9]*|'') echo "CATLINK_WEB_PORT must be numeric" >&2; exit 1 ;; esac

docker compose "$@" config --quiet
docker compose "$@" build --pull backend web
docker compose "$@" up -d

i=0
until curl -fsS "http://127.0.0.1:${WEB_PORT}/api/ready" >/dev/null; do
  i=$((i + 1))
  if [ "$i" -ge 60 ]; then
    docker compose "$@" logs --tail=100 backend web
    exit 1
  fi
  sleep 2
done

docker compose "$@" ps
echo "admin: http://127.0.0.1:${WEB_PORT}/admin-vue/#/login"
