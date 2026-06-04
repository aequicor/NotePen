package ru.kyamshanov.notepen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.ToolKind
import ru.kyamshanov.notepen.tools.marker.drawMarkerStroke
import kotlin.math.pow
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas

/** Множитель «расширения» штриха при сильном наклоне пера (0..1 → ×(1..1+gain)). */
private const val TILT_WIDTH_GAIN = 0.5f

/**
 * Floor for any rendered stroke segment in pixels. Below 1px Skia's stroking
 * pipeline produces broken or invisible lines.
 */
private const val MIN_RENDERED_STROKE_PX = 1f

private const val MIN_WIDTH_FACTOR = 0.42f
private const val PRESSURE_GAMMA = 0.65f

/**
 * Android ceiling on either dimension of the off-screen ink-cache bitmap.
 */
internal const val ANDROID_INK_CACHE_MAX_DIMENSION_PX = 3072

/**
 * Bucket size for the completed-ink cache key. Quantising size prevents
 * continuous pinch-zoom from re-rasterising all completed strokes each frame.
 */
internal const val INK_CACHE_DIM_BUCKET_PX = 256

/**
 * Small idle window before rebuilding completed-ink bitmaps after pen-up.
 * Quick handwriting often starts the next stroke within this interval; delaying
 * the rebuild keeps live overlay frames ahead of background cache work.
 */
internal const val COMPLETED_INK_REBUILD_IDLE_DELAY_MS = 120L

/**
 * Upper bound for vector anti-flicker tails while the completed-ink bitmap cache
 * is cold or catching up. Larger tails are deferred to the async bitmap cache:
 * replaying many variable-width strokes on the UI draw path is the exact freeze
 * seen on high-zoom pages with annotations.
 */
internal const val MAX_VECTOR_INK_TAIL_PATHS = 24
internal const val MAX_VECTOR_INK_TAIL_POINTS = 1_200

internal data class CompletedInk(
    val strokeCount: Int,
    val bitmap: ImageBitmap,
)

internal fun CompletedInk.isUpscaledTo(size: IntSize): Boolean = bitmap.width < size.width || bitmap.height < size.height

internal data class InkRenderSpec(
    val surfaceSize: IntSize,
    val extent: PageExtent,
    val targetOnPage: Rect? = null,
) {
    val pdfWidthPx: Float
        get() {
            val targetWidth = targetOnPage?.width ?: extent.width
            return if (targetWidth > 0f) surfaceSize.width.toFloat() / targetWidth else surfaceSize.width.toFloat()
        }

    val pdfHeightPx: Float
        get() {
            val targetHeight = targetOnPage?.height ?: extent.height
            return if (targetHeight > 0f) surfaceSize.height.toFloat() / targetHeight else surfaceSize.height.toFloat()
        }

    val translation: Offset
        get() {
            val target = targetOnPage ?: return Offset.Zero
            return Offset(
                x = (extent.left - target.left) * pdfWidthPx,
                y = (extent.top - target.top) * pdfHeightPx,
            )
        }

    fun mapPageToSurface(point: Offset): Offset =
        translation +
            Offset(
                x = (point.x - extent.left) * pdfWidthPx,
                y = (point.y - extent.top) * pdfHeightPx,
            )
}

internal data class CompletedInkCacheKey(
    val surfaceSize: IntSize,
    val extent: PageExtent,
    val targetOnPage: Rect?,
    val historyVersion: Int,
)

internal fun completedInkCacheKey(
    spec: InkRenderSpec,
    historyVersion: Int,
): CompletedInkCacheKey =
    CompletedInkCacheKey(
        surfaceSize = spec.surfaceSize,
        extent = spec.extent,
        targetOnPage = spec.targetOnPage,
        historyVersion = historyVersion,
    )

internal fun cappedInkCacheSize(
    surfaceSize: IntSize,
    maxDimensionPx: Int,
): IntSize {
    val w = surfaceSize.width
    val h = surfaceSize.height
    if (w <= 0 || h <= 0) return IntSize.Zero
    val longest = maxOf(w, h)
    if (longest <= maxDimensionPx) return surfaceSize
    return IntSize(
        width = (w.toLong() * maxDimensionPx / longest).toInt().coerceAtLeast(1),
        height = (h.toLong() * maxDimensionPx / longest).toInt().coerceAtLeast(1),
    )
}

internal fun bucketedInkCacheSize(
    surfaceSize: IntSize,
    maxDimensionPx: Int,
    bucketPx: Int = INK_CACHE_DIM_BUCKET_PX,
): IntSize {
    val w = surfaceSize.width
    val h = surfaceSize.height
    if (w <= 0 || h <= 0) return IntSize.Zero
    val capped = cappedInkCacheSize(surfaceSize, maxDimensionPx)
    if (capped != surfaceSize) return capped
    val bucket = bucketPx.coerceAtLeast(1)
    return IntSize(
        width = ((w + bucket - 1) / bucket * bucket).coerceAtLeast(bucket),
        height = ((h + bucket - 1) / bucket * bucket).coerceAtLeast(bucket),
    )
}

internal fun completedInkTailStart(
    pathCount: Int,
    cachedStrokeCount: Int?,
): Int = cachedStrokeCount?.coerceIn(0, pathCount) ?: 0

internal inline fun shouldDrawCompletedInkTail(
    paths: List<DrawingPath>,
    tailStart: Int,
    maxTailPaths: Int = MAX_VECTOR_INK_TAIL_PATHS,
    maxTailPoints: Int = MAX_VECTOR_INK_TAIL_POINTS,
    includePath: (DrawingPath) -> Boolean,
): Boolean {
    var pathCount = 0
    var pointCount = 0
    for (i in tailStart until paths.size) {
        val path = paths[i]
        if (!includePath(path)) continue
        pathCount += 1
        pointCount += path.points.size
        if (pathCount > maxTailPaths || pointCount > maxTailPoints) return false
    }
    return true
}

/**
 * Rasterises completed pen strokes into an off-screen bitmap. Marker strokes
 * are intentionally skipped: they must be multiply-blended directly over PDF
 * pixels in the main canvas.
 */
internal suspend fun buildCompletedInk(
    spec: InkRenderSpec,
    paths: List<DrawingPath>,
    density: Density,
    layoutDirection: LayoutDirection,
): ImageBitmap {
    val renderContext = currentCoroutineContext()
    val bw = spec.surfaceSize.width
    val bh = spec.surfaceSize.height
    val bmp = ImageBitmap(bw, bh)
    val gCanvas = GraphicsCanvas(bmp)
    val scope = CanvasDrawScope()
    val scratch = Path()
    scope.draw(
        density = density,
        layoutDirection = layoutDirection,
        canvas = gCanvas,
        size = Size(bw.toFloat(), bh.toFloat()),
    ) {
        withInkTransform(spec) {
            paths.forEach { path ->
                renderContext.ensureActive()
                if (path.toolType != ToolKind.MARKER) {
                    drawStrokeWithPressure(path, spec.pdfWidthPx, spec.pdfHeightPx, spec.extent, scratch)
                }
            }
        }
    }
    prewarmNativeImage(bmp)
    return bmp
}

/**
 * Rasterises completed marker strokes into an off-screen image. The bitmap is
 * filled with opaque white first and marker strokes are multiplied into it;
 * drawing that image back over the PDF with [BlendMode.Multiply] leaves white
 * pixels as no-ops while preserving marker darkening without replaying every
 * marker segment on every frame.
 */
internal suspend fun buildCompletedMarkerInk(
    spec: InkRenderSpec,
    paths: List<DrawingPath>,
    density: Density,
    layoutDirection: LayoutDirection,
): ImageBitmap {
    val renderContext = currentCoroutineContext()
    val bw = spec.surfaceSize.width
    val bh = spec.surfaceSize.height
    val bmp = ImageBitmap(bw, bh)
    val gCanvas = GraphicsCanvas(bmp)
    val scope = CanvasDrawScope()
    val scratch = Path()
    scope.draw(
        density = density,
        layoutDirection = layoutDirection,
        canvas = gCanvas,
        size = Size(bw.toFloat(), bh.toFloat()),
    ) {
        drawRect(Color.White)
        withInkTransform(spec) {
            paths.forEach { path ->
                renderContext.ensureActive()
                if (path.toolType == ToolKind.MARKER) {
                    drawMarkerStroke(
                        points = path.points,
                        colorArgb = path.colorArgb,
                        normalizedStrokeWidth = path.strokeWidth,
                        pdfWidth = spec.pdfWidthPx,
                        pdfHeight = spec.pdfHeightPx,
                        extent = spec.extent,
                        scratch = scratch,
                    )
                }
            }
        }
    }
    prewarmNativeImage(bmp)
    return bmp
}

internal fun DrawScope.drawCompletedMarkerInk(
    paths: List<DrawingPath>,
    cached: CompletedInk?,
    spec: InkRenderSpec,
    scratch: Path,
    nativeImageDrawCache: NativeImageDrawCache? = null,
) {
    cached?.let { completed ->
        drawCompletedInkBitmap(
            nativeImageDrawCache = nativeImageDrawCache,
            bitmap = completed.bitmap,
            dstSize = spec.surfaceSize,
            blendMode = BlendMode.Multiply,
        )
    }

    val cachedCount = cached?.strokeCount ?: 0
    val tailStart = completedInkTailStart(paths.size, cached?.strokeCount)
    if (paths.size <= cachedCount && cached != null) return
    if (!shouldDrawCompletedInkTail(paths, tailStart) { it.toolType == ToolKind.MARKER }) return
    withInkTransform(spec) {
        for (i in tailStart until paths.size) {
            val path = paths[i]
            if (path.toolType == ToolKind.MARKER) {
                drawMarkerStroke(
                    points = path.points,
                    colorArgb = path.colorArgb,
                    normalizedStrokeWidth = path.strokeWidth,
                    pdfWidth = spec.pdfWidthPx,
                    pdfHeight = spec.pdfHeightPx,
                    extent = spec.extent,
                    scratch = scratch,
                )
            }
        }
    }
}

internal fun DrawScope.drawCompletedPenInk(
    paths: List<DrawingPath>,
    cached: CompletedInk?,
    spec: InkRenderSpec,
    scratch: Path,
    vectorWhenUpscaled: Boolean,
    nativeImageDrawCache: NativeImageDrawCache? = null,
) {
    if (paths.isEmpty()) return
    val drawVectors = vectorWhenUpscaled && cached?.isUpscaledTo(spec.surfaceSize) == true
    if (drawVectors) {
        withInkTransform(spec) {
            for (i in paths.indices) {
                val path = paths[i]
                if (path.toolType != ToolKind.MARKER) {
                    drawStrokeWithPressure(path, spec.pdfWidthPx, spec.pdfHeightPx, spec.extent, scratch)
                }
            }
        }
        return
    }

    cached?.let { completed ->
        drawCompletedInkBitmap(
            nativeImageDrawCache = nativeImageDrawCache,
            bitmap = completed.bitmap,
            dstSize = spec.surfaceSize,
            blendMode = BlendMode.SrcOver,
        )
    }

    val cachedCount = cached?.strokeCount ?: 0
    val tailStart = completedInkTailStart(paths.size, cached?.strokeCount)
    if (paths.size <= cachedCount && cached != null) return
    if (!shouldDrawCompletedInkTail(paths, tailStart) { it.toolType != ToolKind.MARKER }) return
    withInkTransform(spec) {
        for (i in tailStart until paths.size) {
            val path = paths[i]
            if (path.toolType != ToolKind.MARKER) {
                drawStrokeWithPressure(path, spec.pdfWidthPx, spec.pdfHeightPx, spec.extent, scratch)
            }
        }
    }
}

private fun DrawScope.drawCompletedInkBitmap(
    nativeImageDrawCache: NativeImageDrawCache?,
    bitmap: ImageBitmap,
    dstSize: IntSize,
    blendMode: BlendMode,
) {
    if (nativeImageDrawCache != null) {
        drawNativeCachedImage(
            cache = nativeImageDrawCache,
            image = bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstOffset = IntOffset.Zero,
            dstSize = dstSize,
            blendMode = blendMode,
            filterQuality = FilterQuality.Medium,
        )
    } else {
        drawImage(
            image = bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstOffset = IntOffset.Zero,
            dstSize = dstSize,
            blendMode = blendMode,
        )
    }
}

private inline fun DrawScope.withInkTransform(
    spec: InkRenderSpec,
    block: DrawScope.() -> Unit,
) {
    val translation = spec.translation
    withTransform({
        translate(left = translation.x, top = translation.y)
    }) {
        block()
    }
}

/**
 * Renderer for the in-flight live stroke. Full-stroke renders use the same
 * smoothed variable-width centerline as [drawStrokeWithPressure] so handwriting
 * does not switch appearance at lift-off.
 */
fun DrawScope.drawLiveStroke(
    points: List<DrawingPoint>,
    colorArgb: Long,
    normalizedStrokeWidth: Float,
    pdfWidth: Float,
    pdfHeight: Float,
    extent: PageExtent,
    scratch: Path,
    fromSegmentIndex: Int = 0,
    toSegmentIndexExclusive: Int = points.size - 1,
) {
    if (points.isEmpty()) return
    val color = Color(colorArgb.toInt())
    val baseWidth = normalizedStrokeWidth * pdfWidth
    val offX = -extent.left
    val offY = -extent.top
    if (points.size == 1) {
        val point = points.first()
        drawStartDot(point, color, baseWidth, point.pressure, point.tilt, pdfWidth, pdfHeight, offX, offY)
        return
    }
    if (points.size < 2) return
    val segFrom = fromSegmentIndex.coerceAtLeast(0)
    val segTo = toSegmentIndexExclusive.coerceAtMost(points.size - 1)
    if (segFrom >= segTo) return

    drawVariableWidthPenStroke(
        points = points,
        color = color,
        baseWidth = baseWidth,
        pdfWidth = pdfWidth,
        pdfHeight = pdfHeight,
        offX = offX,
        offY = offY,
        scratch = scratch,
        fromSegmentIndex = segFrom,
        toSegmentIndexExclusive = segTo,
        drawStartDots = segFrom == 0,
    )
}

private fun DrawScope.drawStartDot(
    point: DrawingPoint,
    color: Color,
    baseWidth: Float,
    pressure: Float,
    tilt: Float,
    pdfWidth: Float,
    pdfHeight: Float,
    offX: Float,
    offY: Float,
) {
    val width = renderedPenStrokeWidth(baseWidth, pressure, tilt)
    drawCircle(
        color = color,
        radius = width * 0.5f,
        center = Offset((point.x + offX) * pdfWidth, (point.y + offY) * pdfHeight),
    )
}

private fun pressureWidthFactor(pressure: Float): Float {
    val curved = pressure.coerceIn(0f, 1f).pow(PRESSURE_GAMMA)
    return MIN_WIDTH_FACTOR + (1f - MIN_WIDTH_FACTOR) * curved
}

private class RenderSampleScratch(
    var x: Float = 0f,
    var y: Float = 0f,
    var pressure: Float = 0f,
    var tilt: Float = 0f,
)

internal fun renderedPenStrokeWidth(
    baseWidth: Float,
    pressure: Float,
    tilt: Float,
): Float = (baseWidth * pressureWidthFactor(pressure) * (1f + TILT_WIDTH_GAIN * tilt)).coerceAtLeast(MIN_RENDERED_STROKE_PX)

/**
 * Renders [stroke] with per-segment width modulated by [DrawingPoint.pressure]
 * and [DrawingPoint.tilt]. Used to bake completed strokes into the off-screen
 * cache bitmap.
 */
fun DrawScope.drawStrokeWithPressure(
    stroke: DrawingPath,
    pdfWidth: Float,
    pdfHeight: Float,
    extent: PageExtent,
    scratch: Path,
) {
    val points = stroke.points
    if (points.size < 2) return

    val color = Color(stroke.colorArgb.toInt())
    val baseWidth = stroke.strokeWidth * pdfWidth
    val offX = -extent.left
    val offY = -extent.top

    drawVariableWidthPenStroke(
        points = points,
        color = color,
        baseWidth = baseWidth,
        pdfWidth = pdfWidth,
        pdfHeight = pdfHeight,
        offX = offX,
        offY = offY,
        scratch = scratch,
    )
}

private fun DrawScope.drawVariableWidthPenStroke(
    points: List<DrawingPoint>,
    color: Color,
    baseWidth: Float,
    pdfWidth: Float,
    pdfHeight: Float,
    offX: Float,
    offY: Float,
    scratch: Path,
    fromSegmentIndex: Int = 0,
    toSegmentIndexExclusive: Int = points.size - 1,
    drawStartDots: Boolean = true,
) {
    val segFrom = fromSegmentIndex.coerceAtLeast(0)
    val segTo = toSegmentIndexExclusive.coerceAtMost(points.size - 1)
    if (segFrom >= segTo) return

    val start = RenderSampleScratch()
    val end = RenderSampleScratch()

    if (drawStartDots) {
        for (i in points.indices) {
            if ((i == 0 || points[i].isNewPath) && i >= segFrom && i <= segTo) {
                points.renderSampleAt(i, start)
                drawStartDot(points[i], color, baseWidth, start.pressure, start.tilt, pdfWidth, pdfHeight, offX, offY)
            }
        }
    }

    for (i in segFrom until segTo) {
        if (points[i + 1].isNewPath) continue
        scratch.reset()
        points.appendRenderSegmentTo(scratch, i, pdfWidth, pdfHeight, offX, offY, start, end)
        val pressure: Float
        val tilt: Float
        if (i == 0 || points[i].isNewPath) {
            pressure = start.pressure
            tilt = start.tilt
        } else if (i + 1 == points.lastIndex || points.getOrNull(i + 2)?.isNewPath == true) {
            pressure = end.pressure
            tilt = end.tilt
        } else {
            pressure = (start.pressure + end.pressure) * 0.5f
            tilt = (start.tilt + end.tilt) * 0.5f
        }
        drawPath(
            path = scratch,
            color = color,
            style =
                Stroke(
                    width = renderedPenStrokeWidth(baseWidth, pressure, tilt),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
        )
    }
}

private fun List<DrawingPoint>.renderSampleAt(
    index: Int,
    out: RenderSampleScratch,
) {
    val point = this[index]
    out.x = point.x
    out.y = point.y
    if (index == 0 || index == lastIndex || point.isNewPath) {
        out.pressure = point.pressure
        out.tilt = point.tilt
        return
    }
    val prev = this[index - 1]
    val next = this[index + 1]
    if (next.isNewPath) {
        out.pressure = point.pressure
        out.tilt = point.tilt
        return
    }
    out.pressure = prev.pressure * 0.25f + point.pressure * 0.5f + next.pressure * 0.25f
    out.tilt = prev.tilt * 0.25f + point.tilt * 0.5f + next.tilt * 0.25f
}

private fun List<DrawingPoint>.appendRenderSegmentTo(
    target: Path,
    startIndex: Int,
    pdfW: Float,
    pdfH: Float,
    offX: Float = 0f,
    offY: Float = 0f,
    start: RenderSampleScratch,
    end: RenderSampleScratch,
) {
    renderSampleAt(startIndex, start)
    renderSampleAt(startIndex + 1, end)
    val x1 = (start.x + offX) * pdfW
    val y1 = (start.y + offY) * pdfH
    val x2 = (end.x + offX) * pdfW
    val y2 = (end.y + offY) * pdfH
    target.moveTo(x1, y1)
    target.lineTo(x2, y2)
}
