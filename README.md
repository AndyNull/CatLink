# 有猫腻微信小程序

本分支专门维护有猫腻微信小程序。源码位于 `miniprogram/`，与 Android App 共用使用者自行部署的有猫腻 API，不包含独立数据库，也不提供公共线上服务。

## 环境要求

- Node.js 18 或更新版本
- pnpm 12
- 微信开发者工具
- 已部署并可通过 HTTPS 访问的有猫腻 API

## 配置与运行

1. 进入 `miniprogram/` 安装依赖：

   ```powershell
   pnpm install --frozen-lockfile
   ```

2. 在 `miniprogram/miniprogram/env.ts` 配置自己的 HTTPS API 根地址，不带 `/api/v1`。
3. 在 `miniprogram/project.config.json` 填写自己的微信 AppID。
4. 在微信公众平台将 API 域名加入合法请求、上传和下载域名。
5. 用微信开发者工具导入 `miniprogram/` 目录，编译并预览。

修改源码后，在 `miniprogram/` 运行校验：

```powershell
pnpm run verify
```

开发者工具本地预览可按需关闭域名校验；提交审核和发布前必须恢复并验证平台域名配置。不要提交 AppID 对应的私密凭据、AppSecret、生产环境配置或用户数据。

Android 联网版与服务端位于 `main` 分支；纯本地 Android 版位于 `catlink-local` 分支。

项目自有代码按 [MIT License](LICENSE) 授权，允许商业使用；再发布时须保留版权和许可证声明。第三方依赖及素材按各自原许可证使用。
