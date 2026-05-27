package ru.kyamshanov.notepen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.EraserShape
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.model.StickyHighlight
import ru.kyamshanov.notepen.annotation.domain.model.ToolKind
import ru.kyamshanov.notepen.drawing.api.PdfDrawingState
import ru.kyamshanov.notepen.drawing.api.ToolMode
import ru.kyamshanov.notepen.lowlatency.LowLatencyStrokeOverlay
import ru.kyamshanov.notepen.lowlatency.rememberLowLatencyOverlayAvailable
import ru.kyamshanov.notepen.magnifier.MagnifierState
import ru.kyamshanov.notepen.magnifier.MagnifierTargetOverlay
import ru.kyamshanov.notepen.tablet.LocalTabletInputController
import ru.kyamshanov.notepen.tablet.TabletInputController
import ru.kyamshanov.notepen.tablet.effectivePressure
import ru.kyamshanov.notepen.tools.marker.drawMarkerStroke
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas

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
 * Floor for any rendered stroke segment in pixels. Below 1px Skia's stroking
 * pipeline produces broken or invisible lines (especially on low-DPI screens
 * and at low pressure on thin pens); clamping here guarantees the user never
 * draws a line that disappears at zoom-1, even at the thinnest slider position
 * with minimum pressure.
 */
private const val MIN_RENDERED_STROKE_PX = 1f

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
 * Максимум ещё-не-кэшированных штрихов, дорисовываемых на main-потоке поверх
 * битмапа каждый кадр (anti-flicker хвост).
 *
 * Хвост рассчитан на «текущее слово» — несколько штрихов с последней паузы. Но
 * при ХОЛОДНОМ кэше (первый рендер страницы) `cachedCount = 0`, и без лимита
 * fallback перерисовывал ВСЕ штрихи страницы каждый кадр на main-потоке, пока
 * фоновый [buildCompletedInk] не догонит — отсюда долгий лаг при открытии
 * исписанной страницы. Ограничиваем хвост последними N штрихами: основную массу
 * показывает одноразовый фоновый билд (через ~сотню мс), а per-frame стоимость
 * остаётся ограниченной.
 */
private const val MAX_UNCACHED_TAIL_STROKES = 48

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

/**
 * Растеризованный кэш завершённых штрихов вместе с количеством штрихов
 * ([strokeCount]), вошедших в него. Счётчик нужен, чтобы понять, какие штрихи
 * ещё не попали в кэш (хвост `currentPaths`), и дорисовать их поверх, пока идёт
 * асинхронный rebuild — иначе только что завершённый штрих «пропадал» на время
 * растеризации (см. anti-flicker в [DrawablePdfPage]).
 */
private data class CachedInk(
    val strokeCount: Int,
    val bitmap: ImageBitmap,
)

/**
 * Растеризует все [paths] в off-screen [ImageBitmap] размера [bw]×[bh].
 * Чистая функция без захвата composable-состояния — безопасно вызывать вне
 * main-потока. Битмап покрывает PDF + extent-поля; PDF-пиксель = `bw/ext.width`,
 * штрихи нормализованы в PDF-page координатах (см. `drawStrokeWithPressure`).
 */
private fun buildCompletedInk(
    bw: Int,
    bh: Int,
    paths: List<DrawingPath>,
    ext: PageExtent,
    density: Density,
    layoutDirection: LayoutDirection,
): ImageBitmap {
    val pdfBw = bw.toFloat() / ext.width
    val pdfBh = bh.toFloat() / ext.height
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
        // Marker strokes are NOT baked here: they must be composited against the
        // PDF with BlendMode.Multiply (so text stays readable), which only works
        // when drawn directly onto the canvas that already holds the PDF — see the
        // marker pass in DrawablePdfPage. This bitmap is pen ink only.
        paths.forEach { path ->
            if (path.toolType != ToolKind.MARKER) {
                drawStrokeWithPressure(path, pdfBw, pdfBh, ext, scratch)
            }
        }
    }
    return bmp
}

@Composable
fun DrawablePdfPage(
    bitmap: ImageBitmap,
    pdfDrawingState: PdfDrawingState,
    toolMode: ToolMode,
    penSettings: PenSettings,
    markerSettings: MarkerSettings,
    eraserSettings: EraserSettings,
    /** Sticky-marker highlights for this page; rendered as filled word-aligned rects. */
    highlights: List<StickyHighlight> = emptyList(),
    /**
     * Ширина PDF-битмапа в Dp (без учёта extent-полей). Берётся вызывающим
     * из [ru.kyamshanov.notepen.pdfviewer.PdfPageScope.pdfWidth] — это значение
     * известно уже на layout-pass'е, в отличие от `canvasSize`, которое
     * приходит через `onSizeChanged` на один кадр позже. Использование
     * этого параметра вместо вычисления через canvasSize устраняет
     * one-frame-jitter при росте extent во время штриха.
     */
    pdfWidth: androidx.compose.ui.unit.Dp,
    /** Высота PDF-битмапа в Dp; см. [pdfWidth]. */
    pdfHeight: androidx.compose.ui.unit.Dp,
    /** Текущий [PageExtent] страницы; см. [pdfWidth] про синхронность с layout. */
    pageExtent: PageExtent,
    /**
     * Если не `null`, страница рисуется в «magnifier-режиме»: обычный
     * pen-pipeline отключается, а сверху рендерится рамка-цель
     * [MagnifierTargetOverlay]. Ввод пером идёт через `MagnifierInputPanel`,
     * который коммитит штрихи в этот же [pdfDrawingState], так что
     * результат попадает в страницу без дополнительной синхронизации.
     *
     * Должен передаваться только для той страницы, индекс которой
     * совпадает с `magnifierState.pageIndex` — иначе рамка появится не
     * там, где ожидает пользователь.
     */
    magnifierState: MagnifierState? = null,
    /** Индекс страницы в документе — нужен для multi-page magnifier overlay. */
    pageIndex: Int = 0,
    /**
     * Активен ли drag рамки-цели magnifier'а (пользователь зажал и тянет).
     * Используется только для визуальной подсветки GRAB-состояния в
     * [MagnifierTargetOverlay]; на собственно жесты не влияет.
     */
    isMagnifierGrabbing: Boolean = false,
    /**
     * Лямбда, возвращающая `true` пока активен pinch-жест (zoom). Читается
     * в Draw-фазе через [graphicsLayer], поэтому скрытие чернил происходит
     * в том же кадре, что и GPU-трансформа [PdfViewerState.gestureScale] —
     * без рекомпозиции и без визуального скачка.
     */
    isZooming: () -> Boolean = { false },
    /**
     * Диспетчер для растеризации кэша завершённых штрихов вне main-потока.
     * CPU-bound работа → [Dispatchers.Default] (в commonMain нет `IO`).
     * Инъекция оставлена для тестов; вызывающим менять не нужно.
     */
    rasterDispatcher: CoroutineDispatcher = Dispatchers.Default,
    modifier: Modifier = Modifier,
) {
    val canvasSize = remember { mutableStateOf(IntSize.Zero) }
    val tablet = LocalTabletInputController.current
    val densityLocal = LocalDensity.current

    // PDF-размеры в пикселях, известные на layout-pass'е (без one-frame-lag,
    // который был при выводе из canvasSize через onSizeChanged).
    val pdfWidthPx: Float = with(densityLocal) { pdfWidth.toPx() }
    val pdfHeightPx: Float = with(densityLocal) { pdfHeight.toPx() }

    // Прокидываем ширину PDF (а не всего слота) в magnifier — он использует
    // её для нормировки strokeWidth так же, как обычный pen-pipeline.
    LaunchedEffect(magnifierState, pdfWidthPx, pdfHeightPx) {
        magnifierState?.updatePageCanvasPx(pdfWidthPx, pdfHeightPx)
    }

    val hoverPos by tablet.hoverPosition.collectAsState()

    val indicatorColor = MaterialTheme.colorScheme.outline
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val lowLatencyOverlaySupported = rememberLowLatencyOverlayAvailable()
    // derivedStateOf — критически: иначе ЛЮБОЕ изменение canvasSize
    // (каждый pinch-тик) триггерит рекомпозицию всего DrawablePdfPage,
    // даже если порог не пересечён. С derivedStateOf рекомпозиция только
    // когда сам Boolean меняет значение (пересечение 2400px).
    // While the page is in magnifier-mode (`magnifierState != null`), the user
    // is writing inside the floating panel; running the Android SurfaceView
    // low-latency overlay on this page is pure waste — its output is mostly
    // hidden by the panel, and on Android it contends with the panel for the
    // render thread, producing the lag observed both in the panel and on the
    // page. Falling back to Compose's frame-bound live-stroke render here is
    // imperceptible because the user looks at the panel.
    val lowLatencyOverlayActive by remember(lowLatencyOverlaySupported, magnifierState) {
        derivedStateOf {
            lowLatencyOverlaySupported &&
                magnifierState == null &&
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
    //
    // Растеризация вынесена с main-потока ([rasterDispatcher]): на странице с
    // сотнями штрихов синхронный rebuild при коммите зума (смена бакета
    // [inkCacheDim]) занимал сотни мс и ронял кадры. Пока строится новый
    // битмап, на экране остаётся прежний (GPU-масштабируется тем же
    // graphicsLayer'ом) — без пустого кадра. Счётчик [CachedInk.strokeCount]
    // позволяет дорисовать ещё не вошедшие в кэш штрихи, пока он не догнал
    // (см. anti-flicker ниже).
    val completedInk = remember { mutableStateOf<CachedInk?>(null) }
    LaunchedEffect(inkCacheDim, pdfDrawingState.historyVersion.value) {
        val bw = inkCacheDim.width
        val bh = inkCacheDim.height
        if (bw <= 0 || bh <= 0 || pdfDrawingState.currentPaths.isEmpty()) {
            completedInk.value = null
            return@LaunchedEffect
        }
        // Снимок состояния делаем на composition-потоке — нельзя итерировать
        // SnapshotStateList из фонового диспетчера (конкурентная мутация вводом).
        val paths = pdfDrawingState.currentPaths.toList()
        val ext = pdfDrawingState.extent.value
        val bmp =
            withContext(rasterDispatcher) {
                buildCompletedInk(bw, bh, paths, ext, density, layoutDirection)
            }
        completedInk.value = CachedInk(paths.size, bmp)
    }

    // Ввод рисования поднят на уровень PdfPagesViewer'а (см.
    // [MultiPageDrawingController]) — страница только рендерит штрихи и
    // индикаторы.
    Box(
        modifier =
            modifier
                .onSizeChanged { canvasSize.value = it },
    ) {
        // The PDF bitmap is drawn INSIDE the ink Canvas below (not as a separate
        // composable) so the marker can blend against the PDF pixels with
        // BlendMode.Multiply — a sibling layer would only blend against empty ink.
        // These pdf-only offset/size values position the magnifier target frame
        // (which is normalised to the PDF area, not the full extent).
        val pdfWDp = pdfWidth
        val pdfHDp = pdfHeight
        val pdfOffsetXDp = with(density) { (-pageExtent.left * pdfWidthPx).toDp() }
        val pdfOffsetYDp = with(density) { (-pageExtent.top * pdfHeightPx).toDp() }

        // Scratch Path reused across frames for the live stroke. The cached
        // `completedLayer` already holds all finished strokes; we only need a
        // single Path per frame for the in-flight stroke.
        val livePath = remember { Path() }

        // Чернила во время пинча масштабируются тем же `gestureScale`
        // graphicsLayer'ом, что и PDF-битмап (контейнер страницы общий), поэтому
        // едут вместе с ним. Растровый кэш на доли секунды выглядит мягче (как
        // и сам растянутый PDF), но штрихи остаются видимыми — это лучше, чем
        // их полное исчезновение на время жеста. После коммита зума кэш
        // ре-растеризуется в резкость.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ext = pdfDrawingState.extent.value
            val pdfW = if (ext.width > 0f) size.width / ext.width else size.width
            val pdfH = if (ext.height > 0f) size.height / ext.height else size.height

            // PDF page bitmap. Drawn here (not as a sibling composable) so the
            // marker pass below can multiply against the page's actual pixels.
            // Positioned from `pageExtent` (the layout-pass value) — NOT
            // state.extent — so the page doesn't jitter one frame when extent
            // grows mid-stroke (see KDoc on [pdfWidth]).
            val pdfDstOffset =
                IntOffset(
                    (-pageExtent.left * pdfWidthPx).toInt(),
                    (-pageExtent.top * pdfHeightPx).toInt(),
                )
            val pdfDstSize = IntSize(pdfWidthPx.toInt(), pdfHeightPx.toInt())
            drawImage(
                image = bitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = pdfDstOffset,
                dstSize = pdfDstSize,
                filterQuality = FilterQuality.High,
            )
            drawRect(
                color = indicatorColor.copy(alpha = 0.35f),
                topLeft = Offset(pdfDstOffset.x.toFloat(), pdfDstOffset.y.toFloat()),
                size = Size(pdfDstSize.width.toFloat(), pdfDstSize.height.toFloat()),
                style = Stroke(width = 0.5.dp.toPx()),
            )

            // Sticky-marker highlights — filled word-aligned rects over the PDF,
            // multiply-blended like the marker so text stays readable. Stored geometry
            // is the source of truth, so this pass never depends on text extraction.
            for (h in highlights) {
                val hColor = Color(h.colorArgb.toInt())
                for (r in h.rects) {
                    val x0 = (r.left - ext.left) * pdfW
                    val y0 = (r.top - ext.top) * pdfH
                    drawRect(
                        color = hColor,
                        topLeft = Offset(x0, y0),
                        size = Size((r.right - r.left) * pdfW, (r.bottom - r.top) * pdfH),
                        blendMode = BlendMode.Multiply,
                    )
                }
            }

            val paths = pdfDrawingState.currentPaths

            // Completed MARKER strokes — drawn here, directly over the PDF, with
            // multiply blend (inside drawMarkerStroke) so highlighted text stays
            // readable and the highlight sits visually behind the pen ink below.
            // Not cached: highlights are few and each is a single fill path.
            for (i in 0 until paths.size) {
                val p = paths[i]
                if (p.toolType == ToolKind.MARKER) {
                    drawMarkerStroke(
                        points = p.points,
                        colorArgb = p.colorArgb,
                        normalizedStrokeWidth = p.strokeWidth,
                        pdfWidth = pdfW,
                        pdfHeight = pdfH,
                        extent = ext,
                        scratch = livePath,
                    )
                }
            }

            // Completed PEN ink — single bitmap blit. Cache may be rasterised at
            // a lower resolution than the canvas (see INK_CACHE_MAX_DIMENSION_PX),
            // so stretch via dstSize. Pen ink sits on top of the marker pass.
            val cached = completedInk.value
            cached?.let { c ->
                drawImage(
                    image = c.bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(c.bitmap.width, c.bitmap.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )
            }

            // Anti-flicker: асинхронный кэш мог ещё не вобрать недавно
            // завершённые штрихи. При прерывистом письме (буквы) каждый новый
            // штрих перезапускает async-ребилд, поэтому кэш не догоняет, пока не
            // сделаешь паузу. Чтобы уже завершённые буквы не «пропадали» (а они
            // пропадали, пока мы рисуем следующую — overlay показывает только
            // текущий штрих), дорисовываем поверх битмапа весь хвост
            // `currentPaths`, ещё не вошедший в кэш (`strokeCount`) — в т.ч. во
            // время активного рисования. Это дёшево: кэш держит всё до последней
            // паузы, так что в хвосте лишь несколько штрихов текущего слова.
            // Маркеры здесь пропускаются — они уже отрисованы в marker-проходе
            // выше. Живой (ещё не завершённый) штрих сюда не попадает — он в
            // `livePoints`, не в `currentPaths`, поэтому двойного рендера нет.
            val cachedCount = cached?.strokeCount ?: 0
            if (paths.size > cachedCount) {
                // Лимитируем хвост: при холодном кэше (cachedCount = 0 на первом
                // рендере) иначе перерисовывали бы всю страницу каждый кадр на
                // main-потоке. Основную массу покрывает фоновый билд; здесь —
                // лишь последние штрихи, чтобы только что написанное не «пропадало».
                val tailStart = maxOf(cachedCount, paths.size - MAX_UNCACHED_TAIL_STROKES)
                for (i in tailStart until paths.size) {
                    val p = paths[i]
                    if (p.toolType != ToolKind.MARKER) {
                        drawStrokeWithPressure(p, pdfW, pdfH, ext, livePath)
                    }
                }
            }

            if (!lowLatencyOverlayActive &&
                pdfDrawingState.isDrawing.value &&
                pdfDrawingState.livePoints.size > 1
            ) {
                if (pdfDrawingState.liveToolKind.value == ToolKind.MARKER) {
                    drawMarkerStroke(
                        points = pdfDrawingState.livePoints,
                        colorArgb = pdfDrawingState.liveColorArgb.value,
                        normalizedStrokeWidth = pdfDrawingState.liveStrokeWidth.value,
                        pdfWidth = pdfW,
                        pdfHeight = pdfH,
                        extent = ext,
                        scratch = livePath,
                    )
                } else {
                    drawLiveStroke(
                        points = pdfDrawingState.livePoints,
                        colorArgb = pdfDrawingState.liveColorArgb.value,
                        normalizedStrokeWidth = pdfDrawingState.liveStrokeWidth.value,
                        pdfWidth = pdfW,
                        pdfHeight = pdfH,
                        extent = ext,
                        scratch = livePath,
                    )
                }
            }

            // Индикатор зоны ластика (AC-12). Позиция курсора эрейзера
            // живёт в [PdfDrawingState] — её обновляет lifted overlay
            // (см. [MultiPageDrawingController]), страница только рендерит.
            val pos = pdfDrawingState.eraserPos.value
            if (toolMode == ToolMode.ERASER && pos != null) {
                val cx = (pos.x - ext.left) * pdfW
                val cy = (pos.y - ext.top) * pdfH
                val sizePx = eraserSettings.sizeNormalized * pdfW
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
            // экрана, только для рисующих инструментов. На Android некоторые стилусы
            // (S-Pen / Pencil) продолжают присылать HOVER_MOVE параллельно с ACTION_DOWN,
            // из-за чего кружок «прилипает» к месту начала штриха — поэтому явно
            // гасим индикатор, когда идёт активное рисование.
            val hover = hoverPos
            if (hover != null &&
                !pdfDrawingState.isDrawing.value &&
                (toolMode == ToolMode.PEN || toolMode == ToolMode.MARKER)
            ) {
                val cx = (hover.x - ext.left) * pdfW
                val cy = (hover.y - ext.top) * pdfH
                val toolStrokePx =
                    when (toolMode) {
                        ToolMode.PEN -> penSettings.strokeWidth
                        ToolMode.MARKER -> markerSettings.strokeWidth
                        else -> 0f
                    } * pdfW
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
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (isZooming()) 0f else 1f },
            )
        }

        // Magnifier target frame — drag/resize. Сидит над штрихами, чтобы
        // пользователь всегда видел границу области, попадающей в панель.
        // ВАЖНО: рамку рисуем в pdf-only-области (offset + size совпадают с
        // PDF-битмапом), а не в полной extent-области страницы. targetRect
        // нормализован к PDF [0..1]; без offset/size в pdf-coords дашед-рамка
        // отображалась бы со сдвигом и растяжением относительно фактической
        // области, попадающей в окно лупы.
        if (magnifierState != null) {
            Box(
                modifier =
                    Modifier
                        .offset(x = pdfOffsetXDp, y = pdfOffsetYDp)
                        .size(width = pdfWDp, height = pdfHDp),
            ) {
                MagnifierTargetOverlay(
                    state = magnifierState,
                    pageIndex = pageIndex,
                    frameColor = MaterialTheme.colorScheme.primary,
                    isGrabbing = isMagnifierGrabbing,
                    isScreenPinned =
                        magnifierState.attachment ==
                            ru.kyamshanov.notepen.magnifier.MagnifierAttachment.SCREEN,
                )
            }
        }
    }
}

/**
 * Stylus-aware drag gesture used by all three drawing tools. See KDoc on
 * earlier revisions for the full rationale; key invariants:
 *  - no touch-slop wait (fixes the "spiral defect" — initial fast curves
 *    used to be silently swallowed for ~24dp);
 *  - drains `PointerInputChange.historical` for sub-frame stylus samples;
 *  - finger touches are ignored — only stylus / eraser / mouse draw, чтобы
 *    палец на Android уходил вниз по chain'у в `Modifier.scrollable`;
 *  - cancel-safe via try / finally.
 */
suspend fun PointerInputScope.detectStylusAwareDrag(
    tablet: TabletInputController,
    isPalmRejectionActive: () -> Boolean,
    captureGesture: (position: Offset) -> Boolean = { false },
    onDown: (position: Offset, pressure: Float, tilt: Float) -> Unit,
    onMove: (position: Offset, pressure: Float, tilt: Float) -> Unit,
    onUp: () -> Unit,
    onCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // Рисуем только стилусом / ластиком / мышью. Касания пальцем обычно
        // проходят мимо — иначе на Android они консумятся здесь и не доходят
        // до Modifier.scrollable, ломая вертикальный скролл.
        //
        // Исключение: [captureGesture] = true (быстрая лупа, shortcut лупы,
        // hit-test target-рамки лупы). Жест захватывается НЕЗАВИСИМО от типа
        // указателя, включая PointerType.Unknown. На части Android-устройств
        // первый DOWN приходит с Unknown (TOOL_TYPE_UNKNOWN = 0 — hardware
        // ещё не классифицировал касание); ограничение по типу пропускало
        // такой DOWN в scrollable и начинался скролл вместо выделения лупой.
        val isDrawingPointer =
            down.type == PointerType.Stylus ||
                down.type == PointerType.Eraser ||
                down.type == PointerType.Mouse
        if (!isDrawingPointer && !captureGesture(down.position)) {
            return@awaitEachGesture
        }
        // Палец-рисование (а не стилус) прерывается появлением второго пальца:
        // это pinch-zoom, и штрих не должен ни рисоваться во время жеста, ни
        // оставаться после него. У стилуса второй контакт — это ладонь, её
        // игнорируем (палец-палм не должен отменять перо).
        val fingerGesture = !isDrawingPointer

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
                // Второй палец → pinch-zoom: бросаем штрих (finally → onCancel),
                // не потребляя событие, чтобы pinch-обработчик его подхватил.
                if (fingerGesture && event.changes.count { it.pressed } >= 2) break
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
 *
 * [fromSegmentIndex] / [toSegmentIndexExclusive] let callers render only a
 * range of segments — used by the magnifier's incremental-bake live layer
 * to (a) bake stable older segments once into an off-screen bitmap and
 * (b) draw only the unbaked tail each frame. Default range covers the
 * whole stroke for the main-page renderer.
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
    if (points.size < 2) return
    val segFrom = fromSegmentIndex.coerceAtLeast(0)
    val segTo = toSegmentIndexExclusive.coerceAtMost(points.size - 1)
    if (segFrom >= segTo) return

    val color = Color(colorArgb.toInt())
    val baseWidth = normalizedStrokeWidth * pdfWidth
    val offX = -extent.left
    val offY = -extent.top

    // Fast path applies only when rendering the full stroke; partial-range
    // renders bypass it (cost is dominated by GPU drawPath calls, not the
    // few-point scan, so this is fine).
    if (segFrom == 0 && segTo == points.size - 1) {
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
            points.appendCatmullRomTo(scratch, pdfWidth, pdfHeight, offX, offY)
            drawPath(
                path = scratch,
                color = color,
                style =
                    Stroke(
                        width =
                            (baseWidth * uniformPressure * (1f + TILT_WIDTH_GAIN * uniformTilt))
                                .coerceAtLeast(MIN_RENDERED_STROKE_PX),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
            return
        }
    }

    for (i in segFrom until segTo) {
        val p0 = if (i > 0) points[i - 1] else points[0]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = if (i + 2 < points.size) points[i + 2] else points[i + 1]

        val x1 = (p1.x + offX) * pdfWidth
        val y1 = (p1.y + offY) * pdfHeight
        val x2 = (p2.x + offX) * pdfWidth
        val y2 = (p2.y + offY) * pdfHeight

        scratch.reset()
        scratch.moveTo(x1, y1)
        scratch.cubicTo(
            x1 + (p2.x - p0.x) * pdfWidth / 6f,
            y1 + (p2.y - p0.y) * pdfHeight / 6f,
            x2 - (p3.x - p1.x) * pdfWidth / 6f,
            y2 - (p3.y - p1.y) * pdfHeight / 6f,
            x2,
            y2,
        )
        val avgPressure = (p1.pressure + p2.pressure) * 0.5f
        val avgTilt = (p1.tilt + p2.tilt) * 0.5f
        drawPath(
            path = scratch,
            color = color,
            style =
                Stroke(
                    width =
                        (baseWidth * avgPressure * (1f + TILT_WIDTH_GAIN * avgTilt))
                            .coerceAtLeast(MIN_RENDERED_STROKE_PX),
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

    val uniformPressure = points.first().pressure
    val uniformTilt = points.first().tilt
    val pressureVaries = points.any { it.pressure != uniformPressure }
    val tiltVaries = points.any { it.tilt != uniformTilt }

    if (!pressureVaries && !tiltVaries) {
        scratch.reset()
        points.appendCatmullRomTo(scratch, pdfWidth, pdfHeight, offX, offY)
        drawPath(
            path = scratch,
            color = color,
            style =
                Stroke(
                    width =
                        (baseWidth * uniformPressure * (1f + TILT_WIDTH_GAIN * uniformTilt))
                            .coerceAtLeast(MIN_RENDERED_STROKE_PX),
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

            val x1 = (p1.x + offX) * pdfWidth
            val y1 = (p1.y + offY) * pdfHeight
            val x2 = (p2.x + offX) * pdfWidth
            val y2 = (p2.y + offY) * pdfHeight

            scratch.reset()
            scratch.moveTo(x1, y1)
            scratch.cubicTo(
                x1 + (p2.x - p0.x) * pdfWidth / 6f,
                y1 + (p2.y - p0.y) * pdfHeight / 6f,
                x2 - (p3.x - p1.x) * pdfWidth / 6f,
                y2 - (p3.y - p1.y) * pdfHeight / 6f,
                x2,
                y2,
            )
            val avgPressure = (p1.pressure + p2.pressure) * 0.5f
            val avgTilt = (p1.tilt + p2.tilt) * 0.5f
            drawPath(
                path = scratch,
                color = color,
                style =
                    Stroke(
                        width =
                            (baseWidth * avgPressure * (1f + TILT_WIDTH_GAIN * avgTilt))
                                .coerceAtLeast(MIN_RENDERED_STROKE_PX),
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

private fun List<DrawingPoint>.appendCatmullRomTo(
    target: Path,
    pdfW: Float,
    pdfH: Float,
    offX: Float = 0f,
    offY: Float = 0f,
) {
    if (isEmpty()) return

    // Collect segment start indices (index 0 is always a start).
    val starts = indices.filter { i -> i == 0 || get(i).isNewPath }

    starts.forEachIndexed { si, start ->
        val end = if (si + 1 < starts.size) starts[si + 1] else size
        val seg = subList(start, end)

        target.moveTo((seg[0].x + offX) * pdfW, (seg[0].y + offY) * pdfH)
        if (seg.size < 2) return@forEachIndexed
        if (seg.size == 2) {
            target.lineTo((seg[1].x + offX) * pdfW, (seg[1].y + offY) * pdfH)
            return@forEachIndexed
        }

        // Catmull-Rom: for segment i→i+1 use ghost endpoints at the boundaries.
        for (i in 0 until seg.size - 1) {
            val p0 = if (i > 0) seg[i - 1] else seg[0]
            val p1 = seg[i]
            val p2 = seg[i + 1]
            val p3 = if (i + 2 < seg.size) seg[i + 2] else seg[i + 1]

            val x1 = (p1.x + offX) * pdfW
            val y1 = (p1.y + offY) * pdfH
            val x2 = (p2.x + offX) * pdfW
            val y2 = (p2.y + offY) * pdfH

            target.cubicTo(
                x1 + (p2.x - p0.x) * pdfW / 6f,
                y1 + (p2.y - p0.y) * pdfH / 6f,
                x2 - (p3.x - p1.x) * pdfW / 6f,
                y2 - (p3.y - p1.y) * pdfH / 6f,
                x2,
                y2,
            )
        }
    }
}
