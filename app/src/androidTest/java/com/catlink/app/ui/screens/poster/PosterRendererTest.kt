package com.catlink.app.ui.screens.poster

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosterRendererTest {
    @Test
    fun allLayoutsAndColorsKeepLongContentInsideSafeArea() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val quote = "愿每一次认真记录都能让被看见的生命离家更近一点，也愿每一位没有放弃的救助人，在忙碌和疲惫之后仍然收到温柔的回应。".repeat(2).take(120)
        val audits = auditPosterLayoutSamples(context, quote)

        assertEquals(128, audits.size)
        assertEquals(16, audits.map { it.layout }.toSet().size)
        assertEquals(8, audits.map { it.style }.toSet().size)
        audits.forEach { audit ->
            assertTrue("${audit.style}/${audit.layout} quote truncated", audit.quoteFits)
            assertTrue("${audit.style}/${audit.layout} invalid line count", audit.quoteLineCount in 1..10)
            audit.elements.forEach { element ->
                assertTrue("${audit.style}/${audit.layout}/${element.name} left clipped", element.left >= PosterSafeInset)
                assertTrue("${audit.style}/${audit.layout}/${element.name} top clipped", element.top >= PosterSafeInset)
                assertTrue("${audit.style}/${audit.layout}/${element.name} right clipped", element.right <= 1080f - PosterSafeInset)
                assertTrue("${audit.style}/${audit.layout}/${element.name} bottom clipped", element.bottom <= 1920f - PosterSafeInset)
            }
            val quoteBounds = audit.elements.single { it.name == "quote" }
            val metricBounds = audit.elements.single { it.name == "metric" }
            assertTrue(
                "${audit.style}/${audit.layout} quote overlaps metric",
                quoteBounds.bottom + 20f <= metricBounds.top || metricBounds.bottom + 20f <= quoteBounds.top,
            )
        }
    }

    @Test
    fun rendersAllLayoutsForVisualInspection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val output = File(context.getExternalFilesDir(null), "poster-audit").apply {
            deleteRecursively()
            mkdirs()
        }
        repeat(16) { index ->
            val bitmap = renderPosterSample(
                context,
                index,
                "今天做过的每一件小事，\n都在替它靠近一个家。",
            )
            FileOutputStream(File(output, "layout-${index + 1}.png")).use {
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
        assertEquals(16, output.listFiles()?.size)
    }

    @Test
    fun previewUsesQuarterOfExportBitmapMemoryAndLogoIsTransparent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preview = renderPosterSample(context, 0, "被认真记录的生命，终会被温柔地看见。", bitmapWidth = 540)
        val export = renderPosterSample(context, 0, "被认真记录的生命，终会被温柔地看见。")
        assertEquals(540, preview.width)
        assertEquals(960, preview.height)
        assertEquals(export.allocationByteCount / 4, preview.allocationByteCount)
        preview.recycle()
        export.recycle()

        val logo = BitmapFactory.decodeResource(context.resources, com.catlink.app.R.drawable.catlink_logo_avatar)
        assertTrue("logo must keep a transparent background", logo.getPixel(0, 0) ushr 24 == 0)
        logo.recycle()
    }
}
