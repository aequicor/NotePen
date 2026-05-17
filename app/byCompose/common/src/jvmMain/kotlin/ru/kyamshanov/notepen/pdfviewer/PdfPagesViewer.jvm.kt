package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
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
 */
private class PendingZoom {
    var factor: Float = 1f
        private set
    var focus: Offset? = null
        private set

    fun accumulate(f: Float, p: Offset) {
        factor *= f
        focus = p
    }

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
    pageContent: PdfPageContent,
) {
    val cache = remember(pdfDocument) { PdfBitmapCache(maxEntries = MAX_CACHE_ENTRIES) }
    val density = LocalDensity.current
    val pendingZoom = remember { PendingZoom() }
    val renderDispatcher = Dispatchers.Default

    LaunchedEffect(pages) {
        state.pages = pages
        state.applyPendingInitialScrollIfNeeded()
    }
    LaunchedEffect(state.viewportSize, pages.size) {
        state.applyPendingInitialScrollIfNeeded()
    }

    // Frame-batched zoom: pointerInput накапливает factor, применяем не
    // чаще раза в кадр. Зум применяется напрямую — без анимационной
    // прослойки, чтобы не было видимого отставания фактического масштаба
    // от физического жеста.
    LaunchedEffect(state) {
        while (true) {
            withFrameMillis { }
            val pair = pendingZoom.consume() ?: continue
            state.zoomBy(pair.first, pair.second)
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
                scalePercent = state.scalePercent,
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
                    val targetWidthPx = (basePageWidthPx * snap.scalePercent / 100f * density.density)
                        .toInt()
                        .coerceAtLeast(1)
                        .coerceAtMost(MAX_RENDER_DIM_PX)
                    val targetHeightPx = (targetWidthPx / aspect)
                        .toInt()
                        .coerceAtLeast(1)
                        .coerceAtMost(MAX_RENDER_DIM_PX)
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

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                if (state.viewportSize != size) {
                    state.viewportSize = size
                    state.applyPendingInitialScrollIfNeeded()
                }
            }
            .clipToBounds()
            .pdfDesktopPointerInput(state, pendingZoom),
    ) {
        SubcomposeLayout(modifier = Modifier.fillMaxSize()) { constraints ->
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
            val w = (layout.basePageWidthPx * zoom).roundToInt().coerceAtLeast(1)
            val visualWidthDp = with(density) { w.toDp() }
            val px = pan.x.roundToInt()
            var py = (pan.y + layout.pageTopsPx[first] * zoom).roundToInt()
            for (i in first..last) {
                val h = (layout.pageHeightsPx[i] * zoom).roundToInt().coerceAtLeast(1)
                val visualHeightDp = with(density) { h.toDp() }
                val pagePlaceables = subcompose(i) {
                    val cached = cache.entries[i]?.bitmap
                    val scope = ImmutablePdfPageScope(
                        pageIndex = i,
                        bitmap = cached,
                        visualWidth = visualWidthDp,
                        visualHeight = visualHeightDp,
                    )
                    with(scope) { pageContent() }
                }.map { it.measure(Constraints.fixed(w, h)) }
                val placementY = py
                pagePlaceables.forEach { items.add(Item(px, placementY, it)) }
                py += h
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                items.forEach { it.placeable.place(it.placeableX, it.placeableY) }
            }
        }
    }
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
) : PdfPageScope

/**
 * Pointer-input десктоп-вьювера: zoom вокруг курсора, скролл колесом,
 * горизонтальный скролл с Shift, pan средней кнопкой.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.pdfDesktopPointerInput(
    state: PdfViewerState,
    pendingZoom: PendingZoom,
): Modifier = this.pointerInput(state) {
    awaitPointerEventScope {
        var lastCursor = Offset.Zero
        var middleDragOrigin: Offset? = null
        var zoomBurstFocus: Offset? = null
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
                    }
                    val origin = middleDragOrigin
                    if (origin != null && event.buttons.isTertiaryPressed && change != null) {
                        val delta = change.position - origin
                        state.panBy(delta)
                        middleDragOrigin = change.position
                        change.consume()
                    }
                }
                PointerEventType.Press -> {
                    if (event.buttons.isTertiaryPressed && change != null) {
                        middleDragOrigin = change.position
                        change.consume()
                    }
                }
                PointerEventType.Release -> {
                    if (!event.buttons.isTertiaryPressed) middleDragOrigin = null
                }
                PointerEventType.Scroll -> {
                    if (change == null) continue
                    val delta = change.scrollDelta
                    val ctrl = event.keyboardModifiers.isCtrlPressed
                    val shift = event.keyboardModifiers.isShiftPressed
                    when {
                        ctrl -> {
                            val focus = zoomBurstFocus
                                ?: change.position.takeIf { it != Offset.Zero }
                                ?: lastCursor
                            zoomBurstFocus = focus
                            val factor = ZOOM_BASE.pow(-delta.y)
                            pendingZoom.accumulate(factor, focus)
                        }
                        shift -> {
                            state.panBy(Offset(-delta.y * WHEEL_SCROLL_PX_PER_TICK, 0f))
                        }
                        else -> {
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
