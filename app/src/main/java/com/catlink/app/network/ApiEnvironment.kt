package com.catlink.app.network

import android.content.Context
import java.net.URI
import com.catlink.app.BuildConfig
import com.catlink.app.data.NetworkEndpoint

object ApiEnvironment {
    private const val PREFS = "catlink_debug_api"
    private const val KEY_BASE_URL = "base_url"
    const val DEBUG_DEFAULT = "http://127.0.0.1:8888"

    fun baseUrl(context: Context): String {
        if (!BuildConfig.DEBUG) return BuildConfig.PRODUCTION_API_BASE_URL
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, DEBUG_DEFAULT)
            ?.trimEnd('/')
            .orEmpty()
            .ifBlank { DEBUG_DEFAULT }
    }

    fun resolveServerUrl(context: Context, value: String): String =
        resolveServerUrl(baseUrl(context), value)

    fun resolveServerUrl(baseUrl: String, value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed.startsWith("//")) return ""
        return runCatching {
            val base = URI(baseUrl.trimEnd('/') + "/")
            require(base.scheme.equals("http", true) || base.scheme.equals("https", true))
            require(!base.host.isNullOrBlank() && base.userInfo == null)
            val input = URI(trimmed)
            require(input.rawAuthority == null || input.isAbsolute)
            val resolved = if (input.isAbsolute) input else base.resolve(input)
            require(resolved.scheme.equals("http", true) || resolved.scheme.equals("https", true))
            require(resolved.userInfo == null)
            require(resolved.scheme.equals(base.scheme, true))
            require(resolved.host.equals(base.host, true))
            require(effectivePort(resolved) == effectivePort(base))
            resolved.toString()
        }.getOrDefault("")
    }

    fun serverRelativePath(context: Context, value: String): String =
        serverRelativePath(baseUrl(context), value)

    fun serverRelativePath(baseUrl: String, value: String): String {
        val resolved = resolveServerUrl(baseUrl, value)
        if (resolved.isBlank()) return ""
        val uri = URI(resolved)
        if (uri.rawQuery != null || uri.rawFragment != null) return ""
        return uri.rawPath.takeIf { it.startsWith('/') }.orEmpty()
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", true) -> 443
        else -> 80
    }

    fun saveDebugBaseUrl(context: Context, value: String): Result<String> {
        if (!BuildConfig.DEBUG) return Result.failure(IllegalStateException("release版本不支持调试API配置"))
        val normalized = value.trim().trimEnd('/')
        return runCatching {
            NetworkEndpoint.requireSecure(normalized)
            require(normalized.startsWith("http://127.0.0.1") || normalized.startsWith("http://localhost") || normalized.startsWith("https://")) {
                "HTTP 调试地址仅允许 127.0.0.1 或 localhost"
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_BASE_URL, normalized).apply()
            normalized
        }
    }

    fun resetDebug(context: Context) {
        if (BuildConfig.DEBUG) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
