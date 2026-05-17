package ru.kyamshanov.notepen.magnifier

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

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
fun panelLocalToPageNormalized(local: Offset, panel: Size, target: Rect): Offset {
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
fun pageNormalizedToPanelLocal(page: Offset, panel: Size, target: Rect): Offset {
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
fun zoomFactor(panel: Size, target: Rect, pageCanvasWidthPx: Float): Float {
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
