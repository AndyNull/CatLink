# 有猫腻（本地版）

这是有猫腻的 Android 纯本地版本，适合不需要社区、公开档案和账号同步，只想记录自己照看的猫咪的人。猫咪档案与照片保存在设备上，不会同步到本项目的服务端；AI 功能可以单独配置。

## 使用与备份

首次使用不需要注册账号或部署服务端。请定期从 App 的“备份与恢复”导出备份。本地版可以导入联网版导出的档案备份，但不会恢复联网账号、社区内容或云端会话。

## 构建 Android

需要 JDK 17 和 Android SDK API 35。Windows 可使用仓库自带的 Gradle Wrapper：

```powershell
# 本地版 Debug
.\gradlew.bat :app:assembleLocalDebug

# 本地版 Release
.\gradlew.bat :app:assembleLocalRelease
```

APK 位于 `app/build/outputs/apk/local/`。Release 签名需要自行准备 JKS，并在 `signing/keystore.properties` 配置签名信息；密钥和配置文件不得提交到仓库。

## 项目边界

本分支不包含社区、公开猫咪档案和云端同步功能。在线 AI 是独立配置的网络功能，不会把猫咪档案同步到服务端。联网版与服务端源码见 `main` 分支；微信小程序见 `miniprogram` 分支。

项目自有代码按 [MIT License](LICENSE) 授权，允许商业使用；再发布时须保留版权和许可证声明。第三方依赖及素材按各自原许可证使用。
