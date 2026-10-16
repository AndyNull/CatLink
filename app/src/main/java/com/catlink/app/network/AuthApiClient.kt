package com.catlink.app.network

import com.catlink.contracts.ApiErrorCode
import com.catlink.contracts.ApiErrorEnvelope
import com.catlink.contracts.ApiErrorPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * CatLink 认证/账号 API 客户端。
 * 请求体由调用方以 JSON 文本传入；响应统一映射为 [OnlineError]，网络与JSON异常不直接抛给UI。
 */
data class AuthSessionSnapshot(
    val accessToken: String?,
    val generation: Long,
)

class AuthApiClient(
    private val baseUrlProvider: () -> String,
    private val authSessionProvider: () -> AuthSessionSnapshot = { AuthSessionSnapshot(null, 0L) },
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
    private val client: OkHttpClient = defaultClient(),
    /** access token 失效时刷新并返回新 token；参数为触发失效的旧 token和请求发起时的会话代次。 */
    private val refreshAccessToken: suspend (String?, Long) -> Result<String> = { _, _ -> Result.failure(OnlineErrorException(OnlineError.Authentication(expired = true))) },
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val apiBasePath = "/api/v1"

    fun resolveServerUrl(value: String): String =
        ApiEnvironment.resolveServerUrl(baseUrlProvider(), value)

    suspend fun <T> get(path: String, authenticated: Boolean = true, headers: Map<String, String> = emptyMap(), deserialize: (String) -> T): Result<T> =
        execute("GET", path, null, authenticated, headers, deserialize)

    suspend fun <T> getLarge(path: String, authenticated: Boolean = true, deserialize: (String) -> T): Result<T> =
        execute("GET", path, null, authenticated, emptyMap(), deserialize, MAX_EXPORT_RESPONSE_BYTES)

    suspend fun <T> post(path: String, bodyJson: String?, authenticated: Boolean = true, headers: Map<String, String> = emptyMap(), deserialize: (String) -> T): Result<T> =
        execute("POST", path, bodyJson, authenticated, headers, deserialize)

    suspend fun <T> patch(path: String, bodyJson: String?, authenticated: Boolean = true, headers: Map<String, String> = emptyMap(), deserialize: (String) -> T): Result<T> =
        execute("PATCH", path, bodyJson, authenticated, headers, deserialize)

    suspend fun delete(path: String, authenticated: Boolean = true, headers: Map<String, String> = emptyMap()): Result<Unit> =
        execute("DELETE", path, null, authenticated, headers, deserialize = {})

    suspend fun postForUnit(path: String, bodyJson: String?, authenticated: Boolean = true, headers: Map<String, String> = emptyMap()): Result<Unit> =
        execute("POST", path, bodyJson, authenticated, headers, deserialize = {})

    suspend fun putForUnit(path: String, bodyJson: String?, authenticated: Boolean = true, headers: Map<String, String> = emptyMap()): Result<Unit> =
        execute("PUT", path, bodyJson, authenticated, headers, deserialize = {})

    suspend fun <T> postFile(
        path: String,
        fields: Map<String, String>,
        file: File,
        filename: String,
        mediaType: String,
        deserialize: (String) -> T,
    ): Result<T> {
        require(file.isFile) { "upload file does not exist" }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            fields.forEach { (name, value) -> addFormDataPart(name, value) }
            addFormDataPart("file", filename, file.asRequestBody(mediaType.toMediaType()))
        }.build()
        return executeBody("POST", path, body, authenticated = true, emptyMap(), deserialize)
    }

    private suspend fun <T> execute(
        method: String,
        path: String,
        bodyJson: String?,
        authenticated: Boolean,
        headers: Map<String, String>,
        deserialize: (String) -> T,
        successResponseBytes: Long = MAX_SUCCESS_RESPONSE_BYTES,
    ): Result<T> {
        val requestBody = when {
            bodyJson != null -> bodyJson.toRequestBody(jsonMedia)
            method == "POST" || method == "PATCH" -> "".toRequestBody(null)
            else -> null
        }
        return executeBody(method, path, requestBody, authenticated, headers, deserialize, successResponseBytes)
    }

    private suspend fun <T> executeBody(
        method: String,
        path: String,
        requestBody: RequestBody?,
        authenticated: Boolean,
        headers: Map<String, String>,
        deserialize: (String) -> T,
        successResponseBytes: Long = MAX_SUCCESS_RESPONSE_BYTES,
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            require(path.startsWith('/')) { "API path must start with /" }
            val builder = Request.Builder().url(baseUrlProvider().trimEnd('/') + routePrefix(path) + path)
            headers.forEach { (name, value) ->
                // HTTP header 只允许可见 ASCII；过滤设备名等值中的非 ASCII 字符
                builder.header(name, value.filter { it.code in 0x20..0x7E })
            }
            val session = if (authenticated) authSessionProvider() else AuthSessionSnapshot(null, 0L)
            val failedToken = session.accessToken
            val requestGeneration = session.generation
            if (authenticated) {
                failedToken?.let { token -> builder.header("Authorization", "Bearer $token") }
            }
            // GVA CatLink 客户端标识：所有业务请求需与登录 clientId 一致
            builder.header("clientid", CATLINK_CLIENT_ID)
            builder.method(method, requestBody)
            client.newCall(builder.build()).execute().use { response ->
                val raw = readResponseBody(response.body, response.isSuccessful, successResponseBytes)
                when {
                    response.isSuccessful -> {
                        val businessError = parseBusinessError(raw)
                        if (businessError != null) {
                            if (authenticated && businessError is OnlineError.Authentication) {
                                val refreshed = runCatching { refreshAccessToken(failedToken, requestGeneration) }.getOrElse {
                                    Result.failure<String>(it as? OnlineErrorException ?: OnlineErrorException(OnlineError.Unknown()))
                                }
                                val newToken = refreshed.getOrNull()
                                if (newToken != null) retryOnce(method, path, requestBody, headers, newToken, deserialize, successResponseBytes)
                                else Result.failure(OnlineErrorException(businessError))
                            } else {
                                Result.failure(OnlineErrorException(businessError))
                            }
                        } else {
                            val payload = unwrapData(raw)
                            if (payload.isBlank()) Result.success(deserialize("")) else Result.success(deserialize(payload))
                        }
                    }
                    response.code == 401 && authenticated -> {
                        // access token 失效：刷新一次并重试，避免用户感知到会话过期
                        val refreshed = runCatching { refreshAccessToken(failedToken, requestGeneration) }.getOrElse {
                            Result.failure<String>(it as? OnlineErrorException ?: OnlineErrorException(OnlineError.Unknown()))
                        }
                        val newToken = refreshed.getOrNull()
                        if (newToken != null) {
                            retryOnce(method, path, requestBody, headers, newToken, deserialize, successResponseBytes)
                        } else {
                            Result.failure(OnlineErrorException(mapHttpError(response.code, raw)))
                        }
                    }
                    else -> Result.failure(OnlineErrorException(mapHttpError(response.code, raw)))
                }
            }
        } catch (error: IOException) {
            Result.failure(OnlineErrorException(OnlineErrorMapper.fromThrowable(error)))
        } catch (error: SerializationException) {
            Result.failure(OnlineErrorException(OnlineError.Server(null, "服务响应格式异常")))
        } catch (error: Throwable) {

            Result.failure(OnlineErrorException(OnlineError.Unknown()))
        }
    }

    private fun readResponseBody(body: ResponseBody?, successful: Boolean, successResponseBytes: Long): String {
        if (body == null) return ""
        val limit = if (successful) successResponseBytes else MAX_ERROR_RESPONSE_BYTES
        body.source().use { source ->
            val buffer = okio.Buffer()
            var total = 0L
            while (true) {
                val read = source.read(buffer, minOf(8192L, limit + 1L - total))
                if (read == -1L) break
                total += read
                if (total > limit) throw IOException("response body exceeds safe limit")
            }
            return buffer.readString(Charsets.UTF_8)
        }
    }

    /** 刷新 token 后重试一次（携带新 token）。 */
    private suspend fun <T> retryOnce(
        method: String,
        path: String,
        requestBody: RequestBody?,
        headers: Map<String, String>,
        accessToken: String,
        deserialize: (String) -> T,
        successResponseBytes: Long,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url(baseUrlProvider().trimEnd('/') + routePrefix(path) + path)
            headers.forEach { (name, value) -> builder.header(name, value.filter { it.code in 0x20..0x7E }) }
            builder.header("Authorization", "Bearer $accessToken")
            builder.header("clientid", CATLINK_CLIENT_ID)
            builder.method(method, requestBody)
            client.newCall(builder.build()).execute().use { response ->
                val raw = readResponseBody(response.body, response.isSuccessful, successResponseBytes)
                if (response.isSuccessful) {
                    val businessError = parseBusinessError(raw)
                    if (businessError != null) {
                        Result.failure(OnlineErrorException(businessError))
                    } else {
                        val payload = unwrapData(raw)
                        if (payload.isBlank()) Result.success(deserialize("")) else Result.success(deserialize(payload))
                    }
                } else {
                    Result.failure(OnlineErrorException(mapHttpError(response.code, raw)))
                }
            }
        }.getOrElse { Result.failure(OnlineErrorException(OnlineErrorMapper.fromThrowable(it))) }
    }


    private fun parseBusinessError(raw: String): OnlineError? {
        if (raw.isBlank()) return null
        return runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            val code = root["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@runCatching null
            if (code == 0) return@runCatching null
            val msg = root["msg"]?.jsonPrimitive?.content.orEmpty()
            when {
                code == 7 && listOf("登录", "认证", "会话", "token", "签名").any { msg.contains(it, ignoreCase = true) } -> OnlineError.Authentication(expired = true)
                code == 7 && listOf("权限", "无权", "停用").any { msg.contains(it) } -> OnlineError.Permission(msg.ifBlank { "你没有执行此操作的权限。" })
                code == 7 -> OnlineError.Validation(emptyMap(), msg.ifBlank { "请检查填写内容。" })
                else -> OnlineError.Server(null, msg.ifBlank { "服务暂时不可用，请稍后再试。" })
            }
        }.getOrNull()
    }

    private fun mapHttpError(status: Int, raw: String): OnlineError {
        val payload = runCatching { json.decodeFromString<ApiErrorEnvelope>(raw).error }.getOrNull()
        val safe = payload ?: ApiErrorPayload(code = ApiErrorCode.UNKNOWN, message = "HTTP $status")
        return OnlineErrorMapper.fromPayload(safe)
    }

    /** GVA 响应为 {code,data,...} 包装；解出 data 内容供用户 deserialize；旧服务端无包装则原样。 */
    private fun unwrapData(raw: String): String {
        if (raw.isBlank()) return raw
        return runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            val hasCode = root.containsKey("code")
            val data = root["data"]
            if (hasCode && data != null && !data.toString().equals("null", ignoreCase = true)) data.toString() else raw
        }.getOrDefault(raw)
    }

    /** 所有 CatLink 在线接口统一挂在 /api/v1。 */
    private fun routePrefix(path: String): String = apiBasePath

    companion object {
        /** GVA CatLink App 客户端标识；登录与业务请求保持一致。 */
        const val CATLINK_CLIENT_ID = "e5cd7e4891bf95d1d19206ce24a7b32e"
        private const val MAX_SUCCESS_RESPONSE_BYTES = 8L * 1024 * 1024
        private const val MAX_EXPORT_RESPONSE_BYTES = 64L * 1024 * 1024
        private const val MAX_ERROR_RESPONSE_BYTES = 64L * 1024

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
