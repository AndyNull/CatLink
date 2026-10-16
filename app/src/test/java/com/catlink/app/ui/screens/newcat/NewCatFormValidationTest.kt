package com.catlink.app.ui.screens.newcat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewCatFormValidationTest {
    @Test
    fun `封面缺失优先阻止提交`() {
        assertEquals("请先上传封面照片", catFormValidationError("小橘", ""))
    }

    @Test
    fun `昵称缺失阻止提交`() {
        assertEquals("请填写猫咪昵称", catFormValidationError(" ", "/data/user/0/app/files/photos/cover.jpg"))
    }

    @Test
    fun `封面和昵称齐全允许提交`() {
        assertNull(catFormValidationError("小橘", "/data/user/0/app/files/photos/cover.jpg"))
    }

    @Test
    fun `配置变更不清理草稿但离开页面清理`() {
        assertFalse(shouldDeleteDraftOnDispose(isChangingConfigurations = true))
        assertTrue(shouldDeleteDraftOnDispose(isChangingConfigurations = false))
    }
}
