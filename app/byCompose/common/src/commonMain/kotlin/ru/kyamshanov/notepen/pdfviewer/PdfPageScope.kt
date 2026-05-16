package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp

/**
 * Скоп, в котором живёт композбл одной страницы PDF внутри
 * [PdfDesktopPagesViewer]: даёт текущий битмап и визуальные размеры
 * целевой страницы.
 *
 * @property pageIndex нулевой индекс страницы в документе
 * @property bitmap последний доступный битмап (или `null` до первого
 *   рендера); может быть отрисован при другом масштабе — он будет
 *   растянут в `Modifier.size(visualWidth, visualHeight)` пока
 *   фоновый рендер на текущий масштаб не завершится
 * @property visualWidth текущая визуальная ширина страницы (Dp)
 * @property visualHeight текущая визуальная высота страницы (Dp)
 */
interface PdfPageScope {
    val pageIndex: Int
    val bitmap: ImageBitmap?
    val visualWidth: Dp
    val visualHeight: Dp
}

/** Лямбда содержимого страницы — обычно содержит [DrawablePdfPage]. */
typealias PdfPageContent = @Composable PdfPageScope.() -> Unit
