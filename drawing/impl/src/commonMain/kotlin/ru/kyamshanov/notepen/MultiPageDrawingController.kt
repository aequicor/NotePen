package ru.kyamshanov.notepen

import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.drawing.api.EraseGesture
import ru.kyamshanov.notepen.drawing.api.PdfDrawingState
import ru.kyamshanov.notepen.drawing.api.ToolMode

/** Длительность удержания pointer'а на месте для триггера shape-recognition (мс). */
private const val SHAPE_SNAP_HOLD_MS: Long = 700L

/**
 * Терпимость к джиттеру при удержании — доля нормализованной ширины страницы.
 * 0.005 ≈ 3 dp на A4, чтобы стилус мог микро-дрожать без сброса таймера.
 */
private const val SHAPE_SNAP_TOLERANCE_NORM: Float = 0.005f

/**
 * Драйвер рисования, поднятый над пер-страничным `DrawablePdfPage`.
 *
 * Раньше каждая страница имела собственный `pointerInput` и захватывала жест
 * целиком: при пересечении пером границы вниз стилус-DOWN оставался у верхней
 * страницы, а нижняя ничего не получала — штрих обрывался.
 *
 * Контроллер живёт на уровне всего viewer'а: получает viewport-координаты
 * жеста, переводит их в `(pageIndex, nx, ny)` через [PageLayoutGeometry] и
 * роутит вызовы в нужный [PdfDrawingState]. При уходе пера на соседнюю
 * PDF-страницу текущий штрих финализируется на старой, и стартует новый на
 * новой — визуально для пользователя это одна непрерывная линия (страницы
 * стоят без зазоров).
 *
 * Эрейзер ведёт себя аналогично: при пересечении границы — `end()` сессии
 * на одной странице и `start()` на соседней.
 *
 * Magnifier-страница пропускается: на ней рисование идёт через
 * `MagnifierInputPanel`, лифтнутый overlay не должен дублировать ввод.
 */
class MultiPageDrawingController(
    private val drawingStates: SnapshotStateMap<Int, PdfDrawingState>,
    private val geometry: PageLayoutGeometry,
    private val toolMode: () -> ToolMode,
    private val penSettings: () -> PenSettings,
    private val markerSettings: () -> MarkerSettings,
    private val eraserSettings: () -> EraserSettings,
    private val eraserOverride: () -> Boolean,
    private val skipPage: (Int) -> Boolean,
    private val onGestureStart: (pageIndex: Int, snapshot: List<DrawingPath>) -> Unit,
    private val onStrokeFinished: (pageIndex: Int, path: DrawingPath) -> Unit,
    private val onEraseFinished: (pageIndex: Int, before: List<DrawingPath>, after: List<DrawingPath>) -> Unit,
    /**
     * Scope для таймера hold-to-snap (см. [HoldGestureTracker]). Инжектируется
     * из composable owner'а (`rememberCoroutineScope`), чтобы не дёргать
     * `Dispatchers.*` напрямую (KMP-friendly + проверяемо в тестах).
     */
    private val scope: CoroutineScope,
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

    private val holdTracker = HoldGestureTracker(
        scope = scope,
        delayMs = SHAPE_SNAP_HOLD_MS,
        toleranceNorm = SHAPE_SNAP_TOLERANCE_NORM,
        onHold = ::triggerShapeSnap,
    )

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
            Mode.DRAW -> {
                state.addPoint(nx, ny, pressure, tilt)
                holdTracker.onMove(nx, ny)
            }
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
            geometry.pageTopPx(activePageIndex) + geometry.pdfHeightPx(activePageIndex)
        } else {
            geometry.pageTopPx(activePageIndex)
        }
        val boundaryViewportY = geometry.pan.y + boundaryDocY * geometry.zoom
        val prev = lastViewportPos
        val dy = viewportPos.y - prev.y
        val t = if (dy != 0f) ((boundaryViewportY - prev.y) / dy).coerceIn(0f, 1f) else 0.5f
        val boundaryViewportX = prev.x + t * (viewportPos.x - prev.x)
        val boundaryNx = ((boundaryViewportX - geometry.pan.x) / geometry.zoom) /
            geometry.basePageWidthPx
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
        val prevDocX = (prev.x - geometry.pan.x) / geometry.zoom
        val prevDocY = (prev.y - geometry.pan.y) / geometry.zoom
        val prevNxShared = prevDocX / geometry.basePageWidthPx
        // Прев в координатах НОВОЙ страницы (для goingDown ny < 0).
        val newExtrapolatedNy =
            (prevDocY - geometry.pageTopPx(newPageIndex)) / geometry.pdfHeightPx(newPageIndex)
        // Текущий sample в координатах СТАРОЙ страницы (для goingDown ny > 1).
        val oldExtrapolatedNy = if (goingDown) {
            1f + ny * geometry.pdfHeightPx(newPageIndex) / geometry.pdfHeightPx(activePageIndex)
        } else {
            0f - (1f - ny) * geometry.pdfHeightPx(newPageIndex) / geometry.pdfHeightPx(activePageIndex)
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
        val pdfWidthPx = geometry.basePageWidthPx * geometry.zoom
        if (pdfWidthPx <= 0f) return
        // strokeWidth is already a fraction of page width (DPI-independent),
        // so it can be fed straight into normalizedStrokeWidth without /pdfWidthPx.
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
            normalizedStrokeWidth = settingsStrokeWidth,
            pressure = if (gestureTool == ToolMode.PEN) pressure else 1f,
            tilt = if (gestureTool == ToolMode.PEN) tilt else 0f,
        )
        activePageIndex = pageIndex
        activeMode = Mode.DRAW
        holdTracker.onDown(nx, ny, pageAspectFor(pageIndex))
    }

    private fun finishDraw() {
        holdTracker.cancel()
        val pageIndex = activePageIndex
        val state = drawingStates[pageIndex] ?: return
        val completed = state.finishDrawing()
        if (completed != null) onStrokeFinished(pageIndex, completed)
    }

    private fun triggerShapeSnap() {
        val pageIndex = activePageIndex
        if (pageIndex < 0 || activeMode != Mode.DRAW) return
        val state = drawingStates[pageIndex] ?: return
        state.snapLiveStrokeToShape(pageAspectFor(pageIndex))
    }

    private fun pageAspectFor(pageIndex: Int): Float {
        val w = geometry.basePageWidthPx
        if (pageIndex !in 0 until geometry.pageCount) return 1f
        val h = geometry.pdfHeightPx(pageIndex)
        return if (h > 0f) w / h else 1f
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
        holdTracker.cancel()
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
     * Возвращает `true`, если [viewportPos] попадает внутрь видимой PDF-страницы
     * (nx ∈ [0..1], ny ∈ [0..1] в нормализованных page-координатах).
     *
     * Annotation-зона (extent) снаружи [0..1] не считается «внутри PDF».
     * Используется в `captureGesture`-гейте: когда pencil mode выключен и
     * инструмент активен, палец рисует внутри страницы, но скроллит снаружи.
     */
    fun isInsidePdfPage(viewportPos: Offset): Boolean {
        val (_, nx, ny) = hitTest(viewportPos) ?: return false
        return nx in 0f..1f && ny in 0f..1f
    }

    /**
     * Преобразует viewport-пиксель в `(pageIndex, nx, ny)` PDF-страницы.
     *
     * `nx` / `ny` нормализованы к PDF-странице ([0..1] = внутри PDF; за
     * пределами — допустимо, [PdfDrawingState] расширит extent). Если
     * страниц нет или viewport не измерен, возвращает `null`.
     */
    private fun hitTest(vp: Offset): Triple<Int, Float, Float>? {
        val n = geometry.pageCount
        if (n == 0 || geometry.basePageWidthPx <= 0f || geometry.zoom <= 0f) return null
        val docX = (vp.x - geometry.pan.x) / geometry.zoom
        val docY = (vp.y - geometry.pan.y) / geometry.zoom
        // Бинпоиск страницы по docY: ищем последнюю с pageTopPx(i) <= docY,
        // клампим в [0, n-1] для случая, когда палец ушёл выше первой
        // страницы или ниже последней (extent одной из крайних страниц
        // может покрывать эту зону).
        val pageIndex = when {
            docY <= geometry.pageTopPx(0) -> 0
            else -> {
                var lo = 0
                var hi = n - 1
                while (lo < hi) {
                    val mid = (lo + hi + 1) ushr 1
                    if (geometry.pageTopPx(mid) <= docY) lo = mid else hi = mid - 1
                }
                lo
            }
        }
        val pdfH = geometry.pdfHeightPx(pageIndex)
        val nx = docX / geometry.basePageWidthPx
        val ny = (docY - geometry.pageTopPx(pageIndex)) / pdfH
        return Triple(pageIndex, nx, ny)
    }
}
