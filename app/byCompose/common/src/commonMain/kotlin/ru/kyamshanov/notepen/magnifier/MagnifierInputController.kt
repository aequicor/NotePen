package ru.kyamshanov.notepen.magnifier

import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import ru.kyamshanov.notepen.EraseGesture
import ru.kyamshanov.notepen.EraserSettings
import ru.kyamshanov.notepen.MarkerSettings
import ru.kyamshanov.notepen.PdfDrawingState
import ru.kyamshanov.notepen.PenSettings
import ru.kyamshanov.notepen.ToolMode
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath

/**
 * Управление вводом внутри плавающего окна лупы — теперь с поддержкой
 * мульти-страничного выделения.
 *
 * Координата по Y в окне определяет, в какой [MagnifierPageSegment]
 * попал курсор; затем panel-local точка переводится в page-normalized
 * для соответствующей страницы.
 */
class MagnifierInputController internal constructor(
    private val state: MagnifierState,
    private val pdfDrawingStateProvider: (pageIndex: Int) -> PdfDrawingState,
    private val toolMode: () -> ToolMode,
    private val penSettings: () -> PenSettings,
    private val markerSettings: () -> MarkerSettings,
    private val eraserSettings: () -> EraserSettings,
    private val eraserOverride: () -> Boolean,
    private val eraserPos: MutableState<Offset?>,
    private val onGestureStart: (pageIndex: Int, snapshot: List<DrawingPath>) -> Unit,
    private val onStrokeFinished: (pageIndex: Int, path: DrawingPath) -> Unit,
    private val onEraseFinished: (
        pageIndex: Int,
        before: List<DrawingPath>,
        after: List<DrawingPath>,
    ) -> Unit,
) {

    private var activeErase: EraseGesture? = null
    private var activeMode: Mode = Mode.NONE
    private var activePageIndex: Int = -1
    private var activeDrawingState: PdfDrawingState? = null

    fun onDown(panelLocal: Offset, panelSize: Size, pressure: Float, tilt: Float) {
        val pageCanvasW = state.pageCanvasWidthPx
        if (pageCanvasW <= 0f || panelSize.width <= 0f || panelSize.height <= 0f) return
        val segment = segmentForPanelY(panelLocal.y, panelSize.height) ?: return
        val page = panelLocalToPage(panelLocal, panelSize, segment)
        val effectiveTool = if (eraserOverride()) ToolMode.ERASER else toolMode()
        val pdfDrawingState = pdfDrawingStateProvider(segment.pageIndex)
        activeDrawingState = pdfDrawingState
        activePageIndex = segment.pageIndex
        when (effectiveTool) {
            ToolMode.PEN, ToolMode.MARKER -> {
                val widthPx = if (effectiveTool == ToolMode.PEN) {
                    penSettings().strokeWidth
                } else {
                    markerSettings().strokeWidth
                }
                val colorArgb = if (effectiveTool == ToolMode.PEN) {
                    penSettings().colorArgb
                } else {
                    markerSettings().colorArgb
                }
                onGestureStart(segment.pageIndex, pdfDrawingState.currentPaths.toList())
                pdfDrawingState.strokeColorArgb.value = colorArgb
                pdfDrawingState.strokeWidth.value = widthPx
                val mag = loupeMagnification(panelSize, segment)
                val adaptedWidthPx = (widthPx / mag).coerceAtLeast(MIN_ADAPTED_STROKE_PX)
                pdfDrawingState.startDrawing(
                    x = page.x,
                    y = page.y,
                    normalizedStrokeWidth = adaptedWidthPx / pageCanvasW,
                    pressure = pressure,
                    tilt = tilt,
                )
                activeMode = Mode.DRAW
            }
            ToolMode.ERASER -> {
                val pi = segment.pageIndex
                activeErase = EraseGesture(
                    pdfDrawingState = pdfDrawingState,
                    eraserSettings = eraserSettings(),
                    eraserPos = eraserPos,
                    onGestureStart = { snapshot -> onGestureStart(pi, snapshot) },
                    onEraseFinished = { b, a -> onEraseFinished(pi, b, a) },
                ).also { it.start(page.x, page.y) }
                activeMode = Mode.ERASE
            }
            ToolMode.NONE -> activeMode = Mode.NONE
        }
    }

    fun onMove(panelLocal: Offset, panelSize: Size, pressure: Float, tilt: Float) {
        if (activeMode == Mode.NONE) return
        if (panelSize.width <= 0f || panelSize.height <= 0f) return
        // Сегмент фиксируем в момент Down — если палец переехал в соседний
        // сегмент посреди штриха, продолжаем писать на исходной странице
        // (точка выйдет за пределы targetRect, что приемлемо). Альтернатива
        // — финализировать штрих и начать новый — слишком инвазивно для
        // первой итерации.
        val pi = activePageIndex
        val segment = state.segments.firstOrNull { it.pageIndex == pi } ?: return
        val page = panelLocalToPage(panelLocal, panelSize, segment)
        val pdfDrawingState = activeDrawingState ?: return
        when (activeMode) {
            Mode.DRAW -> if (pdfDrawingState.isDrawing.value) {
                pdfDrawingState.addPoint(x = page.x, y = page.y, pressure = pressure, tilt = tilt)
            }
            Mode.ERASE -> activeErase?.move(page.x, page.y)
            Mode.NONE -> Unit
        }
    }

    fun onUp(panelSize: Size) {
        when (activeMode) {
            Mode.DRAW -> finishDraw(panelSize)
            Mode.ERASE -> finishErase()
            Mode.NONE -> Unit
        }
        activeMode = Mode.NONE
        activePageIndex = -1
        activeDrawingState = null
    }

    fun onCancel() {
        when (activeMode) {
            Mode.DRAW -> activeDrawingState?.finishDrawing()
            Mode.ERASE -> activeErase?.cancel()
            Mode.NONE -> Unit
        }
        activeErase = null
        activeMode = Mode.NONE
        activePageIndex = -1
        activeDrawingState = null
    }

    private fun finishDraw(panelSize: Size) {
        val pdfDrawingState = activeDrawingState ?: return
        val pi = activePageIndex
        val completed = pdfDrawingState.finishDrawing() ?: return
        onStrokeFinished(pi, completed)
        if (state.autoScrollEnabled && panelSize.width > 0f && state.segments.size == 1) {
            // Auto-scroll работает только для single-page (для multi-page
            // понятие «следующая строка» неоднозначно).
            val segment = state.segments[0]
            val last = completed.points.lastOrNull() ?: return
            val panelLocal = pageNormalizedToPanelLocalForSegment(
                Offset(last.x, last.y), panelSize, segment,
            )
            if (panelLocal.x > panelSize.width * AUTO_SCROLL_TRIGGER_FRAC) {
                state.shiftTargetForAutoscroll(AutoScrollDir.RIGHT)
            }
        }
    }

    private fun finishErase() {
        activeErase?.end()
        activeErase = null
    }

    /**
     * Находит сегмент, в чей panel-y диапазон попадает [panelY].
     */
    internal fun segmentForPanelY(panelY: Float, panelHeight: Float): MagnifierPageSegment? {
        if (panelHeight <= 0f) return null
        val frac = (panelY / panelHeight).coerceIn(0f, 1f)
        // Берём первый сегмент, у которого frac < panelBottomFrac. Граничный
        // случай (frac == 0): возвращаем самый верхний.
        return state.segments.firstOrNull { frac < it.panelBottomFrac || it === state.segments.last() }
    }

    /**
     * Конвертирует panel-local координату в page-normalized для конкретного
     * сегмента. По X — линейно через [MagnifierPageSegment.targetOnPage].
     * По Y — переводим panel-y в page-y, учитывая что сегмент занимает
     * [panelTopFrac..panelBottomFrac] полосу панели.
     */
    private fun panelLocalToPage(
        panelLocal: Offset,
        panelSize: Size,
        segment: MagnifierPageSegment,
    ): Offset {
        val target = segment.targetOnPage
        val nx = if (panelSize.width > 0f) {
            target.left + (panelLocal.x / panelSize.width) * (target.right - target.left)
        } else target.left
        val fy = if (panelSize.height > 0f) {
            (panelLocal.y / panelSize.height).coerceIn(0f, 1f)
        } else 0f
        val segH = (segment.panelBottomFrac - segment.panelTopFrac).coerceAtLeast(1e-6f)
        val localY = ((fy - segment.panelTopFrac) / segH).coerceIn(0f, 1f)
        val ny = target.top + localY * (target.bottom - target.top)
        return Offset(nx, ny)
    }

    /**
     * Обратное преобразование для нужд auto-scroll: page-normalized →
     * panel-local в пределах одного сегмента.
     */
    private fun pageNormalizedToPanelLocalForSegment(
        page: Offset,
        panelSize: Size,
        segment: MagnifierPageSegment,
    ): Offset {
        val target = segment.targetOnPage
        val tw = target.right - target.left
        val th = target.bottom - target.top
        val x = if (tw > 0f) (page.x - target.left) / tw * panelSize.width else 0f
        val localY = if (th > 0f) (page.y - target.top) / th else 0f
        val segH = segment.panelBottomFrac - segment.panelTopFrac
        val y = (segment.panelTopFrac + localY * segH) * panelSize.height
        return Offset(x, y)
    }

    private fun loupeMagnification(panelSize: Size, segment: MagnifierPageSegment): Float {
        val targetW = segment.targetOnPage.right - segment.targetOnPage.left
        val pageCanvasW = state.pageCanvasWidthPx
        if (targetW <= 0f || pageCanvasW <= 0f || panelSize.width <= 0f) return 1f
        return (panelSize.width / (pageCanvasW * targetW)).coerceAtLeast(1f)
    }

    private enum class Mode { NONE, DRAW, ERASE }

    private companion object {
        const val AUTO_SCROLL_TRIGGER_FRAC: Float = 0.92f
        const val MIN_ADAPTED_STROKE_PX: Float = 0.5f
    }
}

/**
 * Перевод позиции из viewport-координат в panel-local content. Возвращает
 * `null`, если позиция вне content-области.
 */
internal fun viewportToPanelLocal(state: MagnifierState, viewportPos: Offset): Offset? {
    val r = state.contentBoundsInViewport
    if (r.width <= 0f || r.height <= 0f) return null
    val local = Offset(viewportPos.x - r.left, viewportPos.y - r.top)
    if (local.x < 0f || local.y < 0f || local.x > r.width || local.y > r.height) return null
    return local
}
