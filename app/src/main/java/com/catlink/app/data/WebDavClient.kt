package com.catlink.app.data

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * 极简 WebDAV 客户端（阶段 2 远端同步）：
 * 用标准 HTTP 方法（OPTIONS/PUT/GET）对接任意 WebDAV 服务器（NAS / 坚果云等）。
 * 同步内容为加密备份包，服务器只看到密文。
 */
object WebDavClient {

    data class Result(val ok: Boolean, val message: String)

    /** 连接测试：OPTIONS 检查服务器可达性与凭据 */
    fun testConnection(endpoint: String, user: String, pass: String): Result = try {
        val conn = open(endpoint, "OPTIONS", user, pass)
        val code = conn.responseCode
        conn.disconnect()
        if (code in 200..299) Result(true, "连接成功（HTTP $code）")
        else if (code == 401) Result(false, "凭据无效（HTTP 401）")
        else Result(false, "服务器返回 HTTP $code")
    } catch (e: Exception) {
        Result(false, e.message ?: "连接失败")
    }

    /** 上传文件（PUT 任意远程路径；多数 WebDAV 服务器可自动建父目录） */
    fun upload(endpoint: String, user: String, pass: String, file: File, remoteName: String): Result = try {
        val conn = open(endpoint + remoteName, "PUT", user, pass)
        conn.doOutput = true
        file.inputStream().use { input -> conn.outputStream.use { input.copyTo(it) } }
        val code = conn.responseCode
        conn.disconnect()
        if (code in 200..299) Result(true, "上传成功（HTTP $code）") else Result(false, "上传失败：HTTP $code")
    } catch (e: Exception) {
        Result(false, e.message ?: "上传失败")
    }

    /** 确保远程目录存在（MKCOL，已存在时 405 视为成功） */
    fun mkdir(endpoint: String, user: String, pass: String, remoteDir: String): Result = try {
        val conn = open(endpoint + remoteDir.trimEnd('/') + "/", "MKCOL", user, pass)
        val code = conn.responseCode
        conn.disconnect()
        if (code in 200..299 || code == 405) Result(true, "目录就绪") else Result(false, "创建目录失败：HTTP $code")
    } catch (e: Exception) {
        Result(false, e.message ?: "创建目录失败")
    }

    /** 列出目录内文件名（PROPFIND depth 1），仅返回文件路径 */
    fun list(endpoint: String, user: String, pass: String, remoteDir: String): List<String> = try {
        val conn = open(endpoint + remoteDir.trimEnd('/') + "/", "PROPFIND", user, pass)
        conn.setRequestProperty("Depth", "1")
        conn.setRequestProperty("Content-Type", "application/xml")
        conn.doOutput = true
        conn.outputStream.use { it.write("""<?xml version="1.0"?><propfind xmlns="DAV:"><prop><resourcetype/></prop></propfind>""".toByteArray()) }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            emptyList()
        } else {
            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            Regex("""<D:href>([^<]+)</D:href>|<href>([^<]+)</href>""")
                .findAll(xml)
                .map { it.groupValues[1].ifBlank { it.groupValues[2] } }
                .filter { it != remoteDir.trimEnd('/') + "/" }
                .toList()
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** 下载加密备份包（GET） */
    fun download(endpoint: String, user: String, pass: String, remoteName: String, dest: File): Result {
        return try {
            val conn = open(endpoint + remoteName, "GET", user, pass)
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                Result(false, "下载失败：HTTP $code")
            } else {
                conn.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
                conn.disconnect()
                Result(true, "下载成功")
            }
        } catch (e: Exception) {
            Result(false, e.message ?: "下载失败")
        }
    }

    private fun open(url: String, method: String, user: String, pass: String): HttpURLConnection {
        val conn = URL(ensureTrailingSlash(url)).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10000
        conn.readTimeout = 20000
        if (user.isNotBlank()) {
            val token = Base64.getEncoder().encodeToString("$user:$pass".toByteArray(Charsets.UTF_8))
            conn.setRequestProperty("Authorization", "Basic $token")
        }
        return conn
    }

    private fun ensureTrailingSlash(s: String) = if (s.endsWith("/")) s else "$s/"
}
