package ru.kyamshanov.notepen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.lowlatency.LowLatencyStrokeOverlay
import ru.kyamshanov.notepen.lowlatency.rememberLowLatencyOverlayAvailable
import ru.kyamshanov.notepen.tablet.LocalTabletInputController
import ru.kyamshanov.notepen.tablet.TabletInputController
import ru.kyamshanov.notepen.tablet.effectivePressure
import ru.kyamshanov.notepen.tablet.stylusEventSink

/** Прозрачность заливки индикатора зоны ластика (AC-12, UI / UX § «Индикатор ластика»). */
private const val ERASER_INDICATOR_FILL_ALPHA = 0.35f

/** Толщина обводки индикатора зоны ластика, в пикселях canvas. */
private const val ERASER_INDICATOR_STROKE_WIDTH_PX = 2f

/** Прозрачность контура hover-индикатора кончика пера. */
private const val HOVER_INDICATOR_ALPHA = 0.5f

/** Минимальный радиус hover-индикатора в пикселях канваса. */
private const val HOVER_INDICATOR_MIN_RADIUS_PX = 6f

/** Множитель «расширения» штриха при сильном наклоне пера (0..1 → ×(1..1+gain)). */
private const val TILT_WIDTH_GAIN = 0.5f

/**
 * Hard ceiling on either dimension of the off-screen ink-cache bitmap.
 *
 * The completed-strokes layer is rasterised at `canvasSize`; at 4–8× zoom the
 * page Box can grow past 5000 px, which on Android trips
 * `RecordingCanvas.throwIfCannotDraw` (≈ 100 MB per bitmap). Strokes are
 * normalised, so we rasterise at a capped resolution and let
 * `drawImage(dstSize = canvas)` upscale — visually indistinguishable from a
 * native-resolution cache once the user is already looking at a stretched
 * PDF page bitmap underneath.
 */
private const val INK_CACHE_MAX_DIMENSION_PX = 3072

/**
 * Bucket size for the ink-cache key. Without bucketing, every pixel of
 * `canvasSize` change during a continuous pinch zoom invalidates the cache
 * key and re-rasterises ALL strokes into a freshly allocated big bitmap —
 * at 60 fps this dominates the main thread and trashes the GC.
 *
 * Strokes are normalised; the rasterised bitmap is upscaled by `drawImage`
 * at draw time, so quantising the cache size by ±256 px is visually
 * indistinguishable but cuts rebuild frequency by ~100× during pinch.
 */
private const val INK_CACHE_DIM_BUCKET_PX = 256

/**
 * Hard ceiling on either dimension of the page Box at which the Android
 * low-latency `SurfaceView` overlay is allowed to mount. Above this, the
 * `CanvasFrontBufferedRenderer` would allocate a HardwareBuffer the full
 * size of the page (~ width × height × 4 bytes) for front + multi-buffered
 * layers PER visible page — at 6× zoom on a typical A4 (≈ 2700×3800 px)
 * this is ~40 MB × ~3 buffers × ~3 visible pages = > 350 MB of graphics
 * memory, which trips `dequeueBuffer: createGraphicBuffer failed` on most
 * mid-range devices.
 *
 * Beyond this threshold we drop the overlay; Compose's own `Canvas`-based
 * live-stroke renderer takes over (see `if (!lowLatencyOverlayActive)`
 * below) at frame-bound latency (~ 16 ms) instead of sub-frame (~ 3 ms) —
 * acceptable degradation only at extreme zoom, where the user is rarely
 * drawing.
 */
private const val LOW_LATENCY_OVERLAY_MAX_DIM_PX = 2400

@Composable
fun DrawablePdfPage(
    bitmap: ImageBitmap,
    pdfDrawingState: PdfDrawingState,
    toolMode: ToolMode,
    penSettings: PenSettings,
    markerSettings: MarkerSettings,
    eraserSettings: EraserSettings,
    onGestureStart: (snapshot: List<DrawingPath>) -> Unit = {},
    onStrokeFinished: (path: DrawingPath) -> Unit = {},
    onEraseFinished: (before: List<DrawingPath>, after: List<DrawingPath>) -> Unit = { _, _ -> },
    /**
     * Read at gesture start: when `true`, the gesture is routed to the erase
     * pipeline even though the user-selected [toolMode] is PEN or MARKER.
     * Lets the stylus eraser tip / barrel button trigger erase without
     * relying on Compose recomposition to swap [toolMode] (which would
     * restart `pointerInput` and lose the in-flight DOWN event).
     */
    eraserOverride: () -> Boolean = { false },
    /**
     * When `true`, palm-rejection is forced active regardless of whether a
     * stylus has been seen yet — finger gestures are not consumed and fall
     * through to the parent viewer (single-finger pan, two-finger pinch).
     * Only stylus / eraser-tip events draw. Surfaced on Android via the
     * "Режим стилуса" toolbar toggle (see [PencilModeSupport]).
     */
    pencilModeEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val canvasSize = remember { mutableStateOf(IntSize.Zero) }
    val tablet = LocalTabletInputController.current

    // Read pencilModeEnabled через rememberUpdatedState, чтобы лямбда
    // isPalmRejectionActive внутри pointerInput видела актуальное значение
    // без пересоздания самого pointerInput. Если включить флаг в ключи
    // pointerInput, авто-on по первому stylus-событию (LaunchedEffect в
    // DetailsContent) рекомпонует страницу и отменит активный жест:
    // EraseGesture.cancel() не отправляет onEraseFinished → стёртые
    // штрихи остаются на ПК, синхронизация ластика «теряет» дельты.
    val pencilModeState = rememberUpdatedState(pencilModeEnabled)

    // Becomes `true` after the first stylus / eraser-tip event is seen in this
    // composition. Once set, finger touches stop drawing (palm rejection) and
    // are passed through to parent gestures (pan / zoom). Stays `false` on
    // devices without a stylus so the app remains usable with fingers only.
    val stylusSeen = remember { mutableStateOf(false) }

    // Позиция пальца ластика в нормализованных координатах [0..1] относительно canvas.
    // null → жест ластика не активен (палец не на экране) → индикатор не отрисовывается.
    val eraserPos = remember { mutableStateOf<Offset?>(null) }

    val hoverPos by tablet.hoverPosition.collectAsState()

    // EC-1 / EC-2: при смене инструмента финализируем незавершённый штрих и
    // сбрасываем активную сессию стирания.
    LaunchedEffect(toolMode) {
        if (toolMode != ToolMode.PEN && toolMode != ToolMode.MARKER && pdfDrawingState.isDrawing.value) {
            pdfDrawingState.finishDrawing()
        }
        if (toolMode != ToolMode.ERASER) {
            eraserPos.value = null
        }
    }

    val indicatorColor = MaterialTheme.colorScheme.outline
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val lowLatencyOverlaySupported = rememberLowLatencyOverlayAvailable()
    // derivedStateOf — критически: иначе ЛЮБОЕ изменение canvasSize
    // (каждый pinch-тик) триггерит рекомпозицию всего DrawablePdfPage,
    // даже если порог не пересечён. С derivedStateOf рекомпозиция только
    // когда сам Boolean меняет значение (пересечение 2400px).
    val lowLatencyOverlayActive by remember(lowLatencyOverlaySupported) {
        derivedStateOf {
            lowLatencyOverlaySupported &&
                maxOf(canvasSize.value.width, canvasSize.value.height) <= LOW_LATENCY_OVERLAY_MAX_DIM_PX
        }
    }

    // Bucketed cache dimensions: drops the lowest bits of canvasSize so
    // sub-bucket zoom changes don't invalidate the ink cache. Above the
    // INK_CACHE_MAX_DIMENSION_PX cap the bucket is irrelevant (cap wins),
    // which means at high zoom the key naturally stays constant.
    // derivedStateOf — чтобы рекомпозиция триггерилась только при
    // пересечении границы бакета, а не на каждый пиксельный delta.
    val inkCacheDim: IntSize by remember {
        derivedStateOf {
            val (w, h) = canvasSize.value
            if (w <= 0 || h <= 0) {
                IntSize.Zero
            } else {
                val longest = maxOf(w, h)
                if (longest > INK_CACHE_MAX_DIMENSION_PX) {
                    val scaleNum = INK_CACHE_MAX_DIMENSION_PX
                    IntSize(
                        width = (w.toLong() * scaleNum / longest).toInt().coerceAtLeast(1),
                        height = (h.toLong() * scaleNum / longest).toInt().coerceAtLeast(1),
                    )
                } else {
                    val b = INK_CACHE_DIM_BUCKET_PX
                    IntSize(
                        width = ((w + b - 1) / b * b).coerceAtLeast(b),
                        height = ((h + b - 1) / b * b).coerceAtLeast(b),
                    )
                }
            }
        }
    }

    // Cache of all completed strokes rasterised to an off-screen ImageBitmap.
    // Rebuilt only when bucketed cache dim changes or `historyVersion` is
    // bumped (finishDrawing, undo / redo, eraser, sync). Without this cache,
    // every frame iterated `currentPaths.forEach { drawStrokeWithPressure }` —
    // for varying-pressure strokes this is hundreds of `drawPath` calls per
    // frame per stroke on screen, which dominates the input-to-pixel latency
    // budget once the page has any ink on it.
    val completedLayer: ImageBitmap? = remember(
        inkCacheDim,
        pdfDrawingState.historyVersion.value,
    ) {
        val bw = inkCacheDim.width
        val bh = inkCacheDim.height
        if (bw <= 0 || bh <= 0 || pdfDrawingState.currentPaths.isEmpty()) return@remember null
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
            pdfDrawingState.currentPaths.forEach { path ->
                drawStrokeWithPressure(path, bw.toFloat(), bh.toFloat(), scratch)
            }
        }
        bmp
    }

    Box(
        modifier = modifier
            .onSizeChanged { canvasSize.value = it }
            .stylusEventSink(tablet)
            .then(
                when (toolMode) {
                    ToolMode.PEN -> Modifier.pointerInput(toolMode, penSettings, eraserSettings) {
                        // Per-gesture eraser-override session: non-null while
                        // the current stylus gesture started with the eraser
                        // tip / barrel button down. Routes onMove/onUp to the
                        // erase pipeline so the user doesn't have to switch
                        // tools manually.
                        var activeErase: EraseGesture? = null
                        detectStylusAwareDrag(
                            tablet = tablet,
                            isPalmRejectionActive = { pencilModeState.value || stylusSeen.value },
                            onStylusSeen = { stylusSeen.value = true },
                            onDown = { off, pressure, tilt ->
                                val (w, h) = canvasSize.value
                                if (w > 0 && h > 0) {
                                    val nx = off.x / w
                                    val ny = off.y / h
                                    if (eraserOverride()) {
                                        activeErase = EraseGesture(
                                            pdfDrawingState = pdfDrawingState,
                                            eraserSettings = eraserSettings,
                                            eraserPos = eraserPos,
                                            onGestureStart = onGestureStart,
                                            onEraseFinished = onEraseFinished,
                                        ).also { it.start(nx, ny) }
                                    } else {
                                        onGestureStart(pdfDrawingState.currentPaths.toList())
                                        pdfDrawingState.strokeColorArgb.value = penSettings.colorArgb
                                        pdfDrawingState.strokeWidth.value = penSettings.strokeWidth
                                        pdfDrawingState.startDrawing(
                                            x = nx,
                                            y = ny,
                                            normalizedStrokeWidth = penSettings.strokeWidth / w,
                                            pressure = pressure,
                                            tilt = tilt,
                                        )
                                    }
                                }
                            },
                            onMove = { off, pressure, tilt ->
                                val (w, h) = canvasSize.value
                                if (w > 0 && h > 0) {
                                    val nx = off.x / w
                                    val ny = off.y / h
                                    val erase = activeErase
                                    if (erase != null) {
                                        erase.move(nx, ny)
                                    } else {
                                        pdfDrawingState.addPoint(
                                            x = nx,
                                            y = ny,
                                            pressure = pressure,
                                            tilt = tilt,
                                        )
                                    }
                                }
                            },
                            onUp = {
                                val erase = activeErase
                                if (erase != null) {
                                    erase.end()
                                    activeErase = null
                                } else {
                                    val completed = pdfDrawingState.finishDrawing()
                                    if (completed != null) onStrokeFinished(completed)
                                }
                            },
                            onCancel = {
                                val erase = activeErase
                                if (erase != null) {
                                    erase.cancel()
                                    activeErase = null
                                } else {
                                    pdfDrawingState.finishDrawing()
                                }
                            },
                        )
                    }

                    ToolMode.MARKER -> Modifier.pointerInput(toolMode, markerSettings, eraserSettings) {
                        var activeErase: EraseGesture? = null
                        detectStylusAwareDrag(
                            tablet = tablet,
                            isPalmRejectionActive = { pencilModeState.value || stylusSeen.value },
                            onStylusSeen = { stylusSeen.value = true },
                            onDown = { off, _, _ ->
                                val (w, h) = canvasSize.value
                                if (w > 0 && h > 0) {
                                    val nx = off.x / w
                                    val ny = off.y / h
                                    if (eraserOverride()) {
                                        activeErase = EraseGesture(
                                            pdfDrawingState = pdfDrawingState,
                                            eraserSettings = eraserSettings,
                                            eraserPos = eraserPos,
                                            onGestureStart = onGestureStart,
                                            onEraseFinished = onEraseFinished,
                                        ).also { it.start(nx, ny) }
                                    } else {
                                        onGestureStart(pdfDrawingState.currentPaths.toList())
                                        pdfDrawingState.strokeColorArgb.value = markerSettings.colorArgb
                                        pdfDrawingState.strokeWidth.value = markerSettings.strokeWidth
                                        pdfDrawingState.startDrawing(
                                            x = nx,
                                            y = ny,
                                            normalizedStrokeWidth = markerSettings.strokeWidth / w,
                                        )
                                    }
                                }
                            },
                            onMove = { off, _, _ ->
                                val (w, h) = canvasSize.value
                                if (w > 0 && h > 0) {
                                    val nx = off.x / w
                                    val ny = off.y / h
                                    val erase = activeErase
                                    if (erase != null) erase.move(nx, ny)
                                    else pdfDrawingState.addPoint(nx, ny)
                                }
                            },
                            onUp = {
                                val erase = activeErase
                                if (erase != null) {
                                    erase.end()
                                    activeErase = null
                                } else {
                                    val completed = pdfDrawingState.finishDrawing()
                                    if (completed != null) onStrokeFinished(completed)
                                }
                            },
                            onCancel = {
                                val erase = activeErase
                                if (erase != null) {
                                    erase.cancel()
                                    activeErase = null
                                } else {
                                    pdfDrawingState.finishDrawing()
                                }
                            },
                        )
                    }

                    ToolMode.ERASER -> Modifier.pointerInput(toolMode, eraserSettings) {
                        var session: EraseGesture? = null
                        detectStylusAwareDrag(
                            tablet = tablet,
                            isPalmRejectionActive = { pencilModeState.value || stylusSeen.value },
                            onStylusSeen = { stylusSeen.value = true },
                            onDown = { off, _, _ ->
                                val (w, h) = canvasSize.value
                                if (w > 0 && h > 0) {
                                    session = EraseGesture(
                                        pdfDrawingState = pdfDrawingState,
                                        eraserSettings = eraserSettings,
                                        eraserPos = eraserPos,
                                        onGestureStart = onGestureStart,
                                        onEraseFinished = onEraseFinished,
                                    ).also { it.start(off.x / w, off.y / h) }
                                }
                            },
                            onMove = { off, _, _ ->
                                val (w, h) = canvasSize.value
                                if (w > 0 && h > 0) session?.move(off.x / w, off.y / h)
                            },
                            onUp = {
                                session?.end()
                                session = null
                            },
                            onCancel = {
                                session?.cancel()
                                session = null
                            },
                        )
                    }

                    ToolMode.NONE -> Modifier
                }
            )
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "PDF Page",
            modifier = Modifier.fillMaxSize(),
            // FillBounds (не Fit) — Box и bitmap могут иметь aspect-ratio,
            // отличающиеся на доли пикселя из-за rounding при вычислении
            // визуальной высоты / target render-resolution. С Fit это даёт
            // letterbox-полосу сверху или снизу страницы — выглядит как
            // зазор между страницами при continuous-зуме. FillBounds
            // растягивает битмап точно по Box (sub-pixel искажение
            // незаметно), стыки идеально совпадают.
            contentScale = ContentScale.FillBounds,
        )

        // Scratch Path reused across frames for the live stroke. The cached
        // `completedLayer` already holds all finished strokes; we only need a
        // single Path per frame for the in-flight stroke.
        val livePath = remember { Path() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Completed strokes — bitmap blit, single draw call regardless of
            // how much ink is on the page. Cache may be rasterised at a lower
            // resolution than the canvas (see INK_CACHE_MAX_DIMENSION_PX), so
            // stretch via dstSize.
            completedLayer?.let { cache ->
                drawImage(
                    image = cache,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(cache.width, cache.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )
            }

            // Live stroke. Rendered with a single drawPath at uniform width
            // (average pressure of the existing samples × base) for minimum
            // per-frame cost. On `finishDrawing` the stroke is re-rasterised
            // into `completedLayer` with full per-segment varying-width fidelity
            // (`drawStrokeWithPressure`), so the user sees the higher-quality
            // render exactly when they lift the pen.
            //
            // Skipped when a low-latency overlay is in charge of the live
            // stroke on this platform (Android API 29+) — otherwise the
            // stroke would be drawn twice (once with frame-bound latency,
            // once with sub-frame latency).
            if (!lowLatencyOverlayActive &&
                pdfDrawingState.isDrawing.value &&
                pdfDrawingState.livePoints.size > 1
            ) {
                drawLiveStroke(
                    points = pdfDrawingState.livePoints,
                    colorArgb = pdfDrawingState.liveColorArgb.value,
                    normalizedStrokeWidth = pdfDrawingState.liveStrokeWidth.value,
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    scratch = livePath,
                )
            }

            // Индикатор зоны ластика (AC-12).
            val pos = eraserPos.value
            if (toolMode == ToolMode.ERASER && pos != null) {
                val cx = pos.x * size.width
                val cy = pos.y * size.height
                val sizePx = eraserSettings.sizeNormalized * size.width
                val halfPx = sizePx / 2f
                when (eraserSettings.shape) {
                    EraserShape.CIRCLE -> {
                        drawCircle(
                            color = indicatorColor.copy(alpha = ERASER_INDICATOR_FILL_ALPHA),
                            radius = halfPx,
                            center = Offset(cx, cy),
                        )
                        drawCircle(
                            color = indicatorColor,
                            radius = halfPx,
                            center = Offset(cx, cy),
                            style = Stroke(width = ERASER_INDICATOR_STROKE_WIDTH_PX),
                        )
                    }
                    EraserShape.SQUARE -> {
                        val topLeft = Offset(cx - halfPx, cy - halfPx)
                        val rectSize = Size(sizePx, sizePx)
                        drawRect(
                            color = indicatorColor.copy(alpha = ERASER_INDICATOR_FILL_ALPHA),
                            topLeft = topLeft,
                            size = rectSize,
                        )
                        drawRect(
                            color = indicatorColor,
                            topLeft = topLeft,
                            size = rectSize,
                            style = Stroke(width = ERASER_INDICATOR_STROKE_WIDTH_PX),
                        )
                    }
                }
            }

            // Hover-индикатор кончика пера. Показывается только пока перо не касается
            // экрана, только для рисующих инструментов.
            val hover = hoverPos
            if (hover != null && (toolMode == ToolMode.PEN || toolMode == ToolMode.MARKER)) {
                val cx = hover.x * size.width
                val cy = hover.y * size.height
                val toolStrokePx = when (toolMode) {
                    ToolMode.PEN -> penSettings.strokeWidth
                    ToolMode.MARKER -> markerSettings.strokeWidth
                    else -> 0f
                } * size.width
                val radiusPx = kotlin.math.max(HOVER_INDICATOR_MIN_RADIUS_PX, toolStrokePx * 0.5f)
                drawCircle(
                    color = indicatorColor.copy(alpha = HOVER_INDICATOR_ALPHA),
                    radius = radiusPx,
                    center = Offset(cx, cy),
                    style = Stroke(width = ERASER_INDICATOR_STROKE_WIDTH_PX),
                )
            }
        }

        // Low-latency front-buffered overlay for the live stroke on platforms
        // that support it (Android API 29+). Sits above the Compose Canvas; on
        // platforms where it is a no-op, the Compose Canvas above still
        // renders the live stroke itself (`drawLiveStroke` above).
        if (lowLatencyOverlayActive) {
            LowLatencyStrokeOverlay(
                drawingState = pdfDrawingState,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Stylus-aware drag gesture used by all three drawing tools. See KDoc on
 * earlier revisions for the full rationale; key invariants:
 *  - no touch-slop wait (fixes the "spiral defect" — initial fast curves
 *    used to be silently swallowed for ~24dp);
 *  - drains `PointerInputChange.historical` for sub-frame stylus samples;
 *  - palm rejection once a stylus event has been seen;
 *  - cancel-safe via try / finally.
 */
private suspend fun PointerInputScope.detectStylusAwareDrag(
    tablet: TabletInputController,
    isPalmRejectionActive: () -> Boolean,
    onStylusSeen: () -> Unit,
    onDown: (position: Offset, pressure: Float, tilt: Float) -> Unit,
    onMove: (position: Offset, pressure: Float, tilt: Float) -> Unit,
    onUp: () -> Unit,
    onCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downIsStylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
        if (downIsStylus) onStylusSeen()
        if (!downIsStylus && isPalmRejectionActive()) {
            return@awaitEachGesture
        }

        onDown(
            down.position,
            down.effectivePressure(tablet.latestPressure.value),
            tablet.tilt.value,
        )
        down.consume()
        var ended = false
        try {
            while (!ended) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                if (!change.pressed) {
                    onUp()
                    change.consume()
                    ended = true
                    break
                }
                val pressureNow = change.effectivePressure(tablet.latestPressure.value)
                val tiltNow = tablet.tilt.value
                for (h in change.historical) {
                    onMove(h.position, pressureNow, tiltNow)
                }
                onMove(change.position, pressureNow, tiltNow)
                change.consume()
            }
        } finally {
            if (!ended) onCancel()
        }
    }
}

/**
 * Renderer for the in-flight live stroke. Mirrors the per-segment width
 * modulation used by [drawStrokeWithPressure] when baking finished strokes —
 * without this, varying pressure during the gesture would repaint the *whole*
 * stroke at a single averaged width every frame, so pressing harder made the
 * already-drawn part visually thicken until lift-off.
 *
 * Fast path: when every point shares the same pressure and tilt (mouse /
 * marker without pressure data) the stroke is painted as a single
 * Catmull-Rom-smoothed path — identical cost to the previous renderer.
 *
 * [points] is the receiver's `livePoints` list and is guaranteed to contain
 * a single sub-path (only the first point has `isNewPath = true`), so no
 * sub-path splitting is needed here.
 */
private fun DrawScope.drawLiveStroke(
    points: List<DrawingPoint>,
    colorArgb: Long,
    normalizedStrokeWidth: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    scratch: Path,
) {
    if (points.size < 2) return

    val color = Color(colorArgb.toInt())
    val baseWidth = normalizedStrokeWidth * canvasWidth

    val uniformPressure = points.first().pressure
    val uniformTilt = points.first().tilt
    var pressureVaries = false
    var tiltVaries = false
    for (p in points) {
        if (!pressureVaries && p.pressure != uniformPressure) pressureVaries = true
        if (!tiltVaries && p.tilt != uniformTilt) tiltVaries = true
        if (pressureVaries && tiltVaries) break
    }

    if (!pressureVaries && !tiltVaries) {
        scratch.reset()
        points.appendCatmullRomTo(scratch, canvasWidth, canvasHeight)
        drawPath(
            path = scratch,
            color = color,
            style = Stroke(
                width = baseWidth * uniformPressure * (1f + TILT_WIDTH_GAIN * uniformTilt),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        return
    }

    for (i in 0 until points.size - 1) {
        val p0 = if (i > 0) points[i - 1] else points[0]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = if (i + 2 < points.size) points[i + 2] else points[i + 1]

        val x1 = p1.x * canvasWidth; val y1 = p1.y * canvasHeight
        val x2 = p2.x * canvasWidth; val y2 = p2.y * canvasHeight

        scratch.reset()
        scratch.moveTo(x1, y1)
        scratch.cubicTo(
            x1 + (p2.x - p0.x) * canvasWidth / 6f, y1 + (p2.y - p0.y) * canvasHeight / 6f,
            x2 - (p3.x - p1.x) * canvasWidth / 6f, y2 - (p3.y - p1.y) * canvasHeight / 6f,
            x2, y2,
        )
        val avgPressure = (p1.pressure + p2.pressure) * 0.5f
        val avgTilt = (p1.tilt + p2.tilt) * 0.5f
        drawPath(
            path = scratch,
            color = color,
            style = Stroke(
                width = baseWidth * avgPressure * (1f + TILT_WIDTH_GAIN * avgTilt),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/**
 * Renders [stroke] with per-segment width modulated by [DrawingPoint.pressure]
 * and [DrawingPoint.tilt]. Used to bake completed strokes into the off-screen
 * cache bitmap.
 *
 * When every point has the same pressure **and** tilt (typical mouse / marker
 * stroke), the whole stroke is painted as a single Catmull-Rom-smoothed path —
 * cheap and visually identical to the legacy renderer. When pressure or tilt
 * varies (tablet), each segment is painted as its own short cubic with width
 * derived from the average pressure × tilt-boost of its two endpoints.
 *
 * Segments are joined with [StrokeCap.Round] so the width steps are invisible.
 */
private fun DrawScope.drawStrokeWithPressure(
    stroke: DrawingPath,
    w: Float,
    h: Float,
    scratch: Path,
) {
    val points = stroke.points
    if (points.size < 2) return

    val color = Color(stroke.colorArgb.toInt())
    val baseWidth = stroke.strokeWidth * w

    val uniformPressure = points.first().pressure
    val uniformTilt = points.first().tilt
    val pressureVaries = points.any { it.pressure != uniformPressure }
    val tiltVaries = points.any { it.tilt != uniformTilt }

    if (!pressureVaries && !tiltVaries) {
        scratch.reset()
        points.appendCatmullRomTo(scratch, w, h)
        drawPath(
            path = scratch,
            color = color,
            style = Stroke(
                width = baseWidth * uniformPressure * (1f + TILT_WIDTH_GAIN * uniformTilt),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        return
    }

    // Per-segment render: split on sub-paths first so erased gaps stay gaps.
    val starts = points.indices.filter { i -> i == 0 || points[i].isNewPath }
    starts.forEachIndexed { si, start ->
        val end = if (si + 1 < starts.size) starts[si + 1] else points.size
        val seg = points.subList(start, end)
        if (seg.size < 2) return@forEachIndexed

        for (i in 0 until seg.size - 1) {
            val p0 = if (i > 0) seg[i - 1] else seg[0]
            val p1 = seg[i]
            val p2 = seg[i + 1]
            val p3 = if (i + 2 < seg.size) seg[i + 2] else seg[i + 1]

            val x1 = p1.x * w; val y1 = p1.y * h
            val x2 = p2.x * w; val y2 = p2.y * h

            scratch.reset()
            scratch.moveTo(x1, y1)
            scratch.cubicTo(
                x1 + (p2.x - p0.x) * w / 6f, y1 + (p2.y - p0.y) * h / 6f,
                x2 - (p3.x - p1.x) * w / 6f, y2 - (p3.y - p1.y) * h / 6f,
                x2, y2,
            )
            val avgPressure = (p1.pressure + p2.pressure) * 0.5f
            val avgTilt = (p1.tilt + p2.tilt) * 0.5f
            drawPath(
                path = scratch,
                color = color,
                style = Stroke(
                    width = baseWidth * avgPressure * (1f + TILT_WIDTH_GAIN * avgTilt),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

/**
 * Appends a smooth Catmull-Rom → cubic-Bézier approximation of the receiver's
 * points to [target]. Caller is responsible for [Path.reset]ting [target] first
 * if a fresh path is wanted — passing an existing Path lets callers reuse one
 * scratch instance across many strokes instead of allocating per frame.
 *
 * Sub-strokes (points with [DrawingPoint.isNewPath] == true) are appended as
 * independent smooth curves. Segments shorter than 3 points fall back to
 * straight lines so every recorded point is still included.
 */
/**
 * Per-gesture eraser state machine, reused by the ERASER pointer-input branch
 * and by the PEN/MARKER branches when the stylus eraser-override is detected
 * at gesture start. Encapsulates:
 *  - the pre-gesture snapshot used for sync diffing,
 *  - the distance gate (skip mutation if pointer hasn't moved enough), and
 *  - the bitmap-rebuild throttle (visual refresh ≤ ~12 fps during the gesture
 *    to keep the main thread responsive on dense pages).
 */
private class EraseGesture(
    private val pdfDrawingState: PdfDrawingState,
    private val eraserSettings: EraserSettings,
    private val eraserPos: androidx.compose.runtime.MutableState<Offset?>,
    private val onGestureStart: (snapshot: List<DrawingPath>) -> Unit,
    private val onEraseFinished: (before: List<DrawingPath>, after: List<DrawingPath>) -> Unit,
) {
    private var preEraseSnapshot: List<DrawingPath> = emptyList()
    private var lastEraseX = Float.NaN
    private var lastEraseY = Float.NaN
    private val halfSize = eraserSettings.sizeNormalized / 2f
    private val moveThresholdSq = (halfSize * 0.25f).let { it * it }
    private var lastHistoryBump = kotlin.time.TimeSource.Monotonic.markNow()
    private var pendingBump = false

    fun start(nx: Float, ny: Float) {
        preEraseSnapshot = pdfDrawingState.currentPaths.toList()
        onGestureStart(preEraseSnapshot)
        eraserPos.value = Offset(nx, ny)
        lastEraseX = nx
        lastEraseY = ny
        val changed = pdfDrawingState.eraseInZone(
            centerX = nx,
            centerY = ny,
            halfSizeNormalized = halfSize,
            settings = eraserSettings,
            bumpHistory = false,
        )
        if (changed) {
            pdfDrawingState.markHistoryChanged()
            lastHistoryBump = kotlin.time.TimeSource.Monotonic.markNow()
            pendingBump = false
        }
    }

    fun move(nx: Float, ny: Float) {
        eraserPos.value = Offset(nx, ny)
        val dx = nx - lastEraseX
        val dy = ny - lastEraseY
        if (dx * dx + dy * dy < moveThresholdSq) return
        lastEraseX = nx
        lastEraseY = ny
        val changed = pdfDrawingState.eraseInZone(
            centerX = nx,
            centerY = ny,
            halfSizeNormalized = halfSize,
            settings = eraserSettings,
            bumpHistory = false,
        )
        if (changed) {
            pendingBump = true
            if (lastHistoryBump.elapsedNow().inWholeMilliseconds >= 80L) {
                pdfDrawingState.markHistoryChanged()
                lastHistoryBump = kotlin.time.TimeSource.Monotonic.markNow()
                pendingBump = false
            }
        }
    }

    fun end() {
        eraserPos.value = null
        if (pendingBump) {
            pdfDrawingState.markHistoryChanged()
            pendingBump = false
        }
        onEraseFinished(preEraseSnapshot, pdfDrawingState.currentPaths.toList())
    }

    fun cancel() {
        eraserPos.value = null
        if (pendingBump) {
            pdfDrawingState.markHistoryChanged()
            pendingBump = false
        }
        // Жест мог быть отменён pointerInput-restart'ом (смена ключей,
        // рекомпозиция родителя) уже после того, как eraseInZone реально
        // изменил currentPaths. Если не уведомить sync — пир остаётся со
        // старыми штрихами. Сравниваем по identity: ничего не стёрто →
        // ничего не шлём.
        val after = pdfDrawingState.currentPaths.toList()
        if (after.size != preEraseSnapshot.size ||
            after.zip(preEraseSnapshot).any { (a, b) -> a !== b }
        ) {
            onEraseFinished(preEraseSnapshot, after)
        }
    }
}

private fun List<DrawingPoint>.appendCatmullRomTo(target: Path, w: Float, h: Float) {
    if (isEmpty()) return

    // Collect segment start indices (index 0 is always a start).
    val starts = indices.filter { i -> i == 0 || get(i).isNewPath }

    starts.forEachIndexed { si, start ->
        val end = if (si + 1 < starts.size) starts[si + 1] else size
        val seg = subList(start, end)

        target.moveTo(seg[0].x * w, seg[0].y * h)
        if (seg.size < 2) return@forEachIndexed
        if (seg.size == 2) {
            target.lineTo(seg[1].x * w, seg[1].y * h)
            return@forEachIndexed
        }

        // Catmull-Rom: for segment i→i+1 use ghost endpoints at the boundaries.
        for (i in 0 until seg.size - 1) {
            val p0 = if (i > 0) seg[i - 1] else seg[0]
            val p1 = seg[i]
            val p2 = seg[i + 1]
            val p3 = if (i + 2 < seg.size) seg[i + 2] else seg[i + 1]

            val x1 = p1.x * w; val y1 = p1.y * h
            val x2 = p2.x * w; val y2 = p2.y * h

            target.cubicTo(
                x1 + (p2.x - p0.x) * w / 6f, y1 + (p2.y - p0.y) * h / 6f,
                x2 - (p3.x - p1.x) * w / 6f, y2 - (p3.y - p1.y) * h / 6f,
                x2, y2,
            )
        }
    }
}
