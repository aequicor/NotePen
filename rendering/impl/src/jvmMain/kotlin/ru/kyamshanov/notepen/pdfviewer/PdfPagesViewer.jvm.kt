package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.pointer.PointerEventPass
import com.sun.jna.Native
import com.sun.jna.Platform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import ru.kyamshanov.notepen.pdf.presentation.toImageBitmap

private const val WHEEL_SCROLL_PX_PER_TICK = 60f

/**
 * База показательной функции зума: `factor = ZOOM_BASE^(-delta.y)`.
 * При стандартной мыши с дискретным колесом `delta.y = ±1.0` это даёт
 * привычные ±~7% за щелчок. На прецизионном тачпаде / smooth-scroll
 * мыши `delta.y` приходит дробным (например 0.1) — формула даёт
 * соответственно мелкий плавный шаг ~0.7%, и зум становится визуально
 * непрерывным без какой-либо анимации.
 *
 * Снизили с прежних 1.1 → 1.075: на обычной мыши шаг ощущается мягче,
 * на тачпаде разница незаметна.
 */
private const val ZOOM_BASE = 1.075f

/**
 * Горизонтальная составляющая скролла без Shift применяется только если
 * `|dx| >= |dy| * ratio`. Ratio=4 означает: движение должно быть в 4 раза
 * горизонтальнее, чем вертикально — подавляет случайный дрейф при вертикальном
 * двухпальцевом свайпе на тачпаде.
 */
/**
 * Минимальный |delta.x| для горизонтального скролла — отсекает абсолютный
 * шум тачпада (значения ≤ 0.05 практически не несут намерения).
 */
private const val SCROLL_H_DEAD_ZONE = 0.05f

/**
 * Коэффициент сглаживания EMA для оценки доминирующей оси скролла.
 * Меньше → более инерционная оценка; больше → быстрее реагирует на смену оси.
 */
private const val SCROLL_EMA_ALPHA = 0.3f

/**
 * Горизонталь подавляется, если EMA(|dy|) > EMA(|dx|) × этот коэффициент.
 * JBR разделяет вертикальные и горизонтальные события: во время
 * горизонтального свайпа вертикальные события не приходят → EMA вертикали
 * затухает, горизонталь разблокируется уже через 1–2 тика.
 */
private const val SCROLL_H_SUPPRESS_RATIO = 2.0f

/**
 * Если ширина страничной колонки помещается во вьюпорт, горизонтальный
 * тачпад-скролл подавляется — страница уже видна целиком. НО: если
 * пользователь смести документ мышью (drag-to-pan), [pan.x] уходит от
 * центрального положения; тогда скролл разрешается, чтобы вернуть
 * документ в центр. Этот порог — допустимое отклонение от центра в пикселях,
 * при котором скролл ещё считается «центрированным» и остаётся заблокированным.
 */
private const val SCROLL_H_CENTER_TOLERANCE_PX = 2f

/**
 * Дебаунс между остановкой пользователя и запуском high-res рендера.
 * Должен быть БОЛЬШЕ типичного интервала между wheel-тиками при
 * медленном зуме (~200–250 мс), иначе render запускается между тиками
 * → cache.put на main → рекомпозиция всей видимой страницы → cascade.
 */
private const val RENDER_DEBOUNCE_MS = 300L
private const val MAX_CACHE_ENTRIES = 6
private const val MAX_RENDER_DIM_PX = 4000
private const val BUFFER_PAGES = 1
private const val ZOOM_BURST_RESET_PX = 8f

/**
 * Сколько миллисекунд idle (без новых wheel-тиков) до того, как
 * `commitPinchGesture` впечёт transient `gestureScale` / `gestureTranslation`
 * в `zoom` / `pan` и снимет layer-трансформу. Должен быть меньше
 * [RENDER_DEBOUNCE_MS], иначе high-res рендер не дождётся настоящего `zoom`.
 */
private const val GESTURE_COMMIT_IDLE_MS = 100L

/**
 * Off-screen битмап с масштабом > 2× или < 0.5× от текущего считается
 * "устаревшим" и выкидывается из кэша сразу же.
 */
private const val STALE_SCALE_RATIO_THRESHOLD = 2f

/**
 * Накопитель wheel-zoom тиков для применения раз в кадр. Один Ctrl+wheel
 * burst может выдавать > 100 событий в секунду (прецизионный тачпад);
 * рекомпозиция per-tick захлёбывает main-поток. Cursor-anchored zoom
 * мультипликативно коммутативен (`N` тиков по `f` == один тик `f^N`
 * вокруг той же точки), батч даёт идентичный визуальный результат при
 * меньшей нагрузке.
 *
 * Thread-safe: [accumulate] вызывается с Compose main thread (wheel через
 * [pdfDesktopPointerInput]) и с AppKit main thread (pinch через
 * [MacosGestureBridge]). [consume] работает на Compose main thread.
 */
private class PendingZoom {
    @Volatile private var factor: Float = 1f
    @Volatile private var focus: Offset? = null

    @Synchronized
    fun accumulate(f: Float, p: Offset) {
        factor *= f
        focus = p
    }

    @Synchronized
    fun consume(): Pair<Float, Offset>? {
        val f = factor
        val p = focus ?: return null
        if (f == 1f) return null
        factor = 1f
        return f to p
    }
}

/**
 * Desktop-реализация [PdfPagesViewer].
 *
 * - **Cursor-anchored zoom** через Ctrl+wheel — пиксель под курсором
 *   остаётся под курсором.
 * - **Адаптивный рендер** — битмап страницы рендерится с разрешением
 *   текущего масштаба, дебаунс [RENDER_DEBOUNCE_MS]. До завершения нового
 *   рендера показывается предыдущий битмап.
 * - **Виртуализация** через [SubcomposeLayout] — composing только видимые
 *   страницы + [BUFFER_PAGES] буферных сверху/снизу.
 * - **Мышиные жесты**: wheel — вертикальный скролл, Shift+wheel —
 *   горизонтальный, средняя кнопка drag — pan.
 * - **HiDPI** — render scale умножается на `density` экрана.
 *
 * Все жесты обрабатываются на [PointerEventPass.Initial], чтобы вложенный
 * `DrawablePdfPage` (рисование пером) не перехватывал скролл/зум.
 */
@OptIn(ExperimentalComposeUiApi::class, FlowPreview::class)
@Composable
actual fun PdfPagesViewer(
    state: PdfViewerState,
    pdfDocument: PdfDocument?,
    pages: List<PdfPageInfo>,
    renderer: PdfPageRenderer,
    modifier: Modifier,
    gestureModifier: Modifier,
    primaryDragPanEnabled: () -> Boolean,
    pageContent: PdfPageContent,
) {
    val cache = remember(pdfDocument) { PdfBitmapCache(maxEntries = MAX_CACHE_ENTRIES) }
    val density = LocalDensity.current
    val pendingZoom = remember { PendingZoom() }
    // Shared cursor position: updated by pointer-input on Compose thread,
    // read by macOS gesture callback on AppKit thread → AtomicReference.
    val lastCursorRef = remember { java.util.concurrent.atomic.AtomicReference(Offset.Zero) }
    val renderDispatcher = Dispatchers.Default

    // macOS trackpad pinch: NSEventMaskMagnify via libnotepen_gesture.dylib.
    // Skiko intercepts NSView.magnifyWithEvent: natively and never forwards it
    // to Compose's PointerInputScope, so we bypass Skiko entirely with an
    // NSEvent local monitor — same technique as the tablet pressure bridge.
    //
    // The callback MUST be held in `remember`, not a local variable — JNA
    // callbacks are GC'd if no strong Java reference exists, causing the native
    // monitor to fire into a dangling pointer and silently do nothing.
    val gestureCallback = remember(pendingZoom, lastCursorRef) {
        MacosGestureBridge.OnMagnify { magnification, x, y ->
            val factor = 1f + magnification
            if (factor > 0f) pendingZoom.accumulate(factor, lastCursorRef.get())
        }
    }
    DisposableEffect(gestureCallback) {
        if (!Platform.isMac()) return@DisposableEffect onDispose {}
        // Mirror addComposeResourcesToJnaPath() from CocoaTabletInputController:
        // in dev (:run) compose.application.resources.dir may be absent, but
        // build.gradle.kts already adds -Djna.library.path=assets/, so this
        // is belt-and-suspenders for packaged distributions.
        System.getProperty("compose.application.resources.dir")?.let { dir ->
            val cur = System.getProperty("jna.library.path").orEmpty()
            if (dir !in cur.split(java.io.File.pathSeparator)) {
                System.setProperty(
                    "jna.library.path",
                    if (cur.isEmpty()) dir else "$dir${java.io.File.pathSeparator}$cur",
                )
            }
        }
        val bridge = runCatching {
            Native.load("notepen_gesture", MacosGestureBridge::class.java)
        }.getOrNull() ?: return@DisposableEffect onDispose {}
        bridge.notepen_gesture_start(gestureCallback)
        onDispose { bridge.notepen_gesture_stop() }
    }

    LaunchedEffect(pages) {
        state.pages = pages
        state.applyPendingInitialScrollIfNeeded()
    }
    LaunchedEffect(state.viewportSize, pages.size) {
        state.applyPendingInitialScrollIfNeeded()
    }

    // Frame-batched zoom: pointerInput накапливает factor, применяем не
    // чаще раза в кадр. Во время бёрста зум идёт в transient
    // `gestureScale` / `gestureTranslation` (graphicsLayer на SubcomposeLayout)
    // — без layout-pass'а и без ре-растеризации битмапов. Когда новых тиков
    // нет [GESTURE_COMMIT_IDLE_MS] мс, `commitPinchGesture` атомарно
    // впекает накопленное в `zoom` / `pan`, layer возвращается в identity,
    // и snapshotFlow ниже видит новый `zoom` → high-res рендер.
    LaunchedEffect(state) {
        var lastZoomMillis = 0L
        var hasPendingCommit = false
        while (true) {
            val now = withFrameMillis { it }
            val pair = pendingZoom.consume()
            if (pair != null) {
                val focus = pair.second
                state.pinchGestureUpdate(focus, focus, pair.first)
                lastZoomMillis = now
                hasPendingCommit = true
            } else if (hasPendingCommit && now - lastZoomMillis >= GESTURE_COMMIT_IDLE_MS) {
                state.commitPinchGesture()
                hasPendingCommit = false
            }
        }
    }

    LaunchedEffect(pdfDocument, state, renderer) {
        val doc = pdfDocument ?: return@LaunchedEffect
        snapshotFlow {
            val range = PdfViewerMath.visiblePageRange(
                layout = state.layout,
                panY = state.pan.y,
                zoom = state.zoom,
                viewportHeight = state.viewportSize.height.toFloat(),
            )
            VisibleSnapshot(
                first = (range.first - BUFFER_PAGES).coerceAtLeast(0),
                last = (range.last + BUFFER_PAGES).coerceAtMost(state.pages.lastIndex),
                // Растеризуем до layoutCap; зум сверх него — GPU-апскейл.
                scalePercent = state.renderScalePercent,
                basePageWidthPx = state.basePageWidthPx,
                viewportSize = state.viewportSize,
            )
        }
            .distinctUntilChanged()
            .debounce(RENDER_DEBOUNCE_MS)
            .collectLatest { snap ->
                if (snap.viewportSize.width <= 0 || snap.first < 0 || snap.last < snap.first) {
                    return@collectLatest
                }
                val basePageWidthPx = snap.basePageWidthPx
                if (basePageWidthPx <= 0f) return@collectLatest
                val visible = (snap.first..snap.last).toSet()
                cache.evictStaleScale(
                    visibleIndices = visible,
                    currentScale = snap.scalePercent,
                    maxScaleRatio = STALE_SCALE_RATIO_THRESHOLD,
                )
                for (i in snap.first..snap.last) {
                    val cached = cache.get(i)
                    if (cached != null && cached.renderedAtScalePercent >= snap.scalePercent) continue
                    val page = state.pages.getOrNull(i) ?: continue
                    val aspect = page.aspectRatio.takeIf { it > 0f } ?: 1f
                    val desiredWidthPx = (basePageWidthPx * snap.scalePercent / 100f * density.density)
                        .toInt()
                        .coerceAtLeast(1)
                    // Clamp обе оси с сохранением aspect: если высота, рассчитанная
                    // от полной ширины, выходит за MAX_RENDER_DIM_PX, уменьшаем
                    // и ширину пропорционально. Иначе битмап получит aspect,
                    // отличный от page.aspectRatio, и лупа (которая использует
                    // `target × bmp.dims` без коррекции) покажет искажённое
                    // изображение. Обычный PDF-display этого не видит — он
                    // через FillBounds компенсирует distortion обратно.
                    val widthCapped = desiredWidthPx.coerceAtMost(MAX_RENDER_DIM_PX)
                    val heightFromWidth = (widthCapped / aspect).toInt().coerceAtLeast(1)
                    val (targetWidthPx, targetHeightPx) = if (heightFromWidth > MAX_RENDER_DIM_PX) {
                        val cappedH = MAX_RENDER_DIM_PX
                        val cappedW = (cappedH * aspect).toInt().coerceAtLeast(1)
                        cappedW to cappedH
                    } else {
                        widthCapped to heightFromWidth
                    }
                    launch {
                        val bitmap = withContext(renderDispatcher) {
                            renderer.renderPage(doc, i, targetWidthPx, targetHeightPx)
                                .toImageBitmap()
                        }
                        cache.put(
                            i,
                            RenderedPage(
                                bitmap = bitmap,
                                renderedAtScalePercent = snap.scalePercent,
                            ),
                        )
                    }
                }
            }
    }

    // Outer Box measures viewport size and hosts scrollbars as siblings of the
    // pointer-capturing inner Box. Siblings receive hit-tested events directly,
    // bypassing the Initial-pass handler on the inner Box — so scrollbar thumb
    // drags are never consumed by drag-to-pan.
    Box(
        modifier = modifier
            .onSizeChanged { size ->
                if (state.viewportSize != size) {
                    state.viewportSize = size
                    state.applyPendingInitialScrollIfNeeded()
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pdfDesktopPointerInput(state, pendingZoom, primaryDragPanEnabled, lastCursorRef)
                .then(gestureModifier),
        ) {
        SubcomposeLayout(
            modifier = Modifier
                .fillMaxSize()
                // Transient zoom-трансформа: scale + translate через GPU
                // render node, без layout-pass'а. См. KDoc у
                // [PdfViewerState.gestureScale]. Lambda-форма читает state
                // в graphicsLayer-блоке — он переоценивается на DRAW-pass'е,
                // без рекомпозиции / ремежа SubcomposeLayout'а.
                .graphicsLayer {
                    // residualScale — зум сверх layoutCap, не запечённый в размер
                    // layout'а; домножаем его сюда. Ниже cap residualScale == 1f.
                    val s = state.gestureScale * state.residualScale
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = state.gestureTranslation.x
                    translationY = state.gestureTranslation.y
                },
        ) { constraints ->
            val layout = state.layout
            val zoom = state.zoom
            val pan = state.pan
            val pageCount = layout.pageHeightsPx.size
            if (pageCount == 0 || layout.basePageWidthPx <= 0f) {
                return@SubcomposeLayout layout(constraints.maxWidth, constraints.maxHeight) {}
            }
            val visible = PdfViewerMath.visiblePageRange(
                layout = layout,
                panY = pan.y,
                zoom = zoom,
                viewportHeight = constraints.maxHeight.toFloat(),
            )
            if (visible.isEmpty()) {
                return@SubcomposeLayout layout(constraints.maxWidth, constraints.maxHeight) {}
            }
            val first = (visible.first - BUFFER_PAGES).coerceAtLeast(0)
            val last = (visible.last + BUFFER_PAGES).coerceAtMost(pageCount - 1)

            data class Item(val placeableX: Int, val placeableY: Int, val placeable: androidx.compose.ui.layout.Placeable)
            val items = mutableListOf<Item>()
            // Размер/растеризацию ведём в layoutZoom (≤ cap); зум сверх cap даёт
            // graphicsLayer через residualScale. Размещение — в полном zoom,
            // пред-делённое на residualScale (layer домножит обратно).
            val lz = state.layoutZoom
            val rs = state.residualScale
            for (i in first..last) {
                val ext = layout.pageExtents[i]
                val pdfH = layout.pdfHeightsPx[i]
                val w = (layout.pageWidthsPx[i] * lz).roundToInt().coerceAtLeast(1)
                val h = (pdfH * ext.height * lz).roundToInt().coerceAtLeast(1)
                val visualWidthDp = with(density) { w.toDp() }
                val visualHeightDp = with(density) { h.toDp() }
                val pdfWidthDp = with(density) {
                    (layout.basePageWidthPx * lz).roundToInt().coerceAtLeast(1).toDp()
                }
                val pdfHeightDp = with(density) {
                    (pdfH * lz).roundToInt().coerceAtLeast(1).toDp()
                }
                val pagePlaceables = subcompose(i) {
                    val cached = cache.entries[i]?.bitmap
                    val scope = ImmutablePdfPageScope(
                        pageIndex = i,
                        bitmap = cached,
                        visualWidth = visualWidthDp,
                        visualHeight = visualHeightDp,
                        pdfWidth = pdfWidthDp,
                        pdfHeight = pdfHeightDp,
                        extent = ext,
                    )
                    with(scope) { pageContent() }
                }.map { it.measure(Constraints.fixed(w, h)) }
                val slotX = ((pan.x + ext.left * layout.basePageWidthPx * zoom) / rs).roundToInt()
                val slotY = ((pan.y + (layout.pageTopsPx[i] + ext.top * pdfH) * zoom) / rs).roundToInt()
                pagePlaceables.forEach { items.add(Item(slotX, slotY, it)) }
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                items.forEach { it.placeable.place(it.placeableX, it.placeableY) }
            }
        }
        } // inner Box

        val hAdapter = remember(state) { PanScrollbarAdapter(state, horizontal = true) }
        val vAdapter = remember(state) { PanScrollbarAdapter(state, horizontal = false) }
        val showH by remember {
            derivedStateOf {
                // Show horizontal scrollbar when the page column overflows the viewport OR
                // when the document has been dragged off its natural centred position (so the
                // user can drag the scrollbar to re-centre it).
                val pageColumnW = state.layout.basePageWidthPx * state.zoom
                val viewportW = state.viewportSize.width
                if (pageColumnW > viewportW) {
                    true
                } else {
                    val centredPanX = (viewportW - pageColumnW) / 2f
                    kotlin.math.abs(state.pan.x - centredPanX) > SCROLL_H_CENTER_TOLERANCE_PX
                }
            }
        }
        val showV by remember {
            derivedStateOf {
                (state.layout.contentBottomPx - state.layout.contentTopPx) * state.zoom >
                    state.viewportSize.height
            }
        }
        if (showH) {
            HorizontalScrollbar(
                adapter = hAdapter,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
            )
        }
        if (showV) {
            VerticalScrollbar(
                adapter = vAdapter,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    } // outer Box
}

private data class VisibleSnapshot(
    val first: Int,
    val last: Int,
    val scalePercent: Int,
    val basePageWidthPx: Float,
    val viewportSize: IntSize,
)

private data class ImmutablePdfPageScope(
    override val pageIndex: Int,
    override val bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    override val visualWidth: androidx.compose.ui.unit.Dp,
    override val visualHeight: androidx.compose.ui.unit.Dp,
    override val pdfWidth: androidx.compose.ui.unit.Dp,
    override val pdfHeight: androidx.compose.ui.unit.Dp,
    override val extent: ru.kyamshanov.notepen.annotation.domain.model.PageExtent,
) : PdfPageScope

/**
 * Pointer-input десктоп-вьювера: zoom вокруг курсора, скролл колесом,
 * горизонтальный скролл с Shift, pan средней и (опционально) левой кнопкой.
 *
 * [primaryDragPanEnabled] возвращает `true`, когда левая кнопка должна
 * перетаскивать документ (а не рисовать) — передаётся из `DetailsContent`
 * как `{ toolMode == ToolMode.NONE }`.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.pdfDesktopPointerInput(
    state: PdfViewerState,
    pendingZoom: PendingZoom,
    primaryDragPanEnabled: () -> Boolean,
    lastCursorRef: java.util.concurrent.atomic.AtomicReference<Offset>,
): Modifier = this.pointerInput(state) {
    awaitPointerEventScope {
        var lastCursor = Offset.Zero
        var middleDragOrigin: Offset? = null
        var primaryDragOrigin: Offset? = null
        var zoomBurstFocus: Offset? = null
        // EMA of recent |dx| and |dy| across scroll events; used to detect the
        // dominant scroll axis and suppress the minor axis (see SCROLL_H_SUPPRESS_RATIO).
        var hScrollEma = 0f
        var vScrollEma = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull()
            when (event.type) {
                PointerEventType.Move, PointerEventType.Enter -> {
                    val newPos = change?.position
                    if (newPos != null) {
                        val burst = zoomBurstFocus
                        if (burst != null && (newPos - burst).getDistance() > ZOOM_BURST_RESET_PX) {
                            zoomBurstFocus = null
                        }
                        lastCursor = newPos
                        lastCursorRef.set(newPos)
                    }
                    val middleOrigin = middleDragOrigin
                    if (middleOrigin != null && event.buttons.isTertiaryPressed && change != null) {
                        // Bake любую pending-gesture transform до пана:
                        // pan-дельта в viewport-пикселях, и если оставить
                        // gestureScale != 1, 1px движения мыши даст scale*1
                        // пикселей визуально — рассинхрон с курсором.
                        state.commitPinchGesture()
                        state.panBy(change.position - middleOrigin)
                        middleDragOrigin = change.position
                        change.consume()
                    }
                    val primOrigin = primaryDragOrigin
                    if (primOrigin != null && event.buttons.isPrimaryPressed &&
                        primaryDragPanEnabled() && change != null
                    ) {
                        state.commitPinchGesture()
                        state.panBy(change.position - primOrigin)
                        primaryDragOrigin = change.position
                        change.consume()
                    }
                }
                PointerEventType.Press -> {
                    if (event.buttons.isTertiaryPressed && change != null) {
                        state.commitPinchGesture()
                        middleDragOrigin = change.position
                        change.consume()
                    } else if (event.buttons.isPrimaryPressed && primaryDragPanEnabled() &&
                        change != null
                    ) {
                        state.commitPinchGesture()
                        primaryDragOrigin = change.position
                        change.consume()
                    }
                }
                PointerEventType.Release -> {
                    if (!event.buttons.isTertiaryPressed) middleDragOrigin = null
                    if (!event.buttons.isPrimaryPressed) primaryDragOrigin = null
                }
                PointerEventType.Scroll -> {
                    if (change == null) continue
                    val delta = change.scrollDelta
                    val awtEvent = event.awtEventOrNull as? java.awt.event.MouseWheelEvent
                    val ctrl = event.keyboardModifiers.isCtrlPressed ||
                        event.keyboardModifiers.isMetaPressed ||
                        awtEvent?.isControlDown == true
                    val shift = event.keyboardModifiers.isShiftPressed
                    when {
                        ctrl -> {
                            val focus = zoomBurstFocus
                                ?: change.position.takeIf { it != Offset.Zero }
                                ?: lastCursor
                            zoomBurstFocus = focus
                            // macOS trackpad pinch via JBR: NSEventTypeMagnify →
                            // Ctrl+Scroll with |delta.y| ≈ 0.01–0.05.
                            // JBR sign: pinch-out (zoom in) → delta.y > 0,
                            // so factor = 1 + delta.y > 1 → zoom in ✓.
                            // Discrete mouse wheel: |delta.y| ≥ 1.0,
                            // exponential formula gives ~7% per click ✓.
                            val rawDelta = awtEvent?.preciseWheelRotation?.toFloat() ?: delta.y
                            val factor = if (kotlin.math.abs(rawDelta) < 0.5f) {
                                1f + rawDelta
                            } else {
                                ZOOM_BASE.pow(-rawDelta)
                            }
                            pendingZoom.accumulate(factor, focus)
                        }
                        shift -> {
                            state.commitPinchGesture()
                            // On macOS with JBR, trackpad horizontal swipe arrives as
                            // shift=true + delta.x, dy=0 (synthetic shift — not a keyboard key).
                            // Update EMA for both axes (dy=0 here → vScrollEma decays).
                            // Suppress horizontal if vertical EMA dominates.
                            hScrollEma = hScrollEma * (1f - SCROLL_EMA_ALPHA) +
                                kotlin.math.abs(delta.x) * SCROLL_EMA_ALPHA
                            vScrollEma = vScrollEma * (1f - SCROLL_EMA_ALPHA) +
                                kotlin.math.abs(delta.y) * SCROLL_EMA_ALPHA
                            val pageColumnW = state.layout.basePageWidthPx * state.zoom
                            val pageColumnFits = pageColumnW <= state.viewportSize.width
                            // Suppress H-scroll when page fits AND pan.x is near its natural
                            // centred position. If the user drag-panned the document off-centre
                            // we allow trackpad scroll to bring it back.
                            val suppressDueToFit = pageColumnFits && run {
                                val centredPanX = (state.viewportSize.width - pageColumnW) / 2f
                                kotlin.math.abs(state.pan.x - centredPanX) <= SCROLL_H_CENTER_TOLERANCE_PX
                            }
                            val suppressH = suppressDueToFit ||
                                vScrollEma > hScrollEma * SCROLL_H_SUPPRESS_RATIO
                            val absDx = kotlin.math.abs(delta.x)
                            val hPx = if (suppressH || absDx < SCROLL_H_DEAD_ZONE) 0f
                            else -delta.x * WHEEL_SCROLL_PX_PER_TICK
                            state.panBy(Offset(hPx, -delta.y * WHEEL_SCROLL_PX_PER_TICK))
                        }
                        else -> {
                            state.commitPinchGesture()
                            // dx=0 here in JBR vertical events → hScrollEma decays.
                            vScrollEma = vScrollEma * (1f - SCROLL_EMA_ALPHA) +
                                kotlin.math.abs(delta.y) * SCROLL_EMA_ALPHA
                            hScrollEma = hScrollEma * (1f - SCROLL_EMA_ALPHA) +
                                kotlin.math.abs(delta.x) * SCROLL_EMA_ALPHA
                            state.panBy(Offset(0f, -delta.y * WHEEL_SCROLL_PX_PER_TICK))
                        }
                    }
                    change.consume()
                }
                else -> Unit
            }
        }
    }
}

private class PanScrollbarAdapter(
    private val state: PdfViewerState,
    private val horizontal: Boolean,
) : ScrollbarAdapter {

    override val scrollOffset: Double
        get() {
            val layout = state.layout
            return if (horizontal) {
                -(state.pan.x + layout.contentLeftPx * state.zoom).toDouble()
            } else {
                -(state.pan.y + layout.contentTopPx * state.zoom).toDouble()
            }
        }

    override val contentSize: Double
        get() {
            val l = state.layout
            val z = state.zoom
            return if (horizontal) {
                ((l.contentRightPx - l.contentLeftPx) * z).toDouble()
            } else {
                ((l.contentBottomPx - l.contentTopPx) * z).toDouble()
            }
        }

    override val viewportSize: Double
        get() = if (horizontal) {
            state.viewportSize.width.toDouble()
        } else {
            state.viewportSize.height.toDouble()
        }

    override suspend fun scrollTo(scrollOffset: Double) {
        val layout = state.layout
        if (horizontal) {
            val newPanX = -(scrollOffset.toFloat() + layout.contentLeftPx * state.zoom)
            state.panBy(Offset(newPanX - state.pan.x, 0f))
        } else {
            val newPanY = -(scrollOffset.toFloat() + layout.contentTopPx * state.zoom)
            state.panBy(Offset(0f, newPanY - state.pan.y))
        }
    }
}
