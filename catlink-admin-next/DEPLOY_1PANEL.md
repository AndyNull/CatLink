# 1Panel 部署

## 端口拓扑

- `8000`：后端统一 API，包含 App `/api/v1/**` 和管理端 `/admin-api/**`。
- `9527`：仅本地 Vite 开发服务器，不用于生产部署。
- `58888`：生产 Compose 对宿主机暴露的统一入口，提供管理页面并将 API 转发到容器内 `backend:8000`。

1Panel 网站反向代理目标填写 `http://127.0.0.1:58888`。不要直接反代 `8000`，否则只能访问 API，无法加载管理页面。

## 准备

1. 在 1Panel MySQL 中创建空数据库 `catlink_admin` 和专用用户 `catlink`，字符集使用 `utf8mb4`。
2. 确认 MySQL 容器和部署容器共用的 Docker 网络：`docker network ls`。
3. 解压部署包到 `/opt/catlink-admin-next`，进入该目录。
4. 执行 `cp env.example .env` 并填写 `.env`；包内同时保留 `.env.example` 供本地开发使用。

从旧 GVA 切换时不要创建空库后直接上线。先按 [GVA 清理与重部署](./REDEPLOY_FROM_GVA.md) 克隆旧业务库和媒体文件，再启动新服务。

数据库地址支持 1Panel 容器 DNS：

```text
CATLINK_ADMIN_DATABASE_SOURCE=catlink:数据库密码@tcp(1Panel-mysql-Dmuo:3306)/catlink_admin?charset=utf8mb4&parseTime=True&loc=Local
CATLINK_1PANEL_NETWORK=1panel-network
```

四个密钥必须互不相同且不少于 32 字节，可分别执行四次 `openssl rand -base64 48` 生成：

```text
CATLINK_ADMIN_JWT_SECRET=管理端令牌密钥
CATLINK_JWT_SIGNING_KEY=App令牌密钥
CATLINK_DATA_MASTER_KEY=档案数据主密钥
CATLINK_CONFIG_MASTER_KEY=数据库敏感配置加密密钥
```

## 构建启动

```bash
cd /opt/catlink-admin-next
docker compose -f docker-compose.yml --env-file .env config
sh install.sh
docker compose -f docker-compose.yml --env-file .env ps
docker compose -f docker-compose.yml --env-file .env logs --tail=200 backend worker web
```

安装脚本启动 Backend、执行幂等迁移，再交互读取一次 `admin` 密码并将 bcrypt 哈希写入数据库。密码不写入 `.env`。后续修改密码只更新数据库，无需重启或重新编译。Compose 不创建 MySQL 或 Redis。

## 验收

```bash
curl -f http://127.0.0.1:58888/healthz
curl -f http://127.0.0.1:58888/readyz
curl -f http://127.0.0.1:58888/api/v1/health
curl -I http://127.0.0.1:58888/
```

浏览器访问 `https://你的域名/`。同一域名下的 `/admin-api/**`、`/api/**`、`/uploads/**` 和 `/share/**` 均由部署包内 Nginx 转发，无需在 1Panel 再拆分路径规则。

## 回滚

```bash
docker compose -f docker-compose.yml --env-file .env down
```

同时备份 `catlink_uploads`、`catlink_admin_uploads`、`catlink_logs` 和 `catlink_config` 卷，然后恢复旧反代。生产验收完成前保留旧容器镜像、旧源码、数据库备份和对象存储清单。
