# 1Panel 全新空数据部署

## 结果

本流程永久删除旧 GVA 容器、CatLink Docker 卷、旧源码、旧 `gva` 数据库和已存在的 `catlink_admin` 数据库，然后部署一套没有历史业务数据的新系统。MySQL、Redis 和 `1panel-network` 本身不会删除。

以下命令假设：

- 旧源码：`/opt/catlink-server`
- 新源码：`/opt/catlink-admin-next`
- MySQL 容器：`1Panel-mysql-Dmuo`
- Docker 网络：`1panel-network`
- 新数据库：`catlink_admin`
- 新数据库用户：`catlink`

## 1. 删除旧运行数据

确认目录后停止旧、新 CatLink 容器：

```bash
test "$(readlink -f /opt/catlink-admin-next)" = "/opt/catlink-admin-next"

if [ -f /opt/catlink-server/docker-compose.yml ]; then
  cd /opt/catlink-server
  docker compose -f docker-compose.yml down -v --remove-orphans || true
fi

cd /opt/catlink-admin-next
if [ -f .env ]; then
  docker compose -f docker-compose.yml --env-file .env down -v --remove-orphans || true
fi

docker rm -f catlink-backend catlink-web catlink-admin-next-backend catlink-admin-next-worker catlink-admin-next-web 2>/dev/null || true
docker volume ls -q --filter label=com.docker.compose.project=catlink | xargs -r docker volume rm
docker network ls -q --filter label=com.docker.compose.project=catlink | xargs -r docker network rm
```

检查 CatLink 残留。输出中不得包含旧业务容器和旧业务卷：

```bash
docker ps -a --format '{{.Names}}\t{{.Image}}' | grep -Ei 'catlink|gva|gin-vue' || true
docker volume ls --format '{{.Name}}' | grep -Ei 'catlink|gva|gin-vue' || true
```

删除旧源码，命令带有绝对路径校验，不会删除新项目：

```bash
OLD_DIR="$(readlink -f /opt/catlink-server 2>/dev/null || true)"
if [ "$OLD_DIR" = "/opt/catlink-server" ]; then
  rm -rf -- "$OLD_DIR"
fi
test ! -e /opt/catlink-server
```

查看旧镜像后逐个删除，不执行全局清理命令：

```bash
docker image ls --format '{{.Repository}}:{{.Tag}}' | grep -Ei 'catlink|gva|gin-vue' || true
docker image rm catlink-admin-next-backend:local catlink-admin-next-web:local 2>/dev/null || true
```

不要执行 `docker system prune -a`，它会影响 1Panel 的其他业务。

## 2. 删除并重建数据库

在 1Panel 的“数据库”页面执行：

1. 删除旧数据库 `gva`。
2. 删除已存在的 `catlink_admin`。
3. 删除已存在的数据库用户 `catlink`。
4. 创建空数据库 `catlink_admin`，字符集选择 `utf8mb4`。
5. 创建用户 `catlink`，授权范围只选择 `catlink_admin`，访问范围允许容器网络。

数据库密码使用下面命令生成，并立即填写到 1Panel：

```bash
openssl rand -hex 24
```

如果 1Panel 删除数据库时没有同步删除用户，必须单独删除旧 `catlink` 用户后重新创建。不要复用旧数据库密码。

## 3. 检查源码和网络

```bash
cd /opt/catlink-admin-next
test -f docker-compose.yml
test -f backend/Dockerfile
test -f web/Dockerfile
docker network inspect 1panel-network >/dev/null
docker inspect 1Panel-mysql-Dmuo --format '{{range $name,$config := .NetworkSettings.Networks}}{{$name}}{{"\n"}}{{end}}'
```

MySQL 容器必须连接到 `1panel-network`。如果没有：

```bash
docker network connect 1panel-network 1Panel-mysql-Dmuo
```

## 4. 创建全新环境配置

```bash
cd /opt/catlink-admin-next
umask 077
read -rsp "输入刚在1Panel创建的catlink数据库密码: " DB_PASSWORD
echo

ADMIN_JWT="$(openssl rand -hex 48)"
APP_JWT="$(openssl rand -hex 48)"
DATA_KEY="$(openssl rand -hex 48)"
CONFIG_KEY="$(openssl rand -hex 48)"

printf '%s\n' \
"CATLINK_ADMIN_DATABASE_SOURCE=catlink:${DB_PASSWORD}@tcp(1Panel-mysql-Dmuo:3306)/catlink_admin?charset=utf8mb4&parseTime=True&loc=Local" \
"CATLINK_ADMIN_JWT_SECRET=${ADMIN_JWT}" \
"CATLINK_JWT_SIGNING_KEY=${APP_JWT}" \
"CATLINK_DATA_MASTER_KEY=${DATA_KEY}" \
"CATLINK_CONFIG_MASTER_KEY=${CONFIG_KEY}" \
"CATLINK_1PANEL_NETWORK=1panel-network" \
"CATLINK_BIND_ADDRESS=127.0.0.1" \
"CATLINK_HTTP_PORT=58888" \
"GOPROXY=https://goproxy.cn,direct" > .env

unset DB_PASSWORD ADMIN_JWT APP_JWT DATA_KEY CONFIG_KEY
chmod 600 .env
docker compose -f docker-compose.yml --env-file .env config >/dev/null
```

`.env` 必须长期保存。写入数据后不得更换 `CATLINK_DATA_MASTER_KEY`；配置 SMTP、存储等敏感信息后不得更换 `CATLINK_CONFIG_MASTER_KEY`。

## 5. 构建并启动

```bash
cd /opt/catlink-admin-next
sh install.sh
docker compose -f docker-compose.yml --env-file .env ps
docker compose -f docker-compose.yml --env-file .env logs --tail=200 backend worker web
```

安装脚本先建立数据库表并执行全部幂等迁移，再交互读取一次 `admin` 密码并将 bcrypt 哈希写入数据库。明文密码不会写入 `.env`。固定的公开密码 `admin/123456` 不启用。

## 6. 管理员登录

账号为 `admin`，密码是运行 `install.sh` 时输入的密码。后续在管理端修改密码只更新数据库，无需重启或重新编译。

## 7. 验收

```bash
curl -f http://127.0.0.1:58888/healthz
curl -f http://127.0.0.1:58888/readyz
curl -f http://127.0.0.1:58888/api/v1/health
curl -I http://127.0.0.1:58888/
docker compose -f docker-compose.yml --env-file .env ps
```

使用 `admin` 登录管理端，确认以下页面可打开：

- 运营首页
- App 用户
- 完整猫咪档案
- 公开猫咪
- 社区帖子、评论、举报
- 媒体库
- 存储管理
- 邮件服务
- 积木扩展

全新部署的 App 用户、猫咪档案、帖子和媒体数量应为 `0`。

## 8. 1Panel 反向代理

在 1Panel 网站中将 `api.example.com` 的代理地址设置为：

```text
http://127.0.0.1:58888
```

不要反代 `8000` 或 `9527`：

- `8000` 是容器内部 API 端口。
- `9527` 只用于本地 Vite 开发。
- `58888` 是生产 Web、App API 和管理 API 的统一入口。

反代后验证：

```bash
curl -f https://api.example.com/readyz
curl -f https://api.example.com/api/v1/health
curl -I https://api.example.com/
```

## 9. 最终检查

```bash
test ! -e /opt/catlink-server
docker ps -a --format '{{.Names}}\t{{.Status}}' | grep -Ei 'catlink|gva|gin-vue' || true
docker volume ls --format '{{.Name}}' | grep -Ei 'catlink|gva|gin-vue' || true
```

最终应只保留：

- `catlink-admin-next-backend`
- `catlink-admin-next-worker`
- `catlink-admin-next-web`
- 新项目的 `catlink_catlink_uploads`、`catlink_catlink_admin_uploads`、`catlink_catlink_logs`、`catlink_catlink_config`
- MySQL 中的 `catlink_admin`
