package ru.kyamshanov.notepen.magnifier

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import ru.kyamshanov.notepen.pdfviewer.PdfPagesLayout
import ru.kyamshanov.notepen.pdfviewer.SpreadMode

/**
 * Чистые функции преобразования координат между двумя пространствами лупы:
 *
 *  - **page-normalized** `[0..1]×[0..1]` — точка на странице PDF
 *  - **panel-local pixels** `[0..panel.width]×[0..panel.height]` — точка
 *    внутри окна ввода magnifier'а
 *
 * Связь устанавливается через `target: Rect` (page-normalized), который
 * описывает, какую область страницы показывает панель.
 *
 * Вынесено в отдельный файл, чтобы можно было тестировать без Compose UI.
 */

/**
 * Перевод точки из локальных координат панели в page-normalized.
 *
 * @param local координата внутри панели в пикселях (0..panel.width / 0..panel.height)
 * @param panel размер панели в пикселях
 * @param target область страницы, отображаемая в панели (page-normalized)
 */
fun panelLocalToPageNormalized(
    local: Offset,
    panel: Size,
    target: Rect,
): Offset {
    if (panel.width <= 0f || panel.height <= 0f) return Offset.Zero
    val nx = target.left + (local.x / panel.width) * (target.right - target.left)
    val ny = target.top + (local.y / panel.height) * (target.bottom - target.top)
    return Offset(nx, ny)
}

/**
 * Обратное преобразование: page-normalized → локальные координаты панели.
 *
 * Если точка лежит вне `target`, результат тоже окажется вне `[0..panel]` —
 * вызывающий код решает, обрезать ли (стандартный `Modifier.clipToBounds()`
 * на панели). Это даёт корректную отрисовку «выходящих» штрихов без
 * специальной обработки.
 */
fun pageNormalizedToPanelLocal(
    page: Offset,
    panel: Size,
    target: Rect,
): Offset {
    val tw = target.right - target.left
    val th = target.bottom - target.top
    if (tw <= 0f || th <= 0f) return Offset.Zero
    val x = (page.x - target.left) / tw * panel.width
    val y = (page.y - target.top) / th * panel.height
    return Offset(x, y)
}

/**
 * Множитель увеличения: во сколько раз содержимое панели крупнее
 * соответствующей области страницы.
 *
 * Возвращает 0, если данные недостаточны для расчёта.
 */
fun zoomFactor(
    panel: Size,
    target: Rect,
    pageCanvasWidthPx: Float,
): Float {
    val tw = target.right - target.left
    if (tw <= 0f || pageCanvasWidthPx <= 0f || panel.width <= 0f) return 0f
    return panel.width / (tw * pageCanvasWidthPx)
}

/**
 * Обрезает `target` к [0..1] по обеим осям, сохраняя ширину/высоту если
 * это возможно (rect не больше 1×1). Если rect больше — обрезается так,
 * чтобы влезть в [0..1].
 */
fun clampTargetToPage(target: Rect): Rect {
    val w = (target.right - target.left).coerceAtMost(1f).coerceAtLeast(MIN_TARGET_DIM)
    val h = (target.bottom - target.top).coerceAtMost(1f).coerceAtLeast(MIN_TARGET_DIM)
    val left = target.left.coerceIn(0f, 1f - w)
    val top = target.top.coerceIn(0f, 1f - h)
    return Rect(left, top, left + w, top + h)
}

/** Минимальная ширина/высота рамки-цели (доля от страницы) — иначе деление на ~0. */
const val MIN_TARGET_DIM = 0.02f

/**
 * Возвращает индекс страницы, содержащей `docY` (document-space координата
 * по вертикали). При `docY` выше первой страницы — возвращает `0`; при
 * `docY` ниже последней — последний валидный индекс.
 *
 * Использует бинарный поиск по `pageTopsPx`, который монотонно возрастает.
 */
fun resolvePageForDocY(
    pageTopsPx: FloatArray,
    docY: Float,
): Int {
    val n = pageTopsPx.size
    if (n == 0) return 0
    if (docY < pageTopsPx[0]) return 0
    var lo = 0
    var hi = n - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) ushr 1
        if (pageTopsPx[mid] <= docY) lo = mid else hi = mid - 1
    }
    return lo
}

/**
 * Индекс страницы, содержащей точку `(docX, docY)` в document-space, с учётом
 * раскладки разворота ([SpreadMode.SPREAD]).
 *
 * В [SpreadMode.SINGLE] эквивалентно [resolvePageForDocY] — колонка одна. В
 * развороте левая и правая страницы пары делят один Y-ряд (`pageTopsPx`
 * совпадает), поэтому одного `docY` недостаточно: ряд определяется по Y, а
 * левая/правая страница пары — по `docX` относительно границы колонок
 * (середина «корешка» [PdfPagesLayout.SPREAD_GUTTER_PX]). Без этого выбора
 * рамка лупы на правой половине листа маппится в document-X левой половины.
 */
fun resolvePageForDocSpace(
    layout: PdfPagesLayout,
    docX: Float,
    docY: Float,
): Int {
    val idx = resolvePageForDocY(layout.pageTopsPx, docY)
    if (layout.spreadMode != SpreadMode.SPREAD) return idx
    val leftPage = if (idx % 2 == 1) idx - 1 else idx
    val rightPage = leftPage + 1
    val hasRight =
        rightPage < layout.pageTopsPx.size &&
            layout.pageTopsPx[rightPage] == layout.pageTopsPx[leftPage]
    val columnSplit = layout.basePageWidthPx + PdfPagesLayout.SPREAD_GUTTER_PX / 2f
    return if (hasRight && docX >= columnSplit) rightPage else leftPage
}
