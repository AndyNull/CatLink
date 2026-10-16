# CatLink gin-vue-admin backend (独立 server-gin 目录)
FROM golang:1.24-alpine AS backend-build
WORKDIR /src
COPY server-gin/go.mod server-gin/go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod go mod download
COPY server-gin/ ./
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/catlink-server .

FROM node:22-alpine AS web-build
WORKDIR /src
COPY admin-web/package.json admin-web/package-lock.json ./
RUN npm ci
COPY admin-web/ ./
RUN npm run build

FROM alpine:3.21 AS backend
ARG YQ_VERSION=v4.45.1
ARG TARGETARCH
RUN apk add --no-cache ca-certificates tzdata wget \
    && case "$TARGETARCH" in \
         amd64) YQ_SHA256="654d2943ca1d3be2024089eb4f270f4070f491a0610481d128509b2834870049" ;; \
         arm64) YQ_SHA256="ceea73d4c86f2e5c91926ee0639157121f5360da42beeb8357783d79c2cc6a1d" ;; \
         *) echo "unsupported TARGETARCH: $TARGETARCH" >&2; exit 1 ;; \
       esac \
    && wget -qO /usr/local/bin/yq "https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/yq_linux_${TARGETARCH}" \
    && echo "${YQ_SHA256}  /usr/local/bin/yq" | sha256sum -c - \
    && chmod +x /usr/local/bin/yq
WORKDIR /app
ENV TZ=Asia/Shanghai GIN_MODE=release CATLINK_USE_REDIS=false CATLINK_REDIS_PASSWORD=
COPY --from=backend-build /out/catlink-server /app/catlink-server
COPY server-gin/config.docker.yaml /app/config.docker.yaml
COPY server-gin/resource /app/resource
COPY deploy/gin-vue-admin-entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh && mkdir -p /app/uploads /app/log /app/config-data \
    && addgroup -S catlink && adduser -S -G catlink catlink \
    && chown -R catlink:catlink /app
USER catlink
EXPOSE 8888
VOLUME ["/app/uploads", "/app/log", "/app/config-data"]
ENTRYPOINT ["/app/entrypoint.sh"]

FROM nginx:1.27-alpine AS web
COPY --from=web-build /src/dist /usr/share/nginx/html/admin-vue
COPY deploy/nginx-catlink.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
