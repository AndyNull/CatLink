package com.catlink.app.data

import com.catlink.app.network.AuthApiClient
import com.catlink.app.network.AuthSessionSnapshot
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PublicApiFeedTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `feed normalizes same-origin media and rejects cross-origin urls`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"items":[{"id":"1","topic":"rescue","mediaUrls":"/uploads/a.png,https://cdn.example/b.webp"}],"nextCursor":null},"msg":"ok"}""",
            ),
        )
        val publicApi = PublicApi(
            AuthApiClient(
                baseUrlProvider = { server.url("/").toString() },
                authSessionProvider = { AuthSessionSnapshot(null, 0L) },
            ),
        )

        val post = publicApi.feed("rescue").getOrThrow().single()

        assertEquals(
            "${server.url("/").toString().trimEnd('/')}/uploads/a.png",
            post.mediaUrls,
        )
    }

    @Test
    fun `post detail normalizes relative media urls`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"id":"1","mediaUrls":"/uploads/detail.png"},"msg":"ok"}""",
            ),
        )
        val publicApi = PublicApi(
            AuthApiClient(
                baseUrlProvider = { server.url("/").toString() },
                authSessionProvider = { AuthSessionSnapshot(null, 0L) },
            ),
        )

        val post = publicApi.postDetail("1").getOrThrow()

        assertEquals("${server.url("/").toString().trimEnd('/')}/uploads/detail.png", post.mediaUrls)
    }

    @Test
    fun `nearby feed sends encoded city and district`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"items":[],"nextCursor":null},"msg":"ok"}"""))
        val publicApi = PublicApi(
            AuthApiClient(
                baseUrlProvider = { server.url("/").toString() },
                authSessionProvider = { AuthSessionSnapshot(null, 0L) },
            ),
        )

        publicApi.feed("rescue", "深圳市", "南山区").getOrThrow()

        assertEquals(
            "/api/v1/community/feed?topic=rescue&city=%E6%B7%B1%E5%9C%B3%E5%B8%82&district=%E5%8D%97%E5%B1%B1%E5%8C%BA&keyword=&updatedDays=&before=&limit=24",
            server.takeRequest().path,
        )
    }

    @Test
    fun `ad config decodes backend controlled height`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":[{"id":"7","slot":"community","heightMode":"fixed","heightValue":360}],"msg":"ok"}"""))
        val publicApi = PublicApi(
            AuthApiClient(
                baseUrlProvider = { server.url("/").toString() },
                authSessionProvider = { AuthSessionSnapshot(null, 0L) },
            ),
        )

        val ad = publicApi.adConfig("community").getOrThrow().single()

        assertEquals("fixed", ad.heightMode)
        assertEquals(360f, ad.heightValue)
    }

    @Test
    fun `post detail accepts null public cat reference`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"id":"1","catRef":null},"msg":"ok"}"""))
        val publicApi = PublicApi(
            AuthApiClient(
                baseUrlProvider = { server.url("/").toString() },
                authSessionProvider = { AuthSessionSnapshot(null, 0L) },
            ),
        )

        assertEquals(null, publicApi.postDetail("1").getOrThrow().catRef)
    }

    @Test
    fun `following feed uses authenticated dedicated route`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"items":[],"nextCursor":null},"msg":"ok"}"""))
        val publicApi = PublicApi(
            AuthApiClient(
                baseUrlProvider = { server.url("/").toString() },
                authSessionProvider = { AuthSessionSnapshot("access-token", 0L) },
            ),
        )

        assertEquals(emptyList<CommunityFeedItem>(), publicApi.followingFeed("rescue").getOrThrow())

        val request = server.takeRequest()
        assertEquals("/api/v1/community/feed/following?topic=rescue", request.path)
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
    }

    @Test
    fun `signed in feed sends bearer for liked state`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"items":[],"nextCursor":null},"msg":"ok"}"""))
        val publicApi = PublicApi(
            AuthApiClient(
                baseUrlProvider = { server.url("/").toString() },
                authSessionProvider = { AuthSessionSnapshot("access-token", 0L) },
            ),
        )

        publicApi.feed(authenticated = true).getOrThrow()

        assertEquals("Bearer access-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `temporary upload keeps server relative url for post confirmation`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"id":"9","url":"/uploads/catlink/misc/photo.png"},"msg":"ok"}"""))
        val publicApi = PublicApi(
            AuthApiClient(
                baseUrlProvider = { server.url("/").toString() },
                authSessionProvider = { AuthSessionSnapshot("access-token", 0L) },
            ),
        )

        val result = publicApi.uploadTemporaryMedia("photo.png", "base64").getOrThrow()

        assertEquals("9", result.id)
        assertEquals("/uploads/catlink/misc/photo.png", result.url)
    }

    @Test
    fun `post publish sends ordered stable media ids`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"id":"21"},"msg":"ok"}"""))
        val publicApi = PublicApi(
            AuthApiClient(
                baseUrlProvider = { server.url("/").toString() },
                authSessionProvider = { AuthSessionSnapshot("access-token", 0L) },
            ),
        )

        publicApi.createPost(
            PublicApi.PostInput(
                id = "5cba1fd6-1703-4d48-884a-35b2d6f312a2",
                topic = "rescue",
                title = "救助记录",
                body = "正文",
                mediaUrls = "/uploads/two.jpg,/uploads/one.jpg",
                mediaIds = listOf("12", "11"),
            ),
        ).getOrThrow()

        val request = server.takeRequest()
        assertEquals("/api/v1/community/posts", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"mediaIds\":[\"12\",\"11\"]"))
        assertTrue(body.contains("\"id\":\"5cba1fd6-1703-4d48-884a-35b2d6f312a2\""))
    }

    @Test
    fun `cat album upload is temporary and categorized`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"id":"10","url":"/uploads/catlink/cat/photo.webp"},"msg":"ok"}"""))
        val publicApi = PublicApi(
            AuthApiClient(
                baseUrlProvider = { server.url("/").toString() },
                authSessionProvider = { AuthSessionSnapshot("access-token", 0L) },
            ),
        )

        publicApi.uploadTemporaryCatMedia("photo.webp", "base64").getOrThrow()

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("/api/v1/media/upload", request.path)
        assertTrue(body.contains("\"category\":\"cat\""))
        assertTrue(body.contains("\"temporary\":true"))
    }

    @Test
    fun `persistent cat album upload returns media identity`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"id":"11","url":"/uploads/catlink/cat/album.jpg"},"msg":"ok"}"""))
        val publicApi = PublicApi(
            AuthApiClient(
                baseUrlProvider = { server.url("/").toString() },
                authSessionProvider = { AuthSessionSnapshot("access-token", 0L) },
            ),
        )

        val result = publicApi.uploadCatAlbumMedia("album.jpg", "base64").getOrThrow()

        assertEquals("11", result.id)
        assertEquals("/uploads/catlink/cat/album.jpg", result.url)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"category\":\"cat\""))
        assertTrue(body.contains("\"temporary\":false"))
    }

    @Test
    fun `avatar upload uses multipart without base64 inflation`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"id":"12","url":"/uploads/catlink/avatar/avatar.jpg"},"msg":"ok"}"""))
        val publicApi = PublicApi(
            AuthApiClient(
                baseUrlProvider = { server.url("/").toString() },
                authSessionProvider = { AuthSessionSnapshot("access-token", 0L) },
            ),
        )
        val file = File.createTempFile("avatar", ".jpg").apply { writeBytes(ByteArray(1024 * 1024)) }

        val url = publicApi.uploadMedia("avatar", file).getOrThrow()

        assertEquals("/uploads/catlink/avatar/avatar.jpg", url)
        val request = server.takeRequest()
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data; boundary="))
        assertTrue(request.bodySize < file.length() + 2048)
        val body = request.body.clone().readUtf8()
        assertTrue(body.contains("name=\"category\""))
        assertTrue(body.contains("avatar"))
        file.delete()
        Unit
    }

    @Test
    fun `temporary media cleanup targets owned media endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"id":"10"},"msg":"ok"}"""))
        val publicApi = PublicApi(
            AuthApiClient(
                baseUrlProvider = { server.url("/").toString() },
                authSessionProvider = { AuthSessionSnapshot("access-token", 0L) },
            ),
        )

        publicApi.deleteTemporaryMedia("10").getOrThrow()

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/media/10", request.path)
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
    }
}
