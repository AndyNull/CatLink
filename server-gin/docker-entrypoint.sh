#!/bin/sh
set -eu

: "${CATLINK_JWT_SIGNING_KEY:?CATLINK_JWT_SIGNING_KEY is required}"
: "${CATLINK_DB_HOST:?CATLINK_DB_HOST is required}"
: "${CATLINK_DB_PORT:=3306}"
: "${CATLINK_DB_NAME:?CATLINK_DB_NAME is required}"
: "${CATLINK_DB_USER:?CATLINK_DB_USER is required}"
: "${CATLINK_DB_PASSWORD:?CATLINK_DB_PASSWORD is required}"

if [ "${#CATLINK_JWT_SIGNING_KEY}" -lt 32 ] || [ "${CATLINK_JWT_SIGNING_KEY}" = "qmPlus" ]; then
  echo "CATLINK_JWT_SIGNING_KEY must be at least 32 non-default characters" >&2
  exit 1
fi

CONFIG=/app/config.production.yaml
cp /app/config.docker.yaml "$CONFIG"
yq -i \
  '.jwt.signing-key = strenv(CATLINK_JWT_SIGNING_KEY) |
   .jwt.issuer = "CatLink" |
   .system.addr = 8888 |
   .system.db-type = "mysql" |
   .system.router-prefix = "/api" |
   .system.disable-auto-migrate = (strenv(CATLINK_DISABLE_AUTO_MIGRATE) == "true") |
   .system.use-redis = (strenv(CATLINK_USE_REDIS) == "true") |
   .system.use-multipoint = (strenv(CATLINK_USE_REDIS) == "true") |
   .mysql.path = strenv(CATLINK_DB_HOST) |
   .mysql.port = strenv(CATLINK_DB_PORT) |
   .mysql.db-name = strenv(CATLINK_DB_NAME) |
   .mysql.username = strenv(CATLINK_DB_USER) |
   .mysql.password = strenv(CATLINK_DB_PASSWORD) |
   .mysql.config = "charset=utf8mb4&parseTime=True&loc=Local" |
   .local.path = "/uploads" |
   .local.store-path = "/app/uploads" |
   .zap.log-in-console = true |
   .zap.level = "info"' "$CONFIG"

if [ "${CATLINK_USE_REDIS:-false}" = "true" ]; then
  : "${CATLINK_REDIS_ADDR:?CATLINK_REDIS_ADDR is required when Redis is enabled}"
  yq -i \
    '.redis.addr = strenv(CATLINK_REDIS_ADDR) |
     .redis.password = strenv(CATLINK_REDIS_PASSWORD) |
     .redis.db = 0 |
     .redis-list[0].addr = strenv(CATLINK_REDIS_ADDR) |
     .redis-list[0].password = strenv(CATLINK_REDIS_PASSWORD) |
     .redis-list[0].db = 0' "$CONFIG"
fi

chmod 600 "$CONFIG"

exec /app/catlink-server -c "$CONFIG"
