package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo

/**
 * Состояние PDF-вьювера: единственный источник правды по позиции, зуму и
 * перечню страниц. Аналог [androidx.compose.foundation.lazy.LazyListState],
 * но с поддержкой произвольного float-зума вокруг точки и без LazyColumn'а
 * под капотом.
 *
 * ## Координаты
 *
 * - [zoom] — визуальный масштаб (1.0 = 100%). Кламп: 0.25..8.0.
 * - [pan] — top-left угол документа в координатах вьюпорта (пиксели).
 *   Используется и для горизонтального центрирования (страницы лежат
 *   стопкой при X ∈ [0; basePageWidthPx]; видимый x = pan.x + docX * zoom),
 *   и для вертикального скролла (видимый y = pan.y + docY * zoom).
 *
 * ## Sync-совместимость
 *
 * [firstVisiblePageIndex] и [firstVisiblePageOffsetPx] совпадают с
 * семантикой `LazyListState.firstVisibleItemIndex` /
 * `firstVisibleItemScrollOffset`, поэтому существующий протокол
 * `ViewStateSync` работает без изменений.
 */
@Suppress("TooManyFunctions")
class PdfViewerState internal constructor(
    initialZoom: Float = 1f,
    initialPanX: Float = 0f,
    initialPanY: Float = 0f,
    initialPageIndex: Int = 0,
    initialPageOffsetPx: Int = 0,
) {

    /** Текущий зум (1.0 = 100%). Меняется через [zoomTo]. */
    var zoom: Float by mutableFloatStateOf(initialZoom.coerceIn(PdfViewerMath.MIN_ZOOM, PdfViewerMath.MAX_ZOOM))
        private set

    /** Текущий сдвиг документа во вьюпорте. */
    var pan: Offset by mutableStateOf(Offset(initialPanX, initialPanY))
        private set

    /** Размер вьюпорта в физических пикселях. Устанавливается из layout-pass. */
    var viewportSize: IntSize by mutableStateOf(IntSize.Zero)
        internal set

    /** Текущий список страниц документа. Устанавливается извне. */
    var pages: List<PdfPageInfo> by mutableStateOf(emptyList())
        internal set

    /**
     * Ширина "базовой" страничной колонки (= ширина страницы при zoom = 1).
     * Совпадает с прежним `viewportWidth * 2/3`, чтобы при первом открытии
     * (zoom = 1) страница занимала те же 2/3 ширины окна, что и раньше.
     */
    internal val basePageWidthPx: Float
        get() = viewportSize.width * BASE_PAGE_WIDTH_FRACTION

    /** Layout стопки страниц при базовой ширине. */
    internal val layout: PdfPagesLayout by derivedStateOf {
        PdfPagesLayout.build(
            pages = pages,
            basePageWidthPx = basePageWidthPx,
            pageSpacingPx = 0f,
        )
    }

    /**
     * Сохранённые во время первого layout-pass-а начальные параметры
     * скролла: применяются один раз, когда [viewportSize] и [pages] оба
     * становятся валидны. Иначе при перезапуске сессии sync не успевает
     * восстановить позицию.
     */
    private var pendingInitialPage: Int? = initialPageIndex.takeIf { it > 0 || initialPageOffsetPx > 0 }
    private var pendingInitialOffset: Int = initialPageOffsetPx
    private var pendingInitialScalePercent: Int? = null

    internal fun applyPendingInitialScrollIfNeeded() {
        pendingInitialScalePercent?.let { sc ->
            if (viewportSize.width > 0) {
                setScalePercent(sc)
                pendingInitialScalePercent = null
            }
        }
        // При первой готовности viewport + pages: центрируем pan по X в
        // целочисленной арифметике, чтобы левый и правый пиксельные
        // зазоры были равны (с разницей не более 1 px при нечётной
        // суммарной величине). Float-центрирование через ` / 2f` + последующий
        // `roundToInt()` при placement даёт visible смещение ~1 px.
        // pan.y оставляем 0 — показываем top документа.
        if (viewportSize.width > 0 && pages.isNotEmpty() && pan == Offset.Zero) {
            val pageW = (layout.basePageWidthPx * zoom).roundToInt()
            val centerX = (viewportSize.width - pageW) / 2
            pan = Offset(centerX.toFloat(), 0f)
        }
        val page = pendingInitialPage ?: return
        if (viewportSize.width <= 0 || pages.isEmpty()) return
        scrollToPage(page.coerceIn(0, pages.lastIndex), pendingInitialOffset)
        pendingInitialPage = null
        pendingInitialOffset = 0
    }

    /**
     * Откладывает применение зума и позиции до того момента, как
     * viewer измерится (viewportSize > 0) и страницы будут загружены.
     * Используется при восстановлении состояния из аннотационного
     * бандла или sync-сообщения, пришедшего до первого layout.
     */
    fun applyInitialState(scalePercent: Int, pageIndex: Int, pageOffsetPx: Int) {
        if (viewportSize.width > 0 && pages.isNotEmpty()) {
            setScalePercent(scalePercent)
            scrollToPage(pageIndex, pageOffsetPx)
        } else {
            pendingInitialScalePercent = scalePercent
            pendingInitialPage = pageIndex
            pendingInitialOffset = pageOffsetPx
        }
    }

    /** Индекс первой видимой страницы (см. контракт `LazyListState`). */
    val firstVisiblePageIndex: Int by derivedStateOf {
        PdfViewerMath.firstVisiblePageIndex(layout, pan.y, zoom)
    }

    /** Смещение, на которое первая видимая страница ушла за верх вьюпорта (px). */
    val firstVisiblePageOffsetPx: Int by derivedStateOf {
        PdfViewerMath.pageScrollOffsetPx(layout, firstVisiblePageIndex, pan.y, zoom)
    }

    /** Текущий масштаб в процентах ([MIN_SCALE]..[MAX_SCALE]). */
    val scalePercent: Int by derivedStateOf { (zoom * 100f).roundToInt() }

    /**
     * Cursor-anchored zoom: переводит масштаб в [targetZoom], сохраняя точку
     * под [focus] на месте. [focus] — viewport-координаты курсора/центра
     * жеста.
     *
     * НИКАКОГО clamp здесь не делаем: даже мягкое центрирование (когда
     * контент меньше вьюпорта) перетирает только что вычисленный
     * cursor-anchor `pan` — и точка под курсором уплывает каждый тик. Если
     * пользователь зум-аутом увёл документ за край вьюпорта — это его
     * собственное действие; вернуть в центр он может через `resetView()`
     * / `fitToWidth()`. Начальное центрирование при открытии делается
     * отдельно в [applyPendingInitialScrollIfNeeded].
     */
    fun zoomTo(targetZoom: Float, focus: Offset) {
        val (newPan, newZoom) = PdfViewerMath.zoomAroundFocus(
            focus = focus,
            panOld = pan,
            zoomOld = zoom,
            zoomTarget = targetZoom,
        )
        zoom = newZoom
        pan = newPan
    }

    /** Умножает зум на [factor] вокруг [focus]. */
    fun zoomBy(factor: Float, focus: Offset) {
        zoomTo(zoom * factor, focus)
    }

    /** Установка зума в процентах (для toolbar / sync). Якорь — центр вьюпорта. */
    fun setScalePercent(percent: Int) {
        val target = percent / 100f
        val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        zoomTo(target, center)
    }

    /** Сдвигает [pan] на [delta] (viewport-пиксели), с клампом. */
    fun panBy(delta: Offset) {
        pan = clamped(pan + delta)
    }

    /** Прокручивает к началу страницы [pageIndex] + [offsetPx] (см. `scrollToItem`). */
    fun scrollToPage(pageIndex: Int, offsetPx: Int = 0) {
        if (pages.isEmpty()) return
        val idx = pageIndex.coerceIn(0, pages.lastIndex)
        val newPan = PdfViewerMath.panForPageScroll(
            layout = layout,
            pageIndex = idx,
            offsetPx = offsetPx,
            zoom = zoom,
            currentPanX = pan.x,
        )
        pan = clamped(newPan)
    }

    /** Зум "по ширине" — страница занимает всю ширину вьюпорта, верх первой страницы. */
    fun fitToWidth(pageIndex: Int = firstVisiblePageIndex) {
        if (viewportSize.width <= 0 || pages.isEmpty()) return
        val newZoom = PdfViewerMath.fitToWidthZoom(layout, viewportSize.width.toFloat())
        zoom = newZoom
        pan = PdfViewerMath.panForPageTop(layout, pageIndex, newZoom, viewportSize.width.toFloat())
            .let(::clamped)
    }

    /** Зум "по странице" — текущая страница помещается целиком во вьюпорт. */
    fun fitToPage(pageIndex: Int = firstVisiblePageIndex) {
        if (viewportSize.width <= 0 || viewportSize.height <= 0 || pages.isEmpty()) return
        val vp = FloatSize(viewportSize.width.toFloat(), viewportSize.height.toFloat())
        val newZoom = PdfViewerMath.fitToPageZoom(layout, pageIndex, vp)
        zoom = newZoom
        pan = PdfViewerMath.panForPageTop(layout, pageIndex, newZoom, vp.width).let(::clamped)
    }

    /** Сбрасывает на 100% и верх документа. */
    fun resetView() {
        if (viewportSize.width <= 0) return
        zoom = 1f
        pan = PdfViewerMath.panForPageTop(layout, 0, 1f, viewportSize.width.toFloat())
            .let(::clamped)
    }

    private fun clamped(p: Offset): Offset = PdfViewerMath.clampPan(
        pan = p,
        layout = layout,
        zoom = zoom,
        viewportSize = FloatSize(viewportSize.width.toFloat(), viewportSize.height.toFloat()),
    )

    companion object {

        /** Доля ширины окна, занимаемая страничной колонкой при zoom = 1. */
        internal const val BASE_PAGE_WIDTH_FRACTION: Float = 2f / 3f

        /** Saver для [rememberSaveable]: сохраняет zoom + положение скролла. */
        val Saver: Saver<PdfViewerState, Any> = listSaver(
            save = { s: PdfViewerState ->
                listOf(
                    s.zoom.toDouble(),
                    s.firstVisiblePageIndex,
                    s.firstVisiblePageOffsetPx,
                )
            },
            restore = { saved: List<Any?> ->
                PdfViewerState(
                    initialZoom = (saved[0] as Number).toFloat(),
                    initialPageIndex = (saved[1] as Number).toInt(),
                    initialPageOffsetPx = (saved[2] as Number).toInt(),
                )
            },
        )
    }
}

/** Создаёт и запоминает [PdfViewerState] с сохранением между рекомпозициями. */
@Composable
fun rememberPdfViewerState(
    initialZoom: Float = 1f,
    initialPage: Int = 0,
    initialPageOffsetPx: Int = 0,
): PdfViewerState = rememberSaveable(saver = PdfViewerState.Saver) {
    PdfViewerState(
        initialZoom = initialZoom,
        initialPageIndex = initialPage,
        initialPageOffsetPx = initialPageOffsetPx,
    )
}
