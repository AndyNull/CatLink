package com.catlink.app.network

import com.catlink.contracts.AccountSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class AuthApiClientTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(token: String? = "test-token", base: String? = null): AuthApiClient =
        AuthApiClient(
            baseUrlProvider = { base ?: server.url("/").toString() },
            authSessionProvider = { AuthSessionSnapshot(token, 0L) },
            json = json,
        )

    @Test
    fun `GET sends bearer header and parses payload`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"a1","email":"e@x.com","emailVerified":true,"displayName":""}""",
            ),
        )
        val result = client().get("/account", authenticated = true) {
            json.decodeFromString<AccountSummary>(it)
        }
        assertTrue(result.isSuccess)
        assertEquals("a1", result.getOrThrow().id)
        val request = server.takeRequest()
        assertEquals("/api/v1/account", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        assertTrue(request.getHeader("X-Device-Name").isNullOrEmpty())
    }

    @Test
    fun `custom headers are attached`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"accepted":true}"""))
        client().postForUnit(
            "/auth/email/verify",
            """{"token":"t"}""",
            authenticated = false,
            headers = mapOf("X-Device-Name" to "test-device"),
        )
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/email/verify", request.path)
        assertEquals("test-device", request.getHeader("X-Device-Name"))
        assertEquals("""{"token":"t"}""", request.body.readUtf8())
    }

    @Test
    fun `account export accepts response larger than normal API limit`() = runBlocking {
        val payload = "x".repeat(9 * 1024 * 1024)
        server.enqueue(MockResponse().setBody(payload))

        val result = client().getLarge("/account/export") { it.length }

        assertEquals(payload.length, result.getOrThrow())
    }

    @Test
    fun `401 envelope maps to Authentication error`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"error":{"code":"UNAUTHENTICATED","message":"登录状态已失效","requestId":"req-1"}}""",
            ),
        )
        val result = client().get<Unit>("/account") { }
        val error = (result.exceptionOrNull() as OnlineErrorException).error
        assertEquals(OnlineError.Authentication(expired = false), error)
    }

    @Test
    fun `unknown error code degrades to Server not crash`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(500).setBody(
                """{"error":{"code":"SOME_NEW_CODE","message":"x"}}""",
            ),
        )
        val result = client().get<Unit>("/vault") { }
        assertTrue(result.exceptionOrNull() is OnlineErrorException)
        assertTrue((result.exceptionOrNull() as OnlineErrorException).error is OnlineError.Server)
    }

    @Test
    fun `malformed body maps to Server format error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))
        val result = client().get("/account") { json.decodeFromString<AccountSummary>(it) }
        val error = (result.exceptionOrNull() as OnlineErrorException).error
        assertTrue(error is OnlineError.Server)
    }

    @Test
    fun `business code 7 maps to Authentication on HTTP 200`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":7,"data":null,"msg":"设备会话已失效，请重新登录"}"""))
        val result = client(token = null).get<Unit>("/account", authenticated = false) { }
        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as OnlineErrorException).error
        assertEquals(OnlineError.Authentication(expired = true), error)
    }

    @Test
    fun `connection failure maps to Offline`() = runBlocking {
        val offline = client(base = "http://127.0.0.1:1")
        val result = offline.get<Unit>("/account") { }
        assertEquals(OnlineError.Offline, (result.exceptionOrNull() as OnlineErrorException).error)
    }

    @Test
    fun `request from old session is not replayed with new account token`() = runBlocking {
        var token = "account-a-token"
        var generation = 10L
        var snapshotReads = 0
        val client = AuthApiClient(
            baseUrlProvider = { server.url("/").toString() },
            authSessionProvider = {
                snapshotReads += 1
                AuthSessionSnapshot(token, generation)
            },
            refreshAccessToken = { _, expectedGeneration ->
                if (expectedGeneration != generation) {
                    Result.failure(OnlineErrorException(OnlineError.Authentication(expired = true)))
                } else {
                    Result.success(token)
                }
            },
            json = json,
        )
        server.enqueue(
            MockResponse()
                .setBodyDelay(150, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setResponseCode(401)
                .setBody("""{"error":{"code":"UNAUTHENTICATED","message":"expired"}}"""),
        )

        val oldRequest = async {
            client.post<Unit>("/community/posts/42/like", null, authenticated = true) { }
        }
        delay(25)
        generation = 12L
        token = "account-b-token"

        assertTrue(oldRequest.await().isFailure)
        assertEquals(1, snapshotReads)
        assertEquals(1, server.requestCount)
        assertEquals("Bearer account-a-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `401 triggers refresh once and retries with new token`() = runBlocking {
        var refreshed = false
        val client = AuthApiClient(
            baseUrlProvider = { server.url("/").toString() },
            authSessionProvider = {
                AuthSessionSnapshot(if (refreshed) "new-token" else "expired-token", 0L)
            },
            refreshAccessToken = { failedToken, expectedGeneration ->
                assertEquals("expired-token", failedToken)
                assertEquals(0L, expectedGeneration)
                refreshed = true
                Result.success("new-token")
            },
            json = json,
        )
        // 第一次请求返回 401（token 过期）
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"UNAUTHENTICATED","message":"expired"}}"""))
        // 刷新后重试成功
        server.enqueue(
            MockResponse().setBody("""{"ok":true}""").addHeader("Content-Type", "application/json"),
        )
        val result = client.get("/public/cats", authenticated = true) { it }
        val ok = result.getOrNull()
        assertTrue("should refresh on 401", refreshed)
        assertTrue("retry should succeed: $result", ok != null && ok.contains("ok"))
        // 两个请求：第一个带旧 token，第二个带新 token
        val first = server.takeRequest()
        assertEquals("Bearer expired-token", first.headers["Authorization"])
        val second = server.takeRequest()
        assertEquals("Bearer new-token", second.headers["Authorization"])
    }

    @Test
    fun `multipart file upload streams without base64 inflation`() = runBlocking {
        val file = File.createTempFile("catlink-upload", ".jpg").apply {
            writeBytes(ByteArray(1024 * 1024) { (it % 251).toByte() })
            deleteOnExit()
        }
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"id":"9","url":"/uploads/photo.jpg"}}"""))

        val result = client().postFile(
            path = "/media/upload",
            fields = mapOf("category" to "post", "temporary" to "true"),
            file = file,
            filename = "photo.jpg",
            mediaType = "image/jpeg",
        ) { it }

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data; boundary="))
        assertTrue("multipart payload should stay near source size", request.bodySize < file.length() + 2048)
        assertTrue(request.body.clone().readUtf8().contains("name=\"category\""))
        file.delete()
        Unit
    }
}
