package com.catlink.app.ui.screens.community

import com.catlink.app.data.PublicApi
import com.catlink.app.network.AuthApiClient
import com.catlink.app.network.AuthSessionSnapshot
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostMediaUploadTest {
    @Test
    fun `four photos stream with bounded concurrency and stable order`() = runBlocking {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val sequence = AtomicInteger()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val now = active.incrementAndGet()
                    maximum.updateAndGet { maxOf(it, now) }
                    Thread.sleep(250)
                    active.decrementAndGet()
                    val id = sequence.incrementAndGet()
                    return MockResponse().setBody("""{"code":0,"data":{"id":"$id","url":"/uploads/$id.jpg"}}""")
                }
            }
            start()
        }
        val directory = Files.createTempDirectory("catlink-post-upload").toFile()
        try {
            val photos = (1..4).map { index ->
                val file = directory.resolve("post-$index.jpg").apply {
                    writeBytes(ByteArray(1024 * 1024) { (it % 251).toByte() })
                }
                PendingPostPhoto(index.toString(), "source-$index", file.absolutePath)
            }
            val api = PublicApi(
                AuthApiClient(
                    baseUrlProvider = { server.url("/").toString() },
                    authSessionProvider = { AuthSessionSnapshot("token", 0L) },
                )
            )
            val started = System.nanoTime()
            val result = uploadPostPhotos(api, photos, directory)
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000

            assertEquals(0, result.failed)
            assertEquals(listOf("1", "2", "3", "4"), result.photos.map(PendingPostPhoto::id))
            assertEquals(2, maximum.get())
            assertTrue("two upload waves should beat one-second serial time: $elapsedMillis ms", elapsedMillis < 900)
            repeat(4) {
                val request = server.takeRequest()
                assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data; boundary="))
                assertTrue(request.bodySize < 1024 * 1024 + 2048)
            }
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }
}
