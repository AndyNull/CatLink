package com.catlink.app.ui.screens.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiChatHeaderStateTest {
    @Test
    fun `连接状态按请求 配置和网络优先级生成`() {
        assertEquals("请求中", state(loading = true).connectionLabel)
        assertEquals("待配置", state(configured = false).connectionLabel)
        assertEquals("当前离线", state(networkAvailable = false).connectionLabel)
        assertEquals("联网可用", state().connectionLabel)
    }

    @Test
    fun `模型和会话状态保留真实信息`() {
        val longModel = "provider/very-long-vision-language-model-2026-08-22"
        assertEquals(longModel, state(model = longModel).modelLabel)
        assertEquals("救助记录", state(sessionTitle = " 救助记录 ", messageCount = 8).sessionLabel)
        assertEquals("8 条消息", state(sessionTitle = null, messageCount = 8).sessionLabel)
        assertEquals("新会话", state(sessionTitle = null, messageCount = 0).sessionLabel)
        assertEquals("未选择模型", state(model = " ").modelLabel)
    }

    private fun state(
        model: String = "deepseek-chat",
        configured: Boolean = true,
        networkAvailable: Boolean = true,
        loading: Boolean = false,
        sessionTitle: String? = null,
        messageCount: Int = 0,
    ) = aiHeaderState(model, configured, networkAvailable, loading, sessionTitle, messageCount)
}
