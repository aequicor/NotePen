package ru.kyamshanov.notepen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.EraserShape
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.PageNote
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.model.StickyHighlight
import ru.kyamshanov.notepen.annotation.domain.model.ToolKind
import ru.kyamshanov.notepen.drawing.api.PdfDrawingState
import ru.kyamshanov.notepen.drawing.api.ToolMode
import ru.kyamshanov.notepen.lowlatency.LocalLowLatencyOverlayBounds
import ru.kyamshanov.notepen.lowlatency.LowLatencyStrokeOverlay
import ru.kyamshanov.notepen.lowlatency.rememberLowLatencyOverlayAvailable
import ru.kyamshanov.notepen.lowlatency.rememberLowLatencyOverlayMaxDimensionPx
import ru.kyamshanov.notepen.magnifier.MagnifierState
import ru.kyamshanov.notepen.magnifier.MagnifierTargetOverlay
import ru.kyamshanov.notepen.tablet.LocalTabletInputController
import ru.kyamshanov.notepen.tablet.PenPointerEventType
import ru.kyamshanov.notepen.tablet.TabletInputController
import ru.kyamshanov.notepen.tablet.effectivePressure
import ru.kyamshanov.notepen.tools.marker.drawMarkerStroke

/** Прозрачность заливки индикатора зоны ластика (AC-12, UI / UX § «Индикатор ластика»). */
private const val ERASER_INDICATOR_FILL_ALPHA = 0.35f

/** Толщина обводки индикатора зоны ластика, в пикселях canvas. */
private const val ERASER_INDICATOR_STROKE_WIDTH_PX = 2f

/** Прозрачность заливки зоны заметки ([PageNote]) поверх PDF. */
private const val NOTE_FILL_ALPHA = 0.18f

/** Толщина подчёркивания заметки под её зоной, в пикселях canvas. */
private const val NOTE_UNDERLINE_PX = 2.5f

/** Размер тап-бэйджа заметки (открывает редактор тела заметки). */
private val NOTE_BADGE_SIZE = 18.dp

/** Прозрачность контура hover-индикатора кончика пера. */
private const val HOVER_INDICATOR_ALPHA = 0.5f

/** Минимальный радиус hover-индикатора в пикселях канваса. */
private const val HOVER_INDICATOR_MIN_RADIUS_PX = 6f

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

private fun cappedLowLatencyOverlaySize(
    pageSize: IntSize,
    maxDimensionPx: Int,
): IntSize {
    val w = pageSize.width
    val h = pageSize.height
    if (w <= 0 || h <= 0) return IntSize.Zero
    val longest = maxOf(w, h)
    if (longest <= maxDimensionPx) return pageSize
    return IntSize(
        width = (w.toLong() * maxDimensionPx / longest).toInt().coerceAtLeast(1),
        height = (h.toLong() * maxDimensionPx / longest).toInt().coerceAtLeast(1),
    )
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
     * Текстовые заметки ([PageNote]) для этой страницы. Геометрия ([PageNote.rects])
     * — источник истины, как у [highlights]: рисуются заливкой + подчёркиванием.
     */
    notes: List<PageNote> = emptyList(),
    /**
     * Колбэк тапа по бэйджу заметки. Если `null`, бэйджи не монтируются (лупа /
     * превью), так что заметки рисуются, но не перехватывают ввод пером.
     */
    onNoteTapped: ((PageNote) -> Unit)? = null,
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
    lowLatencyViewportScale: Float = 1f,
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
    val lowLatencyOverlayMaxDimensionPx = rememberLowLatencyOverlayMaxDimensionPx()
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
    val lowLatencyOverlaySize by remember {
        derivedStateOf { cappedLowLatencyOverlaySize(canvasSize.value, lowLatencyOverlayMaxDimensionPx) }
    }
    val lowLatencyOverlayActive by remember(lowLatencyOverlaySupported, magnifierState) {
        derivedStateOf {
            lowLatencyOverlaySupported &&
                magnifierState == null &&
                lowLatencyOverlaySize != IntSize.Zero
        }
    }

    val inkCacheMaxDimensionPx = completedInkCacheMaxDimensionPx()
    val vectorCompletedInkWhenUpscaled = vectorCompletedInkWhenCacheUpscaled()

    // Bucketed cache dimensions: drops the lowest bits of canvasSize so
    // sub-bucket zoom changes don't invalidate the ink cache. Above the
    // platform cap the bucket is irrelevant (cap wins),
    // which means at high zoom the key naturally stays constant.
    // derivedStateOf — чтобы рекомпозиция триггерилась только при
    // пересечении границы бакета, а не на каждый пиксельный delta.
    val inkCacheDim: IntSize by remember {
        derivedStateOf {
            bucketedInkCacheSize(canvasSize.value, inkCacheMaxDimensionPx)
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
    // graphicsLayer'ом) — без пустого кадра. Счётчик [CompletedInk.strokeCount]
    // позволяет дорисовать ещё не вошедшие в кэш штрихи, пока он не догнал
    // (см. anti-flicker ниже).
    val completedInk = remember { mutableStateOf<CompletedInk?>(null) }
    val isDrawingForInkCache = pdfDrawingState.isDrawing.value
    LaunchedEffect(inkCacheDim, pdfDrawingState.historyVersion.value, isDrawingForInkCache) {
        if (isDrawingForInkCache) return@LaunchedEffect
        val bw = inkCacheDim.width
        val bh = inkCacheDim.height
        if (bw <= 0 || bh <= 0 || pdfDrawingState.currentPaths.isEmpty()) {
            completedInk.value = null
            return@LaunchedEffect
        }
        delay(COMPLETED_INK_REBUILD_IDLE_DELAY_MS)
        if (pdfDrawingState.isDrawing.value) return@LaunchedEffect
        // Снимок состояния делаем на composition-потоке — нельзя итерировать
        // SnapshotStateList из фонового диспетчера (конкурентная мутация вводом).
        val paths = pdfDrawingState.currentPaths.toList()
        if (paths.isEmpty()) {
            completedInk.value = null
            return@LaunchedEffect
        }
        val ext = pdfDrawingState.extent.value
        val spec = InkRenderSpec(surfaceSize = inkCacheDim, extent = ext)
        val bmp =
            withContext(rasterDispatcher) {
                buildCompletedInk(spec, paths, density, layoutDirection)
            }
        completedInk.value = CompletedInk(paths.size, bmp)
    }
    val completedInkCoverageCount by remember {
        derivedStateOf {
            val cached = completedInk.value
            val size = canvasSize.value
            if (vectorCompletedInkWhenUpscaled && cached?.isUpscaledTo(size) == true) {
                pdfDrawingState.currentPaths.size
            } else {
                cached?.strokeCount ?: 0
            }
        }
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

            // Text notes — geometry is the source of truth (like highlights). A faint
            // multiply-blended fill marks the note's area and a solid underline at the
            // bottom edge anchors it visually; the tappable badge (below the Canvas)
            // opens the note body. Never depends on text extraction.
            for (n in notes) {
                val nColor = Color(n.colorArgb.toInt())
                for (r in n.rects) {
                    val x0 = (r.left - ext.left) * pdfW
                    val y0 = (r.top - ext.top) * pdfH
                    val w = (r.right - r.left) * pdfW
                    val h = (r.bottom - r.top) * pdfH
                    drawRect(
                        color = nColor.copy(alpha = NOTE_FILL_ALPHA),
                        topLeft = Offset(x0, y0),
                        size = Size(w, h),
                        blendMode = BlendMode.Multiply,
                    )
                    drawRect(
                        color = nColor,
                        topLeft = Offset(x0, y0 + h - NOTE_UNDERLINE_PX),
                        size = Size(w, NOTE_UNDERLINE_PX),
                    )
                }
            }

            val paths = pdfDrawingState.currentPaths
            val canvasWidth = size.width.toInt()
            val canvasHeight = size.height.toInt()
            val inkSpec = InkRenderSpec(surfaceSize = IntSize(canvasWidth, canvasHeight), extent = ext)

            // Completed markers stay in the main canvas so their Multiply blend
            // sees the PDF pixels. Completed pen ink uses the shared bitmap cache
            // plus an anti-flicker tail while the async rebuild catches up.
            drawCompletedMarkers(paths, inkSpec, livePath)
            drawCompletedPenInk(
                paths = paths,
                cached = completedInk.value,
                spec = inkSpec,
                scratch = livePath,
                vectorWhenUpscaled = vectorCompletedInkWhenUpscaled,
            )

            if (magnifierState == null &&
                !lowLatencyOverlayActive &&
                pdfDrawingState.isDrawing.value &&
                pdfDrawingState.livePoints.isNotEmpty()
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

        // Tappable note badges — one per note, anchored to the top-right of the
        // note's first rect. Mounted only when [onNoteTapped] is provided (not in
        // loupe / preview), and placed BEFORE the low-latency overlay so it never
        // steals active stylus-draw events. Keyed by noteId for stable identity
        // under add / remove / sync churn.
        if (onNoteTapped != null) {
            val ext = pageExtent
            for (n in notes) {
                val r = n.rects.firstOrNull() ?: continue
                val badgePxX = (r.right - ext.left) * pdfWidthPx
                val badgePxY = (r.top - ext.top) * pdfHeightPx
                val badgeXDp = with(density) { badgePxX.toDp() } - NOTE_BADGE_SIZE / 2
                val badgeYDp = with(density) { badgePxY.toDp() } - NOTE_BADGE_SIZE / 2
                key(n.noteId) {
                    Box(
                        modifier =
                            Modifier
                                .offset(x = badgeXDp, y = badgeYDp)
                                .size(NOTE_BADGE_SIZE)
                                .background(
                                    color = Color(n.colorArgb.toInt()),
                                    shape = RoundedCornerShape(3.dp),
                                )
                                .pointerInput(n.noteId) {
                                    detectTapGestures(onTap = { onNoteTapped(n) })
                                },
                    )
                }
            }
        }

        // Overlay for the live stroke on platforms that support a separate
        // layer: Android uses a front-buffered SurfaceView, JVM/Desktop uses a
        // Compose Canvas in the same page tree. Where the overlay is unavailable,
        // the Compose Canvas above renders the live stroke itself.
        if (lowLatencyOverlayActive) {
            val overlayBounds = LocalLowLatencyOverlayBounds.current
            val overlayWidthDp = with(density) { lowLatencyOverlaySize.width.toDp() }
            val overlayHeightDp = with(density) { lowLatencyOverlaySize.height.toDp() }
            val pageW = canvasSize.value.width
            val pageH = canvasSize.value.height
            LowLatencyStrokeOverlay(
                drawingState = pdfDrawingState,
                viewportScale = lowLatencyViewportScale,
                windowBounds = overlayBounds?.windowRect,
                completedStrokeCount = completedInkCoverageCount,
                modifier =
                    Modifier
                        .size(width = overlayWidthDp, height = overlayHeightDp)
                        .graphicsLayer {
                            scaleX =
                                if (lowLatencyOverlaySize.width > 0) {
                                    pageW.toFloat() / lowLatencyOverlaySize.width
                                } else {
                                    1f
                                }
                            scaleY =
                                if (lowLatencyOverlaySize.height > 0) {
                                    pageH.toFloat() / lowLatencyOverlaySize.height
                                } else {
                                    1f
                                }
                            transformOrigin = TransformOrigin(0f, 0f)
                            alpha = if (isZooming()) 0f else 1f
                        },
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

suspend fun detectNativePenDrag(
    tablet: TabletInputController,
    positionOffset: () -> Offset,
    onDown: (position: Offset, pressure: Float, tilt: Float) -> Unit,
    onMove: (position: Offset, pressure: Float, tilt: Float) -> Unit,
    onUp: () -> Unit,
    onCancel: () -> Unit,
) {
    var active = false
    tablet.penPointerEvents.collect { event ->
        val localPosition = event.position - positionOffset()
        when (event.type) {
            PenPointerEventType.DOWN -> {
                if (active) onCancel()
                onDown(localPosition, event.pressure, event.tilt)
                active = true
            }
            PenPointerEventType.UPDATE -> {
                if (active) onMove(localPosition, event.pressure, event.tilt)
            }
            PenPointerEventType.UP -> {
                if (active) {
                    onMove(localPosition, event.pressure, event.tilt)
                    onUp()
                    active = false
                }
            }
            PenPointerEventType.CANCEL -> {
                if (active) {
                    onCancel()
                    active = false
                }
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
    acceptDrawingPointer: () -> Boolean = { true },
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
        if (isDrawingPointer && !acceptDrawingPointer()) {
            return@awaitEachGesture
        }
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
