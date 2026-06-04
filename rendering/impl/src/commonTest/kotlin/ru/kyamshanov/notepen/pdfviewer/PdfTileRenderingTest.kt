package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfTileRenderingTest {
    @Test
    fun visiblePdfTileRequestsReturnsOnlyIntersectingTiles() {
        val requests =
            visiblePdfTileRequests(
                layout = oneSquarePageLayout(),
                pageIndex = 0,
                source = PageSourceSpec(sourceIndex = 0),
                pan = Offset(x = -1500f, y = 0f),
                zoom = 4f,
                viewportSize = IntSize(width = 1000, height = 1000),
                scaleBucket = 400,
                rotationQuarters = 0,
                cropSignature = 0,
                tileSizePx = 1000,
                preloadTileRing = 0,
            )

        assertEquals(setOf(1 to 0, 2 to 0), requests.map { it.key.tileX to it.key.tileY }.toSet())
        assertTrue(requests.all { it.fullPageWidthPx == 4000 && it.fullPageHeightPx == 4000 })
    }

    @Test
    fun visiblePdfTileRequestsExpandsByPreloadRing() {
        val requests =
            visiblePdfTileRequests(
                layout = oneSquarePageLayout(),
                pageIndex = 0,
                source = PageSourceSpec(sourceIndex = 0),
                pan = Offset(x = -1500f, y = 0f),
                zoom = 4f,
                viewportSize = IntSize(width = 1000, height = 1000),
                scaleBucket = 400,
                rotationQuarters = 0,
                cropSignature = 0,
                tileSizePx = 1000,
                preloadTileRing = 1,
            )

        assertEquals((0..3).flatMap { x -> listOf(x to 0, x to 1) }.toSet(), requests.map { it.key.tileX to it.key.tileY }.toSet())
    }

    @Test
    fun pdfLayerHasVisibleBaseForFullBitmapAndPreview() {
        assertTrue(PdfPageLayer.FullBitmap(ImageBitmap(10, 10)).hasVisiblePdfBase())
        assertTrue(
            PdfPageLayer.Tiles(
                tiles = emptyList(),
                missingTiles = listOf(tilePlaceholder()),
                lowResPreview = ImageBitmap(10, 10),
            ).hasVisiblePdfBase(),
        )
    }

    @Test
    fun pdfLayerHasVisibleBaseOnlyWhenAllVisibleTilesAreReadyWithoutPreview() {
        assertFalse(PdfPageLayer.FullBitmap(null).hasVisiblePdfBase())
        assertFalse(
            PdfPageLayer.Tiles(
                tiles = emptyList(),
                missingTiles = listOf(tilePlaceholder()),
                lowResPreview = null,
            ).hasVisiblePdfBase(),
        )
        assertFalse(
            PdfPageLayer.Tiles(
                tiles = listOf(tile()),
                missingTiles = listOf(tilePlaceholder(tileLeftPx = 10)),
                lowResPreview = null,
            ).hasVisiblePdfBase(),
        )
        assertTrue(
            PdfPageLayer.Tiles(
                tiles = listOf(tile()),
                missingTiles = emptyList(),
                lowResPreview = null,
            ).hasVisiblePdfBase(),
        )
    }

    @Test
    fun pdfTileCachePutAllStoresBatchAndEvictsUnprotectedTiles() {
        val cache = PdfTileCache(maxTotalPixels = 250)
        val oldTile = tile(tileX = 0)
        val newTiles = listOf(tile(tileX = 1), tile(tileX = 2))

        cache.put(oldTile, protectedKeys = setOf(oldTile.key))
        cache.putAll(newTiles, protectedKeys = newTiles.map { it.key }.toSet())

        assertEquals(newTiles.map { it.key }.toSet(), cache.entries.keys.toSet())
    }

    private fun oneSquarePageLayout(): PdfPagesLayout =
        PdfPagesLayout.build(
            pages = listOf(PdfPageInfo(pageIndex = 0, widthPt = 1000f, heightPt = 1000f)),
            basePageWidthPx = 1000f,
        )

    private fun tilePlaceholder(tileLeftPx: Int = 0): PdfTilePlaceholder =
        PdfTilePlaceholder(
            tileLeftPx = tileLeftPx,
            tileTopPx = 0,
            tileWidthPx = 10,
            tileHeightPx = 10,
            fullPageWidthPx = 20,
            fullPageHeightPx = 20,
        )

    private fun tile(tileX: Int = 0): PdfTile =
        PdfTile(
            bitmap = ImageBitmap(10, 10),
            key =
                PdfTileKey(
                    logicalPageIndex = 0,
                    sourcePageIndex = 0,
                    scaleBucket = 400,
                    rotationQuarters = 0,
                    cropSignature = 0,
                    tileX = tileX,
                    tileY = 0,
                ),
            tileLeftPx = tileX * 10,
            tileTopPx = 0,
            tileWidthPx = 10,
            tileHeightPx = 10,
            fullPageWidthPx = 20,
            fullPageHeightPx = 20,
            renderedScalePercent = 400,
        )
}
