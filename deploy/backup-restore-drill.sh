#!/bin/sh
set -eu

: "${CATLINK_DB_NAME:?set CATLINK_DB_NAME}"
: "${CATLINK_DB_ADMIN_USER:?set a temporary backup administrator in CATLINK_DB_ADMIN_USER}"
: "${CATLINK_DB_ADMIN_PASSWORD:?set CATLINK_DB_ADMIN_PASSWORD}"

case "$CATLINK_DB_NAME" in *[!A-Za-z0-9_]*|'') echo "invalid CATLINK_DB_NAME" >&2; exit 1 ;; esac
case "$CATLINK_DB_ADMIN_USER" in *[!A-Za-z0-9_.@-]*|'') echo "invalid CATLINK_DB_ADMIN_USER" >&2; exit 1 ;; esac

HOST="${CATLINK_DB_HOST:-127.0.0.1}"
PORT="${CATLINK_DB_PORT:-3306}"
DRILL_DB="${CATLINK_DB_NAME}_restore_drill_$(date +%Y%m%d%H%M%S)"
DUMP="$(mktemp "${TMPDIR:-/tmp}/catlink-restore-drill.XXXXXX.sql")"
chmod 600 "$DUMP"
export MYSQL_PWD="$CATLINK_DB_ADMIN_PASSWORD"

cleanup() {
  mysql -h "$HOST" -P "$PORT" -u "$CATLINK_DB_ADMIN_USER" -e "DROP DATABASE IF EXISTS \`$DRILL_DB\`" >/dev/null 2>&1 || true
  rm -f "$DUMP"
}
trap cleanup EXIT INT TERM

started="$(date +%s)"
mysqldump -h "$HOST" -P "$PORT" -u "$CATLINK_DB_ADMIN_USER" --single-transaction --routines --triggers "$CATLINK_DB_NAME" > "$DUMP"
mysql -h "$HOST" -P "$PORT" -u "$CATLINK_DB_ADMIN_USER" -e "CREATE DATABASE \`$DRILL_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
mysql -h "$HOST" -P "$PORT" -u "$CATLINK_DB_ADMIN_USER" "$DRILL_DB" < "$DUMP"

tables="$(mysql -N -h "$HOST" -P "$PORT" -u "$CATLINK_DB_ADMIN_USER" "$DRILL_DB" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DRILL_DB'")"
users="$(mysql -N -h "$HOST" -P "$PORT" -u "$CATLINK_DB_ADMIN_USER" "$DRILL_DB" -e "SELECT COUNT(*) FROM sys_users")"
[ "$tables" -gt 0 ]
[ "$users" -gt 0 ]

elapsed="$(( $(date +%s) - started ))"
echo "restore_drill=PASS database=$DRILL_DB tables=$tables users=$users rto_seconds=$elapsed"
