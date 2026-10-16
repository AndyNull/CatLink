package com.catlink.app.ui.screens.about

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpPrivacyContentTest {
    private val copy = HelpPrivacyContent.sections
        .flatMap { section -> listOf(section.title) + section.lines }
        .joinToString("\n")

    @Test
    fun `说明覆盖真实数据边界`() {
        listOf(
            "私有数据库",
            "每 6 小时",
            "密文",
            "Android Keystore",
            "网络出口 IP",
            "精确定位",
            "OpenStreetMap",
            "不申请相机或存储权限",
            "Photo Picker",
            "游客可以阅读",
            "必须先登录",
            "领养人、合同、费用及私密备注",
            "OpenAI 兼容模型服务商",
            "AES-256-GCM",
            "明文可移植 ZIP",
            "媒体文件",
            "SHA-256 校验清单",
            "30 天",
            "Android 系统备份已关闭",
        ).forEach { required -> assertTrue("缺少披露：$required", required in copy) }
    }

    @Test
    fun `说明不包含过时结论或旧品牌`() {
        listOf(
            "CatLink ·",
            "社区规划中",
            "所有档案默认只保存在此设备",
            "不联网、不注册也可完整使用",
            "WebDAV 同步",
            "当前个人数据导出为明文 JSON 或 CSV ZIP",
            "不包含媒体文件",
            "Ktor",
            "RuoYi",
        ).forEach { forbidden -> assertFalse("仍包含过时说明：$forbidden", forbidden in copy) }
    }
}
