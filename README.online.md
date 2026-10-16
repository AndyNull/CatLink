# 有猫腻（联网版）

联网版包含账号、跨设备档案同步、公开猫咪档案、社区和协作功能。使用者需要自行部署服务端与管理端，并维护自己的域名、数据库和对象存储。

**环境要求：** JDK 17、Android SDK API 35。Windows 使用仓库自带的 Gradle Wrapper；首次构建需要网络下载 Gradle 和 Android 依赖。

先按[服务端部署说明](catlink-admin-next/CLEAN_DEPLOY_1PANEL.md)部署 API、数据库与管理端，再在 App 登录页打开“服务器设置”，填入自己的 HTTPS 根地址，例如 `https://api.example.com`。地址不含 `/api/v1`，保存后 App 会请求对应服务。更换服务地址前先退出账号。

首次获取源码后，在项目根目录执行：

```powershell
.\gradlew.bat :app:assembleOnlineDebug
```

Debug APK 输出到 `app/build/outputs/apk/online/debug/app-online-debug.apk`，可安装到 Android 设备或模拟器验证。

服务端代码位于 `catlink-admin-next/`；健康检查为 `/healthz` 和 `/readyz`。部署完成后确认 HTTPS 反向代理将 API、管理端和分享页转发到正确入口，再在 App 中配置该域名。

签名 Release 前，在 `signing/keystore.properties` 配置 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`，然后执行 `:app:assembleOnlineRelease`。APK 输出到 `app/build/outputs/apk/online/release/`。JKS 和签名配置已排除在 Git 外，必须自行备份，不能提交到公开仓库。
