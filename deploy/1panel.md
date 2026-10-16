# 有猫腻 1Panel 部署

## 入口

| 项目 | 地址 |
|---|---|
| 统一 HTTP 入口 | `http://服务器IP:58888` |
| 管理端 | `http://服务器IP:58888/admin-vue/#/login` |
| App 服务地址 | `http://服务器IP:58888` 或 `https://你的域名` |
| App API | `/api/v1`，由 App 自动追加 |
| 存活检查 | `GET /api/v1/health` |
| 依赖就绪检查 | `GET /readyz`（兼容旧脚本：`/api/ready`） |

Gin 只在容器网络监听 `8888`。宿主机只映射 Web/Nginx 的 `58888`，Compose 不创建 MySQL 或 Redis。

最终部署包为 `build/deliverables/catlink-1.0.0-final-20260822-172542/catlink-server-1.0.0-final.zip`。上传前使用同目录 `SHA256SUMS.txt` 校验。

## 1Panel 准备

在 1Panel 已有 MySQL 中创建空数据库 `gva` 和独立用户 `catlink`，字符集使用 `utf8mb4`。该用户只授予 `gva.*` 权限，不使用 root 账号运行服务。Redis 建议启用密码；MySQL 和 Redis 端口不要放行到公网。

复制配置并填写真实值：

```bash
cp .env.production.example .env
chmod 600 .env
openssl rand -base64 48
```

至少填写 `CATLINK_DB_HOST`、`CATLINK_DB_PORT`、`CATLINK_DB_NAME`、`CATLINK_DB_USER`、`CATLINK_DB_PASSWORD`、`CATLINK_REDIS_ADDR`、`CATLINK_REDIS_PASSWORD`、`CATLINK_JWT_SIGNING_KEY` 和首次引导密码。模板不包含真实密钥。

## 连接现有容器

推荐使用共享容器网络，不公开数据库端口：

1. 在 1Panel 容器页面读取 MySQL、Redis 所在 Docker 网络名和容器名。
2. 将 `.env` 的 `CATLINK_DB_HOST` 改为 MySQL 容器名，`CATLINK_REDIS_ADDR` 改为 `Redis容器名:6379`，填写 `CATLINK_1PANEL_NETWORK`。
3. 使用 network 模式部署：

```bash
sh deploy/deploy-linux.sh /opt/catlink network
```

如果 1Panel 已将数据库端口映射到宿主机，可使用默认 host 模式：

```dotenv
CATLINK_DB_HOST=host.docker.internal
CATLINK_REDIS_ADDR=host.docker.internal:6379
```

```bash
sh deploy/deploy-linux.sh /opt/catlink host
```

此模式依赖 MySQL/Redis 监听 Docker host-gateway 可达地址；仍应通过服务器防火墙拒绝公网访问 `3306/6379`。

## 初始化与迁移

首次启动保持 `CATLINK_DISABLE_AUTO_MIGRATE=false`。Gin 会增量创建 GVA 系统表、CatLink 业务表、索引、角色、菜单和权限；不会删除现有数据。仅空库且设置 `CATLINK_BOOTSTRAP_ADMIN_PASSWORD` 时创建管理员：

```text
用户名：admin
邮箱：admin@catlink.local
密码：CATLINK_BOOTSTRAP_ADMIN_PASSWORD 的值
```

首次登录并修改密码后清空该变量，再执行：

```bash
docker compose -f docker-compose.yml -f deploy/docker-compose.onepanel-network.yml up -d --force-recreate backend
```

`sql-init/gin-vue-admin/` 是人工审计和禁用自动迁移时的参考。人工方式必须按文件编号执行 CatLink 迁移脚本；不要导入旧 Ktor/RuoYi SQL。

## 域名反代

在 1Panel 网站中将域名反向代理到 `http://127.0.0.1:58888`，开启 HTTPS。WebSocket 无特殊配置，上传体限制至少 `24 MiB`。App Debug 页只填写域名或源站，例如 `https://cat.example.com`，不要追加 `/api` 或 `/api/v1`。

默认 `CATLINK_TRUSTED_PROXY_CIDRS=172.30.88.0/24` 只信任 CatLink Web 容器。若 1Panel 代理经其他容器网络转发，追加该代理的精确 IP/CIDR，以保证登录限流、审计日志和 IP 粗定位读取真实客户端地址。禁止填写 `0.0.0.0/0`。

IP 粗定位有两种来源：

- 在管理端“系统配置 → 定位服务”保存地点搜索、反向解析、IP 定位地址和可信代理头开关。配置写入 `ga_config`，保存后立即生效，不需要重启或重新编译。
- 首次迁移前可临时使用 `CATLINK_IP_GEO_ENDPOINT`、`CATLINK_GEOCODER_SEARCH_URL`、`CATLINK_GEOCODER_URL` 和 `CATLINK_TRUST_IP_GEO_HEADERS` 作为兼容回退；管理端保存后以数据库配置为准。
- IP 地址模板必须是 HTTPS 且包含 `{ip}`，接口返回 JSON `city`、`district`。只有可信反代注入 `X-Geo-City`、`X-Geo-District` 时才开启信任开关；端口 `58888` 此时必须只允许反代访问。

附近功能打开时先用 IP 做无需权限的城市级粗定位；用户授权精确定位后，再用 GPS 区县与坐标覆盖粗定位并计算真实距离。IP 不能可靠提供实时距离，不能替代 GPS。

## 验证、备份与回滚

```bash
curl -fsS http://127.0.0.1:58888/readyz
curl -fsS http://127.0.0.1:58888/api/v1/health
docker compose ps
docker compose logs --tail=100 backend web
```

数据库优先使用 1Panel 计划任务备份。独立恢复演练需要宿主机安装 MySQL 客户端，并临时导出数据库地址、库名以及具备创建临时库权限的 `CATLINK_DB_ADMIN_USER/CATLINK_DB_ADMIN_PASSWORD` 后运行；管理员凭据不写入 App `.env`：

```bash
sh deploy/backup-restore-drill.sh
```

升级前同时备份 1Panel MySQL、Redis、`catlink_uploads`、`catlink_admin_uploads`、`catlink_logs` 和 `catlink_config` 卷。应用回滚使用上一版 ZIP 重建容器；数据库结构回滚使用升级前备份，GORM 不负责自动降级。禁止执行 `docker compose down -v`。
