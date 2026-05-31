package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import ru.kyamshanov.notepen.pdf.presentation.toImageBitmap
import kotlin.math.roundToInt

/**
 * Период сэмплинга рендер-триггера. Раньше тут был `debounce`, но он ждёт
 * «тишины»: при непрерывном скролле видимый диапазон меняется каждый кадр,
 * дебаунс постоянно сбрасывается, и страницы НЕ перерисовываются до полной
 * остановки — входящие страницы видны мыльными (растянутый битмап меньшего
 * масштаба) или пустыми. `sample` отдаёт последнее значение раз в период даже
 * при непрерывном потоке → новые страницы растеризуются прямо во время
 * скролла, не дожидаясь остановки. Рендер идёт на [renderDispatcher] (вне
 * main-потока), поэтому на плавность самого скролла не влияет.
 */
private const val RENDER_SAMPLE_MS = 100L

/**
 * Лимит записей кэша растров. В книжном развороте ([SpreadMode.SPREAD]) окно
 * растеризации — текущий разворот (2 стр.) плюс по одному соседнему развороту
 * с каждой стороны (см. [PdfViewerMath.bufferedRenderRange]) = до 6 страниц;
 * прежние 6 не оставляли запаса и при churn'е вытесняли видимое. 10 держит
 * полное окно разворота + буфер; общий объём всё равно ограничен
 * pixel-ceiling LRU внутри [PdfBitmapCache].
 */
private const val MAX_CACHE_ENTRIES = 10

/**
 * Cap on PDF bitmap dimensions. Above this, the page is just upscaled by
 * `Image.ContentScale.FillBounds` — sub-pixel blur is invisible at the kind
 * of zoom levels where the cap kicks in, but each render allocates two big
 * Bitmaps + an IntArray copy (≈ `widthPx * heightPx * 12` bytes on the GC
 * path), so cutting the cap from 3000 → 2400 reduces peak per-render
 * allocation by ~36 % and shrinks the GPU draw cost on mobile.
 */
private const val MAX_RENDER_DIM_PX = 2400

/**
 * Нижний порог отношения «пиксели растра / пиксели на экране» (суперсэмплинг).
 * Без порога страница растеризуется ровно в размер показа, и даунскейл с
 * дефолтным `FilterQuality.Low` смягчает текст. 2× даёт запас на сглаживание;
 * на типичном планшете `density` уже ≥ 2, так что [maxOf] там обычно no-op, и
 * лишней памяти при открытии не тратится. Сверху ограничено [MAX_RENDER_DIM_PX].
 */
private const val MIN_RENDER_SUPERSAMPLE = 2.0f
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
 * - **Адаптивный рендер** — snapshotFlow следит за видимым диапазоном и
 *   масштабом и раз в [RENDER_SAMPLE_MS] (sample, не debounce — чтобы
 *   рендерить и во время непрерывного скролла, а не только после остановки)
 *   перерисовывает битмапы на текущем масштабе через общий [PdfBitmapCache];
 *   до прихода нового битмапа `Image` растягивает предыдущий.
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
    primaryDragPanEnabled: (position: Offset) -> Boolean,
    userRotationQuarters: (pageIndex: Int) -> Int,
    pageSource: (logicalIndex: Int) -> PageSourceSpec,
    pageContent: PdfPageContent,
) {
    val cache = remember(pdfDocument) { PdfBitmapCache(maxEntries = MAX_CACHE_ENTRIES) }
    val density = LocalDensity.current
    val renderDispatcher = Dispatchers.Default

    // Резолвер «логический индекс → исходная страница + вырезка» меняется при
    // переключении разделения разворотов (#4). Рендер-эффект ниже ключится на
    // (pdfDocument, state, renderer) и НЕ пересоздаётся при смене pageSource —
    // читаем актуальный через rememberUpdatedState, иначе запущенная корутина
    // держала бы старое замыкание (spreadSplit=false) при уже удвоённом
    // state.pages и слала бы логические индексы как исходные → IndexOutOfBounds.
    val currentPageSource by rememberUpdatedState(pageSource)

    LaunchedEffect(pages) {
        state.pages = pages
        state.applyPendingInitialScrollIfNeeded()
        // Разделение страниц (#4) меняет [pages]: layout пере-строен — применяем
        // отложенную перецентровку (с сохранением масштаба) на свежем layout'е.
        state.applyPendingRecenterIfNeeded()
    }
    LaunchedEffect(state.viewportSize, pages.size) {
        state.applyPendingInitialScrollIfNeeded()
    }

    val flingScope = rememberCoroutineScope()
    val flingHolder = remember(state) { PdfFlingJobHolder() }

    LaunchedEffect(pdfDocument, state, renderer) {
        val doc = pdfDocument ?: return@LaunchedEffect
        snapshotFlow {
            val range =
                PdfViewerMath.visiblePageRange(
                    layout = state.layout,
                    panY = state.pan.y,
                    zoom = state.zoom,
                    viewportHeight = state.viewportSize.height.toFloat(),
                )
            // Окно растеризации: видимое + буфер, выровненный по разворотам (#F4).
            val window =
                PdfViewerMath.bufferedRenderRange(
                    layout = state.layout,
                    visible = range,
                    bufferPages = BUFFER_PAGES,
                    pageCount = state.pages.size,
                )
            val first = if (window.isEmpty()) -1 else window.first
            val last = if (window.isEmpty()) -1 else window.last
            VisibleSnapshot(
                first = first,
                last = last,
                visFirst = if (range.isEmpty()) -1 else range.first,
                visLast = if (range.isEmpty()) -1 else range.last,
                // Растеризуем PDF до layoutCap; зум сверх него — GPU-апскейл,
                // поэтому коммит зума за cap не должен запускать ре-рендер.
                scalePercent = state.renderScalePercent,
                basePageWidthPx = state.basePageWidthPx,
                viewportSize = state.viewportSize,
                rotationSignature =
                    if (last >= first) {
                        (first..last).sumOf { userRotationQuarters(it) * 31 + it }
                    } else {
                        0
                    },
                cropSignature =
                    if (last >= first) {
                        (first..last).sumOf { cropSignatureOf(currentPageSource(it)) * 131 + it }
                    } else {
                        0
                    },
            )
        }
            .distinctUntilChanged()
            .sample(RENDER_SAMPLE_MS)
            .collectLatest { snap ->
                if (snap.viewportSize.width <= 0 || snap.first < 0 || snap.last < snap.first) {
                    return@collectLatest
                }
                val basePageWidthPx = snap.basePageWidthPx
                if (basePageWidthPx <= 0f) return@collectLatest
                val window = (snap.first..snap.last).toSet()
                cache.evictStaleScale(
                    visibleIndices = window,
                    currentScale = snap.scalePercent,
                    maxScaleRatio = STALE_SCALE_RATIO_THRESHOLD,
                )
                // Видимые страницы — раньше буферных: под сериализующим
                // pdfium-локом обе страницы текущего разворота должны занять
                // очередь раньше опережающего буфера (#F4).
                val order =
                    PdfViewerMath.renderPriorityOrder(
                        window = snap.first..snap.last,
                        visible = snap.visFirst..snap.visLast,
                    )
                for (i in order) {
                    val rotation = userRotationQuarters(i)
                    val src = currentPageSource(i)
                    // Защита от транзиентного рассинхрона при переключении #4:
                    // исходный индекс вне диапазона документа — пропускаем кадр
                    // вместо падения рендера (исключение убило бы экран редактора).
                    if (src.sourceIndex !in 0 until doc.info.pageCount) continue
                    val cropSig = cropSignatureOf(src)
                    val cached = cache.get(i)
                    if (cached != null && cache.isFresh(cached, snap.scalePercent, rotation, cropSig)) {
                        continue
                    }
                    val page = state.pages.getOrNull(i) ?: continue
                    val aspect = page.aspectRatio.takeIf { it > 0f } ?: 1f
                    val supersample = maxOf(density.density, MIN_RENDER_SUPERSAMPLE)
                    val targetWidthPx =
                        (basePageWidthPx * snap.scalePercent / 100f * supersample)
                            .toInt()
                            .coerceAtLeast(1)
                            .coerceAtMost(MAX_RENDER_DIM_PX)
                    val targetHeightPx =
                        (targetWidthPx / aspect)
                            .toInt()
                            .coerceAtLeast(1)
                            .coerceAtMost(MAX_RENDER_DIM_PX)
                    launch {
                        val bitmap =
                            withContext(renderDispatcher) {
                                renderer.renderPage(
                                    doc,
                                    src.sourceIndex,
                                    targetWidthPx,
                                    targetHeightPx,
                                    rotation,
                                    src.cropLeftN,
                                    src.cropTopN,
                                    src.cropRightN,
                                    src.cropBottomN,
                                ).toImageBitmap()
                            }
                        cache.put(
                            i,
                            RenderedPage(
                                bitmap = bitmap,
                                renderedAtScalePercent = snap.scalePercent,
                                renderedAtRotationQuarters = rotation,
                                renderedAtCropSignature = cropSig,
                            ),
                        )
                    }
                }
            }
    }

    // React when the layout mode flips (book-spread on/off). The row width changes
    // (single column ⇄ two columns + gutter), so the old pan/zoom leaves the page
    // off-centre — turning spread ON strands the page half-screen with the next
    // page glued to the right, turning it OFF strands the single column left of
    // centre. A pending requestRecenter (set by the toggle handler) re-centres the
    // page on the fresh layout while PRESERVING the current zoom (the mode toggle
    // must not rescale the page to fill the screen); absent one, just re-centre.
    // `drop(1)` skips the initial value so a restored scroll position isn't
    // clobbered on first compose.
    LaunchedEffect(state) {
        snapshotFlow { state.spreadMode }
            .drop(1)
            .collect {
                if (!state.applyPendingRecenterIfNeeded() && state.viewportSize.width > 0) {
                    state.reCenterAfterResize()
                }
            }
    }

    Box(
        modifier =
            modifier
                .onSizeChanged { size ->
                    if (state.viewportSize != size) {
                        val hadWidth = state.viewportSize.width > 0
                        state.viewportSize = size
                        state.applyPendingInitialScrollIfNeeded()
                        // A genuine resize (panel opened/closed, divider dragged)
                        // re-centres the page in the new viewport.
                        if (hadWidth && size.width > 0) state.reCenterAfterResize()
                    }
                }
                .clipToBounds()
                // Pinch — Initial pass, перехват до pan-обработчика; жесты <2
                // пальцев проходят дальше.
                .pdfAndroidPointerInput(state)
                // Одно-пальцевый pan по обеим осям (диагональ) + инерционный fling.
                // Стоит ПЕРЕД gestureModifier: рисование/лупа (inner) обрабатывают
                // Main-pass раньше и, если потребили жест, pan отступает.
                .pdfSingleFingerPanInput(
                    state = state,
                    flingScope = flingScope,
                    flingHolder = flingHolder,
                )
                // gestureModifier должен быть ПОСЛЕ pan-обработчика: в Main-pass
                // события идут inner→outer, т.е. этот modifier обрабатывает
                // события раньше pan'а. Если gesture-handler потребил событие
                // (consume), pan видит isConsumed=true и отступает — pan не
                // запускается параллельно с рисованием / выделением лупой.
                .then(gestureModifier),
    ) {
        SubcomposeLayout(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Transient pinch-трансформа: scale + translate через GPU
                    // render node, без layout-pass'а. См. KDoc у
                    // [PdfViewerState.gestureScale]. Lambda-форма читает state
                    // в graphicsLayer-блоке — он переоценивается на DRAW-pass'е,
                    // без рекомпозиции / ремежа SubcomposeLayout'а.
                    .graphicsLayer {
                        // residualScale — зум сверх layoutCap, который НЕ запечён в
                        // размер layout'а (страница разложена в layoutZoom-пиксели),
                        // поэтому домножаем его сюда. Ниже cap residualScale == 1f.
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
            val visible =
                PdfViewerMath.visiblePageRange(
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
            // Размер/растеризацию ведём в layoutZoom (≤ cap), визуальный зум
            // сверх cap добавляет graphicsLayer через residualScale. Размещение
            // считаем в полном zoom и пред-делим на residualScale, т.к. layer
            // домножит координаты обратно. Ниже cap residualScale == 1f.
            val lz = state.layoutZoom
            val rs = state.residualScale
            for (i in first..last) {
                val ext = layout.pageExtents[i]
                val pdfH = layout.pdfHeightsPx[i]
                val w = (layout.pageWidthsPx[i] * lz).roundToInt().coerceAtLeast(1)
                val h = (pdfH * ext.height * lz).roundToInt().coerceAtLeast(1)
                val visualWidthDp = with(density) { w.toDp() }
                val visualHeightDp = with(density) { h.toDp() }
                val pdfWidthDp =
                    with(density) {
                        (layout.basePageWidthPx * lz).roundToInt().coerceAtLeast(1).toDp()
                    }
                val pdfHeightDp =
                    with(density) {
                        (pdfH * lz).roundToInt().coerceAtLeast(1).toDp()
                    }
                val pagePlaceables =
                    subcompose(i) {
                        val cached = cache.entries[i]?.bitmap
                        val scope =
                            ImmutablePdfPageScope(
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
                val slotX =
                    ((pan.x + (layout.pageLeftsPx[i] + ext.left * layout.basePageWidthPx) * zoom) / rs).roundToInt()
                val slotY = ((pan.y + (layout.pageTopsPx[i] + ext.top * pdfH) * zoom) / rs).roundToInt()
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
    val visFirst: Int = first,
    val visLast: Int = last,
    val scalePercent: Int,
    val basePageWidthPx: Float,
    val viewportSize: IntSize,
    val rotationSignature: Int = 0,
    val cropSignature: Int = 0,
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
