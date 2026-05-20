package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
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

private const val RENDER_DEBOUNCE_MS = 150L
private const val MAX_CACHE_ENTRIES = 6

/**
 * Cap on PDF bitmap dimensions. Above this, the page is just upscaled by
 * `Image.ContentScale.FillBounds` — sub-pixel blur is invisible at the kind
 * of zoom levels where the cap kicks in, but each render allocates two big
 * Bitmaps + an IntArray copy (≈ `widthPx * heightPx * 12` bytes on the GC
 * path), so cutting the cap from 3000 → 2400 reduces peak per-render
 * allocation by ~36 % and shrinks the GPU draw cost on mobile.
 */
private const val MAX_RENDER_DIM_PX = 2400
private const val BUFFER_PAGES = 1
private const val STALE_SCALE_RATIO_THRESHOLD = 2f

/**
 * Android-реализация [PdfPagesViewer].
 *
 * - **Single-zoom модель** — один «закоммиченный» `state.zoom: Float`,
 *   страницы получают размер `basePage * zoom` через `Constraints.fixed`.
 *   Никакого split-scale + debounced bake (что было корнем "скачков" в
 *   legacy-вьювере).
 * - **Pinch-трансформа без layout-pass'а** — во время активного pinch'а
 *   обновляется только `state.gestureScale` / `state.gestureTranslation`,
 *   которые накладываются `Modifier.graphicsLayer` на корень
 *   `SubcomposeLayout`. `zoom` / `pan` НЕ меняются → SubcomposeLayout не
 *   перемеривается, `Image.FillBounds` не пере-стрейчит огромные битмапы,
 *   ink-кэш в [DrawablePdfPage] не ре-растеризуется. На отпускании пинча
 *   `commitPinchGesture` атомарно впекает gesture-state в `zoom` / `pan`,
 *   gestureScale → 1f — за один Compose-кадр, без визуального скачка.
 * - **Гесты**: двух-пальцевый pinch с anchor в centroid (см.
 *   [pdfAndroidPointerInput]); single-finger drag — нативный fling-скролл
 *   через [Modifier.scrollable] с дефолтным [ScrollableDefaults.flingBehavior].
 * - **Виртуализация** через [SubcomposeLayout] + [BUFFER_PAGES] буферных
 *   страниц сверху/снизу.
 * - **Адаптивный рендер** — после commit'а pinch'а snapshotFlow видит
 *   новый `zoom`, через [RENDER_DEBOUNCE_MS] перерисовывает битмапы на
 *   новом масштабе через общий [PdfBitmapCache]; до прихода нового
 *   битмапа `Image` растягивает предыдущий.
 *
 * Pinch перехватывается на [androidx.compose.ui.input.pointer.PointerEventPass.Initial],
 * чтобы выиграть у scrollable и вложенного stylus-handler'а
 * [DrawablePdfPage]. Stylus и single-finger drag НЕ перехватываются
 * pinch'ом и доходят до соответствующих обработчиков.
 */
@OptIn(FlowPreview::class)
@Composable
actual fun PdfPagesViewer(
    state: PdfViewerState,
    pdfDocument: PdfDocument?,
    pages: List<PdfPageInfo>,
    renderer: PdfPageRenderer,
    modifier: Modifier,
    gestureModifier: Modifier,
    pageContent: PdfPageContent,
) {
    val cache = remember(pdfDocument) { PdfBitmapCache(maxEntries = MAX_CACHE_ENTRIES) }
    val density = LocalDensity.current
    val renderDispatcher = Dispatchers.Default

    LaunchedEffect(pages) {
        state.pages = pages
        state.applyPendingInitialScrollIfNeeded()
    }
    LaunchedEffect(state.viewportSize, pages.size) {
        state.applyPendingInitialScrollIfNeeded()
    }

    val scrollableState = remember(state) {
        // Возвращаем фактически потреблённую дельту: PdfViewerMath.clampPan
        // не даст сдвинуть документ за край, и fling сам остановится у
        // границы вместо проскальзывания.
        ScrollableState { delta ->
            val before = state.pan.y
            state.panBy(Offset(0f, delta))
            state.pan.y - before
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
            // Pinch — Initial pass, перехват до scrollable; жесты <2 пальцев
            // проходят дальше.
            .pdfAndroidPointerInput(state)
            // Vertical scroll + native fling для одно-пальцевого drag'a.
            // delta от scrollable положительна при drag вниз (+Y);
            // panBy добавляет её к pan.y — контент следует за пальцем.
            .scrollable(
                state = scrollableState,
                orientation = Orientation.Vertical,
                flingBehavior = ScrollableDefaults.flingBehavior(),
                reverseDirection = false,
            )
            // gestureModifier должен быть ПОСЛЕ scrollable: в Main-pass
            // события идут inner→outer, т.е. этот modifier обрабатывает
            // события раньше scrollable. Если gesture-handler потребил
            // событие (consume), scrollable видит isConsumed=true и
            // awaitPointerSlopOrCancellation возвращает null — скролл
            // не запускается параллельно с рисованием / выделением лупой.
            .then(gestureModifier),
    ) {
        SubcomposeLayout(
            modifier = Modifier
                .fillMaxSize()
                // Transient pinch-трансформа: scale + translate через GPU
                // render node, без layout-pass'а. См. KDoc у
                // [PdfViewerState.gestureScale]. Lambda-форма читает state
                // в graphicsLayer-блоке — он переоценивается на DRAW-pass'е,
                // без рекомпозиции / ремежа SubcomposeLayout'а.
                .graphicsLayer {
                    val s = state.gestureScale
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

            data class Item(
                val placeableX: Int,
                val placeableY: Int,
                val placeable: androidx.compose.ui.layout.Placeable,
            )
            val items = mutableListOf<Item>()
            for (i in first..last) {
                val ext = layout.pageExtents[i]
                val pdfH = layout.pdfHeightsPx[i]
                val w = (layout.pageWidthsPx[i] * zoom).roundToInt().coerceAtLeast(1)
                val h = (pdfH * ext.height * zoom).roundToInt().coerceAtLeast(1)
                val visualWidthDp = with(density) { w.toDp() }
                val visualHeightDp = with(density) { h.toDp() }
                val pdfWidthDp = with(density) {
                    (layout.basePageWidthPx * zoom).roundToInt().coerceAtLeast(1).toDp()
                }
                val pdfHeightDp = with(density) {
                    (pdfH * zoom).roundToInt().coerceAtLeast(1).toDp()
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
                // PDF-страница i фиксирована в document space на pageTopsPx[i];
                // слот всего лишь выходит наружу по extent. Соседние страницы
                // не двигаются, PDF под пером тоже не смещается при росте
                // extent.
                val slotX = (pan.x + ext.left * layout.basePageWidthPx * zoom).roundToInt()
                val slotY = (pan.y + (layout.pageTopsPx[i] + ext.top * pdfH) * zoom).roundToInt()
                pagePlaceables.forEach { items.add(Item(slotX, slotY, it)) }
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
    override val pdfWidth: androidx.compose.ui.unit.Dp,
    override val pdfHeight: androidx.compose.ui.unit.Dp,
    override val extent: ru.kyamshanov.notepen.annotation.domain.model.PageExtent,
) : PdfPageScope
