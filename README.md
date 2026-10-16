# 有猫腻（联网版）

这是有猫腻 Android 联网版与配套自建服务端的主分支，适合需要多设备同步、公开猫咪档案和社区协作的救助者。项目不提供公共服务地址；使用前需要自行部署服务端，并在 App 登录页的“服务器设置”中填写自己的 HTTPS API 根地址。

## 功能

- 猫咪档案、照片、健康记录、领养人、回访和待办
- 账号登录、多设备同步、公开档案、关注、社区动态与救助协作
- AI 助手、统计、备份恢复和管理端
- Go API、管理端、数据库迁移与 Docker 部署配置

## 部署服务端

部署入口为 `catlink-admin-next/`。按[1Panel 部署说明](catlink-admin-next/CLEAN_DEPLOY_1PANEL.md)配置自己的环境文件、数据库、对象存储和 HTTPS 域名，再按文档启动服务并验证健康检查。部署辅助脚本位于根目录 `deploy/`。

部署完成后，在 App 登录页打开“服务器设置”，填入服务根地址，例如 `https://api.example.com`，不要附加 `/api/v1`。服务端不会自动连接 App，地址由使用者在 App 内配置；更换服务前先退出当前账号。

## 构建 Android

需要 JDK 17 和 Android SDK API 35。Windows 可使用仓库自带的 Gradle Wrapper：

```powershell
# 联网版 Debug
.\gradlew.bat :app:assembleOnlineDebug

# 联网版 Release
.\gradlew.bat :app:assembleOnlineRelease
```

APK 位于 `app/build/outputs/apk/online/`。Release 签名需要自行准备 JKS，并在 `signing/keystore.properties` 配置签名信息；密钥和配置文件不得提交到仓库。

## 其他版本

- `catlink-local`：纯本地 Android 版，不含社区和档案分享，不要求部署本项目服务端。
- `miniprogram`：微信小程序，使用者需配置自己的 API HTTPS 地址和微信 AppID。

每个分支的根目录 README 都是该版本的专属说明。项目自有代码按 [MIT License](LICENSE) 授权，允许商业使用；再发布时须保留版权和许可证声明。第三方依赖及素材按各自原许可证使用。
