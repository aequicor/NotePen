package ru.kyamshanov.notepen

import androidx.compose.ui.geometry.Offset

/**
 * Геометрия раскладки страниц PDF-вьюера, нужная драйверу рисования для
 * перевода viewport-координат в `(pageIndex, nx, ny)`.
 *
 * Абстрагирует `PdfViewerState`/`PdfPagesLayout` из модуля рендеринга, чтобы
 * [MultiPageDrawingController] не зависел от рендеринга. Реализация —
 * адаптер поверх `PdfViewerState` в `:rendering:impl`. Все значения читаются
 * «вживую» на момент вызова (pan/zoom меняются каждый кадр).
 */
interface PageLayoutGeometry {
    /** Количество страниц в раскладке. */
    val pageCount: Int

    /** PDF-ширина колонки страниц при `zoom = 1` (px). */
    val basePageWidthPx: Float

    /** Текущий зум (1.0 = 100%). */
    val zoom: Float

    /** Текущий сдвиг документа во вьюпорте (viewport-пиксели). */
    val pan: Offset

    /** Верх страницы [index] в document space (px). */
    fun pageTopPx(index: Int): Float

    /** Высота PDF-страницы [index] без extent (px). */
    fun pdfHeightPx(index: Int): Float
}
