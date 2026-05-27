package ru.kyamshanov.notepen.magnifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import ru.kyamshanov.notepen.pdfviewer.PdfViewerState

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

    /** Высота PDF-области страницы в пикселях viewport'а; см. [pageCanvasWidthPx]. */
    var pageCanvasHeightPx: Float by mutableStateOf(0f)
        private set

    /**
     * Aspect-ratio активной страницы (`pageCanvasWidthPx / pageCanvasHeightPx`).
     * Используется alignment-функциями: target-rect нормализован к [0..1] от
     * PDF, поэтому page-normalized aspect != page-pixel aspect, и при
     * ресайзе панели/рамки aspect-факторы должны учитываться явно.
     */
    private val pageAspect: Float
        get() = if (pageCanvasHeightPx > 0f) pageCanvasWidthPx / pageCanvasHeightPx else 1f

    /**
     * Битмапы страниц по индексу — для каждой задетой страницы лупой.
     * Подаются из `pageContent` в `DetailsContent` через [updatePageBitmap].
     */
    private val pageBitmapsState = mutableStateMapOf<Int, ImageBitmap>()

    /**
     * Высокоразрешённые битмапы для активных страниц лупы. Рендерятся
     * отдельно от viewer'овских (которые имеют разрешение под текущий
     * zoom) и используются панелью лупы как источник — даёт чёткое
     * изображение при сильном увеличении.
     */
    private val highResBitmapsState = mutableStateMapOf<Int, ImageBitmap>()

    /** Битмап страницы по индексу — high-res имеет приоритет над viewer-битмапом. */
    fun pageBitmap(pageIndex: Int): ImageBitmap? = highResBitmapsState[pageIndex] ?: pageBitmapsState[pageIndex]

    /** Низкоразрешённый viewer-битмап (без приоритета high-res); для рендера на странице. */
    fun viewerPageBitmap(pageIndex: Int): ImageBitmap? = pageBitmapsState[pageIndex]

    /** Битмап первой страницы — для обратной совместимости. */
    val pageBitmap: ImageBitmap?
        get() = pageBitmap(pageIndex)

    /** Обновить high-res битмап страницы [pageIndex]. */
    fun updateHighResBitmap(
        pageIndex: Int,
        bitmap: ImageBitmap?,
    ) {
        if (bitmap == null) {
            highResBitmapsState.remove(pageIndex)
        } else {
            highResBitmapsState[pageIndex] = bitmap
        }
    }

    /** Включена ли авто-прокрутка рамки при подходе пера к правому краю панели. */
    var autoScrollEnabled: Boolean by mutableStateOf(false)
        private set

    /**
     * Способ привязки target rect лупы:
     *  - [MagnifierAttachment.PAGE] (по умолчанию) — рамка хранится в
     *    page-normalized координатах и визуально движется вместе со
     *    страницей при скролле/зуме;
     *  - [MagnifierAttachment.SCREEN] — рамка визуально статична во
     *    viewport: при скролле/зуме контент под ней меняется, а
     *    `targetOnPage`/`pageIndex` пересчитываются внешним эффектом через
     *    [repinFromViewportRect].
     *
     * GRAB-состояние (пользователь зажимает рамку и тянет/скроллит) —
     * transient; представлено `MagnifierTargetGestureController.isActive`
     * и не хранится в [attachment].
     */
    var attachment: MagnifierAttachment by mutableStateOf(MagnifierAttachment.PAGE)
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
     * Включить лупу из тулбара. Окно — квадрат ~1/3 ширины viewport, открывается
     * слева, но правее тулрейла (см. [startInsetPx]), по центру по вертикали.
     * Рамка — посередине [targetCenterOnPage] (обычно центр видимого viewport'а).
     * Aspect рамки подобран так, чтобы её page-pixel размер совпал с aspect панели.
     *
     * @param startInsetPx левый инсет (px viewport'а), занятый тулрейлом/сайдбаром
     *   (`PdfViewerState.fitWidthInsetStartPx`) — панель открывается правее него;
     *   `0` → дефолтный левый отступ [PANEL_BOTTOM_MARGIN_PX].
     */
    fun enable(
        onPage: Int,
        viewportSize: Size,
        targetCenterOnPage: Offset = Offset(0.5f, 0.5f),
        startInsetPx: Float = 0f,
    ) {
        // Квадратное окно лупы — комфортнее для письма, и совпадает с
        // квадратной (в page-pixels) дефолтной рамкой.
        val panelSide =
            minOf(viewportSize.width * 0.3f, viewportSize.height * 0.5f)
                .coerceAtLeast(MIN_USABLE_PANEL_W_PX)
        panelSize = Size(panelSide, panelSide)
        // Правее тулрейла (startInsetPx уже включает его ширину + зазор; при 0 —
        // дефолтный отступ), по центру по вертикали. Клампим, чтобы панель не
        // уехала за правый край при широком инсете.
        val leftX =
            maxOf(startInsetPx, PANEL_BOTTOM_MARGIN_PX)
                .coerceAtMost((viewportSize.width - panelSide).coerceAtLeast(0f))
        panelTopLeft =
            Offset(
                x = leftX,
                y = ((viewportSize.height - panelSide) / 2f).coerceAtLeast(0f),
            )

        // target.W/target.H × pageAspect = panel.W/panel.H
        // → target.H = target.W × pageAspect × panel.H/panel.W
        // Для квадратной панели panel.H/panel.W = 1, поэтому target.H = target.W × pageAspect.
        // В page-pixels это даёт квадратную рамку (target.W × basePageW = target.H × pdfH).
        val targetWidthN = TOOLBAR_DEFAULT_TARGET_WIDTH_FRAC
        val targetHeightN =
            (targetWidthN * pageAspect * panelSide / panelSide)
                .coerceIn(MIN_TARGET_DIM, 1f)
        val safeWidthN = targetWidthN.coerceIn(MIN_TARGET_DIM, 1f)
        val left = (targetCenterOnPage.x - safeWidthN / 2f).coerceIn(0f, 1f - safeWidthN)
        val top = (targetCenterOnPage.y - targetHeightN / 2f).coerceIn(0f, 1f - targetHeightN)

        segments =
            listOf(
                MagnifierPageSegment(
                    pageIndex = onPage,
                    targetOnPage = Rect(left, top, left + safeWidthN, top + targetHeightN),
                    panelTopFrac = 0f,
                    panelBottomFrac = 1f,
                ),
            )
        enabled = true
    }

    /**
     * Перепривязать single-page рамку к (возможно другой) странице с новым
     * прямоугольником. Используется при cross-page move рамки.
     */
    fun setSingleSegmentTarget(
        pageIndex: Int,
        targetOnPage: Rect,
    ) {
        if (segments.size != 1) return
        segments = listOf(segments[0].copy(pageIndex = pageIndex, targetOnPage = targetOnPage))
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
            segs =
                listOf(
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
        panelTopLeft =
            Offset(
                x = rawLeft.coerceIn(0f, maxLeft),
                y = rawTop.coerceIn(0f, maxTop),
            )
        enabled = true
    }

    /** Выключить лупу. */
    fun disable() {
        enabled = false
        attachment = MagnifierAttachment.PAGE
        pageBitmapsState.clear()
        highResBitmapsState.clear()
        segments = emptyList()
    }

    fun toggleAutoScroll() {
        autoScrollEnabled = !autoScrollEnabled
    }

    /** Переключить тип привязки рамки PAGE ↔ SCREEN. */
    fun toggleAttachment() {
        attachment =
            when (attachment) {
                MagnifierAttachment.PAGE -> MagnifierAttachment.SCREEN
                MagnifierAttachment.SCREEN -> MagnifierAttachment.PAGE
            }
    }

    /**
     * Пересчитывает `targetOnPage` (и при необходимости `pageIndex`) первого
     * сегмента так, чтобы рамка в viewport-координатах совпала с
     * [viewportRect]. Используется при включённом [MagnifierAttachment.SCREEN]
     * для удержания рамки на одном месте на экране при изменении `pan`/`zoom`.
     *
     * Multi-page — no-op.
     */
    fun repinFromViewportRect(
        viewportRect: Rect,
        viewerState: PdfViewerState,
    ) {
        if (segments.size != 1) return
        val layout = viewerState.layout
        val zoom = viewerState.zoom
        val basePageW = layout.basePageWidthPx
        if (zoom <= 0f || basePageW <= 0f) return
        val pan = viewerState.pan
        val docLeft = (viewportRect.left - pan.x) / zoom
        val docTop = (viewportRect.top - pan.y) / zoom
        val docRight = (viewportRect.right - pan.x) / zoom
        val docBottom = (viewportRect.bottom - pan.y) / zoom
        val pageIdx =
            resolvePageForDocY(layout.pageTopsPx, docTop)
                .coerceIn(0, layout.pdfHeightsPx.size - 1)
        val pageTop = layout.pageTopsPx[pageIdx]
        val pdfH = layout.pdfHeightsPx[pageIdx]
        if (pdfH <= 0f) return
        val leftN = docLeft / basePageW
        val rightN = docRight / basePageW
        val topN = (docTop - pageTop) / pdfH
        val bottomN = (docBottom - pageTop) / pdfH
        val clamped = clampTargetToPage(Rect(leftN, topN, rightN, bottomN))
        setSingleSegmentTarget(pageIndex = pageIdx, targetOnPage = clamped)
    }

    /**
     * Возвращает текущий viewport-прямоугольник target rect первого сегмента
     * (для запоминания pinned-позиции). `null` если данных layout'а не хватает
     * или multi-page.
     */
    fun targetRectInViewport(viewerState: PdfViewerState): Rect? {
        if (segments.size != 1) return null
        val seg = segments[0]
        val layout = viewerState.layout
        val pi = seg.pageIndex
        if (pi !in 0 until layout.pageHeightsPx.size) return null
        val zoom = viewerState.zoom
        if (zoom <= 0f) return null
        val basePageW = layout.basePageWidthPx
        val pdfH = layout.pdfHeightsPx[pi]
        val pageTop = layout.pageTopsPx[pi]
        val pan = viewerState.pan
        val t = seg.targetOnPage
        return Rect(
            left = pan.x + t.left * basePageW * zoom,
            top = pan.y + (pageTop + t.top * pdfH) * zoom,
            right = pan.x + t.right * basePageW * zoom,
            bottom = pan.y + (pageTop + t.bottom * pdfH) * zoom,
        )
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
        val moved =
            clampTargetToPage(
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
    fun resizeTarget(
        newWidth: Float,
        newHeight: Float,
    ) {
        if (segments.size != 1) return
        val s = segments[0]
        val r = s.targetOnPage
        val clamped =
            clampTargetToPage(
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

    /**
     * Масштабирует `targetOnPage` вокруг фокусной точки [focusPanelLocal]
     * (в локальных координатах content-области панели). [scaleFactor] > 1
     * → меньше rect → выше зум (pinch-out). Page-normalized координата под
     * фокусом сохраняется. Single-page only; multi-page — no-op.
     *
     * Aspect-ratio после масштабирования выравнивается под текущий
     * `panelSize` через [alignTargetAspectToPanel] — pinch управляет
     * шириной, высота подстраивается.
     */
    fun zoomTargetAroundPanelFocus(
        scaleFactor: Float,
        focusPanelLocal: Offset,
        panelSize: Size,
    ) {
        if (segments.size != 1) return
        if (scaleFactor <= 0f || !scaleFactor.isFinite()) return
        if (panelSize.width <= 0f || panelSize.height <= 0f) return
        val s = segments[0]
        val r = s.targetOnPage
        val tw = r.right - r.left
        val th = r.bottom - r.top
        if (tw <= 0f || th <= 0f) return

        val fx = (focusPanelLocal.x / panelSize.width).coerceIn(0f, 1f)
        val fy = (focusPanelLocal.y / panelSize.height).coerceIn(0f, 1f)
        val anchorX = r.left + fx * tw
        val anchorY = r.top + fy * th

        val newW = (tw / scaleFactor).coerceIn(MIN_TARGET_DIM, 1f)
        val newH = (th / scaleFactor).coerceIn(MIN_TARGET_DIM, 1f)
        val newLeft = anchorX - fx * newW
        val newTop = anchorY - fy * newH
        val clamped =
            clampTargetToPage(
                Rect(newLeft, newTop, newLeft + newW, newTop + newH),
            )
        segments = listOf(s.copy(targetOnPage = clamped))
        alignTargetAspectToPanel()
    }

    /**
     * Изменить кратность лупы вокруг центра окна — для кнопок зума −/+ в
     * нижнем блоке панели. [scaleFactor] > 1 — крупнее (меньше рамка-цель),
     * < 1 — мельче. Single-page only; aspect рамки выравнивается под панель.
     */
    fun magnifyBy(scaleFactor: Float) {
        zoomTargetAroundPanelFocus(
            scaleFactor = scaleFactor,
            focusPanelLocal = Offset(panelSize.width / 2f, panelSize.height / 2f),
            panelSize = panelSize,
        )
    }

    /**
     * Сдвинуть `targetOnPage` на [deltaPanelPx] (panel-pixel дельту),
     * пересчитав её в page-normalized с учётом текущих `panelSize` и
     * `targetOnPage`. Single-page only.
     */
    fun panTargetByPanelPx(
        deltaPanelPx: Offset,
        panelSize: Size,
    ) {
        if (segments.size != 1) return
        if (panelSize.width <= 0f || panelSize.height <= 0f) return
        val r = segments[0].targetOnPage
        val tw = r.right - r.left
        val th = r.bottom - r.top
        if (tw <= 0f || th <= 0f) return
        moveTarget(
            Offset(
                x = deltaPanelPx.x / panelSize.width * tw,
                y = deltaPanelPx.y / panelSize.height * th,
            ),
        )
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
    fun updatePageCanvasPx(
        widthPx: Float,
        heightPx: Float = pageCanvasHeightPx,
    ) {
        pageCanvasWidthPx = widthPx
        if (heightPx > 0f) pageCanvasHeightPx = heightPx
    }

    /** Обновить ссылку на битмап страницы [pageIndex]. */
    fun updatePageBitmap(
        pageIndex: Int,
        bitmap: ImageBitmap?,
    ) {
        if (bitmap == null) {
            pageBitmapsState.remove(pageIndex)
        } else {
            pageBitmapsState[pageIndex] = bitmap
        }
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
                    segments =
                        listOf(
                            s.copy(targetOnPage = Rect(shifted, r.top, shifted + w, r.bottom)),
                        )
                    true
                } else {
                    // Перевод строки.
                    val newTop = r.top + h * AUTO_SCROLL_LINE_FEED
                    if (newTop + h > 1f) return false
                    segments =
                        listOf(
                            s.copy(
                                targetOnPage =
                                    Rect(
                                        LINE_LEFT_MARGIN,
                                        newTop,
                                        LINE_LEFT_MARGIN + w,
                                        newTop + h,
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
        // Page-pixel aspect: panelW/panelH = (tw * basePageW)/(th * pdfH)
        //                                  = (tw/th) * pageAspect.
        // Без учёта pageAspect alignment даёт неправильный aspect окна
        // относительно области, попадающей в зум — bitmap-region и dstSize
        // имеют разные aspect'ы, и drawImage растягивает анизотропно.
        val newPanelH = panelSize.width * th / tw / pageAspect
        panelSize = Size(panelSize.width, newPanelH.coerceAtLeast(MIN_PANEL_DIM_PX))
    }

    private fun alignTargetAspectToPanel() {
        if (segments.size != 1) return
        val s = segments[0]
        val r = s.targetOnPage
        val tw = r.right - r.left
        if (tw <= 0f || panelSize.width <= 0f) return
        // Обратное к alignPanelAspectToTarget: target.height (page-normalized)
        // = panel-pixel-aspect / page-pixel-aspect * target.width.
        val targetH = tw * panelSize.height / panelSize.width * pageAspect
        val newRect =
            clampTargetToPage(
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

        /** Дефолтная ширина рамки (page-normalized) для тулбар-enable. */
        const val TOOLBAR_DEFAULT_TARGET_WIDTH_FRAC: Float = 0.25f

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
enum class AutoScrollDir { RIGHT, }

/**
 * Способ привязки target rect лупы к содержимому.
 *
 *  - [PAGE]: рамка зафиксирована на странице (page-normalized).
 *  - [SCREEN]: рамка зафиксирована на экране (viewport-координаты).
 */
enum class MagnifierAttachment { PAGE, SCREEN }
