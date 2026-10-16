package com.catlink.app.network

import com.catlink.app.runtime.AppRuntimeStateStore
import com.catlink.app.runtime.SessionRuntimeState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AuthSessionManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var store: FakeSessionStorage
    private lateinit var runtime: AppRuntimeStateStore
    private val json = Json { ignoreUnknownKeys = true }

    private val getInfoBody =
        """{"code":200,"data":{"user":{"userId":"a1","nickName":"x","userName":"x"}}}"""

    private val tokensBody = """
        {"code":0,"data":{"accessToken":"at-2","refreshToken":"rt-2","accessExpiresAt":4102444800000,"account":{"id":"a1","email":"e@x.com","emailVerified":true,"displayName":"x","nickname":"x"},"deviceId":"device-2"},"msg":"ok"}
        """.trimIndent()

    private val unverifiedTokensBody = tokensBody
        .replace("at-2", "at-unverified")
        .replace("rt-2", "rt-unverified")
        .replace("e@x.com", "new@x.com")
        .replace("\"emailVerified\":true", "\"emailVerified\":false")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakeSessionStorage()
        runtime = AppRuntimeStateStore()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun manager(): AuthSessionManager = AuthSessionManager(
        api = AuthApiClient(
            baseUrlProvider = { server.url("/").toString() },
            authSessionProvider = { AuthSessionSnapshot(null, 0L) },
            json = json,
        ),
        store = store,
        runtime = runtime,
        json = json,
    )

    private suspend fun AuthSessionManager.loginForTest(
        email: String = "e@x.com",
        password: String = "password123",
        deviceName: String = "device-1",
    ): Result<Unit> = login(email, password, "captcha-id", "1234", deviceName)

    @Test
    fun `captcha request decodes challenge and refreshes previous id`() = runBlocking {
        val challengeBody = """{"code":0,"data":{"captchaId":"captcha-id-1","imageBase64":"data:image/png;base64,AA==","length":4,"expiresInSeconds":180},"msg":"ok"}"""
        server.enqueue(MockResponse().setBody(challengeBody))
        server.enqueue(MockResponse().setBody(challengeBody.replace("captcha-id-1", "captcha-id-2")))
        val manager = manager()

        val first = manager.captcha().getOrThrow()
        val second = manager.captcha(first.captchaId).getOrThrow()

        assertEquals("captcha-id-1", first.captchaId)
        assertEquals("captcha-id-2", second.captchaId)
        val initialRequest = server.takeRequest()
        assertEquals("/api/v1/auth/captcha", initialRequest.path)
        assertEquals("{}", initialRequest.body.readUtf8())
        val refreshRequest = server.takeRequest()
        assertEquals("captcha-id-1", json.parseToJsonElement(refreshRequest.body.readUtf8()).jsonObject["captchaId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `captcha rejects malformed image payload`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"captchaId":"captcha-id-1","imageBase64":"data:image/png;base64,%%%","length":4,"expiresInSeconds":180},"msg":"ok"}""",
            ),
        )

        assertTrue(manager().captcha().isFailure)
    }

    @Test
    fun `concurrent requireAccessToken refreshes only once`() = runBlocking {
        server.enqueue(MockResponse().setBody(tokensBody))
        store.saveSession(refreshToken = "rt-1", accountId = "a1", email = "e@x.com", emailVerified = true)

        val manager = manager()
        coroutineScope {
            launch { manager.requireAccessToken() }
            launch { manager.requireAccessToken() }
        }
        assertEquals(1, server.requestCount)
        assertEquals("/api/v1/auth/refresh", server.takeRequest().path)
        assertEquals("rt-2", store.refreshToken())
        assertEquals(SessionRuntimeState.SignedIn("a1", true), runtime.state.value.session)
    }

    @Test
    fun `refresh rejection clears local session`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"UNAUTHENTICATED","message":"失效"}}"""))
        store.saveSession(refreshToken = "rt-1", accountId = "a1", email = "e@x.com", emailVerified = true)

        val manager = manager()
        val result = manager.requireAccessToken()
        assertTrue(result.isFailure)
        assertNull(store.refreshToken())
        assertEquals(SessionRuntimeState.Guest, runtime.state.value.session)
    }

    @Test
    fun `login stores session and updates runtime`() = runBlocking {
        server.enqueue(MockResponse().setBody(tokensBody))
        val manager = manager()
        val result = manager.loginForTest()
        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/login", request.path)
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("captcha-id", body["captchaId"]?.jsonPrimitive?.content)
        assertEquals("1234", body["captcha"]?.jsonPrimitive?.content)
        assertTrue("code" !in body)
        assertEquals("rt-2", store.refreshToken())
        assertEquals(SessionRuntimeState.SignedIn("a1", true), runtime.state.value.session)
        assertEquals("at-2", manager.currentAccessToken())
    }

    @Test
    fun `new unverified login does not inherit previous account verification`() = runBlocking {
        store.saveSession(refreshToken = "old-rt", accountId = "old", email = "old@x.com", emailVerified = true)
        server.enqueue(MockResponse().setBody(unverifiedTokensBody))
        val manager = manager()

        assertTrue(manager.loginForTest(email = "new@x.com", deviceName = "device-2").isSuccess)

        assertEquals("rt-unverified", store.refreshToken())
        assertEquals("new@x.com", store.email())
        assertEquals(false, store.emailVerified())
        assertEquals(SessionRuntimeState.SignedIn("a1", false), runtime.state.value.session)
    }

    @Test
    fun `forced refresh replaces still valid access token`() = runBlocking {
        server.enqueue(MockResponse().setBody(tokensBody))
        server.enqueue(MockResponse().setBody(tokensBody.replace("at-2", "at-3").replace("rt-2", "rt-3")))
        val manager = manager()

        assertTrue(manager.loginForTest().isSuccess)
        assertEquals("at-2", manager.currentAccessToken())
        assertTrue(manager.requireAccessToken(force = true).isSuccess)

        assertEquals("at-3", manager.currentAccessToken())
        assertEquals("rt-3", store.refreshToken())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `stale 401 reuses already refreshed access token`() = runBlocking {
        server.enqueue(MockResponse().setBody(tokensBody))
        server.enqueue(MockResponse().setBody(tokensBody.replace("at-2", "at-3").replace("rt-2", "rt-3")))
        val manager = manager()

        assertTrue(manager.loginForTest().isSuccess)
        val failedToken = manager.currentAccessToken()
        assertTrue(manager.requireAccessToken(force = true, failedToken = failedToken).isSuccess)
        assertTrue(manager.requireAccessToken(force = true, failedToken = failedToken).isSuccess)

        assertEquals("at-3", manager.currentAccessToken())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `restore after login preserves signed in runtime`() = runBlocking {
        server.enqueue(MockResponse().setBody(tokensBody))
        val manager = manager()

        assertTrue(manager.loginForTest().isSuccess)
        assertTrue(manager.restoreSession().isSuccess)

        assertEquals(1, server.requestCount)
        assertEquals("rt-2", store.refreshToken())
        assertEquals("at-2", manager.currentAccessToken())
        assertEquals(SessionRuntimeState.SignedIn("a1", true), runtime.state.value.session)
    }

    @Test
    fun `login racing stale restore keeps the new session`() = runBlocking {
        store.saveSession(refreshToken = "stale-rt", accountId = "old", email = "old@x.com", emailVerified = true)
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBodyDelay(200, TimeUnit.MILLISECONDS)
                .setBody("""{"error":{"code":"UNAUTHENTICATED","message":"失效"}}"""),
        )
        server.enqueue(MockResponse().setBody(tokensBody))
        val manager = manager()

        val restore = async { manager.restoreSession() }
        delay(25)
        val login = async { manager.loginForTest() }

        assertTrue(restore.await().isFailure)
        assertTrue(login.await().isSuccess)
        assertEquals("rt-2", store.refreshToken())
        assertEquals("at-2", manager.currentAccessToken())
        assertEquals(SessionRuntimeState.SignedIn("a1", true), runtime.state.value.session)
        assertEquals("/api/v1/auth/refresh", server.takeRequest().path)
        assertEquals("/api/v1/auth/login", server.takeRequest().path)
    }

    @Test
    fun `restoreSession with no refresh token becomes Guest`() = runBlocking {
        val manager = manager()
        val result = manager.restoreSession()
        assertTrue(result.isSuccess)
        assertEquals(SessionRuntimeState.Guest, runtime.state.value.session)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `restoreSession network failure leaves retryable guest state`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("service unavailable"))
        store.saveSession(refreshToken = "rt-1", accountId = "a1", email = "e@x.com", emailVerified = true)
        val manager = manager()

        assertTrue(manager.restoreSession().isFailure)
        assertEquals(SessionRuntimeState.Guest, runtime.state.value.session)
        assertEquals("rt-1", store.refreshToken())
    }

    @Test
    fun `logout racing delayed refresh cannot restore the cleared session`() = runBlocking {
        store.saveSession(refreshToken = "rt-1", accountId = "a1", email = "e@x.com", emailVerified = true)
        server.enqueue(
            MockResponse()
                .setBodyDelay(200, TimeUnit.MILLISECONDS)
                .setBody(tokensBody.replace("at-2", "at-late").replace("rt-2", "rt-late")),
        )
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"ok":true},"msg":"ok"}"""))
        val manager = manager()

        val refresh = async { manager.requireAccessToken(force = true) }
        delay(25)
        val logout = async { manager.logout() }

        assertTrue(refresh.await().isSuccess)
        assertTrue(logout.await().isSuccess)
        assertNull(store.refreshToken())
        assertNull(manager.currentAccessToken())
        assertEquals(SessionRuntimeState.Guest, runtime.state.value.session)
        assertEquals("/api/v1/auth/refresh", server.takeRequest().path)
        val logoutRequest = server.takeRequest()
        assertEquals("/api/v1/auth/logout", logoutRequest.path)
        assertEquals("Bearer at-late", logoutRequest.getHeader("Authorization"))
    }

    @Test
    fun `logout clears session even when server unreachable`() = runBlocking {
        store.saveSession(refreshToken = "rt-1", accountId = "a1", email = "e@x.com", emailVerified = true)
        val manager = manager()
        // 先建立有效 access：login 成功响应
        server.enqueue(MockResponse().setBody(tokensBody))
        val login = manager.loginForTest()
        assertTrue(login.isSuccess)
        // logout 请求返回500，但本地会话必须清除
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"code":"INTERNAL_ERROR","message":"x"}}"""))
        val result = manager.logout()
        assertTrue(result.isFailure)
        assertNull(store.refreshToken())
        assertEquals(SessionRuntimeState.Guest, runtime.state.value.session)
        assertNull(manager.currentAccessToken())
    }
}

private class FakeSessionStorage : SessionStorage {
    private var refresh: String? = null
    private var accountId: String? = null
    private var email: String? = null
    private var verified: Boolean = false

    override fun saveSession(refreshToken: String, accountId: String, email: String, emailVerified: Boolean): Boolean {
        refresh = refreshToken
        this.accountId = accountId
        this.email = email
        verified = emailVerified
        return true
    }

    override fun refreshToken(): String? = refresh
    override fun accountId(): String? = accountId
    override fun email(): String? = email
    override fun emailVerified(): Boolean = verified
    override fun clear(): Boolean {
        refresh = null
        accountId = null
        email = null
        verified = false
        return true
    }
}
