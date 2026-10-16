package com.catlink.app.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdoptionApplicationsScreenTest {
    @Test
    fun `owner actions follow server state machine`() {
        assertEquals(listOf("contact", "home_visit", "approved", "rejected"), adoptionOwnerActions("pending").map { it.second })
        assertEquals(listOf("home_visit", "approved", "rejected"), adoptionOwnerActions("contact").map { it.second })
        assertEquals(listOf("approved", "rejected"), adoptionOwnerActions("home_visit").map { it.second })
        assertTrue(adoptionOwnerActions("approved").isEmpty())
        assertEquals("已撤回", adoptionStatus("withdrawn"))
        assertEquals(listOf("https://example.com/a", "https://example.com/b"), adoptionMediaLinks("https://example.com/a, https://example.com/b"))
    }
}
