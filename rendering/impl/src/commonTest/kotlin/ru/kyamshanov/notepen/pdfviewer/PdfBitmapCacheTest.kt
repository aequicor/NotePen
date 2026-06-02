package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfBitmapCacheTest {
    @Test
    fun freshRejectsBitmapSmallerThanCurrentTarget() {
        val rendered =
            RenderedPage(
                bitmap = ImageBitmap(800, 1200),
                renderedAtScalePercent = 100,
            )

        assertFalse(
            PdfBitmapCache(maxEntries = 1).isFresh(
                rendered = rendered,
                scalePercent = 100,
                rotationQuarters = 0,
                cropSignature = 0,
                targetWidthPx = 1200,
                targetHeightPx = 1600,
                maxOversizeRatio = 1.75f,
            ),
        )
    }

    @Test
    fun freshRejectsBitmapMuchLargerThanCurrentTarget() {
        val rendered =
            RenderedPage(
                bitmap = ImageBitmap(2400, 3200),
                renderedAtScalePercent = 100,
            )

        assertFalse(
            PdfBitmapCache(maxEntries = 1).isFresh(
                rendered = rendered,
                scalePercent = 100,
                rotationQuarters = 0,
                cropSignature = 0,
                targetWidthPx = 1000,
                targetHeightPx = 1400,
                maxOversizeRatio = 1.75f,
            ),
        )
    }

    @Test
    fun freshAcceptsBitmapNearCurrentTarget() {
        val rendered =
            RenderedPage(
                bitmap = ImageBitmap(1200, 1600),
                renderedAtScalePercent = 100,
            )

        assertTrue(
            PdfBitmapCache(maxEntries = 1).isFresh(
                rendered = rendered,
                scalePercent = 100,
                rotationQuarters = 0,
                cropSignature = 0,
                targetWidthPx = 1000,
                targetHeightPx = 1400,
                maxOversizeRatio = 1.75f,
            ),
        )
    }

    @Test
    fun putDoesNotEvictProtectedVisiblePages() {
        val cache = PdfBitmapCache(maxEntries = 2)

        cache.put(1, RenderedPage(bitmap = ImageBitmap(10, 10), renderedAtScalePercent = 100))
        cache.put(2, RenderedPage(bitmap = ImageBitmap(10, 10), renderedAtScalePercent = 100))
        cache.put(
            3,
            RenderedPage(bitmap = ImageBitmap(10, 10), renderedAtScalePercent = 100),
            protectedPageIndices = setOf(1, 3),
        )

        assertNotNull(cache.entries[1])
        assertNull(cache.entries[2])
        assertNotNull(cache.entries[3])
    }
}
