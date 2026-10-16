# 有猫腻微信小程序

小程序源码独立维护在 `miniprogram` 分支的 `miniprogram/` 子目录，面向已部署有猫腻 API 的使用者。它与 Android App 共用服务端接口，不包含独立数据库。环境要求为 Node.js 18+、pnpm 12 和微信开发者工具。

开始前：

1. 进入仓库的 `miniprogram/` 子目录，安装依赖：`pnpm install --frozen-lockfile`。
2. 在 `miniprogram/miniprogram/env.ts` 设置自己的 HTTPS API 根地址，不带 `/api/v1`。
3. 在 `miniprogram/project.config.json` 将 `appid` 替换为自己的微信 AppID；个人预览可使用开发者工具的测试号，但体验版和正式发布需要已配置的小程序 AppID。
4. 在微信公众平台把 API 域名加入合法请求、上传和下载域名；本地开发时可在开发者工具中临时关闭域名校验，发布前必须恢复并验证域名配置。
5. 用微信开发者工具导入 `miniprogram/` 子目录（含 `project.config.json`），编译后在模拟器预览；真机调试、体验版上传和发布均在开发者工具中进行。

依赖安装与校验均在 `miniprogram/` 子目录执行：

```powershell
cd miniprogram
pnpm install --frozen-lockfile
pnpm run verify
```

`pnpm run verify` 执行 TypeScript 类型检查和小程序页面结构校验。修改源码后先运行该命令，再使用微信开发者工具编译。小程序 API 地址与 AppID 属于部署者配置，不要提交自己的生产域名、AppSecret 或其他凭据。服务端部署和 Android 版本说明见仓库根目录 README。
