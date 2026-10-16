# 有猫腻 Gin 服务端

`server-gin/` 是项目唯一运行后端，基于 gin-vue-admin v3 二次开发。旧 Ktor/RuoYi 服务不参与构建或部署。

## 入口

```text
Go 入口：server-gin/main.go
本地端口：http://127.0.0.1:8888
管理端：http://127.0.0.1:8888/admin-vue/#/login
App API：http://127.0.0.1:8888/api/v1
依赖就绪：http://127.0.0.1:8888/api/ready
```

本机开发数据库管理员为 `admin / CatLink@Admin2026`。该密码不是生产默认密码；生产通过一次性的 `CATLINK_BOOTSTRAP_ADMIN_PASSWORD` 创建管理员，首次改密后清空变量。

## 本地验证

```powershell
& '..\.toolchain\go\bin\go.exe' test ./... -count=1
& '..\.toolchain\go\bin\go.exe' vet ./...
& '..\.toolchain\go\bin\go.exe' build -o gva-server.exe .
```

本地配置为 `config.yaml`，容器模板为 `config.docker.yaml`。生产配置由 `deploy/gin-vue-admin-entrypoint.sh` 根据环境变量生成，不要把真实数据库、Redis、SMTP、AI 或对象存储密钥写入仓库配置。

## 目录职责

| 目录 | 职责 |
|---|---|
| `api/v1/catlink` | App 与 CatLink 运营 API |
| `router/catlink.go` | CatLink 实际路由清单 |
| `initialize` | GORM、Redis、路由、角色、菜单和任务初始化 |
| `middleware` | App/GVA 鉴权、限流、审计、数据范围和安全头 |
| `model` | GVA 与 CatLink GORM 模型 |
| `service` | GVA 系统、媒体和插件服务 |
| `plugin` | 存储等 GVA 插件 |
| `resource` | 静态资源和代码生成模板 |

部署入口、1Panel 反向代理与备份演练脚本位于仓库根目录 `deploy/`。
