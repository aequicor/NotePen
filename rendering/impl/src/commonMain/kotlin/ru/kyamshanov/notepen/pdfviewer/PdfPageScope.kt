package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent

/**
 * Скоп, в котором живёт композбл одной страницы PDF внутри
 * [PdfPagesViewer]: даёт текущий битмап и визуальные размеры
 * целевой страницы.
 *
 * @property pageIndex нулевой индекс страницы в документе
 * @property bitmap последний доступный битмап (или `null` до первого
 *   рендера); может быть отрисован при другом масштабе — он будет
 *   растянут в `Modifier.size(pdfWidth, pdfHeight)` пока
 *   фоновый рендер на текущий масштаб не завершится
 * @property visualWidth ширина **слота** страницы (Dp) — включает
 *   расширенную рисуемую область за пределами PDF
 * @property visualHeight высота **слота** страницы (Dp)
 * @property pdfWidth ширина PDF-битмапа внутри слота (Dp) — соответствует
 *   `basePageWidthPx * zoom` в [PdfPagesLayout]
 * @property pdfHeight высота PDF-битмапа внутри слота (Dp)
 * @property extent рисуемая область страницы в PDF-page координатах
 *   (см. [PageExtent]). `Pdf` означает, что слот равен PDF-странице.
 */
interface PdfPageScope {
    val pageIndex: Int
    val bitmap: ImageBitmap?
    val visualWidth: Dp
    val visualHeight: Dp
    val pdfWidth: Dp
    val pdfHeight: Dp
    val extent: PageExtent
}

/** Лямбда содержимого страницы — обычно содержит [DrawablePdfPage]. */
typealias PdfPageContent = @Composable PdfPageScope.() -> Unit
