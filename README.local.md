# 有猫腻（本地版）

本地版适合只想记录自己照看的猫咪、不需要社区和账号服务的使用者。档案与照片保存在设备上，不会同步到本项目的服务端；在线 AI 可以单独配置。

**环境要求：** JDK 17、Android SDK API 35。Windows 使用仓库自带的 Gradle Wrapper；首次构建需要网络下载 Gradle 和 Android 依赖。

获取源码后切到 `catlink-local` 分支，在项目根目录构建：

```powershell
.\gradlew.bat :app:assembleLocalDebug
```

Debug APK 输出到 `app/build/outputs/apk/local/debug/app-local-debug.apk`，可安装到 Android 设备或模拟器验证。签名 Release 前，在 `signing/keystore.properties` 配置 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`，然后执行：

```powershell
.\gradlew.bat :app:assembleLocalRelease
```

Release APK 输出到 `app/build/outputs/apk/local/release/`。签名密钥和配置不提交到 Git，需自行妥善备份。首次安装后请定期从“备份与恢复”导出备份；联网版导出的资料备份可在本地版导入，本地版不会恢复联网账号、社区或云端会话。
