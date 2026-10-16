package com.catlink.app.ui.screens.notifications

import com.catlink.app.data.PublicApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsScreenTest {
    @Test
    fun `only adoption links open application list`() {
        assertTrue(opensAdoptionApplications(notification("adoption", "/adoption/12")))
        assertFalse(opensAdoptionApplications(notification("announcement", "/adoption/12")))
        assertFalse(opensAdoptionApplications(notification("adoption", "/post/12")))
    }

    private fun notification(kind: String, refId: String) = PublicApi.NotificationItem(
        id = "1",
        kind = kind,
        title = "通知",
        refType = "link",
        refId = refId,
    )
}
