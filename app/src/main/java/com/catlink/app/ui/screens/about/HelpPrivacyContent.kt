package com.catlink.app.ui.screens.about

internal data class HelpPrivacySection(
    val title: String,
    val lines: List<String>,
)

internal object HelpPrivacyContent {
    val sections = listOf(
        HelpPrivacySection(
            title = "数据与账号",
            lines = listOf(
                "猫咪、医疗、领养流程、合同、回访和待办等资料先保存在 App 私有数据库，无账号也可离线使用。",
                "登录后，App 会在联网时立即并约每 6 小时尝试同步；业务记录先在设备端加密为密文再上传，退出登录后不再执行同步。",
                "登录会话、AI 密钥和同步密钥由 Android Keystore 保护；服务端仍会处理账号、公开社区、通知及必要的安全日志。",
            ),
        ),
        HelpPrivacySection(
            title = "定位与权限",
            lines = listOf(
                "首页和社区的附近功能优先由 Gin 根据网络出口 IP 获取城市或区县级粗略位置，不需要设备定位权限。",
                "只有点击“精确定位”并授权后，App 才读取设备坐标；系统无法解析区县时，坐标会发送至 Gin，并由配置的地图服务或 OpenStreetMap 反向解析。",
                "App 不申请相机或存储权限：拍照交给系统相机，相册选择使用系统 Photo Picker；拒绝定位不会影响档案等其他功能。",
                "通知权限只用于系统待办提醒；拒绝后不会删除 App 内待办，也不影响 App 内消息。",
            ),
        ),
        HelpPrivacySection(
            title = "媒体与社区",
            lines = listOf(
                "档案照片和合同图片选取后会复制到 App 私有目录，避免原相册授权失效；上传头像、帖子或公开猫咪照片时，所选文件会发送至 Gin。",
                "游客可以阅读公开社区和公开猫咪；发帖、评论、互动、上传媒体和发布猫咪必须先登录。",
                "猫咪档案只有在你确认发布公开草稿后，才上传白名单字段和所选照片；领养人、合同、费用及私密备注不包含在公开草稿中。",
            ),
        ),
        HelpPrivacySection(
            title = "AI 助手",
            lines = listOf(
                "AI API Key 由 Android Keystore 加密保存在本机，不会写入 Gin 业务数据库。",
                "发送消息时，当前文本、最近对话、本地生成的摘要和可选的压缩图片会直接发送至你选择的 OpenAI 兼容模型服务商，Gin 不转发 AI 助手对话。",
                "AI 回复仅供参考；使用前请同时阅读所选模型服务商的数据处理规则。",
            ),
        ),
        HelpPrivacySection(
            title = "备份、导出与删除",
            lines = listOf(
                "加密备份使用 AES-256-GCM，并写入你选择的位置；请自行保管口令，遗失后无法恢复。",
                "个人数据导出为明文可移植 ZIP，包含当前设备的完整 JSON、逐表 CSV、HTML 摘要、媒体文件、manifest 和 SHA-256 校验清单；登录时还会包含服务端社区、互动、消息与账号数据。",
                "删除的猫咪档案进入回收站，30 天内可恢复；到期后自动清理，也可手动永久删除。",
                "Android 系统备份已关闭；卸载 App 会删除本机私有数据，但不会自动删除已发布的社区内容或云端账号数据。",
            ),
        ),
    )
}
