#!/bin/sh
set -eu

: "${CATLINK_JWT_SIGNING_KEY:?CATLINK_JWT_SIGNING_KEY is required}"
: "${CATLINK_DB_HOST:?CATLINK_DB_HOST is required}"
: "${CATLINK_DB_PORT:=3306}"
: "${CATLINK_DB_NAME:?CATLINK_DB_NAME is required}"
: "${CATLINK_DB_USER:?CATLINK_DB_USER is required}"
: "${CATLINK_DB_PASSWORD:?CATLINK_DB_PASSWORD is required}"
: "${CATLINK_MINIO_USE_SSL:=true}"
: "${CATLINK_S3_PATH_PREFIX:=catlink}"
: "${CATLINK_S3_FORCE_PATH_STYLE:=false}"
: "${CATLINK_S3_DISABLE_SSL:=false}"

if [ "${#CATLINK_JWT_SIGNING_KEY}" -lt 32 ] || [ "${CATLINK_JWT_SIGNING_KEY}" = "qmPlus" ]; then
  echo "CATLINK_JWT_SIGNING_KEY must be at least 32 non-default characters" >&2
  exit 1
fi

CONFIG=/app/config-data/config.production.yaml
if [ ! -f "$CONFIG" ]; then
  cp /app/config.docker.yaml "$CONFIG"
fi

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

if [ -n "${CATLINK_OSS_TYPE:-}" ]; then
  yq -i '.system.oss-type = strenv(CATLINK_OSS_TYPE)' "$CONFIG"
fi

if [ -n "${CATLINK_MINIO_ENDPOINT:-}" ]; then
  : "${CATLINK_MINIO_ACCESS_KEY:?CATLINK_MINIO_ACCESS_KEY is required}"
  : "${CATLINK_MINIO_SECRET_KEY:?CATLINK_MINIO_SECRET_KEY is required}"
  : "${CATLINK_MINIO_BUCKET:?CATLINK_MINIO_BUCKET is required}"
  yq -i \
    '.minio.endpoint = strenv(CATLINK_MINIO_ENDPOINT) |
     .minio.access-key-id = strenv(CATLINK_MINIO_ACCESS_KEY) |
     .minio.access-key-secret = strenv(CATLINK_MINIO_SECRET_KEY) |
     .minio.bucket-name = strenv(CATLINK_MINIO_BUCKET) |
     .minio.use-ssl = (strenv(CATLINK_MINIO_USE_SSL) == "true") |
     .minio.bucket-url = strenv(CATLINK_MINIO_BASE_URL)' "$CONFIG"
fi

if [ -n "${CATLINK_S3_ENDPOINT:-}" ]; then
  : "${CATLINK_S3_BUCKET:?CATLINK_S3_BUCKET is required}"
  : "${CATLINK_S3_REGION:?CATLINK_S3_REGION is required}"
  : "${CATLINK_S3_ACCESS_KEY:?CATLINK_S3_ACCESS_KEY is required}"
  : "${CATLINK_S3_SECRET_KEY:?CATLINK_S3_SECRET_KEY is required}"
  yq -i \
    '.aws-s3.bucket = strenv(CATLINK_S3_BUCKET) |
     .aws-s3.region = strenv(CATLINK_S3_REGION) |
     .aws-s3.endpoint = strenv(CATLINK_S3_ENDPOINT) |
     .aws-s3.secret-id = strenv(CATLINK_S3_ACCESS_KEY) |
     .aws-s3.secret-key = strenv(CATLINK_S3_SECRET_KEY) |
     .aws-s3.base-url = strenv(CATLINK_S3_BASE_URL) |
     .aws-s3.path-prefix = strenv(CATLINK_S3_PATH_PREFIX) |
     .aws-s3.s3-force-path-style = (strenv(CATLINK_S3_FORCE_PATH_STYLE) == "true") |
     .aws-s3.disable-ssl = (strenv(CATLINK_S3_DISABLE_SSL) == "true")' "$CONFIG"
fi

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
