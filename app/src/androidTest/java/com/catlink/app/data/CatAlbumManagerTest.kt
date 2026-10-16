package com.catlink.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.catlink.app.data.db.AppDatabase
import com.catlink.app.network.AuthApiClient
import com.catlink.app.network.AuthSessionSnapshot
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class CatAlbumManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val server = MockWebServer()
    private var database: AppDatabase? = null
    private var databaseName = ""

    @After
    fun tearDown() {
        database?.close()
        if (databaseName.isNotBlank()) context.deleteDatabase(databaseName)
        server.shutdown()
        MediaFiles.photosDir(context).listFiles()?.filter { it.name.startsWith("task11-") }?.forEach(File::delete)
    }

    @Test
    fun partialUploadPersistsAndRetryUsesServerUrlAfterRestart() = runBlocking {
        server.start()
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"id":"31","url":"/uploads/catlink/cat/one.png"},"msg":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"code":1,"data":{},"msg":"暂时不可用"}"""))
        databaseName = "album-${System.nanoTime()}.db"
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        val repository = VaultRepository(database!!.vaultDao(), context)
        val api = PublicApi(
            AuthApiClient(
                baseUrlProvider = { server.url("/").toString() },
                authSessionProvider = { AuthSessionSnapshot("token", 0L) },
            )
        )
        val manager = CatAlbumManager(context, repository, api) { true }
        val one = source("task11-one.png")
        val two = source("task11-two.png")

        val first = manager.import("cat-1", listOf(Uri.fromFile(one), Uri.fromFile(two)))

        assertEquals(2, first.imported)
        assertEquals(1, first.synced)
        assertEquals(1, first.failed)
        database!!.close()
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        val restoredRepository = VaultRepository(database!!.vaultDao(), context)
        val restored = restoredRepository.catMedia("cat-1")
        assertEquals(2, restored.size)
        assertEquals(1, restored.count { it.syncState == AlbumSyncState.FAILED.value })
        assertEquals(1, restored.count { it.uri.startsWith("http") && it.serverMediaId == "31" })

        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"id":"32","url":"/uploads/catlink/cat/two.png"},"msg":"ok"}"""))
        val retryManager = CatAlbumManager(context, restoredRepository, api) { true }
        val retry = retryManager.syncPending(restored.filter { it.syncState == AlbumSyncState.FAILED.value })

        assertEquals(1, retry.synced)
        val final = restoredRepository.catMedia("cat-1")
        assertTrue(final.all { it.syncState == AlbumSyncState.UPLOADED.value })
        assertTrue(final.all { it.uri.startsWith("http") && it.serverMediaId.isNotBlank() })
        final.forEach { restoredRepository.removeMedia(it) }
        one.delete()
        two.delete()
        Unit
    }

    @Test
    fun oversizedImportRemovesCopiedPrivateFile() = runBlocking {
        server.start()
        databaseName = "album-${System.nanoTime()}.db"
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        val repository = VaultRepository(database!!.vaultDao(), context)
        val api = PublicApi(
            AuthApiClient(
                baseUrlProvider = { server.url("/").toString() },
                authSessionProvider = { AuthSessionSnapshot(null, 0L) },
            )
        )
        val before = MediaFiles.photosDir(context).listFiles().orEmpty().map(File::getName).toSet()
        val source = File(context.cacheDir, "task11-oversized.jpg").apply {
            RandomAccessFile(this, "rw").use { it.setLength(8L * 1024 * 1024 + 1) }
        }

        val result = CatAlbumManager(context, repository, api) { false }
            .import("cat-oversized", listOf(Uri.fromFile(source)))

        assertEquals(0, result.imported)
        assertEquals(1, result.failed)
        assertEquals(before, MediaFiles.photosDir(context).listFiles().orEmpty().map(File::getName).toSet())
        assertTrue(repository.catMedia("cat-oversized").isEmpty())
        source.delete()
        Unit
    }

    @Test
    fun oversizedExistingPrivateFileIsNotDeleted() = runBlocking {
        server.start()
        databaseName = "album-${System.nanoTime()}.db"
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        val repository = VaultRepository(database!!.vaultDao(), context)
        val existing = File(MediaFiles.photosDir(context), "task11-existing.jpg").apply {
            RandomAccessFile(this, "rw").use { it.setLength(8L * 1024 * 1024 + 1) }
        }

        val result = CatAlbumManager(
            context,
            repository,
            PublicApi(AuthApiClient(baseUrlProvider = { server.url("/").toString() })),
        ) { false }.import("cat-existing", listOf(Uri.fromFile(existing)))

        assertEquals(0, result.imported)
        assertEquals(1, result.failed)
        assertTrue(existing.isFile)
    }

    @Test
    fun largeAlbumPhotoIsCompressedBeforePersistence() = runBlocking {
        server.start()
        databaseName = "album-${System.nanoTime()}.db"
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        val repository = VaultRepository(database!!.vaultDao(), context)
        val source = File(context.cacheDir, "task18-large-album.png")
        val bitmap = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888)
        FileOutputStream(source).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        val result = CatAlbumManager(
            context,
            repository,
            PublicApi(AuthApiClient(baseUrlProvider = { server.url("/").toString() })),
        ) { false }.import("cat-compressed", listOf(Uri.fromFile(source)))

        assertEquals(1, result.imported)
        val stored = repository.catMedia("cat-compressed").single()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(stored.localUri, bounds)
        assertTrue(maxOf(bounds.outWidth, bounds.outHeight) <= 2048)
        assertTrue(File(stored.localUri).length() in 1..(8L * 1024 * 1024))
        repository.removeMedia(stored)
        source.delete()
        Unit
    }

    @Test
    fun invalidRemoteUrlKeepsLocalRetryAndDeletesServerMedia() = runBlocking {
        server.start()
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"id":"41","url":"https://attacker.example/photo.png"},"msg":"ok"}"""))
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{},"msg":"ok"}"""))
        databaseName = "album-${System.nanoTime()}.db"
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        val repository = VaultRepository(database!!.vaultDao(), context)
        val source = source("task11-invalid-url.png")
        val manager = CatAlbumManager(
            context,
            repository,
            PublicApi(
                AuthApiClient(
                    baseUrlProvider = { server.url("/").toString() },
                    authSessionProvider = { AuthSessionSnapshot("token", 0L) },
                )
            ),
        ) { true }

        val result = manager.import("cat-invalid-url", listOf(Uri.fromFile(source)))
        val saved = repository.catMedia("cat-invalid-url").single()

        assertEquals(1, result.imported)
        assertEquals(1, result.failed)
        assertEquals(AlbumSyncState.FAILED.value, saved.syncState)
        assertTrue(File(saved.localUri).isFile)
        assertEquals("/api/v1/media/upload", server.takeRequest().path)
        val cleanup = server.takeRequest()
        assertEquals("DELETE", cleanup.method)
        assertEquals("/api/v1/media/41", cleanup.path)
        repository.removeMedia(saved)
        source.delete()
        Unit
    }

    @Test
    fun offlineImportUploadsAfterSignIn() = runBlocking {
        server.start()
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"id":"51","url":"/uploads/catlink/cat/offline.png"},"msg":"ok"}"""))
        databaseName = "album-${System.nanoTime()}.db"
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        val repository = VaultRepository(database!!.vaultDao(), context)
        val api = PublicApi(
            AuthApiClient(
                baseUrlProvider = { server.url("/").toString() },
                authSessionProvider = { AuthSessionSnapshot("token", 0L) },
            )
        )
        val source = source("task11-offline.png")

        val imported = CatAlbumManager(context, repository, api) { false }
            .import("cat-offline", listOf(Uri.fromFile(source)))
        val local = repository.catMedia("cat-offline").single()
        assertEquals(AlbumSyncState.LOCAL.value, local.syncState)
        assertTrue(File(local.localUri).isFile)

        val synced = CatAlbumManager(context, repository, api) { true }.syncPending(repository.pendingCatMedia())
        val remote = repository.catMedia("cat-offline").single()

        assertEquals(1, imported.imported)
        assertEquals(0, imported.synced)
        assertEquals(1, synced.synced)
        assertEquals(AlbumSyncState.UPLOADED.value, remote.syncState)
        assertEquals("51", remote.serverMediaId)
        assertTrue(remote.uri.startsWith("http://"))
        repository.removeMedia(remote)
        source.delete()
        Unit
    }

    @Test
    fun databaseFailureRemovesCopiedPhoto() = runBlocking {
        server.start()
        val copied = File(MediaFiles.photosDir(context), "task11-database-failure.png").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }

        val result = persistAlbumCopy(context, copied.absolutePath) { error("database write failed") }

        assertTrue(result.isFailure)
        assertTrue(!copied.exists())
    }

    private fun source(name: String): File = File(context.cacheDir, name).apply {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        FileOutputStream(this).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
