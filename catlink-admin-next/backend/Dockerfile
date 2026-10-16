FROM golang:1.26-alpine AS build
ARG GOPROXY=https://proxy.golang.org,direct
ENV GOPROXY=${GOPROXY}
WORKDIR /src
COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod go mod download
COPY . .
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/catlink-admin .

FROM alpine:3.21
RUN apk add --no-cache ca-certificates tzdata
ENV TZ=Asia/Shanghai
WORKDIR /app
COPY --from=build /out/catlink-admin /app/catlink-admin
COPY config/settings.docker.yml /app/config/settings.yml
COPY config/db-begin-mysql.sql config/db.sql config/db-end-mysql.sql /app/config/
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN mkdir -p /app/static/uploadfile && chmod 755 /app/docker-entrypoint.sh
EXPOSE 8000
ENTRYPOINT ["/app/docker-entrypoint.sh"]
