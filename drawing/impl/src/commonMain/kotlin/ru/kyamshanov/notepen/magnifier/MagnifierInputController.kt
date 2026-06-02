package ru.kyamshanov.notepen.magnifier

import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.CoroutineScope
import ru.kyamshanov.notepen.DrawingGestureMode
import ru.kyamshanov.notepen.DrawingGestureSession
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.drawing.api.EraserPosition
import ru.kyamshanov.notepen.drawing.api.PdfDrawingState
import ru.kyamshanov.notepen.drawing.api.ToolMode

/**
 * Управление вводом внутри плавающего окна лупы — теперь с поддержкой
 * мульти-страничного выделения.
 *
 * Координата по Y в окне определяет, в какой [MagnifierPageSegment]
 * попал курсор; затем panel-local точка переводится в page-normalized
 * для соответствующей страницы.
 */
class MagnifierInputController(
    private val geometry: MagnifierGeometry,
    private val pdfDrawingStateProvider: (pageIndex: Int) -> PdfDrawingState,
    private val toolMode: () -> ToolMode,
    private val penSettings: () -> PenSettings,
    private val markerSettings: () -> MarkerSettings,
    private val eraserSettings: () -> EraserSettings,
    private val eraserOverride: () -> Boolean,
    private val eraserPos: MutableState<EraserPosition?>,
    private val onGestureStart: (pageIndex: Int, snapshot: List<DrawingPath>) -> Unit,
    private val onStrokeFinished: (pageIndex: Int, path: DrawingPath) -> Unit,
    private val onEraseFinished: (
        pageIndex: Int,
        before: List<DrawingPath>,
        after: List<DrawingPath>,
    ) -> Unit,
    /** Scope для таймера hold-to-snap; инжектируется из owner-composable'а. */
    private val scope: CoroutineScope,
    /**
     * Provider физического аспект-рашио страницы (`pageWidthPx / pageHeightPx`).
     * Нужен shape-recognizer'у, чтобы метрики считались в физическом
     * пространстве, а не в нормализованных `[0..1]`.
     */
    private val pageAspect: (pageIndex: Int) -> Float,
) {
    private val session =
        DrawingGestureSession(
            penSettings = penSettings,
            markerSettings = markerSettings,
            eraserSettings = eraserSettings,
            eraserPosition = { eraserPos },
            onGestureStart = onGestureStart,
            onStrokeFinished = onStrokeFinished,
            onEraseFinished = onEraseFinished,
            pageAspect = pageAspect,
            scope = scope,
        )

    fun onDown(
        panelLocal: Offset,
        panelSize: Size,
        pressure: Float,
        tilt: Float,
    ) {
        val pageCanvasW = geometry.pageCanvasWidthPx
        if (pageCanvasW <= 0f || panelSize.width <= 0f || panelSize.height <= 0f) return
        val segment = segmentForPanelY(panelLocal.y, panelSize.height) ?: return
        val page = panelLocalToPage(panelLocal, panelSize, segment)
        val effectiveTool = if (eraserOverride()) ToolMode.ERASER else toolMode()
        val pdfDrawingState = pdfDrawingStateProvider(segment.pageIndex)
        when (effectiveTool) {
            ToolMode.PEN, ToolMode.MARKER -> {
                // strokeWidth is a fraction of page width. Inside the loupe the
                // page is magnified by `mag`, so we divide by `mag` to keep the
                // visual stroke thickness the same as outside the loupe.
                val mag = loupeMagnification(panelSize, segment)
                session.startDraw(
                    pageIndex = segment.pageIndex,
                    state = pdfDrawingState,
                    nx = page.x,
                    ny = page.y,
                    pressure = pressure,
                    tilt = tilt,
                    tool = effectiveTool,
                    strokeWidthScale = 1f / mag,
                    minNormalizedStroke = MIN_NORMALIZED_STROKE,
                )
            }
            ToolMode.ERASER -> {
                session.startErase(segment.pageIndex, pdfDrawingState, page.x, page.y)
            }
            // «Заметка» не рисует на полотне (создаётся из выделения текста в чтении),
            // поэтому в лупе ведёт себя как [NONE].
            ToolMode.NONE, ToolMode.NOTE -> session.cancel()
        }
    }

    fun onMove(
        panelLocal: Offset,
        panelSize: Size,
        pressure: Float,
        tilt: Float,
    ) {
        if (session.activeMode == DrawingGestureMode.NONE) return
        if (panelSize.width <= 0f || panelSize.height <= 0f) return

        val pi = session.activePageIndex
        val segment = geometry.segments.firstOrNull { it.pageIndex == pi } ?: return
        val page = panelLocalToPage(panelLocal, panelSize, segment)
        val pdfDrawingState = pdfDrawingStateProvider(pi)
        when (session.activeMode) {
            DrawingGestureMode.DRAW -> session.addPoint(pi, pdfDrawingState, page.x, page.y, pressure, tilt)
            DrawingGestureMode.ERASE -> session.moveErase(pi, pdfDrawingState, page.x, page.y)
            DrawingGestureMode.NONE -> Unit
        }
    }

    fun onUp(panelSize: Size) {
        val pageIndex = session.activePageIndex
        val completed = session.finish()
        if (completed != null) autoScrollAfterStroke(pageIndex, completed, panelSize)
    }

    fun onCancel() {
        session.cancel()
    }

    private fun autoScrollAfterStroke(
        pi: Int,
        completed: DrawingPath,
        panelSize: Size,
    ) {
        // Авто-прокрутка после отрыва пера. Если последняя точка штриха
        // была в одной из edge-зон панели, сдвигаем target-rect в эту
        // сторону на AUTO_SCROLL_LIFT_OFF_FRAC своего размера. Только для
        // single-page (для multi-page понятие «следующая строка» сейчас
        // не определено).
        if (!geometry.autoScrollEnabled || geometry.segments.size != 1) return
        if (panelSize.width <= 0f || panelSize.height <= 0f) return
        val seg = geometry.segments.firstOrNull { it.pageIndex == pi } ?: return
        val last = completed.points.lastOrNull() ?: return
        val target = seg.targetOnPage
        val width = target.right - target.left
        val height = target.bottom - target.top
        val tw = (target.right - target.left).coerceAtLeast(1e-6f)
        val th = (target.bottom - target.top).coerceAtLeast(1e-6f)
        val lastPanelX = (last.x - target.left) / tw * panelSize.width
        val lastPanelY = (last.y - target.top) / th * panelSize.height

        var shiftX = 0f
        var shiftY = 0f
        val rightZone = panelSize.width * (1f - AUTO_SCROLL_EDGE_FRAC)
        val leftZone = panelSize.width * AUTO_SCROLL_EDGE_FRAC
        val bottomZone = panelSize.height * (1f - AUTO_SCROLL_EDGE_FRAC)
        val topZone = panelSize.height * AUTO_SCROLL_EDGE_FRAC
        if (lastPanelX > rightZone) {
            shiftX = width * AUTO_SCROLL_LIFT_OFF_FRAC
        } else if (lastPanelX < leftZone) {
            shiftX = -width * AUTO_SCROLL_LIFT_OFF_FRAC
        }
        if (lastPanelY > bottomZone) {
            shiftY = height * AUTO_SCROLL_LIFT_OFF_FRAC
        } else if (lastPanelY < topZone) {
            shiftY = -height * AUTO_SCROLL_LIFT_OFF_FRAC
        }

        if (shiftX == 0f && shiftY == 0f) return
        val maxLeft = (1f - width).coerceAtLeast(0f)
        val maxTop = (1f - height).coerceAtLeast(0f)
        val newLeft = (target.left + shiftX).coerceIn(0f, maxLeft)
        val newTop = (target.top + shiftY).coerceIn(0f, maxTop)
        if (newLeft == target.left && newTop == target.top) return
        geometry.setSingleSegmentTarget(
            pageIndex = pi,
            targetOnPage = Rect(newLeft, newTop, newLeft + width, newTop + height),
        )
    }

    /**
     * Находит сегмент, в чей panel-y диапазон попадает [panelY].
     */
    internal fun segmentForPanelY(
        panelY: Float,
        panelHeight: Float,
    ): MagnifierPageSegment? {
        if (panelHeight <= 0f) return null
        val frac = (panelY / panelHeight).coerceIn(0f, 1f)
        // Берём первый сегмент, у которого frac < panelBottomFrac. Граничный
        // случай (frac == 0): возвращаем самый верхний.
        return geometry.segments.firstOrNull { frac < it.panelBottomFrac || it === geometry.segments.last() }
    }

    /**
     * Конвертирует panel-local координату в page-normalized для конкретного
     * сегмента. Делегирует в [panelLocalToPageInSegment] — единый источник
     * истины маппинга, обратный которому ([pageToPanelLocalInSegment])
     * применяет рендер панели. Так ввод и отрисовка остаются согласованными
     * при любом размере панели (в т.ч. после ресайза окна лупы).
     */
    private fun panelLocalToPage(
        panelLocal: Offset,
        panelSize: Size,
        segment: MagnifierPageSegment,
    ): Offset = panelLocalToPageInSegment(panelLocal, panelSize, segment)

    private fun loupeMagnification(
        panelSize: Size,
        segment: MagnifierPageSegment,
    ): Float {
        val targetW = segment.targetOnPage.right - segment.targetOnPage.left
        val pageCanvasW = geometry.pageCanvasWidthPx
        if (targetW <= 0f || pageCanvasW <= 0f || panelSize.width <= 0f) return 1f
        return (panelSize.width / (pageCanvasW * targetW)).coerceAtLeast(1f)
    }

    private companion object {
        /** Edge-зона: 20% панели у каждой из 4 сторон — там штрих считается «у края». */
        const val AUTO_SCROLL_EDGE_FRAC: Float = 0.20f

        /**
         * Сдвиг рамки на отрыве пера, если штрих кончился в edge-зоне —
         * в долях размера рамки. 0.35 = ~⅓ окна, остаётся ⅔ для перехлёста
         * предыдущего письма (комфортнее, чем прежние 0.85).
         */
        const val AUTO_SCROLL_LIFT_OFF_FRAC: Float = 0.35f

        /** Lower bound for stroke width as a fraction of page width (~0.04 mm on A4). */
        const val MIN_NORMALIZED_STROKE: Float = 0.0002f
    }
}
