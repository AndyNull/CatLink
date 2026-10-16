package com.catlink.app.network

import com.catlink.app.runtime.AppRuntimeStateStore
import com.catlink.app.runtime.SessionRuntimeState
import com.catlink.contracts.AccountSummary
import com.catlink.contracts.AuthTokensResponse
import com.catlink.contracts.DeviceSessionsResponse
import com.catlink.contracts.EmailVerificationRequest
import com.catlink.contracts.LoginCaptchaRequest
import com.catlink.contracts.LoginCaptchaResponse
import com.catlink.contracts.LoginRequest
import com.catlink.contracts.PasswordResetConfirmRequest
import com.catlink.contracts.PasswordResetRequest
import com.catlink.contracts.RefreshRequest
import com.catlink.contracts.RegisterRequest
import com.catlink.contracts.ResendVerificationRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 账号会话管理：access token 仅驻留内存，refresh token 加密持久化；
 * 多请求并发触发刷新时由 Mutex 串行化；刷新失败且认证失效时清除本地会话。
 */
class AuthSessionManager(
    private val api: AuthApiClient,
    private val store: SessionStorage,
    private val runtime: AppRuntimeStateStore,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true },
    private val now: () -> Long = System::currentTimeMillis,
    private val accessLeewayMillis: Long = 30_000,
) {
    private val refreshMutex = Mutex()
    @Volatile
    private var publishedSnapshot = AuthSessionSnapshot(null, 0L)
    private var accessToken: String? = null
    private var accessExpiresAt: Long = 0L
    private var sessionGeneration: Long = 0L
    private var sessionInvalidated: Boolean = false

    init {
        runtime.updateSession(
            if (store.refreshToken() != null) SessionRuntimeState.Restoring else SessionRuntimeState.Guest,
        )
    }

    /** 供 AuthApiClient 注入当前 access token。 */
    fun currentAccessToken(): String? = publishedSnapshot.accessToken

    fun currentSessionSnapshot(): AuthSessionSnapshot = publishedSnapshot

    /** 当前是否具备登录会话（有 token 或本地会话）。 */
    fun isSignedIn(): Boolean = accessToken != null || store.refreshToken() != null

    /** 当前会话邮箱（本地摘要，不联网）。 */
    fun currentEmail(): String? = store.email()

    /** 当前会话邮箱是否已验证。 */
    fun currentEmailVerified(): Boolean = store.emailVerified()

    /** 注册后待验证邮箱（尚未建立会话）。 */
    private var pendingVerificationEmail: String? = null
    private var pendingPasswordResetEmail: String? = null

    fun setPendingVerificationEmail(email: String) {
        pendingVerificationEmail = email
    }

    fun pendingVerificationEmail(): String? = pendingVerificationEmail
    fun pendingPasswordResetEmail(): String? = pendingPasswordResetEmail

    /** 启动时恢复会话：有 refresh token 则尝试刷新；认证失效清除，网络失败保留存储。 */
    suspend fun restoreSession(): Result<Unit> = refreshMutex.withLock {
        if (isAccessValid()) return@withLock Result.success(Unit)
        if (store.refreshToken() == null) {
            if (!isAccessValid()) runtime.updateSession(SessionRuntimeState.Guest)
            return@withLock Result.success(Unit)
        }
        runtime.updateSession(SessionRuntimeState.Restoring)
        requireAccessTokenLocked(force = false, failedToken = null, tokenAtCall = accessToken).map { }
            .onFailure {
                if (runtime.state.value.session is SessionRuntimeState.Restoring) {
                    runtime.updateSession(SessionRuntimeState.Guest)
                }
            }
    }

    /** 获取有效 access token；并发时只执行一次刷新。 */
    suspend fun requireAccessToken(
        force: Boolean = false,
        failedToken: String? = null,
        expectedGeneration: Long? = null,
    ): Result<String> {
        if (expectedGeneration != null && expectedGeneration != sessionGeneration) return staleSessionFailure()
        if (!force && isAccessValid()) return Result.success(accessToken!!)
        val tokenAtCall = accessToken
        return refreshMutex.withLock {
            if (expectedGeneration != null && expectedGeneration != sessionGeneration) return@withLock staleSessionFailure()
            requireAccessTokenLocked(force, failedToken, tokenAtCall)
        }
    }

    private fun staleSessionFailure(): Result<String> =
        Result.failure(OnlineErrorException(OnlineError.Authentication(expired = true)))

    private suspend fun requireAccessTokenLocked(force: Boolean, failedToken: String?, tokenAtCall: String?): Result<String> {
        if (force && failedToken != null && isAccessValid() && accessToken != failedToken) {
            return Result.success(accessToken!!)
        }
        if (isAccessValid() && (!force || accessToken != tokenAtCall)) {
            return Result.success(accessToken!!)
        }
        val refresh = store.refreshToken()
            ?: return Result.failure(OnlineErrorException(OnlineError.Authentication(expired = true)))
        val generationAtCall = sessionGeneration
        return api.post(
            path = "/auth/refresh",
            bodyJson = json.encodeToString(RefreshRequest(refresh)),
            authenticated = false,
        ) { json.decodeFromString<AuthTokensResponse>(it) }
            .mapCatching {
                applyTokens(it, generationAtCall)
                accessToken ?: error("token not applied")
            }
            .onFailure {
                if ((it as? OnlineErrorException)?.error is OnlineError.Authentication) clearLocalSession()
            }
    }

    suspend fun register(email: String, password: String, deviceName: String): Result<Unit> =
        api.postForUnit("/auth/register", json.encodeToString(RegisterRequest(email, password, deviceName)), authenticated = false)
            .onSuccess { pendingVerificationEmail = email }

    suspend fun captcha(previousCaptchaId: String? = null): Result<LoginCaptchaResponse> =
        api.post(
            path = "/auth/captcha",
            bodyJson = json.encodeToString(LoginCaptchaRequest(previousCaptchaId)),
            authenticated = false,
        ) { json.decodeFromString<LoginCaptchaResponse>(it) }.mapCatching { challenge ->
            require(challenge.captchaId.length in 10..128)
            require(challenge.imageBase64.startsWith("data:image/") && challenge.imageBase64.length <= 512 * 1024)
            require(challenge.length in 4..8 && challenge.expiresInSeconds in 1..600)
            require(challenge.imageBytes().size in 1..256 * 1024)
            challenge
        }

    suspend fun login(
        email: String,
        password: String,
        captchaId: String,
        captcha: String,
        deviceName: String,
    ): Result<Unit> =
        refreshMutex.withLock {
            api.post(
                path = "/auth/login",
                bodyJson = json.encodeToString(LoginRequest(email, password, captchaId, captcha, deviceName)),
                authenticated = false,
            ) { json.decodeFromString<AuthTokensResponse>(it) }
                .mapCatching { applyNewSessionTokens(it) }
        }

    /** 登录后拉 /auth/getUserInfo 获取真实用户 id；失败返回空串不阻塞登录。 */
    private suspend fun fetchUserId(accessToken: String): String {
        return try {
            api.get("/auth/getUserInfo", authenticated = false, headers = mapOf("Authorization" to "Bearer $accessToken")) { body ->
                val root = json.parseToJsonElement(body).jsonObject
                val account = root["account"]?.jsonObject ?: root["data"]?.jsonObject?.get("account")?.jsonObject
                account?.get("id")?.jsonPrimitive?.content.orEmpty()
            }.getOrNull().orEmpty()
        } catch (e: Exception) { "" }
    }

    suspend fun verifyEmail(email: String, code: String, deviceName: String): Result<Unit> =
        refreshMutex.withLock {
            api.post(
                path = "/auth/email/verify",
                bodyJson = json.encodeToString(EmailVerificationRequest(email, code)),
                authenticated = false,
                headers = mapOf("X-Device-Name" to deviceName),
            ) { json.decodeFromString<AuthTokensResponse>(it) }
                .mapCatching { applyNewSessionTokens(it); pendingVerificationEmail = null }
        }

    suspend fun resendVerification(email: String): Result<Unit> =
        api.postForUnit("/auth/email/resend", json.encodeToString(ResendVerificationRequest(email)), authenticated = false)

    suspend fun forgotPassword(email: String): Result<Unit> =
        api.postForUnit("/auth/password/reset", json.encodeToString(PasswordResetRequest(email)), authenticated = false)
            .onSuccess { pendingPasswordResetEmail = email }

    suspend fun resetPassword(email: String, code: String, newPassword: String): Result<Unit> =
        api.postForUnit("/auth/password/reset/confirm", json.encodeToString(PasswordResetConfirmRequest(email, code, newPassword)), authenticated = false)
            .onSuccess { pendingPasswordResetEmail = null }

    suspend fun logout(): Result<Unit> {
        val preparation = refreshMutex.withLock {
            val token = when {
                isAccessValid() -> accessToken
                store.refreshToken() != null -> requireAccessTokenLocked(
                    force = false,
                    failedToken = null,
                    tokenAtCall = accessToken,
                ).getOrNull()
                else -> null
            }
            sessionGeneration += 1
            sessionInvalidated = true
            val cleared = clearLocalSession()
            token to cleared
        }
        val token = preparation.first
        val serverResult = if (token == null) {
            Result.success(Unit)
        } else {
            api.postForUnit(
                "/auth/logout",
                null,
                authenticated = false,
                headers = mapOf("Authorization" to "Bearer $token"),
            )
        }
        return when {
            !preparation.second -> Result.failure(IllegalStateException("无法安全清除本地登录会话"))
            else -> serverResult
        }
    }

    suspend fun account(): Result<AccountSummary> =
        requireAccessToken().flatMap {
            api.get("/account", authenticated = true) { json.decodeFromString<AccountSummary>(it) }
        }

    suspend fun devices(): Result<DeviceSessionsResponse> =
        requireAccessToken().flatMap {
            api.get("/account/devices", authenticated = true) { json.decodeFromString<DeviceSessionsResponse>(it) }
        }

    suspend fun revokeDevice(deviceId: String): Result<Unit> =
        requireAccessToken().flatMap {
            api.delete("/account/devices/$deviceId", authenticated = true)
        }

    /** 更新昵称（用户中心）；成功返回最新账号信息。 */
    suspend fun updateNickname(nickname: String): Result<AccountSummary> =
        requireAccessToken().flatMap {
            api.patch(
                "/account",
                json.encodeToString(com.catlink.contracts.UpdateProfileRequest(nickname = nickname)),
                authenticated = true,
            ) { json.decodeFromString<AccountSummary>(it) }
        }

    suspend fun updateAvatar(avatarUrl: String): Result<AccountSummary> =
        requireAccessToken().flatMap {
            api.patch(
                "/account",
                json.encodeToString(com.catlink.contracts.UpdateProfileRequest(avatarUrl = avatarUrl)),
                authenticated = true,
            ) { json.decodeFromString<AccountSummary>(it) }
        }

    private fun applyNewSessionTokens(tokens: AuthTokensResponse) {
        beginNewSessionGeneration()
        applyTokens(tokens, sessionGeneration)
    }

    private fun beginNewSessionGeneration() {
        sessionGeneration += 1
        sessionInvalidated = false
    }

    private fun applyTokens(tokens: AuthTokensResponse, expectedGeneration: Long) {
        check(!sessionInvalidated && sessionGeneration == expectedGeneration) { "stale session response" }
        val id = tokens.account.id.ifBlank { store.accountId() ?: "" }
        val email = tokens.account.email.ifBlank { store.email() ?: "" }
        val verified = tokens.account.emailVerified
        check(
            store.saveSession(
                refreshToken = tokens.refreshToken,
                accountId = id,
                email = email,
                emailVerified = verified,
            ),
        ) { "无法安全保存登录会话" }
        accessToken = tokens.accessToken
        accessExpiresAt = tokens.accessExpiresAt
        publishedSnapshot = AuthSessionSnapshot(accessToken, sessionGeneration)
        runtime.updateSession(
            SessionRuntimeState.SignedIn(id, verified),
        )
    }

    /** 登录成功后触发后台同步（当前包内可访问，由调用方在 UI 层调度）。 */
    fun notifySignedIn(context: android.content.Context) {
        com.catlink.app.sync.SyncWorker.schedule(context)
    }

    private fun clearLocalSession(): Boolean {
        val cleared = store.clear()
        accessToken = null
        accessExpiresAt = 0L
        publishedSnapshot = AuthSessionSnapshot(null, sessionGeneration)
        runtime.updateSession(SessionRuntimeState.Guest)
        return cleared
    }

    private fun isAccessValid(): Boolean =
        accessToken != null && now() < accessExpiresAt - accessLeewayMillis

    private suspend fun <T, R> Result<T>.flatMap(transform: suspend (T) -> Result<R>): Result<R> =
        fold(onSuccess = { transform(it) }, onFailure = { Result.failure(it) })
}
