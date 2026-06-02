package ru.kyamshanov.notepen

import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.drawing.api.PdfDrawingState
import ru.kyamshanov.notepen.drawing.api.ToolMode

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
 * При включённой лупе страница НЕ блокируется: маршрутизация «панель vs
 * страница» делается выше (в `EditorPanel.routedOnDown`), сюда доходят только
 * жесты вне панели/рамки лупы, которые должны рисовать на странице как обычно.
 */
class MultiPageDrawingController(
    private val drawingStates: SnapshotStateMap<Int, PdfDrawingState>,
    private val geometry: PageLayoutGeometry,
    private val toolMode: () -> ToolMode,
    private val penSettings: () -> PenSettings,
    private val markerSettings: () -> MarkerSettings,
    private val eraserSettings: () -> EraserSettings,
    private val eraserOverride: () -> Boolean,
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
    private val session =
        DrawingGestureSession(
            penSettings = penSettings,
            markerSettings = markerSettings,
            eraserSettings = eraserSettings,
            eraserPosition = { state -> state.eraserPos },
            onGestureStart = onGestureStart,
            onStrokeFinished = onStrokeFinished,
            onEraseFinished = onEraseFinished,
            pageAspect = ::pageAspectFor,
            scope = scope,
        )

    /**
     * Viewport-позиция предыдущего sample'а активного жеста. Нужна для
     * точной интерполяции точки пересечения границы страниц: snap в X
     * текущего sample'а оставляет видимый излом, потому что фактическая
     * траектория пера пересекла границу где-то между prev и curr.
     */
    private var lastViewportPos: Offset = Offset.Zero

    fun onDown(
        viewportPos: Offset,
        pressure: Float,
        tilt: Float,
    ) {
        session.cancel()
        val hit = hitTest(viewportPos) ?: return
        val (pageIndex, nx, ny) = hit
        val tool = toolMode()
        lastViewportPos = viewportPos
        val state = drawingStates.getOrPut(pageIndex) { PdfDrawingState() }
        if (eraserOverride() || tool == ToolMode.ERASER) {
            session.startErase(pageIndex, state, nx, ny)
        } else if (tool == ToolMode.PEN || tool == ToolMode.MARKER) {
            session.startDraw(pageIndex, state, nx, ny, pressure, tilt, tool)
        }
    }

    fun onMove(
        viewportPos: Offset,
        pressure: Float,
        tilt: Float,
    ) {
        if (session.activeMode == DrawingGestureMode.NONE) return
        val hit = hitTest(viewportPos) ?: return
        val (pageIndex, nx, ny) = hit
        if (pageIndex != session.activePageIndex) {
            handBoundary(viewportPos, pageIndex, nx, ny, pressure, tilt)
            lastViewportPos = viewportPos
            return
        }
        val state = drawingStates[pageIndex] ?: return
        when (session.activeMode) {
            DrawingGestureMode.DRAW -> session.addPoint(pageIndex, state, nx, ny, pressure, tilt)
            DrawingGestureMode.ERASE -> session.moveErase(pageIndex, state, nx, ny)
            DrawingGestureMode.NONE -> Unit
        }
        lastViewportPos = viewportPos
    }

    fun onUp() {
        session.finish()
    }

    fun onCancel() {
        session.cancel()
    }

    /**
     * Переход активного жеста на другую страницу в книжном развороте
     * (FEATURE #5): соседние страницы лежат бок-о-бок, вертикальная сшивка
     * [handBoundary] для них некорректна, поэтому просто завершаем текущий штрих
     * и начинаем новый на [newPageIndex] в точке `(nx, ny)`.
     */
    private fun handSpreadBoundary(
        newPageIndex: Int,
        nx: Float,
        ny: Float,
        pressure: Float,
        tilt: Float,
    ) {
        val tool = session.activeTool
        when (session.activeMode) {
            DrawingGestureMode.DRAW -> {
                session.finish()
                val state = drawingStates.getOrPut(newPageIndex) { PdfDrawingState() }
                session.startDraw(newPageIndex, state, nx, ny, pressure, tilt, tool)
            }
            DrawingGestureMode.ERASE -> {
                session.finish()
                val state = drawingStates.getOrPut(newPageIndex) { PdfDrawingState() }
                session.startErase(newPageIndex, state, nx, ny)
            }
            DrawingGestureMode.NONE -> Unit
        }
    }

    private fun handBoundary(
        viewportPos: Offset,
        newPageIndex: Int,
        nx: Float,
        ny: Float,
        pressure: Float,
        tilt: Float,
    ) {
        // Книжный разворот (FEATURE #5): соседние страницы пары лежат бок-о-бок
        // (горизонтальная граница через корешок), а вертикальная сшивка ниже
        // рассчитана на верх/низ-стекинг и для side-by-side некорректна. MVP:
        // завершаем текущий штрих и начинаем новый на новой странице чисто, без
        // экстраполяции через границу (штрих через корешок не сшивается).
        if (geometry.isSpread) {
            handSpreadBoundary(newPageIndex, nx, ny, pressure, tilt)
            return
        }
        // Сшиваем штрих в одной геометрической точке на границе через
        // интерполяцию между [lastViewportPos] и [viewportPos]: snap по X
        // текущего sample'а оставляет излом, потому что фактическая
        // траектория пера пересекла границу где-то между prev и curr.
        //
        // Соседние страницы стекаются без spacing (pageSpacingPx=0),
        // поэтому boundary-точка лежит в одной физической координате
        // viewport'а для обеих страниц.
        val oldPageIndex = session.activePageIndex
        val tool = session.activeTool
        val goingDown = newPageIndex > oldPageIndex
        val oldBoundaryNy = if (goingDown) 1f else 0f
        val newBoundaryNy = if (goingDown) 0f else 1f
        val boundaryDocY =
            if (goingDown) {
                geometry.pageTopPx(oldPageIndex) + geometry.pdfHeightPx(oldPageIndex)
            } else {
                geometry.pageTopPx(oldPageIndex)
            }
        val boundaryViewportY = geometry.pan.y + boundaryDocY * geometry.zoom
        val prev = lastViewportPos
        val dy = viewportPos.y - prev.y
        val t = if (dy != 0f) ((boundaryViewportY - prev.y) / dy).coerceIn(0f, 1f) else 0.5f
        val boundaryViewportX = prev.x + t * (viewportPos.x - prev.x)
        val boundaryNx =
            ((boundaryViewportX - geometry.pan.x) / geometry.zoom) /
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
        val oldExtrapolatedNy =
            if (goingDown) {
                1f + ny * geometry.pdfHeightPx(newPageIndex) / geometry.pdfHeightPx(oldPageIndex)
            } else {
                0f - (1f - ny) * geometry.pdfHeightPx(newPageIndex) / geometry.pdfHeightPx(oldPageIndex)
            }
        when (session.activeMode) {
            DrawingGestureMode.DRAW -> {
                // Без явной boundary-точки: верхний штрих заканчивается в
                // extrapolated-точке за границей (в зоне extent — скрыта
                // bitmap'ом нижней страницы), нижний стартует в
                // extrapolated-точке перед границей. Сегмент между ними —
                // прямая, проходящая через границу непрерывно. У штрихов
                // нет control-point cap'ов в видимой зоне.
                val oldState = drawingStates[oldPageIndex]
                oldState?.let { state ->
                    session.addPoint(oldPageIndex, state, nx, oldExtrapolatedNy, pressure, tilt)
                }
                session.finish()
                val state = drawingStates.getOrPut(newPageIndex) { PdfDrawingState() }
                session.startDraw(newPageIndex, state, prevNxShared, newExtrapolatedNy, pressure, tilt, tool)
                session.addPoint(newPageIndex, state, nx, ny, pressure, tilt)
            }
            DrawingGestureMode.ERASE -> {
                // Эрейзер: продление не нужно — он работает по точкам/штрихам,
                // нет визуального cap'а. Просто сшиваем сессии на границе.
                val oldState = drawingStates[oldPageIndex]
                oldState?.let { state -> session.moveErase(oldPageIndex, state, boundaryNx, oldBoundaryNy) }
                session.finish()
                val state = drawingStates.getOrPut(newPageIndex) { PdfDrawingState() }
                session.startErase(newPageIndex, state, boundaryNx, newBoundaryNy)
                session.moveErase(newPageIndex, state, nx, ny)
            }
            DrawingGestureMode.NONE -> Unit
        }
    }

    private fun pageAspectFor(pageIndex: Int): Float {
        val w = geometry.basePageWidthPx
        if (pageIndex !in 0 until geometry.pageCount) return 1f
        val h = geometry.pdfHeightPx(pageIndex)
        return if (h > 0f) w / h else 1f
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
        var pageIndex =
            when {
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
        // Книжный разворот (FEATURE #5): пара страниц делит один Y-ряд, бинпоиск
        // по docY попадает на ЛЕВУЮ страницу пары. Если docX лёг в правую колонку
        // (за её левым краем pageLeftPx) и в паре есть правая страница (тот же
        // pageTopPx) — переключаемся на неё, чтобы перо рисовало на правой
        // странице. Иначе остаёмся на левой.
        if (geometry.isSpread) {
            val sibling = pageIndex + 1
            if (sibling < n &&
                geometry.pageTopPx(sibling) == geometry.pageTopPx(pageIndex) &&
                docX >= geometry.pageLeftPx(sibling)
            ) {
                pageIndex = sibling
            }
        }
        val pdfH = geometry.pdfHeightPx(pageIndex)
        // nx считаем относительно ЛЕВОГО края колонки этой страницы (в развороте
        // правая колонка смещена на pageLeftPx; в одностраничном он == 0).
        val nx = (docX - geometry.pageLeftPx(pageIndex)) / geometry.basePageWidthPx
        val ny = (docY - geometry.pageTopPx(pageIndex)) / pdfH
        return Triple(pageIndex, nx, ny)
    }
}
