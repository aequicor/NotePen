package ru.kyamshanov.notepen.lowlatency

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.graphics.lowlatency.CanvasFrontBufferedRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import ru.kyamshanov.notepen.PdfDrawingState
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent

/**
 * Width modulation factor applied per tilt unit, mirroring the gain used in
 * the Compose-rendered live stroke so the visual width matches when the
 * overlay hands off to the cached bitmap on lift-off.
 */
private const val TILT_WIDTH_GAIN = 0.5f

/**
 * How long to keep the multi-buffered overlay visible after lift-off, giving
 * Compose enough time to recompose, rebuild the completed-strokes bitmap
 * cache and draw it underneath. ~3 frames at 60 Hz; chosen empirically as
 * the shortest interval that hides the handoff flash on mid-range tablets.
 */
private const val HANDOFF_HOLD_MS = 50L

/**
 * Stroke segment fed to `CanvasFrontBufferedRenderer`. One per appended
 * sample — the renderer keeps a queue of all samples and replays them into
 * the multi-buffered layer on commit; the front-buffered layer just draws
 * the new segment (prev → curr) for minimum latency.
 */
private data class StrokeSegment(
    val prev: DrawingPoint?,
    val curr: DrawingPoint,
    val colorArgb: Int,
    val widthPx: Float,
    val extent: PageExtent,
)

@Composable
actual fun LowLatencyStrokeOverlay(drawingState: PdfDrawingState, modifier: Modifier) {
    // CanvasFrontBufferedRenderer requires Android Q (API 29) — it relies on
    // HardwareBuffer + EGL extensions not available before. On older devices
    // we silently fall back to Compose's own live-stroke render.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

    val surfaceViewHolder = remember { mutableStateOf<SurfaceView?>(null) }
    val rendererHolder = remember { mutableStateOf<CanvasFrontBufferedRenderer<StrokeSegment>?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).apply {
                // Sit above the Compose-hosting window's surface so our
                // transparent areas show the Compose render underneath.
                setZOrderOnTop(true)
                holder.setFormat(PixelFormat.TRANSLUCENT)
                isClickable = false
                isFocusable = false
            }.also { surfaceViewHolder.value = it }
        },
        update = { /* no-op: state changes drive the renderer via LaunchedEffect */ },
    )

    // Bind the renderer to the SurfaceView once it is attached, release on dispose.
    DisposableEffect(surfaceViewHolder.value) {
        val sv = surfaceViewHolder.value
        if (sv == null) {
            onDispose { }
        } else {
            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val callback = object : CanvasFrontBufferedRenderer.Callback<StrokeSegment> {
                override fun onDrawFrontBufferedLayer(
                    canvas: Canvas,
                    bufferWidth: Int,
                    bufferHeight: Int,
                    param: StrokeSegment,
                ) {
                    drawSegment(canvas, bufferWidth, bufferHeight, param, paint)
                }

                override fun onDrawMultiBufferedLayer(
                    canvas: Canvas,
                    bufferWidth: Int,
                    bufferHeight: Int,
                    params: Collection<StrokeSegment>,
                ) {
                    for (segment in params) {
                        drawSegment(canvas, bufferWidth, bufferHeight, segment, paint)
                    }
                }
            }
            val renderer = CanvasFrontBufferedRenderer(sv, callback)
            rendererHolder.value = renderer
            onDispose {
                rendererHolder.value = null
                renderer.release(false)
            }
        }
    }

    // Drive the renderer from `drawingState`. We track `lastIndex` so we only
    // submit each new sample once.
    //
    // Lift-off handoff: when `isDrawing` flips to false we **commit** the
    // accumulated samples to the multi-buffered (back) layer instead of
    // calling `cancel()`. Cancel would hide the front buffer immediately,
    // leaving SurfaceView fully transparent before Compose has had a chance
    // to recompose and rebuild its `completedLayer` cache bitmap with the
    // just-finished stroke — for one or two frames the stroke would vanish
    // (the "flash" reported by users). Commit keeps the stroke visible via
    // the multi-buffered layer; we then wait `HANDOFF_HOLD_MS` (long enough
    // for Compose to redraw with the new cache) before calling `clear()` to
    // reveal Compose's render underneath. The brief overlap between the two
    // is visually identical because both render the same point set.
    LaunchedEffect(drawingState, rendererHolder.value) {
        var lastIndex = -1
        snapshotFlow {
            val drawing = drawingState.isDrawing.value
            val size = drawingState.livePoints.size
            Triple(drawing, size, drawingState.historyVersion.value)
        }.collect { (drawing, size, _) ->
            val renderer = rendererHolder.value ?: return@collect
            if (!drawing) {
                if (lastIndex >= 0) {
                    renderer.commit()
                    lastIndex = -1
                    // Let Compose recompose + redraw `completedLayer` with
                    // the new stroke before clearing the overlay.
                    delay(HANDOFF_HOLD_MS)
                    renderer.clear()
                }
                return@collect
            }
            val ext = drawingState.extent.value
            val slotW = surfaceViewHolder.value?.width ?: 0
            // liveStrokeWidth нормализован относительно ширины PDF, не слота.
            // pdfW = slotW / extent.width, поэтому widthPx = liveW * pdfW.
            val widthPx = if (ext.width > 0f) {
                drawingState.liveStrokeWidth.value * slotW / ext.width
            } else {
                0f
            }
            val colorArgb = drawingState.liveColorArgb.value.toInt()
            // Detect a new stroke that started while the collector was busy
            // (e.g. paused in `delay(HANDOFF_HOLD_MS)` after a previous commit,
            // or because snapshotFlow conflated the `isDrawing=false` edge).
            // `startDrawing()` calls `livePoints.clear()` so `size` drops back
            // to 1; without this reset, `lastIndex` would still point past the
            // new list end and the loop below would never run, leaving the
            // start of the new stroke unrendered — or, worse, a subsequent
            // append would render a segment between `livePoints[lastIndex]`
            // and the new point, producing a stray line across the page.
            if (lastIndex >= size) {
                lastIndex = -1
            }
            // Submit every new sample since the previous tick. snapshotFlow
            // coalesces updates per frame, so a burst of 4 samples emits once.
            while (lastIndex + 1 < size) {
                lastIndex++
                val curr = drawingState.livePoints[lastIndex]
                val prev = if (lastIndex > 0 && !curr.isNewPath) {
                    drawingState.livePoints[lastIndex - 1]
                } else {
                    null
                }
                renderer.renderFrontBufferedLayer(
                    StrokeSegment(
                        prev = prev,
                        curr = curr,
                        colorArgb = colorArgb,
                        widthPx = widthPx,
                        extent = ext,
                    ),
                )
            }
        }
    }
}

@Composable
actual fun rememberLowLatencyOverlayAvailable(): Boolean =
    remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q }

private fun drawSegment(
    canvas: Canvas,
    bufferWidth: Int,
    bufferHeight: Int,
    segment: StrokeSegment,
    paint: Paint,
) {
    paint.color = segment.colorArgb
    val tiltBoost = 1f + TILT_WIDTH_GAIN * segment.curr.tilt
    paint.strokeWidth = (segment.widthPx * segment.curr.pressure * tiltBoost).coerceAtLeast(1f)
    val prev = segment.prev
    val curr = segment.curr
    val ext = segment.extent
    val pdfW = if (ext.width > 0f) bufferWidth / ext.width else bufferWidth.toFloat()
    val pdfH = if (ext.height > 0f) bufferHeight / ext.height else bufferHeight.toFloat()
    val offX = -ext.left
    val offY = -ext.top
    val x = (curr.x + offX) * pdfW
    val y = (curr.y + offY) * pdfH
    if (prev == null) {
        // Single-sample "dot" at stroke start — draw a tiny line to itself so
        // the round cap renders a visible point.
        canvas.drawLine(x, y, x, y, paint)
    } else {
        canvas.drawLine((prev.x + offX) * pdfW, (prev.y + offY) * pdfH, x, y, paint)
    }
}
