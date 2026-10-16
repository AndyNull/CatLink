package com.catlink.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * 媒体文件管理：所有照片/合同图片在入库前拷贝到应用私有目录
 * （filesDir/photos），避免 content:// 权限失效；备份/同步时按文件名处理。
 */
object MediaFiles {

    fun photosDir(context: Context): File =
        File(context.filesDir, "photos").apply { mkdirs() }

    fun extensionForMime(mime: String?): String = when {
        mime.orEmpty().lowercase().contains("png") -> "png"
        mime.orEmpty().lowercase().contains("webp") -> "webp"
        mime.orEmpty().lowercase().contains("pdf") -> "pdf"
        else -> "jpg"
    }

    fun serverUploadFilename(context: Context, uri: Uri, prefix: String): String {
        val extension = extensionForMime(context.contentResolver.getType(uri))
        return "$prefix-${System.currentTimeMillis()}.$extension"
    }

    fun copyToUploadCache(context: Context, uri: Uri, prefix: String): File {
        val extension = extensionForMime(context.contentResolver.getType(uri))
        val file = File.createTempFile("${prefix.filter(Char::isLetterOrDigit).take(24)}-", ".$extension", context.cacheDir)
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: error("无法读取文件")
            input.use { source ->
                file.outputStream().use { output ->
                    NetworkEndpoint.copyBounded(source, output, 10L * 1024 * 1024)
                }
            }
            file
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    /** 把任意 uri 拷贝为私有照片文件，返回绝对路径；失败返回原 uri 字符串 */
    fun copyToPrivate(context: Context, uri: String): String {
        if (uri.startsWith("file:///data/") || uri.startsWith("/data/")) return uri
        return try {
            val u = Uri.parse(uri)
            val mime = context.contentResolver.getType(u) ?: "image/jpeg"
            val ext = when {
                mime.contains("png") -> ".png"
                mime.contains("webp") -> ".webp"
                mime.contains("gif") -> ".gif"
                else -> ".jpg"
            }
            val dest = File(photosDir(context), UUID.randomUUID().toString() + ext)
            context.contentResolver.openInputStream(u)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: return uri
            dest.absolutePath
        } catch (_: Exception) {
            uri
        }
    }

    /** 备份时打包目录内的所有媒体文件（相对文件名列表） */
    fun listFiles(context: Context): List<File> =
        photosDir(context).listFiles { f -> f.isFile }?.toList() ?: emptyList()
}
