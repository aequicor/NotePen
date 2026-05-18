package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer

/**
 * Унифицированный PDF-viewer с разными жестами и моделью скролла на каждой
 * платформе. Содержимое одной страницы рендерит [pageContent], которому
 * через [PdfPageScope] прокидывается текущий битмап страницы и её
 * визуальный размер.
 *
 * Платформенные реализации (в порядке отделённости от общего кода):
 *
 * - **Desktop (JVM)** — [SubcomposeLayout]-based; жесты: Ctrl+Wheel zoom
 *   вокруг курсора, Shift+Wheel — горизонтальный pan, средняя кнопка —
 *   pan, обычное Wheel — вертикальный скролл. См. `PdfPagesViewer.jvm.kt`.
 * - **Android** — единственный `zoom: Float` (без split-scale + bake);
 *   битмап перерисовывается на текущем масштабе через общий
 *   [PdfBitmapCache]. Жесты: два-пальцевый pinch с anchor в centroid,
 *   single-finger drag — нативный fling-скролл по вертикали. См.
 *   `PdfPagesViewer.android.kt`.
 *
 * Общая часть: чистая математика ([PdfPagesLayout], [PdfViewerMath]) и
 * структура кэша ([PdfBitmapCache], [RenderedPage]).
 */
@Composable
expect fun PdfPagesViewer(
    state: PdfViewerState,
    pdfDocument: PdfDocument?,
    pages: List<PdfPageInfo>,
    renderer: PdfPageRenderer,
    modifier: Modifier = Modifier,
    pageContent: PdfPageContent,
)

/**
 * Состояние PDF-вьювера — единственный источник правды по позиции и зуму.
 * Платформенные реализации могут отличаться внутренним устройством
 * (desktop держит `pan: Offset` напрямую; Android делегирует Y-скролл во
 * встроенный `LazyListState` для нативного fling'а), но публичный
 * контракт ниже одинаков и используется одинаково в [DetailsContent].
 *
 * Семантика [firstVisiblePageIndex] / [firstVisiblePageOffsetPx]
 * совпадает с `LazyListState.firstVisibleItemIndex` /
 * `firstVisibleItemScrollOffset`.
 */
expect class PdfViewerState {

    /** Индекс первой видимой страницы. */
    val firstVisiblePageIndex: Int

    /** Смещение, на которое первая видимая страница ушла за верх вьюпорта (px). */
    val firstVisiblePageOffsetPx: Int

    /** Текущий масштаб в процентах (MIN_SCALE..MAX_SCALE). */
    val scalePercent: Int

    /**
     * Откладывает применение зума и позиции до того момента, как viewer
     * измерится (viewportSize > 0) и страницы будут загружены.
     * Используется при восстановлении состояния из аннотационного бандла
     * или sync-сообщения, пришедшего до первого layout.
     */
    fun applyInitialState(scalePercent: Int, pageIndex: Int, pageOffsetPx: Int)

    /**
     * Прокручивает к началу страницы [pageIndex] + [offsetPx]
     * (эквивалент `LazyListState.scrollToItem`).
     */
    fun scrollToPage(pageIndex: Int, offsetPx: Int = 0)

    /** Установка зума в процентах (для toolbar / sync). Якорь — центр вьюпорта. */
    fun setScalePercent(percent: Int)

    /** Умножает зум на [factor] вокруг [focus] (viewport-координаты). */
    fun zoomBy(factor: Float, focus: Offset)

    /**
     * Source of truth по [PageExtent] для страницы. Читается viewer'ом при
     * построении layout, поэтому изменение underlying state расширения
     * (`PdfDrawingState.extent`) автоматически триггерит relayout — нет
     * необходимости явно дёргать какой-либо `invalidate()`. По умолчанию
     * возвращает [PageExtent.Pdf].
     */
    var pageExtentProvider: (Int) -> PageExtent

    /** Текущий сдвиг документа во вьюпорте (viewport-пиксели). */
    var pan: Offset
        private set

    /** Текущий зум (1.0 = 100%). */
    var zoom: Float
        private set

    /** PDF-ширина колонки страниц при `zoom = 1`. */
    internal val basePageWidthPx: Float

    /** Композиционный layout страниц в document space. */
    internal val layout: PdfPagesLayout
}

/** Создаёт и запоминает [PdfViewerState] с сохранением между рекомпозициями. */
@Composable
expect fun rememberPdfViewerState(
    initialZoom: Float = 1f,
    initialPage: Int = 0,
    initialPageOffsetPx: Int = 0,
): PdfViewerState
