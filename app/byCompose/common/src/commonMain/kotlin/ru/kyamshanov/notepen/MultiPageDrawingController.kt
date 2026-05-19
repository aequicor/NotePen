package ru.kyamshanov.notepen

import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.pdfviewer.PdfPagesLayout
import ru.kyamshanov.notepen.pdfviewer.PdfViewerState
import ru.kyamshanov.notepen.tablet.TabletInputController

/**
 * Драйвер рисования, поднятый над пер-страничным [DrawablePdfPage].
 *
 * Раньше каждая страница имела собственный `pointerInput` и захватывала жест
 * целиком: при пересечении пером границы вниз стилус-DOWN оставался у верхней
 * страницы, а нижняя ничего не получала — штрих обрывался.
 *
 * Контроллер живёт на уровне всего viewer'а: получает viewport-координаты
 * жеста, переводит их в `(pageIndex, nx, ny)` и роутит вызовы в нужный
 * [PdfDrawingState]. При уходе пера на соседнюю PDF-страницу текущий штрих
 * финализируется на старой, и стартует новый на новой — визуально для
 * пользователя это одна непрерывная линия (страницы стоят без зазоров).
 *
 * Эрейзер ведёт себя аналогично: при пересечении границы — `end()` сессии
 * на одной странице и `start()` на соседней.
 *
 * Magnifier-страница пропускается: на ней рисование идёт через
 * `MagnifierInputPanel`, лифтнутый overlay не должен дублировать ввод.
 */
internal class MultiPageDrawingController(
    private val drawingStates: SnapshotStateMap<Int, PdfDrawingState>,
    private val viewerState: PdfViewerState,
    private val toolMode: () -> ToolMode,
    private val penSettings: () -> PenSettings,
    private val markerSettings: () -> MarkerSettings,
    private val eraserSettings: () -> EraserSettings,
    private val eraserOverride: () -> Boolean,
    private val skipPage: (Int) -> Boolean,
    private val onGestureStart: (pageIndex: Int, snapshot: List<DrawingPath>) -> Unit,
    private val onStrokeFinished: (pageIndex: Int, path: DrawingPath) -> Unit,
    private val onEraseFinished: (pageIndex: Int, before: List<DrawingPath>, after: List<DrawingPath>) -> Unit,
) {

    private enum class Mode { NONE, DRAW, ERASE }

    private var activePageIndex: Int = -1
    private var activeMode: Mode = Mode.NONE
    private var activeErase: EraseGesture? = null
    /**
     * Tool, выбранный на момент `onDown` — фиксируем, чтобы смена инструмента
     * посреди жеста не размазала штрих между пайплайнами.
     */
    private var gestureTool: ToolMode = ToolMode.NONE

    /**
     * Viewport-позиция предыдущего sample'а активного жеста. Нужна для
     * точной интерполяции точки пересечения границы страниц: snap в X
     * текущего sample'а оставляет видимый излом, потому что фактическая
     * траектория пера пересекла границу где-то между prev и curr.
     */
    private var lastViewportPos: Offset = Offset.Zero

    fun onDown(viewportPos: Offset, pressure: Float, tilt: Float) {
        cancelActive()
        val hit = hitTest(viewportPos) ?: return
        val (pageIndex, nx, ny) = hit
        if (skipPage(pageIndex)) return
        val tool = toolMode()
        gestureTool = tool
        lastViewportPos = viewportPos
        val state = drawingStates.getOrPut(pageIndex) { PdfDrawingState() }
        if (eraserOverride() || tool == ToolMode.ERASER) {
            startErase(pageIndex, state, nx, ny)
        } else if (tool == ToolMode.PEN || tool == ToolMode.MARKER) {
            startDraw(pageIndex, state, nx, ny, pressure, tilt)
        }
    }

    fun onMove(viewportPos: Offset, pressure: Float, tilt: Float) {
        if (activeMode == Mode.NONE) return
        val hit = hitTest(viewportPos) ?: return
        val (pageIndex, nx, ny) = hit
        if (pageIndex != activePageIndex) {
            if (skipPage(pageIndex)) {
                lastViewportPos = viewportPos
                return
            }
            handBoundary(viewportPos, pageIndex, nx, ny, pressure, tilt)
            lastViewportPos = viewportPos
            return
        }
        val state = drawingStates[pageIndex] ?: return
        when (activeMode) {
            Mode.DRAW -> state.addPoint(nx, ny, pressure, tilt)
            Mode.ERASE -> activeErase?.move(nx, ny)
            Mode.NONE -> Unit
        }
        lastViewportPos = viewportPos
    }

    fun onUp() {
        when (activeMode) {
            Mode.DRAW -> finishDraw()
            Mode.ERASE -> finishErase()
            Mode.NONE -> Unit
        }
        reset()
    }

    fun onCancel() {
        cancelActive()
        reset()
    }

    private fun handBoundary(
        viewportPos: Offset,
        newPageIndex: Int,
        nx: Float,
        ny: Float,
        pressure: Float,
        tilt: Float,
    ) {
        // Сшиваем штрих в одной геометрической точке на границе через
        // интерполяцию между [lastViewportPos] и [viewportPos]: snap по X
        // текущего sample'а оставляет излом, потому что фактическая
        // траектория пера пересекла границу где-то между prev и curr.
        //
        // Соседние страницы стекаются без spacing (pageSpacingPx=0),
        // поэтому boundary-точка лежит в одной физической координате
        // viewport'а для обеих страниц.
        val goingDown = newPageIndex > activePageIndex
        val oldBoundaryNy = if (goingDown) 1f else 0f
        val newBoundaryNy = if (goingDown) 0f else 1f
        val boundaryDocY = if (goingDown) {
            viewerState.layout.pageTopsPx[activePageIndex] +
                viewerState.layout.pdfHeightsPx[activePageIndex]
        } else {
            viewerState.layout.pageTopsPx[activePageIndex]
        }
        val boundaryViewportY = viewerState.pan.y + boundaryDocY * viewerState.zoom
        val prev = lastViewportPos
        val dy = viewportPos.y - prev.y
        val t = if (dy != 0f) ((boundaryViewportY - prev.y) / dy).coerceIn(0f, 1f) else 0.5f
        val boundaryViewportX = prev.x + t * (viewportPos.x - prev.x)
        val boundaryNx = ((boundaryViewportX - viewerState.pan.x) / viewerState.zoom) /
            viewerState.layout.basePageWidthPx
        // Бесшовная стыковка: оба штриха ПРОДЛЕВАЮТСЯ через границу в зону
        // extent соседней страницы, чтобы их «настоящие» конечные/стартовые
        // cap'ы оказались СКРЫТЫ под bitmap'ом другой страницы. В видимой
        // области у обоих штрихов остаётся только тело, проходящее через
        // границу — round-cap'ов в этой точке нет вовсе.
        //
        // 1. Верхний штрих: …, user_prev, boundary, actual_extrapolated_below.
        //    Конечный cap — в зоне extent.bottom > 1, перекрыт нижней страницей.
        //
        // 2. Нижний штрих: prev_extrapolated_above, boundary, actual.
        //    Стартовый cap — в зоне с ny < 0 (визуально над границей), но
        //    тело сегмента prev → boundary рисуется в lower-slot'е поверх
        //    верхнего штриха — они занимают одну и ту же кривую и
        //    цветово совпадают.
        //
        // Координаты «через границу»: pdfH у соседних страниц обычно
        // одинаков, поэтому boundary-крест ровно в 1f / 0f.
        val prevDocX = (prev.x - viewerState.pan.x) / viewerState.zoom
        val prevDocY = (prev.y - viewerState.pan.y) / viewerState.zoom
        val prevNxShared = prevDocX / viewerState.layout.basePageWidthPx
        // Прев в координатах НОВОЙ страницы (для goingDown ny < 0).
        val newExtrapolatedNy =
            (prevDocY - viewerState.layout.pageTopsPx[newPageIndex]) /
                viewerState.layout.pdfHeightsPx[newPageIndex]
        // Текущий sample в координатах СТАРОЙ страницы (для goingDown ny > 1).
        val oldExtrapolatedNy = if (goingDown) {
            1f + ny * viewerState.layout.pdfHeightsPx[newPageIndex] /
                viewerState.layout.pdfHeightsPx[activePageIndex]
        } else {
            0f - (1f - ny) * viewerState.layout.pdfHeightsPx[newPageIndex] /
                viewerState.layout.pdfHeightsPx[activePageIndex]
        }
        when (activeMode) {
            Mode.DRAW -> {
                // Без явной boundary-точки: верхний штрих заканчивается в
                // extrapolated-точке за границей (в зоне extent — скрыта
                // bitmap'ом нижней страницы), нижний стартует в
                // extrapolated-точке перед границей. Сегмент между ними —
                // прямая, проходящая через границу непрерывно. У штрихов
                // нет control-point cap'ов в видимой зоне.
                val oldState = drawingStates[activePageIndex]
                oldState?.addPoint(nx, oldExtrapolatedNy, pressure, tilt)
                finishDraw()
                val state = drawingStates.getOrPut(newPageIndex) { PdfDrawingState() }
                startDraw(newPageIndex, state, prevNxShared, newExtrapolatedNy, pressure, tilt)
                state.addPoint(nx, ny, pressure, tilt)
            }
            Mode.ERASE -> {
                // Эрейзер: продление не нужно — он работает по точкам/штрихам,
                // нет визуального cap'а. Просто сшиваем сессии на границе.
                activeErase?.move(boundaryNx, oldBoundaryNy)
                finishErase()
                val state = drawingStates.getOrPut(newPageIndex) { PdfDrawingState() }
                startErase(newPageIndex, state, boundaryNx, newBoundaryNy)
                activeErase?.move(nx, ny)
            }
            Mode.NONE -> Unit
        }
    }

    private fun startDraw(
        pageIndex: Int,
        state: PdfDrawingState,
        nx: Float,
        ny: Float,
        pressure: Float,
        tilt: Float,
    ) {
        val pdfWidthPx = viewerState.basePageWidthPx * viewerState.zoom
        if (pdfWidthPx <= 0f) return
        val settingsStrokeWidth = when (gestureTool) {
            ToolMode.PEN -> penSettings().strokeWidth
            ToolMode.MARKER -> markerSettings().strokeWidth
            else -> return
        }
        val settingsColorArgb = when (gestureTool) {
            ToolMode.PEN -> penSettings().colorArgb
            ToolMode.MARKER -> markerSettings().colorArgb
            else -> return
        }
        onGestureStart(pageIndex, state.currentPaths.toList())
        state.strokeColorArgb.value = settingsColorArgb
        state.strokeWidth.value = settingsStrokeWidth
        state.startDrawing(
            x = nx,
            y = ny,
            normalizedStrokeWidth = settingsStrokeWidth / pdfWidthPx,
            pressure = if (gestureTool == ToolMode.PEN) pressure else 1f,
            tilt = if (gestureTool == ToolMode.PEN) tilt else 0f,
        )
        activePageIndex = pageIndex
        activeMode = Mode.DRAW
    }

    private fun finishDraw() {
        val pageIndex = activePageIndex
        val state = drawingStates[pageIndex] ?: return
        val completed = state.finishDrawing()
        if (completed != null) onStrokeFinished(pageIndex, completed)
    }

    private fun startErase(pageIndex: Int, state: PdfDrawingState, nx: Float, ny: Float) {
        val gesture = EraseGesture(
            pdfDrawingState = state,
            eraserSettings = eraserSettings(),
            eraserPos = state.eraserPos,
            onGestureStart = { snapshot -> onGestureStart(pageIndex, snapshot) },
            onEraseFinished = { before, after -> onEraseFinished(pageIndex, before, after) },
        )
        gesture.start(nx, ny)
        activeErase = gesture
        activePageIndex = pageIndex
        activeMode = Mode.ERASE
    }

    private fun finishErase() {
        activeErase?.end()
        activeErase = null
    }

    private fun cancelActive() {
        when (activeMode) {
            Mode.DRAW -> {
                val pageIndex = activePageIndex
                drawingStates[pageIndex]?.finishDrawing()
            }
            Mode.ERASE -> activeErase?.cancel().also { activeErase = null }
            Mode.NONE -> Unit
        }
    }

    private fun reset() {
        activeMode = Mode.NONE
        activePageIndex = -1
        gestureTool = ToolMode.NONE
        activeErase = null
    }

    /**
     * Преобразует viewport-пиксель в `(pageIndex, nx, ny)` PDF-страницы.
     *
     * `nx` / `ny` нормализованы к PDF-странице ([0..1] = внутри PDF; за
     * пределами — допустимо, [PdfDrawingState] расширит extent). Если
     * страниц нет или viewport не измерен, возвращает `null`.
     */
    private fun hitTest(vp: Offset): Triple<Int, Float, Float>? {
        val layout: PdfPagesLayout = viewerState.layout
        val n = layout.pageHeightsPx.size
        if (n == 0 || layout.basePageWidthPx <= 0f || viewerState.zoom <= 0f) return null
        val docX = (vp.x - viewerState.pan.x) / viewerState.zoom
        val docY = (vp.y - viewerState.pan.y) / viewerState.zoom
        val tops = layout.pageTopsPx
        // Бинпоиск страницы по docY: ищем последнюю с tops[i] <= docY,
        // клампим в [0, n-1] для случая, когда палец ушёл выше первой
        // страницы или ниже последней (extent одной из крайних страниц
        // может покрывать эту зону).
        val pageIndex = when {
            docY <= tops[0] -> 0
            else -> {
                var lo = 0
                var hi = n - 1
                while (lo < hi) {
                    val mid = (lo + hi + 1) ushr 1
                    if (tops[mid] <= docY) lo = mid else hi = mid - 1
                }
                lo
            }
        }
        val pdfH = layout.pdfHeightsPx[pageIndex]
        val nx = docX / layout.basePageWidthPx
        val ny = (docY - tops[pageIndex]) / pdfH
        return Triple(pageIndex, nx, ny)
    }
}

/**
 * Pointer-input overlay, лифтнутый над PdfPagesViewer. Захватывает
 * стилус-жесты (на Android — с palm-rejection; на десктопе — мышь как
 * стилус) и делегирует их [controller], который сам разруливает переходы
 * между страницами.
 *
 * Не консумит touch-DOWN на Android при активной palm-rejection — palm/pan
 * по-прежнему попадают в viewer (scrollable / pinch).
 */
internal fun Modifier.pdfMultiPageDrawingInput(
    controller: MultiPageDrawingController,
    tablet: TabletInputController,
    palmRejectionActive: () -> Boolean,
): Modifier = pdfMultiPageDrawingInput(
    key = controller,
    tablet = tablet,
    palmRejectionActive = palmRejectionActive,
    onDown = controller::onDown,
    onMove = controller::onMove,
    onUp = controller::onUp,
    onCancel = controller::onCancel,
)

/**
 * Перегрузка с явными колбэками — используется, когда события маршрутизируются
 * между несколькими получателями (например, drawing vs loupe selection). [key]
 * задаёт identity для `pointerInput`: смена ключа пересоздаёт обработчик и
 * обрывает активный жест.
 */
internal fun Modifier.pdfMultiPageDrawingInput(
    key: Any?,
    tablet: TabletInputController,
    palmRejectionActive: () -> Boolean,
    onDown: (Offset, Float, Float) -> Unit,
    onMove: (Offset, Float, Float) -> Unit,
    onUp: () -> Unit,
    onCancel: () -> Unit,
): Modifier = this.pointerInput(key) {
    detectStylusAwareDrag(
        tablet = tablet,
        isPalmRejectionActive = palmRejectionActive,
        onDown = onDown,
        onMove = onMove,
        onUp = onUp,
        onCancel = onCancel,
    )
}

