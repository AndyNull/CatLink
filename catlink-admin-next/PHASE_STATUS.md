# Phase 0-7 状态

更新时间：2026-09-03

## 已完成

| Phase | 已完成内容 | 验证 |
| --- | --- | --- |
| 0 | 固定 go-admin `v2.4.0+official.1b7dcd8`（该提交是 `v2.4.0` 标签后的官方数据权限/对象存储修复）与 go-admin-ui `v3.1.0`；冻结 134 条路由；保存迁移 checksum；本地 MySQL 迁移前备份 | `baseline/source.json`、`baseline/routes.json`、`backups/gva-pre-go-admin-20260827-1110.sql` |
| 1 | 独立 `backend/web`；`ga_*` 表；`/healthz`、`/readyz`、request ID；`/admin-api`；移除 Demo 运行入口 | Go 全量测试、Web 类型检查/构建 |
| 2 | 管理端用户读取/状态/头像/密码统一到 `sys_users + ga_admin_memberships`；独立 Admin JWT issuer/audience/secret；App/Admin token 隔离 | 本地 `admin` 实际登录、用户列表和 CatLink 统计接口通过 |
| 3 | 统计、完整档案、公开猫咪、帖子、评论、媒体、日志 API 和页面；完整档案详情关联医疗、领养、合同、回访、时间线、待办、亲属、相册和历史版本 | 独立 MySQL 列表、详情与关联删除测试；本轮浏览器控制运行时缺失，未重新执行可视化点击回归 |
| 4 | 六个静态积木注册；数据库密文配置；掩码 Secret 保留；本地/S3/WebDAV 真实读写探针；帖子锁定/删除、公开字段、媒体回收写接口 | Go 本地/WebDAV 读写、幂等、越界删除、密文配置测试；Web 页面接入 |
| 5 | Worker 任务表、租约、退避、失败终态、过期租约恢复；公告通知 Outbox；通知投递与保留策略处理器；API 与 Worker 独立容器 | Worker 中断恢复、公告真实投递、保留策略预览与状态转换测试 |
| 6 | Debug App 固定新入口 `10.0.2.2:8000`；原生 Vault 同步；设备 JWT/会话鉴权；实体级服务端版本、mutation 幂等、墓碑传播、100 条分页；数据库事件桥接旧 SSE；退出不等待媒体同步 | Go 同步/鉴权状态机测试；Android JVM 单测与 Debug 构建；原生路由实测 |
| 7 | 默认 Docker/Compose、管理端与 App API 已切换到 go-admin 单栈；旧源码仅作离线回滚参考 | `8000` API、`9527/58888` Web；默认部署不再构建或启动 `8888` |

## 尚未满足的生产退出条件

以下条件受真实时间和生产流量约束，不能用本地测试伪造：

- Phase 3：连续 7 天影子读取一致，P95 不高于旧接口 120%。
- Phase 4：已将 GVA 修复版的数据库配置、密钥掩码保留、读写探针、迁移状态机和回滚语义迁入 GoAdmin，并重写为积木；本地、模拟 WebDAV/S3 和迁移回滚已有测试，七牛/阿里/腾讯/华为使用原生客户端。真实厂商账号、生产迁移、SMTP 发信和生产回滚演练尚未完成。
- Phase 5：通知 Outbox 与保留策略已切入 Worker；邮件仍同步发送，存储迁移仍由独立可恢复迁移任务执行，尚未统一到 Worker。
- Phase 6：迁移矩阵当前为 `136 native=89 replaced=47 missing=0`；该结果只证明 App 路由有真实 Handler、旧管理路径有明确的新路由目标，不单独证明每个替代接口的业务语义与外部依赖已完成。核心写操作、Vault、多设备合并、申诉 IDOR、媒体删除和存储迁移另有服务测试覆盖。
- Phase 7：新系统稳定运行 14 天并关闭回滚窗口后，才允许归档 `server-gin/admin-web`。

因此当前状态是“App 业务 API 已原生迁移、管理功能可联调、生产观察与真实外部服务验收未完成”，不是生产迁移完成，也不应删除旧备份。

## 本轮性能与信息架构修复

- 运营统计由 15 次并发 `COUNT` 合并为一次标量查询，避免 GORM 并发连接争用。
- 业务菜单平铺到“有猫腻运营”，包含用户、完整档案、公开猫咪、帖子、评论、举报、公告、媒体、存储、通知投递、业务配置、积木扩展和协作消息。
- 分组节点不再自动重定向到首个子项，修复公开猫咪、媒体库等平铺路由被错误劫持的问题。
- 列表页增加 30 秒内存缓存；查询、分页、写操作显式强制刷新。
- `RouterViewKeepAlive` 缓存当前路由及祖先容器，避免分组导航销毁页面状态。
- Chrome SPA 基线脚本：`web/scripts/measure-live-navigation.mjs`。本地实测 21 次导航无空白、无 JS 错误；健康检查 8000 端口 3-6ms，9527 首次 HTML 约 129ms。
- App 路由当前为 `native=89`，旧管理路由 `47` 条由显式映射的 `/admin-api` 替代；通知 SSE、私聊、公开猫咪、媒体、注册邮件、分享、协作、Vault 与 AI 均不再依赖旧服务。
- App 用户已使用独立 `/admin-api/catlink/users` 列表、详情和启停接口及页面，不再把业务用户菜单指向系统管理员页面。
- 首页改为 Element Plus 的核心规模、待处理、今日变化三层结构，删除与侧边栏重复的业务入口；壳层 E2E 10/10、首页 E2E 1/1。
- v20 SQLite 迁移版本为 `1788600018000`；菜单图标检查已修正为检查当前 v20 数据库。

## 数据传输与加密边界

- Release App 固定使用 `https://api.example.com`，Manifest 禁止明文 HTTP；Debug 仅允许本机、模拟器宿主或 HTTPS 地址。
- 私有猫咪档案的 Vault payload 在 App 端使用 AES-GCM 加密，接口传输与数据库 `cat_vaults.payload` 均为密文；服务端使用账号级 DEK 做恢复、迁移与管理员授权查看。
- 登录、社区、公告、公开档案等普通 JSON 由 TLS 保护，没有再套一层 RSA。不得将“Vault 加密”描述为“所有 API data 二次加密”。

## 跨端关联补强

- App 与 Next 的直接调用契约为 82 个调用点，缺失路由为 0；通知 SSE 和私有媒体均为 Next 原生 Handler，不再代理旧 GVA。
- 同源私有媒体使用 App Bearer，公开 Token 媒体和对象存储直连 URL不附加 App Token；聊天媒体按会话成员授权。
- 新头像保存为公开随机 Token URL；历史 `/api/v1/media/{id}` 头像在账号、主页、评论或会话读取时校验归属并迁移，外部对象存储 URL保持原样。
- 通知列表返回 `cursor`，App 按账号持久化游标并通过 SSE `afterId` 续接，空通知列表也从 `0` 开始接收，避免刷新与建连窗口丢消息。

## 本地运行

- API：`http://127.0.0.1:8000`，数据交互端口
- Web：`http://127.0.0.1:9527`，仅管理端开发页面
- App Debug：`http://10.0.2.2:8000`
- 旧 GVA：已停止，`8888` 不监听
- 独立测试库：`catlink_goadmin_test_v5`，本地新后台不读取旧 `gva` 业务数据

## 多设备同步规则

- 每个设备持有稳定 `deviceId`，每次本地变更持有唯一 `mutationId`。
- 新档案 UUID 不同则自动合并；同一档案修改只比较 `baseServerRevision`，不跨设备比较本地 revision。
- 同一 mutation 重试返回 `already-applied`，断网重试不会重复写入。
- 删除写入墓碑并随服务端游标传播，其他设备不能用旧内容无条件复活。
- 新服务提交变更时在同一事务写入 `cat_vault_change_events`；旧 SSE 服务消费事件并通知在线设备立即同步，WorkManager 15 分钟周期仅作兜底。

## 生产门禁

每天保存一份观察记录到 `observations/YYYY-MM-DD.json`，至少包含：

```json
{
  "date": "2026-08-27",
  "routeDrift": 0,
  "readMismatch": 0,
  "authErrors": 0,
  "dataLoss": 0,
  "duplicateBroadcast": 0,
  "p95Ratio": 1.0,
  "rollbackReady": true
}
```

执行 `tools/check-phase-gates.ps1`。脚本通过前禁止删除旧源码或生产数据库表。

## 2026-09-02 收尾复核

- Go 全量测试与 `go vet ./...` 通过；Web 类型检查、255 项单测和生产构建通过。
- App Contracts/JVM、Debug、Release、AndroidTest 构建通过；当前版本 `1.0.21`（versionCode `121`）。Debug 与 Release APK 均通过 `apksigner` V2/V3 校验。
- App/服务端契约门禁：82 个 App 调用点、136 条路由（89 native、47 replaced、0 missing）、47/47 替代语义、私有路由鉴权和菜单图标均通过。
- 新增积木 `AppliedAt`、多设备冲突解决、远端墓碑确认和批量档案事务回滚回归测试；服务端部署包已重建至 `build/deliverables/post-review`。
- Android 仪器测试已在 `127.0.0.1:7555` 执行：85 项通过；唯一 `LiveAuth` 用例因未提供外部凭据按设计跳过。新增 SyncEngine 冲突解决用例包含在内并通过。
- 新增 `1788600052000` 结构兼容迁移：统一旧 GVA 的 `ga_menu_api`、`ga_menu_api_rule` 及 `menu_id/api_id` 列别名，保留并幂等复制既有权限关联；兼容旧 SQLite/MySQL 的 `deleted_at` 空值、整数和时间类型。
- 使用升级前的 `catlink-admin-debug-v20.db` 实际执行 v23 迁移两次：首次完成至 `1788600052000`，第二次幂等通过。SQL 参考迁移实际为 21 个文件，v23 本地服务启动后健康、就绪、管理员登录、菜单、统计、用户、档案、分享、媒体、积木和日志接口均为 200，匿名私有接口为 401。
- 部署打包脚本取消 ZIP 内额外的 `catlink-admin-next/` 目录层级，并补齐 `env.example`；解压后可在当前目录直接执行 `sh install.sh`。
- 修复本地管理端登录 400：验证码字段不再参与基础参数绑定，生产模式仍在认证阶段强制校验；Debug 管理员已重置为 `admin/123456` 并实际验证无验证码字段登录返回 200。
- 修复 App v22 实体默认值变化未递增 Room 版本造成的启动崩溃。数据库升至 v23，`MIGRATION_22_23` 重建同步 Outbox/Fingerprint 表并保留记录；两个模拟器均在不清数据的情况下覆盖升级并冷启动成功，旧库最终 `user_version=23`、identity hash 匹配，专项仪器测试 7/7 通过。

## 2026-09-03 Task 25 回归

- Next/旧 Gin Go 全量测试与 vet、Contracts/App、Android lint/Debug/Release/AndroidTest、Web 类型检查/258 项单测/生产构建均通过。
- 修复空数据库健康探针、详情接口空指针和就绪兼容别名；正式就绪地址为 `/readyz`，旧 `/api/ready` 仅作兼容入口。
- Windows PowerShell 5.1/7 门禁脚本均可解析；路由为 `136 native=89 replaced=47 missing=0`，App 调用契约 85 个，替代语义 47/47。
- 重建部署包 `build/deploy/task25/catlink-admin-next-20260903-095454.zip`，882 项且不含数据库、日志、测试/脚手架配置或旧本地上传媒体；SHA-256 为 `1681e97f717019584f68298e1f6b6097f3c4f715c730f72eb76ecfae8a836410`。管理员头像使用独立持久卷并由 Nginx `/static/` 反代。
- 本地运行态 `8000` 健康/就绪/兼容别名、`9527` 页面、管理员登录与匿名鉴权均通过。Phase 7 仍需真实生产连续 14 天观察，不能由本地数据替代。
