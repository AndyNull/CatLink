package com.catlink.app.ui.screens.community

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PostMediaDraftTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun largeImageIsCompressedToBoundedLocalDraft() = runBlocking {
        val source = File(context.cacheDir, "post-source.png")
        val bitmap = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888)
        FileOutputStream(source).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        val result = preparePostPhotos(context, emptyList(), listOf(Uri.fromFile(source)))
        val draft = result.photos.single()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(draft.localPath, bounds)

        assertEquals(0, result.failed)
        assertTrue(maxOf(bounds.outWidth, bounds.outHeight) <= 1600)
        assertTrue(File(draft.localPath).length() in 1..(8L * 1024 * 1024))
        deletePostDraftFiles(context, result.photos)
        source.delete()
        Unit
    }

    @Test
    fun selectionOverLimitKeepsFourAndReportsIgnoredCount() = runBlocking {
        val sources = (1..5).map { index ->
            File(context.cacheDir, "post-source-$index.png").apply {
                val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                FileOutputStream(this).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }

        val result = preparePostPhotos(context, emptyList(), sources.map(Uri::fromFile))

        assertEquals(4, result.photos.size)
        assertEquals(1, result.ignored)
        assertEquals(0, result.failed)
        deletePostDraftFiles(context, result.photos)
        sources.forEach(File::delete)
    }
}
