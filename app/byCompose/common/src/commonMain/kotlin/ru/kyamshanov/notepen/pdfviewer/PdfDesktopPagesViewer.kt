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
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
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
private const val ZOOM_IN_FACTOR = 1.1f
private const val ZOOM_OUT_FACTOR = 1f / ZOOM_IN_FACTOR

/**
 * Дебаунс между остановкой пользователя и запуском high-res рендера.
 * Должен быть БОЛЬШЕ типичного интервала между wheel-тиками при
 * медленном зуме (~200–250 мс), иначе render запускается между тиками
 * → cache.put на main → рекомпозиция всей видимой страницы → cascade.
 * При 300 мс рендер триггерится только когда пользователь по-настоящему
 * перестал зумить.
 */
private const val RENDER_DEBOUNCE_MS = 300L
private const val MAX_CACHE_ENTRIES = 6
private const val MAX_RENDER_DIM_PX = 4000
private const val BUFFER_PAGES = 1
private const val ZOOM_BURST_RESET_PX = 8f

/**
 * Off-screen битмап с масштабом > 2× или < 0.5× от текущего считается
 * "устаревшим" и выкидывается из кэша сразу же — не дожидаясь LRU.
 * Освобождает 64-МБ битмапы для GC при резком zoom-out от 800 % к 50 %.
 */
private const val STALE_SCALE_RATIO_THRESHOLD = 2f

/**
 * Накопитель wheel-zoom тиков для применения раз в кадр. Один Ctrl+wheel
 * burst может выдавать > 100 событий в секунду; рекомпозиция per-tick
 * захлёбывает main-поток при больших страницах. Cursor-anchored zoom
 * мультипликативно коммутативен (N тиков по `f` == один тик `f^N`), так
 * что батч даёт идентичный визуальный результат при меньшей нагрузке.
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
 * Desktop-only PDF viewer.
 *
 * Реализует современную модель PDF-вьювера (Acrobat / Preview / PDF.js):
 *
 * - **Cursor-anchored zoom** через Ctrl+wheel — пиксель под курсором
 *   остаётся под курсором.
 * - **Адаптивный рендер** — битмап страницы рендерится с разрешением
 *   текущего масштаба, дебаунс [RENDER_DEBOUNCE_MS]. До завершения нового
 *   рендера показывается предыдущий битмап, растянутый в нужный размер.
 * - **Виртуализация** через [SubcomposeLayout] — composing только видимые
 *   страницы + [BUFFER_PAGES] буферных сверху/снизу.
 * - **Мышиные жесты**: wheel — вертикальный скролл, Shift+wheel —
 *   горизонтальный, средняя кнопка drag — pan.
 * - **HiDPI** — render scale умножается на `density` экрана.
 *
 * Все жесты обрабатываются на [PointerEventPass.Initial], чтобы вложенный
 * `DrawablePdfPage` (рисование пером) не перехватывал скролл/зум.
 *
 * @param state источник правды по позиции и зуму; см. [rememberPdfViewerState]
 * @param pdfDocument открытый PDF (null до окончания загрузки)
 * @param pages список страниц — должен совпадать с тем, что передан в [state]
 * @param renderer порт растеризации страниц
 * @param renderDispatcher диспетчер для тяжёлого CPU-рендера; по умолчанию
 *   [Dispatchers.Default]. PDFBox внутренне сериализует свои вызовы
 *   `synchronized(renderer)`, так что несколько корутин не дадут параллелизма
 *   и помощник не нужен
 * @param modifier стандартный modifier контейнера
 * @param pageContent композбл одной страницы — обычно [DrawablePdfPage]
 */
@OptIn(ExperimentalComposeUiApi::class, FlowPreview::class)
@Composable
fun PdfDesktopPagesViewer(
    state: PdfViewerState,
    pdfDocument: PdfDocument?,
    pages: List<PdfPageInfo>,
    renderer: PdfPageRenderer,
    renderDispatcher: CoroutineDispatcher = Dispatchers.Default,
    modifier: Modifier = Modifier,
    pageContent: PdfPageContent,
) {
    val cache = remember(pdfDocument) { PdfBitmapCache(maxEntries = MAX_CACHE_ENTRIES) }
    val density = LocalDensity.current
    val pendingZoom = remember { PendingZoom() }

    // Прокидываем входные `pages` в state — он держит layout в derivedStateOf.
    // После загрузки страниц (когда viewport уже измерен) повторно вызываем
    // `applyPendingInitialScrollIfNeeded` — это даёт начальное центрирование
    // и применяет отложенный bundle-scroll.
    LaunchedEffect(pages) {
        state.pages = pages
        state.applyPendingInitialScrollIfNeeded()
    }

    // Frame-batched zoom: pointerInput накапливает factor, мы применяем не
    // чаще раза в кадр. Без батча main забивается рекомпозициями на
    // скоростях > 30 Hz wheel — на больших страницах появляется лаг
    // > 100 ms и серия cursor-anchor'ов уезжает.
    LaunchedEffect(state) {
        while (true) {
            withFrameMillis { }
            val pair = pendingZoom.consume() ?: continue
            state.zoomBy(pair.first, pair.second)
        }
    }

    // Стартовый и последующие рендеры. snapshotFlow по visible-range + zoom;
    // debounce 120 мс — на остановку скролла/зума.
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
                // collectLatest (не collect) — при новом emission (продолжение
                // зума или скролла) текущий блок и все его дочерние `launch`
                // отменяются. Без этого при серии "зум — пауза — зум — пауза"
                // накапливаются параллельные high-res рендеры устаревших
                // масштабов: они конкурируют за renderDispatcher и сожирают
                // память.
                if (snap.viewportSize.width <= 0 || snap.first < 0 || snap.last < snap.first) {
                    return@collectLatest
                }
                val basePageWidthPx = snap.basePageWidthPx
                if (basePageWidthPx <= 0f) return@collectLatest
                // Освобождаем устаревшие битмапы off-screen страниц сразу,
                // не дожидаясь LRU. Видимые страницы защищены — их битмап
                // остаётся, чтобы было что показать пока новый рендерится.
                val visible = (snap.first..snap.last).toSet()
                cache.evictStaleScale(
                    visibleIndices = visible,
                    currentScale = snap.scalePercent,
                    maxScaleRatio = STALE_SCALE_RATIO_THRESHOLD,
                )
                for (i in snap.first..snap.last) {
                    val cached = cache.get(i)
                    // Пропускаем re-render если кэшированный масштаб уже
                    // >= текущему: высокое разрешение прекрасно
                    // downsample'ится Skia при отображении на меньшем зуме,
                    // и битмап остаётся готовым к следующему zoom-in без
                    // долгой растеризации. Re-render запускаем только если
                    // кэш blurry для текущего вью.
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
                        // toImageBitmap() для битмапа 4000×4000 = 64 МБ
                        // аллокация + копия пикселей. На main-потоке это
                        // блокирует кадр на сотни мс. Перенесли в
                        // renderDispatcher: renderer.renderPage уже там, и
                        // toImageBitmap логически продолжает ту же работу.
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
            // Высота каждой страницы зависит ТОЛЬКО от zoom (не от pan.y).
            // Иначе при continuous-зуме менялась бы pan.y между тиками и
            // `round(pan.y + topNext*zoom) - round(pan.y + top*zoom)` давал
            // бы ±1 px шум — страница пульсировала бы и стыки прыгали.
            // Стекаем последовательно: page i+1 starts at page i's bottom,
            // что гарантирует точное тайлование без зазоров вне зависимости
            // от pan.y.
            var py = (pan.y + layout.pageTopsPx[first] * zoom).roundToInt()
            for (i in first..last) {
                val h = (layout.pageHeightsPx[i] * zoom).roundToInt().coerceAtLeast(1)
                val visualHeightDp = with(density) { h.toDp() }
                // ВАЖНО: чтение `cache.entries[i]?.bitmap` намеренно сделано
                // ВНУТРИ subcompose-лямбды, а не снаружи. Иначе обновление
                // битмапа инвалидирует весь measure-блок, он перечитывает
                // `pan`/`zoom` и страница "прыгает" при каждом рендере.
                // Сейчас обновление кэша рекомпозит только содержимое одной
                // страницы; outer measure не запускается, позиции стабильны.
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

/**
 * Снимок visible-range, по которому переотрисовываются битмапы.
 * `equals`/`hashCode` гарантируют, что [distinctUntilChanged] не пропустит
 * изменения масштаба или ресайза.
 */
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
 *
 * Перехват на [PointerEventPass.Initial] обязателен — иначе вложенный
 * `DrawablePdfPage` или авто-скролл LazyColumn (если бы он был) съели бы
 * событие до нас.
 *
 * `lastCursor` обновляется только по Move/Enter событиям: позиция в
 * Scroll-event в Compose Desktop ненадёжна и может уехать на post-state
 * координаты после рекомпозиции графического слоя.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.pdfDesktopPointerInput(
    state: PdfViewerState,
    pendingZoom: PendingZoom,
): Modifier = this.pointerInput(state) {
    awaitPointerEventScope {
        var lastCursor = Offset.Zero
        var middleDragOrigin: Offset? = null
        // Стабильный focus zoom'а в пределах одного wheel-burst'а. При
        // быстром непрерывном Ctrl+wheel позиция в Scroll-event может
        // фликовать (inertia / синтетика), и каждый сдвиг focus'а уводит
        // anchor — серия ticks "уплывает" вместе со страницей. Захватываем
        // focus один раз в начале burst'а (первый Scroll после Move) и
        // переиспользуем до следующего Move.
        var zoomBurstFocus: Offset? = null
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull()
            when (event.type) {
                PointerEventType.Move, PointerEventType.Enter -> {
                    val newPos = change?.position
                    if (newPos != null) {
                        val burst = zoomBurstFocus
                        // Сбрасываем burst-якорь ТОЛЬКО если курсор реально
                        // ушёл от точки якоря (>ZOOM_BURST_RESET_PX). Compose
                        // Desktop может слать синтетические Move для refresh
                        // hover-state, и они "сбрасывали" якорь на каждом
                        // тике зума → серия Ctrl+wheel дрейфовала.
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
                            // Захватываем focus при первом Scroll после
                            // Move/Enter; до следующего Move/Enter используем
                            // тот же focus, чтобы вся серия ticks анкорилась к
                            // одной и той же точке.
                            val focus = zoomBurstFocus
                                ?: change.position.takeIf { it != Offset.Zero }
                                ?: lastCursor
                            zoomBurstFocus = focus
                            val factor = if (delta.y < 0f) ZOOM_IN_FACTOR else ZOOM_OUT_FACTOR
                            // Накопить вместо немедленного применения —
                            // frame-loop в [PdfDesktopPagesViewer] применит
                            // батч раз в кадр.
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
