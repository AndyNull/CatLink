package com.catlink.app.ui.screens.community

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.catlink.app.data.PublicApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

@Serializable
data class PendingPostPhoto(
    val id: String,
    val sourceKey: String,
    val localPath: String,
    val serverId: String = "",
    val url: String = "",
    val failed: Boolean = false,
)

data class PostPhotoPrepareResult(
    val photos: List<PendingPostPhoto>,
    val failed: Int,
    val ignored: Int,
)

data class PostPhotoUploadResult(
    val photos: List<PendingPostPhoto>,
    val failed: Int,
)

internal const val MAX_POST_PHOTOS = 4

fun remainingPostPhotoSlots(currentCount: Int): Int =
    (MAX_POST_PHOTOS - currentCount).coerceIn(0, MAX_POST_PHOTOS)

internal fun movePostPhoto(photos: List<PendingPostPhoto>, id: String, offset: Int): List<PendingPostPhoto> {
    val from = photos.indexOfFirst { it.id == id }
    if (from < 0) return photos
    val to = (from + offset).coerceIn(0, photos.lastIndex)
    if (from == to) return photos
    return photos.toMutableList().apply { add(to, removeAt(from)) }
}

internal suspend fun preparePostPhotos(
    context: Context,
    current: List<PendingPostPhoto>,
    uris: List<Uri>,
): PostPhotoPrepareResult {
    val photos = current.toMutableList()
    val existing = current.mapTo(mutableSetOf()) { it.sourceKey }
    var failed = 0
    val candidates = uris.distinctBy(Uri::toString)
        .filterNot { it.toString() in existing }
    val selected = candidates.take(remainingPostPhotoSlots(current.size))
    selected.forEach { uri ->
        try {
            photos += preparePostPhoto(context, uri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            failed++
        }
    }
    return PostPhotoPrepareResult(photos, failed, candidates.size - selected.size)
}

internal suspend fun uploadPostPhotos(
    api: PublicApi,
    input: List<PendingPostPhoto>,
    draftDirectory: File,
    onUpdated: (List<PendingPostPhoto>) -> Unit = {},
): PostPhotoUploadResult = coroutineScope {
    val photos = input.toMutableList()
    val stateMutex = Mutex()
    photos.indices.map { index -> async {
        val current = photos[index]
        if (current.serverId.isNotBlank() && current.url.isNotBlank()) return@async
        postUploadSlots.withPermit {
            val updated = try {
                val file = withContext(Dispatchers.IO) {
                val file = File(current.localPath).canonicalFile
                require(file.parentFile == draftDirectory.canonicalFile && file.isFile)
                    require(file.length() in 1..MAX_POST_UPLOAD_BYTES)
                    file
                }
                val uploaded = api.uploadTemporaryPostMedia(file).getOrThrow()
                require(uploaded.id.isNotBlank() && uploaded.url.isNotBlank())
                current.copy(serverId = uploaded.id, url = uploaded.url, failed = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                current.copy(failed = true)
            }
            stateMutex.withLock {
                photos[index] = updated
                onUpdated(photos.toList())
            }
        }
    } }.awaitAll()
    PostPhotoUploadResult(photos, photos.count(PendingPostPhoto::failed))
}

internal fun deletePostDraftFiles(context: Context, photos: List<PendingPostPhoto>) {
    val root = postDraftDir(context).canonicalFile
    photos.forEach { photo ->
        val file = runCatching { File(photo.localPath).canonicalFile }.getOrNull()
        if (file?.parentFile == root) file.delete()
    }
}

private suspend fun preparePostPhoto(context: Context, uri: Uri): PendingPostPhoto = withContext(Dispatchers.IO) {
    val id = UUID.randomUUID().toString()
    val target = File(postDraftDir(context), "post-$id.jpg")
    val temporary = File(target.parentFile, ".${target.name}.tmp")
    var decoded: Bitmap? = null
    var output: Bitmap? = null
    try {
        decoded = decodePostBitmap(context, uri)
        output = if (decoded.hasAlpha()) {
            Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                Canvas(bitmap).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(decoded, 0f, 0f, null)
                }
            }
        } else {
            decoded
        }
        FileOutputStream(temporary).use { stream ->
            check(output.compress(Bitmap.CompressFormat.JPEG, 82, stream))
        }
        require(temporary.length() in 1..MAX_POST_UPLOAD_BYTES) { "图片压缩失败" }
        require(temporary.renameTo(target)) { "图片保存失败" }
        PendingPostPhoto(id, uri.toString(), target.absolutePath)
    } finally {
        if (output !== decoded) output?.recycle()
        decoded?.recycle()
        temporary.delete()
    }
}

private fun decodePostBitmap(context: Context, uri: Uri): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_POST_IMAGE_EDGE) {
                val ratio = MAX_POST_IMAGE_EDGE.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * ratio).toInt().coerceAtLeast(1),
                    (info.size.height * ratio).toInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
    var sample = 1
    while (max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_POST_IMAGE_EDGE) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("无法读取图片")
}

internal fun postDraftDir(context: Context): File =
    File(context.cacheDir, "post-drafts").apply { mkdirs() }

private const val MAX_POST_IMAGE_EDGE = 1600
private const val MAX_POST_UPLOAD_BYTES = 8 * 1024 * 1024
private val postUploadSlots = Semaphore(2)
