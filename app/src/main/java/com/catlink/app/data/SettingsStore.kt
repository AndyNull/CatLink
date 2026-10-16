package com.catlink.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.catlink.app.ui.theme.ThemeStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "catlink_settings")

/** 应用设置：主题、应用锁 PIN（PBKDF2 加盐慢哈希）、种子数据标志 */
class SettingsStore(private val context: Context) {

    private val keyTheme = stringPreferencesKey("theme_style")
    private val keyPinHash = stringPreferencesKey("pin_hash")
    private val keySeeded = booleanPreferencesKey("seeded")
    private val keySyncEndpoint = stringPreferencesKey("sync_endpoint")
    private val keySyncUser = stringPreferencesKey("sync_user")
    private val keySyncPass = stringPreferencesKey("sync_pass")
    private val keyS3Bucket = stringPreferencesKey("s3_bucket")
    private val keyS3Region = stringPreferencesKey("s3_region")
    private val keyS3Ak = stringPreferencesKey("s3_access_key")
    private val keyS3Sk = stringPreferencesKey("s3_secret_key")
    private val keyS3Endpoint = stringPreferencesKey("s3_endpoint")
    private val keyNickname = stringPreferencesKey("nickname")
    private val keyAiEndpoint = stringPreferencesKey("ai_endpoint")
    private val keyAiKey = stringPreferencesKey("ai_api_key")
    private val keyAiModel = stringPreferencesKey("ai_model")
    private val keyAiSessions = stringPreferencesKey("ai_sessions")
    private val keyDeviceId = stringPreferencesKey("device_id")

    val themeStyle: Flow<ThemeStyle> = context.dataStore.data.map { prefs ->
        ThemeStyle.entries.firstOrNull { it.name == prefs[keyTheme] } ?: ThemeStyle.WHITE
    }

    val pinEnabled: Flow<Boolean> = context.dataStore.data.map { it[keyPinHash] != null }

    suspend fun setThemeStyle(style: ThemeStyle) {
        context.dataStore.edit { it[keyTheme] = style.name }
    }

    /** 设置 PIN（null 清除，即关闭应用锁） */
    suspend fun setPin(pin: String?) {
        context.dataStore.edit { prefs ->
            if (pin == null) prefs.remove(keyPinHash) else prefs[keyPinHash] = hashPin(pin)
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val stored = context.dataStore.data.map { it[keyPinHash] }.first()
        if (stored == null) return true
        return verifyHash(stored, pin)
    }

    suspend fun isPinEnabled(): Boolean =
        context.dataStore.data.map { it[keyPinHash] }.first() != null

    val seeded: Flow<Boolean> = context.dataStore.data.map { it[keySeeded] ?: false }

    suspend fun markSeeded() {
        context.dataStore.edit { it[keySeeded] = true }
    }

    /** WebDAV 同步配置（阶段 2） */
    data class SyncConfig(val endpoint: String = "", val user: String = "", val pass: String = "")

    val syncConfig: Flow<SyncConfig> = context.dataStore.data.map {
        SyncConfig(
            endpoint = it[keySyncEndpoint] ?: "",
            user = it[keySyncUser] ?: "",
            pass = it[keySyncPass] ?: "",
        )
    }

    suspend fun setSyncConfig(endpoint: String, user: String, pass: String) {
        context.dataStore.edit {
            it[keySyncEndpoint] = endpoint.trim()
            it[keySyncUser] = user.trim()
            it[keySyncPass] = pass
        }
    }

    /** S3 同步配置（阶段 2）：bucket/region/凭据，endpoint 留空走 AWS 官方地址 */
    data class S3Config(
        val bucket: String = "",
        val region: String = "",
        val accessKey: String = "",
        val secretKey: String = "",
        val endpoint: String = "",
    )

    val s3Config: Flow<S3Config> = context.dataStore.data.map {
        S3Config(
            bucket = it[keyS3Bucket] ?: "",
            region = it[keyS3Region] ?: "",
            accessKey = it[keyS3Ak] ?: "",
            secretKey = it[keyS3Sk] ?: "",
            endpoint = it[keyS3Endpoint] ?: "",
        )
    }

    suspend fun setS3Config(bucket: String, region: String, accessKey: String, secretKey: String, endpoint: String) {
        context.dataStore.edit {
            it[keyS3Bucket] = bucket.trim()
            it[keyS3Region] = region.trim()
            it[keyS3Ak] = accessKey.trim()
            it[keyS3Sk] = secretKey
            it[keyS3Endpoint] = endpoint.trim()
        }
    }

    /** 本地账号昵称（阶段 3 账号体系的基础） */
    val nickname: Flow<String> = context.dataStore.data.map { it[keyNickname] ?: "" }

    suspend fun setNickname(name: String) {
        context.dataStore.edit { it[keyNickname] = name.trim() }
    }

    /** AI 对话配置（默认 DeepSeek，可自主修改） */
    data class AiConfig(
        val endpoint: String = DEFAULT_AI_ENDPOINT,
        val apiKey: String = "",
        val model: String = DEFAULT_AI_MODEL,
    )

    val aiConfig: Flow<AiConfig> = context.dataStore.data.map {
        AiConfig(
            endpoint = it[keyAiEndpoint] ?: DEFAULT_AI_ENDPOINT,
            apiKey = it[keyAiKey] ?: "",
            model = it[keyAiModel] ?: DEFAULT_AI_MODEL,
        )
    }

    suspend fun setAiConfig(endpoint: String, apiKey: String, model: String) {
        context.dataStore.edit {
            it[keyAiEndpoint] = endpoint.trim().ifBlank { DEFAULT_AI_ENDPOINT }
            it[keyAiKey] = apiKey.trim()
            it[keyAiModel] = model.trim().ifBlank { DEFAULT_AI_MODEL }
        }
    }

    /** 恢复 DeepSeek 默认（自动拉取默认接口/模型） */
    suspend fun resetAiConfig() {
        context.dataStore.edit {
            it[keyAiEndpoint] = DEFAULT_AI_ENDPOINT
            it[keyAiModel] = DEFAULT_AI_MODEL
        }
    }

    /** 本机唯一标识（catlink_ 前缀 + 随机串），用于远程存储目录隔离，避免多用户共用同一 S3/WebDAV 时数据混淆 */
    suspend fun ensureDeviceId(): String {
        var id = ""
        context.dataStore.edit { prefs ->
            id = prefs[keyDeviceId]?.takeIf { it.isNotBlank() }
                ?: ("catlink_" + buildString {
                    val chars = "0123456789abcdef"
                    repeat(8) { append(chars[(Math.random() * 16).toInt()]) }
                })
            prefs[keyDeviceId] = id
        }
        return id
    }

    val deviceId: Flow<String> = context.dataStore.data.map { it[keyDeviceId] ?: "" }
    data class AiSession(
        val id: String,
        val title: String,
        val messages: List<ChatMessage>,
        val createdAt: Long,
        val updatedAt: Long,
    )

    val aiSessions: Flow<List<AiSession>> = context.dataStore.data.map { prefs ->
        val raw = prefs[keyAiSessions] ?: return@map emptyList()
        try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val msgs = o.getJSONArray("messages")
                AiSession(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    messages = (0 until msgs.length()).map { j ->
                        val m = msgs.getJSONObject(j)
                        ChatMessage(m.getString("role"), m.getString("content"))
                    },
                    createdAt = o.optLong("createdAt", 0L),
                    updatedAt = o.optLong("updatedAt", 0L),
                )
            }.sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 保存整个会话列表（覆盖式，简单可靠） */
    suspend fun saveAiSessions(sessions: List<AiSession>) {
        context.dataStore.edit { prefs ->
            val arr = JSONArray()
            sessions.forEach { s ->
                val msgs = JSONArray()
                s.messages.forEach { m ->
                    msgs.put(JSONObject().apply { put("role", m.role); put("content", m.content) })
                }
                arr.put(JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("messages", msgs)
                    put("createdAt", s.createdAt)
                    put("updatedAt", s.updatedAt)
                })
            }
            prefs[keyAiSessions] = arr.toString()
        }
    }

    companion object {
        private const val ITERATIONS = 100_000

        /** DeepSeek 默认接口与模型（可自动拉取/自主填写） */
        const val DEFAULT_AI_ENDPOINT = "https://api.deepseek.com"
        const val DEFAULT_AI_MODEL = "deepseek-chat"

        /** 内置 AI 厂商（OpenAI 兼容协议，均支持 /chat/completions 与 /models） */
        data class AiProvider(val name: String, val endpoint: String)

        val AI_PROVIDERS = listOf(
            AiProvider("DeepSeek", "https://api.deepseek.com"),
            AiProvider("OpenAI", "https://api.openai.com/v1"),
            AiProvider("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
            AiProvider("Kimi 月之暗面", "https://api.moonshot.cn/v1"),
            AiProvider("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4"),
            AiProvider("火山方舟", "https://ark.cn-beijing.volces.com/api/v3"),
        )

        /** PBKDF2 加盐慢哈希：格式 saltHex:hashHex（防离线暴力破解） */
        private fun hashPin(pin: String): String {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val hash = derive(salt, pin)
            return salt.toHex() + ":" + hash.toHex()
        }

        private fun verifyHash(stored: String, pin: String): Boolean {
            val parts = stored.split(":")
            if (parts.size != 2) return false
            val salt = parts[0].hexToBytes() ?: return false
            val hash = parts[1].hexToBytes() ?: return false
            return MessageDigest.isEqual(derive(salt, pin), hash)
        }

        private fun derive(salt: ByteArray, pin: String): ByteArray {
            val spec: KeySpec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        private fun String.hexToBytes(): ByteArray? = try {
            ByteArray(length / 2) { Integer.valueOf(substring(it * 2, it * 2 + 2), 16).toByte() }
        } catch (_: Exception) {
            null
        }
    }
}
