package ru.kyamshanov.notepen.magnifier

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * Чистая координатная математика одного [MagnifierPageSegment] лупы.
 *
 * Лупа связывает два пространства:
 *  - **panel-local pixels** — точка внутри content-области окна лупи,
 *    `[0..panel.width] × [0..panel.height]`;
 *  - **page-normalized** `[0..1] × [0..1]` — точка на странице PDF.
 *
 * Сегмент занимает горизонтальную «полосу» панели
 * `panelTopFrac..panelBottomFrac` (в долях `panel.height`) и показывает в ней
 * page-под-прямоугольник [MagnifierPageSegment.targetOnPage].
 *
 * **Инвариант:** для любого размера панели прямой и обратный перевод по Y
 * должны быть точными взаимными обратными — иначе ввод пера «уезжает»
 * относительно отрисованного содержимого (особенно заметно при ресайзе
 * окна, когда `panel.height` меняется). Эти две функции — единственный
 * источник истины этого маппинга; и контроллер ввода
 * ([MagnifierInputController]), и рендер панели обязаны опираться на них.
 *
 * Вынесено отдельно, чтобы покрыть инвариант юнит-тестами без Compose UI.
 */

/**
 * panel-local → page-normalized для сегмента [segment].
 *
 * По X — линейно через [MagnifierPageSegment.targetOnPage]. По Y — panel-y
 * переводится во фракцию панели, затем в локальную фракцию полосы сегмента
 * `panelTopFrac..panelBottomFrac`, затем в page-y внутри `target`.
 *
 * @param panelLocal точка в пикселях content-области панели.
 * @param panel      размер content-области панели в пикселях.
 */
public fun panelLocalToPageInSegment(
    panelLocal: Offset,
    panel: Size,
    segment: MagnifierPageSegment,
): Offset {
    val target = segment.targetOnPage
    val nx =
        if (panel.width > 0f) {
            target.left + (panelLocal.x / panel.width) * (target.right - target.left)
        } else {
            target.left
        }
    val fy =
        if (panel.height > 0f) {
            (panelLocal.y / panel.height).coerceIn(0f, 1f)
        } else {
            0f
        }
    val segFrac = (segment.panelBottomFrac - segment.panelTopFrac).coerceAtLeast(EPS)
    val localY = ((fy - segment.panelTopFrac) / segFrac).coerceIn(0f, 1f)
    val ny = target.top + localY * (target.bottom - target.top)
    return Offset(nx, ny)
}

/**
 * page-normalized → panel-local для сегмента [segment] — точная обратная к
 * [panelLocalToPageInSegment].
 *
 * Это та же формула, что применяет рендер панели при размещении PDF-тайла,
 * завершённых и live-штрихов в полосу сегмента: panel-y страницы =
 * `segTop + (ny - target.top) / th * segH`, где `segTop`/`segH` выражены через
 * `panelTopFrac`/`panelBottomFrac` и `panel.height`.
 *
 * Результат не клампится: точка вне `target`/полосы даёт panel-координату вне
 * `[0..panel]` — вызывающий рендер клипает её `clipRect`/`clipToBounds`.
 *
 * @param page  точка в page-normalized.
 * @param panel размер content-области панели в пикселях.
 */
public fun pageToPanelLocalInSegment(
    page: Offset,
    panel: Size,
    segment: MagnifierPageSegment,
): Offset {
    val target = segment.targetOnPage
    val tw = target.right - target.left
    val th = target.bottom - target.top
    val px =
        if (tw > 0f) (page.x - target.left) / tw * panel.width else 0f
    val segTop = segment.panelTopFrac * panel.height
    val segH = (segment.panelBottomFrac - segment.panelTopFrac) * panel.height
    val py =
        if (th > 0f) segTop + (page.y - target.top) / th * segH else segTop
    return Offset(px, py)
}

/** Защита от деления на ноль для вырожденной полосы сегмента. */
private const val EPS = 1e-6f
