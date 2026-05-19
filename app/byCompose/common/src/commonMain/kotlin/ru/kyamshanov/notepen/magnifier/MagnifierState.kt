package ru.kyamshanov.notepen.magnifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Состояние инструмента «лупа для письма».
 *
 * Лупа состоит из двух связанных UI-частей:
 *  - **target frame** — небольшая прямоугольная рамка на странице,
 *    обозначающая, какая часть страницы попадает под увеличение;
 *  - **input panel** — плавающее окно поверх UI, в котором содержимое
 *    `targetRect` отображается крупно и в которое пользователь пишет пером.
 *
 * Все штрихи коммитятся напрямую в `PdfDrawingState` той страницы, на
 * которой находится рамка — magnifier лишь маппит координаты pointer-входа
 * из панели в page-space. Это сохраняет одну точку истины для штрихов и
 * корректно работает с существующим undo/redo, sync и сохранением.
 *
 * Поведение пропорций: `panelSize` и `targetRect` (в page-pixels) должны
 * иметь одинаковый aspect-ratio — иначе содержимое искажалось бы при
 * масштабировании. Любое изменение одного из размеров перерасчитывает
 * другой так, чтобы выровнять пропорции (см. [resizePanel] / [resizeTarget]).
 */
class MagnifierState {

    /** Включён ли инструмент. */
    var enabled: Boolean by mutableStateOf(false)
        private set

    /**
     * Сегменты выделения по страницам. Один элемент для однополосного
     * выделения; несколько — когда диагональ пересекла границы страниц.
     * Заполняется через [enable].
     */
    var segments: List<MagnifierPageSegment> by mutableStateOf(emptyList())
        private set

    /**
     * Индекс первой страницы в выделении — для обратной совместимости с
     * кодом, который ожидает одну страницу (toolbar-кнопка, undo/redo).
     */
    val pageIndex: Int
        get() = segments.firstOrNull()?.pageIndex ?: 0

    /**
     * Область первой страницы выделения. Эквивалент старого `targetRect` для
     * single-page; для multi-page здесь bounding-rect первого сегмента.
     */
    val targetRect: Rect
        get() = segments.firstOrNull()?.targetOnPage ?: DEFAULT_TARGET

    /**
     * Положение левого-верхнего угла плавающей панели, в пикселях вьюпорта.
     * Двигается через [movePanel].
     */
    var panelTopLeft: Offset by mutableStateOf(Offset.Zero)
        private set

    /** Размер плавающей панели в пикселях. */
    var panelSize: Size by mutableStateOf(DEFAULT_PANEL_SIZE)
        private set

    /**
     * Размер canvas страницы (в пикселях вьюпорта) для активной страницы.
     * Записывается из `DrawablePdfPage` через [updatePageCanvasPx]. Нужен,
     * чтобы рассчитать `normalizedStrokeWidth = penSettings.strokeWidth /
     * pageCanvasPx.width` так же, как это делает обычный pen-pipeline.
     */
    var pageCanvasWidthPx: Float by mutableStateOf(0f)
        private set

    /**
     * Битмапы страниц по индексу — для каждой задетой страницы лупой.
     * Подаются из `pageContent` в `DetailsContent` через [updatePageBitmap].
     */
    private val pageBitmapsState = mutableStateMapOf<Int, ImageBitmap>()

    /** Битмап страницы по индексу или `null`, если ещё не подан. */
    fun pageBitmap(pageIndex: Int): ImageBitmap? = pageBitmapsState[pageIndex]

    /** Битмап первой страницы — для обратной совместимости. */
    val pageBitmap: ImageBitmap?
        get() = pageBitmapsState[pageIndex]

    /** Включена ли авто-прокрутка рамки при подходе пера к правому краю панели. */
    var autoScrollEnabled: Boolean by mutableStateOf(true)
        private set

    /**
     * Фактический прямоугольник content-области панели в viewport-координатах
     * (без titlebar и resize-handle). Обновляется самой панелью через
     * [updateContentBounds] при компоновке — нужен внешнему вводу пера
     * (нативный WM_POINTER bypass'ит Compose pointerInput) для маршрутизации.
     */
    var contentBoundsInViewport: Rect by mutableStateOf(Rect.Zero)
        private set

    /** Обновляет [contentBoundsInViewport]; вызывается из `MagnifierInputPanel`. */
    fun updateContentBounds(bounds: Rect) {
        contentBoundsInViewport = bounds
    }

    // --- mutators -----------------------------------------------------------

    /**
     * Включить лупу на странице [onPage], разместив рамку и панель по
     * умолчанию. [viewportSize] нужен, чтобы припарковать панель внизу
     * экрана.
     */
    fun enable(onPage: Int, viewportSize: Size) {
        segments = listOf(
            MagnifierPageSegment(
                pageIndex = onPage,
                targetOnPage = DEFAULT_TARGET,
                panelTopFrac = 0f,
                panelBottomFrac = 1f,
            ),
        )
        val panelW = (viewportSize.width * 0.6f).coerceAtLeast(MIN_PANEL_DIM_PX)
        val panelH = panelW * DEFAULT_TARGET.height / DEFAULT_TARGET.width
        panelSize = Size(panelW, panelH)
        panelTopLeft = Offset(
            x = (viewportSize.width - panelW) * 0.5f,
            y = (viewportSize.height - panelH - PANEL_BOTTOM_MARGIN_PX).coerceAtLeast(0f),
        )
        enabled = true
    }

    /**
     * Включить лупу с явно заданной рамкой-целью и центром панели.
     *
     * Размер панели рассчитывается так, чтобы дать комфортное письмо:
     *  1. Базовый zoom — [DEFAULT_LOUPE_ZOOM] (3×). Это общепринятый
     *     handwriting-zoom в GoodNotes / Notability / OneNote zoom-box —
     *     достаточно для мелких пометок, не слишком велик, чтобы рука не
     *     уставала тянуться через всё окно.
     *  2. Сырой размер = размер выделения в пикселях × zoom.
     *  3. Ограничение сверху: панель не больше [MAX_PANEL_W_FRAC] ширины
     *     и [MAX_PANEL_H_FRAC] высоты viewport'а — иначе писать неудобно.
     *     Если сырой размер выходит за лимит, downscale'им с сохранением
     *     aspect-ratio (эффективный zoom тогда меньше 3×).
     *  4. Минимум: [MIN_USABLE_PANEL_W_PX] × [MIN_USABLE_PANEL_H_PX], чтобы
     *     даже крошечное выделение давало читаемое окно.
     *
     * @param target          выделенная область, page-normalized [0..1].
     * @param selectionSizePx размер выделения в **пикселях viewport'а** —
     *                        нужен для расчёта корректного zoom.
     * @param panelCenter     центр панели в координатах viewport'а
     *                        (обычно — точка отпускания пера).
     */
    fun enable(
        onPage: Int,
        viewportSize: Size,
        target: Rect,
        selectionSizePx: Size,
        panelCenter: Offset,
    ) {
        val safeTarget = clampTargetToPage(target)
        enableMulti(
            viewportSize = viewportSize,
            segs = listOf(
                MagnifierPageSegment(
                    pageIndex = onPage,
                    targetOnPage = safeTarget,
                    panelTopFrac = 0f,
                    panelBottomFrac = 1f,
                ),
            ),
            selectionSizePx = selectionSizePx,
            panelCenter = panelCenter,
        )
    }

    /**
     * Включить лупу для **мульти-страничного** выделения: список сегментов
     * (для каждой задетой страницы свой [MagnifierPageSegment]). Размер
     * панели — общий, по [selectionSizePx]; контент рендерится «полосами»
     * сверху-вниз.
     */
    fun enableMulti(
        viewportSize: Size,
        segs: List<MagnifierPageSegment>,
        selectionSizePx: Size,
        panelCenter: Offset,
    ) {
        require(segs.isNotEmpty()) { "Magnifier needs at least one segment" }
        segments = segs

        val selW = selectionSizePx.width.coerceAtLeast(1f)
        val selH = selectionSizePx.height.coerceAtLeast(1f)
        val rawW = selW * DEFAULT_LOUPE_ZOOM
        val rawH = selH * DEFAULT_LOUPE_ZOOM

        val maxW = viewportSize.width * MAX_PANEL_W_FRAC
        val maxH = viewportSize.height * MAX_PANEL_H_FRAC
        val sMax = minOf(maxW / rawW, maxH / rawH, 1f)
        val sMin = maxOf(MIN_USABLE_PANEL_W_PX / rawW, MIN_USABLE_PANEL_H_PX / rawH)
        val scale = if (sMin <= sMax) 1f.coerceIn(sMin, sMax) else sMin
        val panelW = rawW * scale
        val panelH = rawH * scale
        panelSize = Size(panelW, panelH)

        val rawLeft = panelCenter.x - panelW / 2f
        val rawTop = panelCenter.y - panelH / 2f
        val maxLeft = (viewportSize.width - panelW).coerceAtLeast(0f)
        val maxTop = (viewportSize.height - panelH).coerceAtLeast(0f)
        panelTopLeft = Offset(
            x = rawLeft.coerceIn(0f, maxLeft),
            y = rawTop.coerceIn(0f, maxTop),
        )
        enabled = true
    }

    /** Выключить лупу. */
    fun disable() {
        enabled = false
        pageBitmapsState.clear()
        segments = emptyList()
    }

    fun toggleAutoScroll() {
        autoScrollEnabled = !autoScrollEnabled
    }

    /**
     * Сдвинуть рамку-цель на [deltaPageSpace] (page-normalized). Размер
     * сохраняется; результат клампится к `[0..1]`. Для multi-page —
     * no-op: в мульти-страничном выделении рамка распределена по
     * нескольким страницам, и интерактивный move/resize в этой версии
     * не поддерживается (пользователь может закрыть и пере-выделить).
     */
    fun moveTarget(deltaPageSpace: Offset) {
        if (segments.size != 1) return
        val s = segments[0]
        val r = s.targetOnPage
        val moved = clampTargetToPage(
            Rect(
                left = r.left + deltaPageSpace.x,
                top = r.top + deltaPageSpace.y,
                right = r.right + deltaPageSpace.x,
                bottom = r.bottom + deltaPageSpace.y,
            ),
        )
        segments = listOf(s.copy(targetOnPage = moved))
    }

    /**
     * Изменить размер рамки. Доступно только для single-page; для multi-page
     * — no-op (см. [moveTarget]).
     */
    fun resizeTarget(newWidth: Float, newHeight: Float) {
        if (segments.size != 1) return
        val s = segments[0]
        val r = s.targetOnPage
        val clamped = clampTargetToPage(
            Rect(
                left = r.left,
                top = r.top,
                right = r.left + newWidth,
                bottom = r.top + newHeight,
            ),
        )
        segments = listOf(s.copy(targetOnPage = clamped))
        alignPanelAspectToTarget()
    }

    /** Сдвинуть плавающую панель на [delta] (viewport-пиксели). */
    fun movePanel(delta: Offset) {
        panelTopLeft = panelTopLeft + delta
    }

    /**
     * Изменить размер панели. Пропорции рамки-цели подстраиваются под
     * новый aspect панели (рамка не пересчитывает свою левую-верхнюю
     * точку; меняется только высота).
     */
    fun resizePanel(newSize: Size) {
        val w = newSize.width.coerceAtLeast(MIN_PANEL_DIM_PX)
        val h = newSize.height.coerceAtLeast(MIN_PANEL_DIM_PX)
        panelSize = Size(w, h)
        alignTargetAspectToPanel()
    }

    /**
     * Обновить размер canvas страницы. Вызывается из `DrawablePdfPage`
     * при изменении его размера, пока magnifier активен на этой странице.
     */
    fun updatePageCanvasPx(widthPx: Float) {
        pageCanvasWidthPx = widthPx
    }

    /** Обновить ссылку на битмап страницы [pageIndex]. */
    fun updatePageBitmap(pageIndex: Int, bitmap: ImageBitmap?) {
        if (bitmap == null) pageBitmapsState.remove(pageIndex)
        else pageBitmapsState[pageIndex] = bitmap
    }

    /**
     * Сдвиг рамки после завершения штриха в указанном направлении
     * (Scribble-like). См. [AutoScrollDir].
     *
     * Возвращает `true`, если сдвиг был выполнен (т.е. рамка не упёрлась
     * в нижний край страницы).
     */
    fun shiftTargetForAutoscroll(direction: AutoScrollDir): Boolean {
        if (segments.size != 1) return false
        val s = segments[0]
        val r = s.targetOnPage
        val w = r.right - r.left
        val h = r.bottom - r.top
        return when (direction) {
            AutoScrollDir.RIGHT -> {
                val shifted = r.left + w * AUTO_SCROLL_ADVANCE
                if (shifted + w <= 1f - EDGE_EPS) {
                    segments = listOf(
                        s.copy(targetOnPage = Rect(shifted, r.top, shifted + w, r.bottom)),
                    )
                    true
                } else {
                    // Перевод строки.
                    val newTop = r.top + h * AUTO_SCROLL_LINE_FEED
                    if (newTop + h > 1f) return false
                    segments = listOf(
                        s.copy(
                            targetOnPage = Rect(
                                LINE_LEFT_MARGIN, newTop,
                                LINE_LEFT_MARGIN + w, newTop + h,
                            ),
                        ),
                    )
                    true
                }
            }
        }
    }

    // --- private helpers ----------------------------------------------------

    private fun alignPanelAspectToTarget() {
        if (segments.size != 1) return
        val r = segments[0].targetOnPage
        val tw = r.right - r.left
        val th = r.bottom - r.top
        if (tw <= 0f || th <= 0f) return
        val newPanelH = panelSize.width * th / tw
        panelSize = Size(panelSize.width, newPanelH.coerceAtLeast(MIN_PANEL_DIM_PX))
    }

    private fun alignTargetAspectToPanel() {
        if (segments.size != 1) return
        val s = segments[0]
        val r = s.targetOnPage
        val tw = r.right - r.left
        if (tw <= 0f || panelSize.width <= 0f) return
        val targetH = tw * panelSize.height / panelSize.width
        val newRect = clampTargetToPage(
            Rect(r.left, r.top, r.right, r.top + targetH),
        )
        segments = listOf(s.copy(targetOnPage = newRect))
    }

    companion object {
        /**
         * Базовый коэффициент увеличения области в окне лупы. 3× — общепринятый
         * handwriting-zoom (GoodNotes / Notability / OneNote zoom-box).
         */
        const val DEFAULT_LOUPE_ZOOM: Float = 3f

        /** Максимальная доля viewport-ширины, которую может занимать панель. */
        const val MAX_PANEL_W_FRAC: Float = 0.6f

        /** Максимальная доля viewport-высоты для панели. */
        const val MAX_PANEL_H_FRAC: Float = 0.5f

        /** Минимальная ширина окна лупы, чтобы текст оставался читаемым. */
        const val MIN_USABLE_PANEL_W_PX: Float = 360f

        /** Минимальная высота окна лупы. */
        const val MIN_USABLE_PANEL_H_PX: Float = 120f

        internal val DEFAULT_TARGET = Rect(0.4f, 0.4f, 0.6f, 0.45f)
        internal val DEFAULT_PANEL_SIZE = Size(800f, 200f)
        internal const val MIN_PANEL_DIM_PX = 120f
        internal const val PANEL_BOTTOM_MARGIN_PX = 24f

        /** Доля ширины рамки, на которую сдвигаемся вправо (85% — остаётся 15% перехлёста). */
        internal const val AUTO_SCROLL_ADVANCE = 0.85f

        /** Доля высоты рамки, на которую опускаемся при переводе строки. */
        internal const val AUTO_SCROLL_LINE_FEED = 0.85f

        /** Левый отступ строки при wrap'е. */
        internal const val LINE_LEFT_MARGIN = 0.05f

        /** Эпсилон для предотвращения дребезга на самой границе. */
        internal const val EDGE_EPS = 1e-4f
    }
}

/** Направление авто-прокрутки рамки при подходе пера к краю панели. */
enum class AutoScrollDir { RIGHT }
