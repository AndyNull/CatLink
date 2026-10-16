package com.catlink.app.data

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 极简 AWS S3 客户端（阶段 2 远端同步）：
 * 手写 AWS Signature V4 签名（PUT/GET/HEAD/ListObjectsV2），不引入 AWS SDK。
 * 支持标准 S3 与 S3 兼容服务（MinIO / 阿里云 OSS 兼容模式等，填 endpoint 即可）。
 * 同步内容为加密备份包，服务器只看到密文。
 */
object S3Client {

    data class Result(val ok: Boolean, val message: String)

    private val UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val SHORT = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** 桶访问基础地址：未填 endpoint 时用 AWS 官方地址 */
    private fun baseUrl(endpoint: String, region: String): String =
        endpoint.trim().ifBlank { "https://s3.$region.amazonaws.com" }

    private fun hmac(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun sha256Hex(data: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(data).joinToString("") { "%02x".format(it) }
    }

    /** URI 编码（S3 要求：路径段与 query 值都按 RFC 3986 编码，空格为 %20） */
    private fun enc(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

    private fun encodePath(path: String): String =
        path.split("/").joinToString("/") { enc(it) }

    /**
     * AWS Signature V4（S3 专用 scope）。
     * query 需为已编码并按参数名排序的 "k=v&k2=v2" 形式（无则空串）。
     */
    private fun signature(
        method: String,
        host: String,
        path: String,
        query: String,
        region: String,
        ak: String,
        sk: String,
        date: String,
        dateShort: String,
    ): String {
        val payloadHash = "UNSIGNED-PAYLOAD"
        val canonicalHeaders = "host:$host\nx-amz-content-sha256:$payloadHash\nx-amz-date:$date\n"
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
        val canonicalRequest = "$method\n$path\n$query\n$canonicalHeaders\n$signedHeaders\n$payloadHash"
        val scope = "$dateShort/$region/s3/aws4_request"
        val stringToSign = "AWS4-HMAC-SHA256\n$date\n$scope\n" + sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8))
        val kDate = hmac(("AWS4$sk").toByteArray(StandardCharsets.UTF_8), dateShort)
        val kRegion = hmac(kDate, region)
        val kService = hmac(kRegion, "s3")
        val kSigning = hmac(kService, "aws4_request")
        return hmac(kSigning, stringToSign).joinToString("") { "%02x".format(it) }
    }

    private fun open(
        method: String,
        endpoint: String,
        region: String,
        bucket: String,
        ak: String,
        sk: String,
        key: String,
        query: String = "",
    ): HttpURLConnection {
        val base = baseUrl(endpoint, region)
        val host = URL(base).host
        val path = "/$bucket/${encodePath(key)}"
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val date = now.format(UTC)
        val dateShort = now.format(SHORT)
        val sig = signature(method, host, path, query, region, ak, sk, date, dateShort)
        val conn = URL(base + path + if (query.isNotBlank()) "?$query" else "").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Host", host)
        conn.setRequestProperty("x-amz-date", date)
        conn.setRequestProperty("x-amz-content-sha256", "UNSIGNED-PAYLOAD")
        conn.setRequestProperty(
            "Authorization",
            "AWS4-HMAC-SHA256 Credential=$ak/$dateShort/$region/s3/aws4_request, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=$sig",
        )
        return conn
    }

    /** 连接测试：HEAD 桶（有权限即 200/204；桶不存在 404） */
    fun testConnection(endpoint: String, region: String, bucket: String, ak: String, sk: String): Result {
        return try {
            val conn = open("HEAD", endpoint, region, bucket, ak, sk, "")
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) Result(true, "连接成功（HTTP $code）")
            else if (code == 403) Result(false, "凭据或权限无效（HTTP 403）")
            else Result(false, "服务器返回 HTTP $code")
        } catch (e: Exception) {
            Result(false, e.message ?: "连接失败")
        }
    }

    /** 上传文件（PUT Object） */
    fun upload(endpoint: String, region: String, bucket: String, ak: String, sk: String, key: String, file: File): Result = try {
        val conn = open("PUT", endpoint, region, bucket, ak, sk, key)
        conn.doOutput = true
        conn.setFixedLengthStreamingMode(file.length().toInt())
        file.inputStream().use { input -> conn.outputStream.use { input.copyTo(it) } }
        val code = conn.responseCode
        conn.disconnect()
        if (code in 200..299) Result(true, "上传成功（HTTP $code）") else Result(false, "上传失败：HTTP $code")
    } catch (e: Exception) {
        Result(false, e.message ?: "上传失败")
    }

    /** 下载对象（GET Object），写至 dest */
    fun download(endpoint: String, region: String, bucket: String, ak: String, sk: String, key: String, dest: File): Result {
        return try {
            val conn = open("GET", endpoint, region, bucket, ak, sk, key)
            val code = conn.responseCode
            if (code !in 200..299) {
                val msg = conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(120) ?: ""
                conn.disconnect()
                Result(false, "下载失败：HTTP $code $msg")
            } else {
                conn.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
                conn.disconnect()
                Result(true, "下载成功（HTTP $code）")
            }
        } catch (e: Exception) {
            Result(false, e.message ?: "下载失败")
        }
    }

    /** 列出指定前缀下的对象 key（ListObjectsV2，单页 1000 条足够） */
    fun list(endpoint: String, region: String, bucket: String, ak: String, sk: String, prefix: String): List<String> = try {
        val q = "list-type=2&max-keys=1000&prefix=${enc(prefix)}"
        val conn = open("GET", endpoint, region, bucket, ak, sk, "", q)
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            emptyList()
        } else {
            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            Regex("""<Key>([^<]+)</Key>""").findAll(xml).map { it.groupValues[1] }.toList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}
