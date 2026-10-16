package com.catlink.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPagingTest {
    @Test
    fun `next page starts only near a nonempty list end`() {
        assertFalse(shouldLoadNextLocationPage(lastVisible = 16, itemCount = 20, hasMore = true, loading = false))
        assertTrue(shouldLoadNextLocationPage(lastVisible = 17, itemCount = 20, hasMore = true, loading = false))
        assertFalse(shouldLoadNextLocationPage(lastVisible = 19, itemCount = 20, hasMore = false, loading = false))
        assertFalse(shouldLoadNextLocationPage(lastVisible = 19, itemCount = 20, hasMore = true, loading = true))
        assertFalse(shouldLoadNextLocationPage(lastVisible = 0, itemCount = 0, hasMore = true, loading = false))
    }
}
