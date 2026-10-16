package com.catlink.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.catlink.app.data.db.MediaEntity
import com.catlink.app.network.ApiEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

enum class AlbumSyncState(val value: String) {
    LOCAL("local"),
    UPLOADING("uploading"),
    UPLOADED("uploaded"),
    FAILED("failed"),
}

data class AlbumBatchResult(
    val imported: Int,
    val synced: Int,
    val failed: Int,
)

class CatAlbumManager(
    context: Context,
    private val repository: VaultRepository,
    private val publicApi: PublicApi,
    private val isSignedIn: () -> Boolean,
) {
    private val appContext = context.applicationContext

    suspend fun import(catId: String, uris: List<Uri>): AlbumBatchResult {
        var imported = 0
        var failed = 0
        val importedItems = mutableListOf<MediaEntity>()
        uris.distinctBy(Uri::toString).forEachIndexed { index, uri ->
            val local = copyLocal(uri).getOrElse {
                failed++
                return@forEachIndexed
            }
            val item = persistAlbumCopy(appContext, local) {
                repository.addCatMedia(
                    catId = catId,
                    uri = local,
                    localUri = local,
                    syncState = if (isSignedIn()) AlbumSyncState.UPLOADING.value else AlbumSyncState.LOCAL.value,
                    sortOrder = System.currentTimeMillis() + index,
                )
            }.getOrElse {
                failed++
                return@forEachIndexed
            }
            imported++
            importedItems += item
        }
        val sync = if (isSignedIn()) syncBatch(importedItems) else AlbumBatchResult(imported, 0, 0)
        return AlbumBatchResult(imported, sync.synced, failed + sync.failed)
    }

    suspend fun syncPending(items: List<MediaEntity>): AlbumBatchResult {
        return syncBatch(items.filter { it.syncState != AlbumSyncState.UPLOADED.value })
    }

    private suspend fun syncBatch(items: List<MediaEntity>): AlbumBatchResult = coroutineScope {
        val results = items.map { item ->
            async { uploadSlots.withPermit { sync(item) } }
        }.awaitAll()
        AlbumBatchResult(items.size, results.count { it.isSuccess }, results.count { it.isFailure })
    }

    suspend fun delete(item: MediaEntity): Result<Unit> = try {
        withContext(NonCancellable) {
            if (item.serverMediaId.isNotBlank()) {
                publicApi.deleteCatAlbumMedia(item.serverMediaId).getOrThrow()
            }
            repository.removeMedia(item)
        }
        Result.success(Unit)
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun sync(item: MediaEntity): Result<Unit> = syncMutexes.computeIfAbsent(item.id) { Mutex() }.withLock {
        val current = repository.mediaById(item.id) ?: return@withLock Result.success(Unit)
        if (current.syncState == AlbumSyncState.UPLOADED.value) return@withLock Result.success(Unit)
        syncCurrent(current)
    }

    private suspend fun syncCurrent(item: MediaEntity): Result<Unit> {
        if (!isSignedIn()) return Result.failure(IllegalStateException("登录后才能同步相册"))
        repository.updateMedia(item.copy(syncState = AlbumSyncState.UPLOADING.value))
        return try {
            val local = item.localUri.ifBlank { item.uri }
            val file = File(local.removePrefix("file://")).canonicalFile
            require(file.isFile && file.parentFile == MediaFiles.photosDir(appContext).canonicalFile) { "本地照片不存在" }
            require(file.length() in 1..MAX_UPLOAD_BYTES) { "照片不能超过 8MB" }
            val uploaded = publicApi.uploadCatAlbumMedia(file).getOrThrow()
            try {
                require(uploaded.id.isNotBlank() && uploaded.url.isNotBlank()) { "服务端未返回媒体信息" }
                val remote = ApiEnvironment.resolveServerUrl(appContext, uploaded.url)
                require(remote.isNotBlank()) { "服务端返回了无效媒体地址" }
                repository.updateMedia(
                    item.copy(
                        uri = remote,
                        remoteUrl = remote,
                        serverMediaId = uploaded.id,
                        syncState = AlbumSyncState.UPLOADED.value,
                    )
                )
            } catch (error: Throwable) {
                if (uploaded.id.isNotBlank()) {
                    withContext(NonCancellable) { publicApi.deleteCatAlbumMedia(uploaded.id) }
                }
                throw error
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                repository.updateMedia(item.copy(syncState = AlbumSyncState.FAILED.value))
            }
            throw cancelled
        } catch (error: Throwable) {
            repository.updateMedia(item.copy(syncState = AlbumSyncState.FAILED.value))
            Result.failure(error)
        }
    }

    private suspend fun copyLocal(uri: Uri): Result<String> {
        val original = runCatching { File(uri.path.orEmpty()).canonicalFile }.getOrNull()
        var copied: File? = null
        return try {
            val stored = withContext(Dispatchers.IO + NonCancellable) {
                copyAlbumPhoto(uri)
            }
            copied = File(stored).canonicalFile
            currentCoroutineContext().ensureActive()
            require(copied.isFile && copied.parentFile == MediaFiles.photosDir(appContext).canonicalFile) { "照片保存失败" }
            require(copied.length() in 1..MAX_UPLOAD_BYTES) { "照片不能超过 8MB" }
            Result.success(stored)
        } catch (cancelled: CancellationException) {
            deleteNewCopy(copied, original)
            throw cancelled
        } catch (error: Throwable) {
            deleteNewCopy(copied, original)
            Result.failure(error)
        }
    }

    private fun deleteNewCopy(file: File?, original: File?) {
        val photos = MediaFiles.photosDir(appContext).canonicalFile
        if (file != null && file != original && file.parentFile == photos) file.delete()
    }

    private fun copyAlbumPhoto(uri: Uri): String {
        val photos = MediaFiles.photosDir(appContext).canonicalFile
        val existing = runCatching { File(uri.path.orEmpty()).canonicalFile }.getOrNull()
        if (existing?.isFile == true && existing.parentFile == photos && existing.length() in 1..MAX_UPLOAD_BYTES) {
            return existing.absolutePath
        }
        val target = File(photos, "${UUID.randomUUID()}.jpg")
        val temporary = File(photos, ".${target.name}.part")
        var decoded: Bitmap? = null
        var output: Bitmap? = null
        try {
            val source = decodeAlbumBitmap(uri).also { decoded = it }
            output = if (source.hasAlpha()) {
                Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { flattened ->
                    Canvas(flattened).apply { drawColor(Color.WHITE); drawBitmap(source, 0f, 0f, null) }
                }
            } else source
            FileOutputStream(temporary).use { check(output.compress(Bitmap.CompressFormat.JPEG, 84, it)) }
            require(temporary.length() in 1..MAX_UPLOAD_BYTES) { "照片压缩后仍超过 8MB" }
            require(temporary.renameTo(target)) { "照片保存失败" }
            return target.absolutePath
        } finally {
            if (output !== decoded) output?.recycle()
            decoded?.recycle()
            temporary.delete()
        }
    }

    private fun decodeAlbumBitmap(uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(appContext.contentResolver, uri)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_IMAGE_EDGE) {
                    val ratio = MAX_IMAGE_EDGE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * ratio).toInt().coerceAtLeast(1), (info.size.height * ratio).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_IMAGE_EDGE) sample *= 2
        return appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("无法读取图片")
    }

    private companion object {
        const val MAX_UPLOAD_BYTES = 8L * 1024 * 1024
        const val MAX_IMAGE_EDGE = 2048
        val uploadSlots = Semaphore(2)
        val syncMutexes = ConcurrentHashMap<String, Mutex>()
    }
}

internal fun sortedAlbumMedia(items: List<MediaEntity>, newestFirst: Boolean): List<MediaEntity> =
    if (newestFirst) items.sortedWith(compareByDescending<MediaEntity> { it.sortOrder }.thenByDescending { it.createdAt })
    else items.sortedWith(compareBy<MediaEntity> { it.sortOrder }.thenBy { it.createdAt })

internal fun albumDisplayUri(item: MediaEntity): String =
    item.localUri.takeIf { it.isNotBlank() && File(it.removePrefix("file://")).isFile }
        ?: item.remoteUrl.ifBlank { item.uri }

internal suspend fun <T> persistAlbumCopy(
    context: Context,
    local: String,
    write: suspend () -> T,
): Result<T> = try {
    Result.success(write())
} catch (cancelled: CancellationException) {
    MediaFiles.deletePrivate(context, listOf(local))
    throw cancelled
} catch (error: Throwable) {
    MediaFiles.deletePrivate(context, listOf(local))
    Result.failure(error)
}
