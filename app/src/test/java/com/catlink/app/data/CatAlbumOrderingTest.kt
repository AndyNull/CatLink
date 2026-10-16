package com.catlink.app.data

import com.catlink.app.data.db.MediaEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CatAlbumOrderingTest {
    @Test
    fun `album supports newest and oldest ordering`() {
        val old = MediaEntity("old", "cat", "old", sortOrder = 10, createdAt = 10)
        val latest = MediaEntity("latest", "cat", "latest", sortOrder = 20, createdAt = 20)

        assertEquals(listOf("latest", "old"), sortedAlbumMedia(listOf(old, latest), newestFirst = true).map { it.id })
        assertEquals(listOf("old", "latest"), sortedAlbumMedia(listOf(latest, old), newestFirst = false).map { it.id })
    }
}
