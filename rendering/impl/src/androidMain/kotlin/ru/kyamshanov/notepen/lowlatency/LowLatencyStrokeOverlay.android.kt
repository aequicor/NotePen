package ru.kyamshanov.notepen.lowlatency

import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.graphics.lowlatency.CanvasFrontBufferedRenderer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.ToolKind
import ru.kyamshanov.notepen.drawing.api.LiveStrokeSample
import ru.kyamshanov.notepen.drawing.api.PdfDrawingState
import ru.kyamshanov.notepen.rendering.api.RenderingConstants
import ru.kyamshanov.notepen.rendering.api.computeSegmentWidth
import kotlin.math.cos
import kotlin.math.sin

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
    val lifecycleOwnerHolder = remember { mutableStateOf<LifecycleOwner?>(null) }
    var overlayMounted by remember { mutableStateOf(drawingState.isDrawing.value) }

    LaunchedEffect(drawingState) {
        snapshotFlow { drawingState.isDrawing.value }
            .collect { drawing ->
                if (drawing) {
                    overlayMounted = true
                } else {
                    delay(HANDOFF_FALLBACK_MS)
                    if (!drawingState.isDrawing.value) overlayMounted = false
                }
            }
    }

    if (!drawingState.isDrawing.value && !overlayMounted) return

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            lifecycleOwnerHolder.value = ctx.findLifecycleOwner()
            SurfaceView(ctx).apply {
                // Sit above the Compose-hosting window's surface so our
                // transparent areas show the Compose render underneath.
                setZOrderOnTop(true)
                holder.setFormat(PixelFormat.TRANSLUCENT)
                isClickable = false
                isFocusable = false
                alpha = HIDDEN_ALPHA
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
                sv.alpha = HIDDEN_ALPHA
                rendererHolder.value = null
                renderer.release(false)
            }
        }
    }

    DisposableEffect(surfaceViewHolder.value, lifecycleOwnerHolder.value) {
        val sv = surfaceViewHolder.value
        val lifecycleOwner = lifecycleOwnerHolder.value
        if (sv == null || lifecycleOwner == null) {
            onDispose { }
        } else {
            fun hideAndClearSurface() {
                sv.alpha = HIDDEN_ALPHA
                runCatching { rendererHolder.value?.clear() }
            }

            hideAndClearSurface()
            val observer =
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE,
                        Lifecycle.Event.ON_STOP,
                        -> {
                            hideAndClearSurface()
                            overlayMounted = false
                        }
                        Lifecycle.Event.ON_START,
                        Lifecycle.Event.ON_RESUME,
                        -> sv.post { hideAndClearSurface() }
                        else -> Unit
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                hideAndClearSurface()
            }
        }
    }

    // Feed accepted pen samples straight into the front buffer. This bypasses
    // snapshotFlow/recomposition batching, which otherwise adds at least one
    // frame of latency on Android tablets.
    DisposableEffect(drawingState, rendererHolder.value, surfaceViewHolder.value) {
        val renderer = rendererHolder.value
        val surfaceView = surfaceViewHolder.value
        if (renderer == null) {
            onDispose { }
        } else {
            if (drawingState.isDrawing.value && drawingState.livePoints.isNotEmpty()) {
                surfaceView?.alpha = VISIBLE_ALPHA
                drawingState.livePoints.forEachIndexed { index, point ->
                    val previous =
                        if (index > 0 && !point.isNewPath) {
                            drawingState.livePoints[index - 1]
                        } else {
                            null
                        }
                    renderer.renderFrontBufferedLayer(
                        LiveStrokeSample(
                            previous = previous,
                            current = point,
                            colorArgb = drawingState.liveColorArgb.value,
                            normalizedStrokeWidth = drawingState.liveStrokeWidth.value,
                            toolKind = drawingState.liveToolKind.value,
                            extent = drawingState.extent.value,
                        ).toStrokeSegment(surfaceView?.width ?: 0),
                    )
                }
            }
            val unregister =
                drawingState.addLiveStrokeListener { sample ->
                    surfaceView?.alpha = VISIBLE_ALPHA
                    renderer.renderFrontBufferedLayer(sample.toStrokeSegment(surfaceView?.width ?: 0))
                }
            onDispose { unregister() }
        }
    }

    // Lift-off handoff: commit the front-buffer samples, then clear after the
    // completed path is visible in Compose.
    LaunchedEffect(drawingState, rendererHolder.value) {
        var committedTargetPathCount: Int? = null
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
                pathCount = drawingState.currentPaths.size,
                completedStrokeCount = completedStrokeCountState.value,
            )
        }.collect { state ->
            val renderer = rendererHolder.value ?: return@collect
            val surfaceView = surfaceViewHolder.value
            if (!state.drawing) {
                val target =
                    state.pathCount.takeIf { it > 0 }
                        ?: run {
                            renderer.clear()
                            surfaceView?.alpha = HIDDEN_ALPHA
                            return@collect
                        }
                if (committedTargetPathCount != target) {
                    renderer.commit()
                    committedTargetPathCount = target
                }
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
                surfaceView?.alpha = HIDDEN_ALPHA
                return@collect
            }
            clearJob?.cancel()
            clearJob = null
            committedTargetPathCount = null
            handoffTargetPathCount = null
            surfaceView?.alpha = VISIBLE_ALPHA
        }
    }
}

private fun LiveStrokeSample.toStrokeSegment(slotWidthPx: Int): StrokeSegment {
    // liveStrokeWidth нормализован относительно ширины PDF, не слота.
    // pdfW = slotW / extent.width, поэтому widthPx = liveW * pdfW.
    val widthPx =
        if (extent.width > 0f) {
            normalizedStrokeWidth * slotWidthPx / extent.width
        } else {
            0f
        }
    return StrokeSegment(
        prev = previous,
        curr = current,
        colorArgb = colorArgb.toInt(),
        widthPx = widthPx,
        extent = extent,
        toolKind = toolKind,
    )
}

@Composable
actual fun rememberLowLatencyOverlayAvailable(): Boolean = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q }

@Composable
actual fun rememberLowLatencyOverlayMaxDimensionPx(): Int = remember { RenderingConstants.LOW_LATENCY_OVERLAY_MAX_DIM_PX }

private const val HIDDEN_ALPHA = 0f
private const val VISIBLE_ALPHA = 1f

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return current as? LifecycleOwner
}

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
    penPaint.strokeWidth = computeSegmentWidth(segment.widthPx, curr.pressure, curr.tilt)
    if (prev == null) {
        canvas.drawCircle(x, y, penPaint.strokeWidth * 0.5f, penPaint)
    } else {
        canvas.drawLine((prev.x + offX) * pdfW, (prev.y + offY) * pdfH, x, y, penPaint)
    }
}
