package ru.kyamshanov.notepen.lowlatency

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.graphics.lowlatency.CanvasFrontBufferedRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.ToolKind
import ru.kyamshanov.notepen.drawing.api.PdfDrawingState
import kotlin.math.cos
import kotlin.math.sin

/**
 * Width modulation factor applied per tilt unit, mirroring the gain used in
 * the Compose-rendered live stroke so the visual width matches when the
 * overlay hands off to the cached bitmap on lift-off.
 */
private const val TILT_WIDTH_GAIN = 0.5f

private const val HANDOFF_FALLBACK_MS = 250L
private const val HANDOFF_FRAME_DELAY = 2

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
    val toolKind: ToolKind,
)

private data class OverlayState(
    val drawing: Boolean,
    val livePointCount: Int,
    val pathCount: Int,
    val completedStrokeCount: Int,
)

@Composable
actual fun LowLatencyStrokeOverlay(
    drawingState: PdfDrawingState,
    modifier: Modifier,
    viewportScale: Float,
    windowBounds: Rect?,
    completedStrokeCount: Int,
) {
    // CanvasFrontBufferedRenderer requires Android Q (API 29) — it relies on
    // HardwareBuffer + EGL extensions not available before. On older devices
    // we silently fall back to Compose's own live-stroke render.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

    val surfaceViewHolder = remember { mutableStateOf<SurfaceView?>(null) }
    val rendererHolder = remember { mutableStateOf<CanvasFrontBufferedRenderer<StrokeSegment>?>(null) }
    val completedStrokeCountState = rememberUpdatedState(completedStrokeCount)

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
            val penPaint =
                Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
            // Marker chisel ribbon: filled quads composited with Multiply so the
            // semi-transparent ink darkens content underneath and self-overlap
            // does not compound — mirroring `drawMarkerStroke`. `setBlendMode`
            // (and `BlendMode.MULTIPLY`) require API 29, guaranteed by the SDK
            // gate above. The path is reused across segments to avoid per-frame
            // allocation on the render thread.
            val markerPaint =
                Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.FILL
                    blendMode = BlendMode.MULTIPLY
                }
            val markerPath = Path()
            val callback =
                object : CanvasFrontBufferedRenderer.Callback<StrokeSegment> {
                    override fun onDrawFrontBufferedLayer(
                        canvas: Canvas,
                        bufferWidth: Int,
                        bufferHeight: Int,
                        param: StrokeSegment,
                    ) {
                        drawSegment(canvas, bufferWidth, bufferHeight, param, penPaint, markerPaint, markerPath)
                    }

                    override fun onDrawMultiBufferedLayer(
                        canvas: Canvas,
                        bufferWidth: Int,
                        bufferHeight: Int,
                        params: Collection<StrokeSegment>,
                    ) {
                        for (segment in params) {
                            drawSegment(canvas, bufferWidth, bufferHeight, segment, penPaint, markerPaint, markerPath)
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
    // Lift-off handoff: commit the front-buffer samples, then clear immediately.
    // The committed path is already in `currentPaths`, so Compose renders it.
    LaunchedEffect(drawingState, rendererHolder.value) {
        var lastIndex = -1
        var handoffTargetPathCount: Int? = null
        var clearJob: kotlinx.coroutines.Job? = null

        suspend fun clearAfterMainCanvasFrames(renderer: CanvasFrontBufferedRenderer<StrokeSegment>) {
            repeat(HANDOFF_FRAME_DELAY) {
                withFrameNanos { }
            }
            if (!drawingState.isDrawing.value) renderer.clear()
        }

        snapshotFlow {
            OverlayState(
                drawing = drawingState.isDrawing.value,
                livePointCount = drawingState.livePoints.size,
                pathCount = drawingState.currentPaths.size,
                completedStrokeCount = completedStrokeCountState.value,
            )
        }.collect { state ->
            val renderer = rendererHolder.value ?: return@collect
            if (!state.drawing) {
                if (lastIndex >= 0) {
                    renderer.commit()
                    lastIndex = -1
                }
                val target = state.pathCount.takeIf { it > 0 } ?: return@collect
                if (handoffTargetPathCount != target) {
                    handoffTargetPathCount = target
                    clearJob?.cancel()
                    clearJob =
                        launch {
                            delay(HANDOFF_FALLBACK_MS)
                            if (!drawingState.isDrawing.value && handoffTargetPathCount == target) {
                                renderer.clear()
                                handoffTargetPathCount = null
                            }
                        }
                }
                if (state.completedStrokeCount >= target || handoffTargetPathCount == target) {
                    clearJob?.cancel()
                    clearJob = null
                    clearAfterMainCanvasFrames(renderer)
                    if (handoffTargetPathCount == target) handoffTargetPathCount = null
                }
                return@collect
            }
            clearJob?.cancel()
            clearJob = null
            handoffTargetPathCount = null
            val ext = drawingState.extent.value
            val slotW = surfaceViewHolder.value?.width ?: 0
            // liveStrokeWidth нормализован относительно ширины PDF, не слота.
            // pdfW = slotW / extent.width, поэтому widthPx = liveW * pdfW.
            val widthPx =
                if (ext.width > 0f) {
                    drawingState.liveStrokeWidth.value * slotW / ext.width
                } else {
                    0f
                }
            val colorArgb = drawingState.liveColorArgb.value.toInt()
            val toolKind = drawingState.liveToolKind.value
            // Detect a new stroke that started while the collector was busy
            // (e.g. because snapshotFlow conflated the `isDrawing=false` edge).
            // `startDrawing()` calls `livePoints.clear()` so `size` drops back
            // to 1; without this reset, `lastIndex` would still point past the
            // new list end and the loop below would never run, leaving the
            // start of the new stroke unrendered — or, worse, a subsequent
            // append would render a segment between `livePoints[lastIndex]`
            // and the new point, producing a stray line across the page.
            if (lastIndex >= state.livePointCount) {
                lastIndex = -1
            }
            // Submit every new sample since the previous tick. snapshotFlow
            // coalesces updates per frame, so a burst of 4 samples emits once.
            while (lastIndex + 1 < state.livePointCount) {
                lastIndex++
                val curr = drawingState.livePoints[lastIndex]
                val prev =
                    if (lastIndex > 0 && !curr.isNewPath) {
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
                        toolKind = toolKind,
                    ),
                )
            }
        }
    }
}

@Composable
actual fun rememberLowLatencyOverlayAvailable(): Boolean = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q }

@Composable
actual fun rememberLowLatencyOverlayMaxDimensionPx(): Int = remember { 2400 }


/** Angle of the marker's chisel nib, in radians (~45°); mirrors `drawMarkerStroke`. */
private const val MARKER_NIB_ANGLE_RADIANS = 0.7853982f

private fun drawSegment(
    canvas: Canvas,
    bufferWidth: Int,
    bufferHeight: Int,
    segment: StrokeSegment,
    penPaint: Paint,
    markerPaint: Paint,
    markerPath: Path,
) {
    val prev = segment.prev
    val curr = segment.curr
    val ext = segment.extent
    val pdfW = if (ext.width > 0f) bufferWidth / ext.width else bufferWidth.toFloat()
    val pdfH = if (ext.height > 0f) bufferHeight / ext.height else bufferHeight.toFloat()
    val offX = -ext.left
    val offY = -ext.top
    val x = (curr.x + offX) * pdfW
    val y = (curr.y + offY) * pdfH

    if (segment.toolKind == ToolKind.MARKER) {
        markerPaint.color = segment.colorArgb
        // Constant nib breadth, independent of pressure/tilt — like the renderer.
        val halfWidthPx = segment.widthPx * 0.5f
        if (halfWidthPx <= 0f) return
        if (prev == null) {
            canvas.drawOval(
                x - halfWidthPx,
                y - halfWidthPx,
                x + halfWidthPx,
                y + halfWidthPx,
                markerPaint,
            )
            return
        }
        val nibX = cos(MARKER_NIB_ANGLE_RADIANS) * halfWidthPx
        val nibY = sin(MARKER_NIB_ANGLE_RADIANS) * halfWidthPx
        val x1 = (prev.x + offX) * pdfW
        val y1 = (prev.y + offY) * pdfH
        markerPath.reset()
        markerPath.moveTo(x1 + nibX, y1 + nibY)
        markerPath.lineTo(x1 - nibX, y1 - nibY)
        markerPath.lineTo(x - nibX, y - nibY)
        markerPath.lineTo(x + nibX, y + nibY)
        markerPath.close()
        canvas.drawPath(markerPath, markerPaint)
        return
    }

    penPaint.color = segment.colorArgb
    val tiltBoost = 1f + TILT_WIDTH_GAIN * curr.tilt
    penPaint.strokeWidth = (segment.widthPx * curr.pressure * tiltBoost).coerceAtLeast(1f)
    if (prev == null) {
        canvas.drawCircle(x, y, penPaint.strokeWidth * 0.5f, penPaint)
    } else {
        canvas.drawLine((prev.x + offX) * pdfW, (prev.y + offY) * pdfH, x, y, penPaint)
    }
}
