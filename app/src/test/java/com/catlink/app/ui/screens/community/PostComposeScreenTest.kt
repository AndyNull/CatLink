package com.catlink.app.ui.screens.community

import com.catlink.app.data.PublicApi
import com.catlink.app.network.AuthApiClient
import com.catlink.app.network.AuthSessionSnapshot
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class PostComposeScreenTest {
    @Test
    fun `remaining photo slots never allow more than four`() {
        assertEquals(4, remainingPostPhotoSlots(0))
        assertEquals(1, remainingPostPhotoSlots(3))
        assertEquals(0, remainingPostPhotoSlots(4))
        assertEquals(0, remainingPostPhotoSlots(8))
    }

    @Test
    fun `photo order can move without changing identity`() {
        val photos = listOf(photo("a"), photo("b"), photo("c"))

        assertEquals(listOf("b", "a", "c"), movePostPhoto(photos, "b", -1).map { it.id })
        assertEquals(listOf("a", "c", "b"), movePostPhoto(photos, "b", 1).map { it.id })
        assertEquals(photos, movePostPhoto(photos, "missing", 1))
    }

    @Test
    fun `partial upload retry sends only failed post photo`() = runBlocking {
        val twoAttempts = AtomicInteger()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.clone().readUtf8()
                    return when {
                        body.contains("post-one.jpg") -> MockResponse().setBody("""{"code":0,"data":{"id":"11","url":"/uploads/post/one.jpg"},"msg":"ok"}""")
                        twoAttempts.incrementAndGet() == 1 -> MockResponse().setBody("""{"code":1,"data":{},"msg":"暂时不可用"}""")
                        else -> MockResponse().setBody("""{"code":0,"data":{"id":"12","url":"/uploads/post/two.jpg"},"msg":"ok"}""")
                    }
                }
            }
            start()
        }
        val root = File(System.getProperty("java.io.tmpdir"), "post-test-${System.nanoTime()}").apply { mkdirs() }
        val one = File(root, "post-one.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val two = File(root, "post-two.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        try {
            val api = PublicApi(
                AuthApiClient(
                    baseUrlProvider = { server.url("/").toString() },
                    authSessionProvider = { AuthSessionSnapshot("token", 0L) },
                ),
            )

            val first = uploadPostPhotos(
                api,
                listOf(photo("one", one.absolutePath), photo("two", two.absolutePath)),
                root,
            )

            assertEquals(1, first.failed)
            assertEquals("11", first.photos[0].serverId)
            assertTrue(first.photos[1].failed)
            val retried = uploadPostPhotos(api, first.photos, root)

            assertEquals(0, retried.failed)
            assertEquals(listOf("11", "12"), retried.photos.map { it.serverId })
            assertEquals(3, server.requestCount)
            repeat(3) {
                val request = server.takeRequest()
                assertEquals("/api/v1/media/upload", request.path)
                assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data; boundary="))
                assertTrue(request.body.clone().readUtf8().contains("name=\"category\""))
            }
        } finally {
            one.delete()
            two.delete()
            root.delete()
            server.shutdown()
        }
    }

    @Test
    fun `configuration change keeps post draft`() {
        assertFalse(shouldCleanupPostDraft(isChangingConfigurations = true))
        assertTrue(shouldCleanupPostDraft(isChangingConfigurations = false))
    }

    private fun photo(id: String, path: String = "/tmp/$id.jpg") =
        PendingPostPhoto(id = id, sourceKey = id, localPath = path)
}
