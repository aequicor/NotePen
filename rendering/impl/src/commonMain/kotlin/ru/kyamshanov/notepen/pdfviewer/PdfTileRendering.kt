package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

data class PdfTileKey(
    val logicalPageIndex: Int,
    val sourcePageIndex: Int,
    val scaleBucket: Int,
    val rotationQuarters: Int,
    val cropSignature: Int,
    val tileX: Int,
    val tileY: Int,
)

data class PdfTile(
    val bitmap: ImageBitmap,
    val key: PdfTileKey,
    val tileLeftPx: Int,
    val tileTopPx: Int,
    val tileWidthPx: Int,
    val tileHeightPx: Int,
    val fullPageWidthPx: Int,
    val fullPageHeightPx: Int,
    val renderedScalePercent: Int,
)

data class PdfTilePlaceholder(
    val tileLeftPx: Int,
    val tileTopPx: Int,
    val tileWidthPx: Int,
    val tileHeightPx: Int,
    val fullPageWidthPx: Int,
    val fullPageHeightPx: Int,
)

sealed interface PdfPageLayer {
    data class FullBitmap(
        val bitmap: ImageBitmap?,
    ) : PdfPageLayer

    data class Tiles(
        val tiles: List<PdfTile>,
        val missingTiles: List<PdfTilePlaceholder>,
        val lowResPreview: ImageBitmap?,
    ) : PdfPageLayer
}

internal fun PdfPageLayer.hasVisiblePdfBase(): Boolean =
    when (this) {
        is PdfPageLayer.FullBitmap -> bitmap != null
        is PdfPageLayer.Tiles -> lowResPreview != null || (tiles.isNotEmpty() && missingTiles.isEmpty())
    }

internal class PdfTileCache(
    private val maxTotalPixels: Long,
) {
    private val accessOrder = ArrayDeque<PdfTileKey>()

    val entries: SnapshotStateMap<PdfTileKey, PdfTile> = SnapshotStateMap()

    fun get(key: PdfTileKey): PdfTile? {
        val tile = entries[key] ?: return null
        touch(key)
        return tile
    }

    fun put(
        tile: PdfTile,
        protectedKeys: Set<PdfTileKey>,
    ) {
        putAll(listOf(tile), protectedKeys)
    }

    fun putAll(
        tiles: List<PdfTile>,
        protectedKeys: Set<PdfTileKey>,
    ) {
        if (tiles.isEmpty()) return
        Snapshot.withMutableSnapshot {
            for (tile in tiles) {
                val key = tile.key
                if (entries.containsKey(key)) {
                    entries[key] = tile
                    touch(key)
                } else {
                    entries[key] = tile
                    accessOrder.addLast(key)
                }
            }
            evictIfNeeded(protectedKeys + tiles.map { it.key })
        }
    }

    fun tilesForRequests(requests: List<PdfTileRequest>): List<PdfTile> = requests.mapNotNull { entries[it.key] }

    fun missingTilesForRequests(requests: List<PdfTileRequest>): List<PdfTilePlaceholder> =
        requests.mapNotNull { request ->
            if (entries.containsKey(request.key)) {
                null
            } else {
                PdfTilePlaceholder(
                    tileLeftPx = request.tileLeftPx,
                    tileTopPx = request.tileTopPx,
                    tileWidthPx = request.tileWidthPx,
                    tileHeightPx = request.tileHeightPx,
                    fullPageWidthPx = request.fullPageWidthPx,
                    fullPageHeightPx = request.fullPageHeightPx,
                )
            }
        }

    private fun touch(key: PdfTileKey) {
        accessOrder.remove(key)
        accessOrder.addLast(key)
    }

    private fun remove(key: PdfTileKey) {
        accessOrder.remove(key)
        entries.remove(key)
    }

    private fun evictIfNeeded(protect: Set<PdfTileKey>) {
        while (totalPixels() > maxTotalPixels) {
            val evicted = accessOrder.firstOrNull { it !in protect } ?: break
            remove(evicted)
        }
    }

    private fun totalPixels(): Long {
        var total = 0L
        for (tile in entries.values) {
            total += tile.bitmap.width.toLong() * tile.bitmap.height
        }
        return total
    }
}

internal data class PdfTileRequest(
    val key: PdfTileKey,
    val tileLeftPx: Int,
    val tileTopPx: Int,
    val tileWidthPx: Int,
    val tileHeightPx: Int,
    val fullPageWidthPx: Int,
    val fullPageHeightPx: Int,
)

private data class VisibleTileGeometry(
    val visibleN: Rect,
    val fullWidthPx: Int,
    val fullHeightPx: Int,
)

internal fun shouldUsePdfTiles(
    scalePercent: Int,
    targetWidthPx: Int,
    targetHeightPx: Int,
    maxRenderDimensionPx: Int,
    minTileScalePercent: Int,
): Boolean =
    scalePercent >= minTileScalePercent ||
        targetWidthPx > maxRenderDimensionPx ||
        targetHeightPx > maxRenderDimensionPx

internal fun pdfTileScaleBucket(scalePercent: Int): Int {
    val buckets = intArrayOf(100, 150, 200, 300, 400, 600, 800)
    return buckets.firstOrNull { it >= scalePercent } ?: buckets.last()
}

internal fun visiblePdfTileRequests(
    layout: PdfPagesLayout,
    pageIndex: Int,
    source: PageSourceSpec,
    pan: androidx.compose.ui.geometry.Offset,
    zoom: Float,
    viewportSize: IntSize,
    scaleBucket: Int,
    rotationQuarters: Int,
    cropSignature: Int,
    tileSizePx: Int,
    preloadTileRing: Int = 1,
): List<PdfTileRequest> {
    val geometry =
        visibleTileGeometry(
            layout = layout,
            pageIndex = pageIndex,
            pan = pan,
            zoom = zoom,
            viewportSize = viewportSize,
            scaleBucket = scaleBucket,
        ) ?: return emptyList()
    return visiblePdfTileRequests(
        geometry = geometry,
        pageIndex = pageIndex,
        source = source,
        scaleBucket = scaleBucket,
        rotationQuarters = rotationQuarters,
        cropSignature = cropSignature,
        tileSizePx = tileSizePx,
        preloadTileRing = preloadTileRing,
    )
}

private fun visibleTileGeometry(
    layout: PdfPagesLayout,
    pageIndex: Int,
    pan: androidx.compose.ui.geometry.Offset,
    zoom: Float,
    viewportSize: IntSize,
    scaleBucket: Int,
): VisibleTileGeometry? {
    val basePageWidth = layout.basePageWidthPx
    val pdfHeight = layout.pdfHeightsPx.getOrNull(pageIndex) ?: 0f
    val invalidViewport = zoom <= 0f || viewportSize.width <= 0 || viewportSize.height <= 0
    val invalidPage = basePageWidth <= 0f || pdfHeight <= 0f
    return if (invalidViewport || invalidPage) {
        null
    } else {
        val pageLeft = layout.pageLeftsPx[pageIndex]
        val pageTop = layout.pageTopsPx[pageIndex]
        val visibleN =
            Rect(
                left = ((-pan.x / zoom) - pageLeft) / basePageWidth,
                top = ((-pan.y / zoom) - pageTop) / pdfHeight,
                right = (((viewportSize.width - pan.x) / zoom) - pageLeft) / basePageWidth,
                bottom = (((viewportSize.height - pan.y) / zoom) - pageTop) / pdfHeight,
            ).intersectUnit()
        if (visibleN.isEmpty) {
            null
        } else {
            VisibleTileGeometry(
                visibleN = visibleN,
                fullWidthPx = (basePageWidth * scaleBucket / 100f).roundToInt().coerceAtLeast(1),
                fullHeightPx = (pdfHeight * scaleBucket / 100f).roundToInt().coerceAtLeast(1),
            )
        }
    }
}

private fun visiblePdfTileRequests(
    geometry: VisibleTileGeometry,
    pageIndex: Int,
    source: PageSourceSpec,
    scaleBucket: Int,
    rotationQuarters: Int,
    cropSignature: Int,
    tileSizePx: Int,
    preloadTileRing: Int,
): List<PdfTileRequest> {
    val visibleN = geometry.visibleN
    val fullWidthPx = geometry.fullWidthPx
    val fullHeightPx = geometry.fullHeightPx
    val ring = preloadTileRing.coerceAtLeast(0)
    val tile = tileSizePx.coerceAtLeast(1)
    val minTileX = floor(visibleN.left * fullWidthPx / tile).toInt().coerceAtLeast(0)
    val minTileY = floor(visibleN.top * fullHeightPx / tile).toInt().coerceAtLeast(0)
    val maxTileX = ceil(visibleN.right * fullWidthPx / tile).toInt().coerceAtLeast(1) - 1
    val maxTileY = ceil(visibleN.bottom * fullHeightPx / tile).toInt().coerceAtLeast(1) - 1
    val lastTileX = (fullWidthPx - 1) / tile
    val lastTileY = (fullHeightPx - 1) / tile
    val fromX = (minTileX - ring).coerceAtLeast(0)
    val toX = (maxTileX + ring).coerceAtMost(lastTileX)
    val fromY = (minTileY - ring).coerceAtLeast(0)
    val toY = (maxTileY + ring).coerceAtMost(lastTileY)
    val centerX = (visibleN.center.x * fullWidthPx / tile).toInt()
    val centerY = (visibleN.center.y * fullHeightPx / tile).toInt()

    return buildList {
        for (ty in fromY..toY) {
            for (tx in fromX..toX) {
                val left = tx * tile
                val top = ty * tile
                val width = minOf(tile, fullWidthPx - left)
                val height = minOf(tile, fullHeightPx - top)
                if (width <= 0 || height <= 0) continue
                add(
                    PdfTileRequest(
                        key =
                            PdfTileKey(
                                logicalPageIndex = pageIndex,
                                sourcePageIndex = source.sourceIndex,
                                scaleBucket = scaleBucket,
                                rotationQuarters = rotationQuarters,
                                cropSignature = cropSignature,
                                tileX = tx,
                                tileY = ty,
                            ),
                        tileLeftPx = left,
                        tileTopPx = top,
                        tileWidthPx = width,
                        tileHeightPx = height,
                        fullPageWidthPx = fullWidthPx,
                        fullPageHeightPx = fullHeightPx,
                    ),
                )
            }
        }
    }.sortedBy { kotlin.math.abs(it.key.tileX - centerX) + kotlin.math.abs(it.key.tileY - centerY) }
}

private fun Rect.intersectUnit(): Rect {
    val l = left.coerceIn(0f, 1f)
    val t = top.coerceIn(0f, 1f)
    val r = right.coerceIn(0f, 1f)
    val b = bottom.coerceIn(0f, 1f)
    return if (r <= l || b <= t) Rect.Zero else Rect(l, t, r, b)
}
