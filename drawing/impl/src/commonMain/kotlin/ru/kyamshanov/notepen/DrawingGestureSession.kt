package ru.kyamshanov.notepen

import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.model.ToolKind
import ru.kyamshanov.notepen.drawing.api.EraseGesture
import ru.kyamshanov.notepen.drawing.api.EraserPosition
import ru.kyamshanov.notepen.drawing.api.PdfDrawingState
import ru.kyamshanov.notepen.drawing.api.ToolMode

/** Длительность удержания pointer'а на месте для триггера shape-recognition (мс). */
private const val SHAPE_SNAP_HOLD_MS: Long = 700L

/** Терпимость к джиттеру при удержании — доля нормализованной ширины страницы. */
private const val SHAPE_SNAP_TOLERANCE_NORM: Float = 0.005f

internal enum class DrawingGestureMode { NONE, DRAW, ERASE }

internal class DrawingGestureSession(
    private val penSettings: () -> PenSettings,
    private val markerSettings: () -> MarkerSettings,
    private val eraserSettings: () -> EraserSettings,
    private val eraserPosition: (PdfDrawingState) -> MutableState<EraserPosition?>,
    private val onGestureStart: (pageIndex: Int, snapshot: List<DrawingPath>) -> Unit,
    private val onStrokeFinished: (pageIndex: Int, path: DrawingPath) -> Unit,
    private val onEraseFinished: (pageIndex: Int, before: List<DrawingPath>, after: List<DrawingPath>) -> Unit,
    private val pageAspect: (pageIndex: Int) -> Float,
    scope: CoroutineScope,
) {
    var activeMode: DrawingGestureMode = DrawingGestureMode.NONE
        private set

    var activePageIndex: Int = -1
        private set

    var activeTool: ToolMode = ToolMode.NONE
        private set

    private var activeDrawingState: PdfDrawingState? = null
    private var activeErase: EraseGesture? = null

    private val holdTracker =
        HoldGestureTracker(
            scope = scope,
            delayMs = SHAPE_SNAP_HOLD_MS,
            toleranceNorm = SHAPE_SNAP_TOLERANCE_NORM,
            onHold = ::triggerShapeSnap,
        )

    fun startDraw(
        pageIndex: Int,
        state: PdfDrawingState,
        nx: Float,
        ny: Float,
        pressure: Float,
        tilt: Float,
        tool: ToolMode,
        strokeWidthScale: Float = 1f,
        minNormalizedStroke: Float = 0f,
    ): Boolean {
        cancel()
        val settingsStrokeWidth =
            when (tool) {
                ToolMode.PEN -> penSettings().strokeWidth
                ToolMode.MARKER -> markerSettings().strokeWidth
                ToolMode.NONE, ToolMode.ERASER, ToolMode.NOTE -> return false
            }
        val settingsColorArgb =
            when (tool) {
                ToolMode.PEN -> penSettings().colorArgb
                ToolMode.MARKER -> markerSettings().colorArgb
                ToolMode.NONE, ToolMode.ERASER, ToolMode.NOTE -> return false
            }
        val scaledWidth = settingsStrokeWidth * strokeWidthScale.coerceAtLeast(0f)
        val adaptedWidth =
            if (minNormalizedStroke > 0f) {
                scaledWidth.coerceAtLeast(minNormalizedStroke)
            } else {
                scaledWidth
            }
        onGestureStart(pageIndex, state.currentPaths.toList())
        state.strokeColorArgb.value = settingsColorArgb
        state.strokeWidth.value = settingsStrokeWidth
        state.strokeToolKind.value =
            if (tool == ToolMode.MARKER) ToolKind.MARKER else ToolKind.PEN
        state.startDrawing(
            x = nx,
            y = ny,
            normalizedStrokeWidth = adaptedWidth,
            pressure = if (tool == ToolMode.PEN) pressure else 1f,
            tilt = if (tool == ToolMode.PEN) tilt else 0f,
        )
        activePageIndex = pageIndex
        activeMode = DrawingGestureMode.DRAW
        activeTool = tool
        activeDrawingState = state
        holdTracker.onDown(nx, ny, pageAspect(pageIndex))
        return true
    }

    fun addPoint(
        pageIndex: Int,
        state: PdfDrawingState,
        nx: Float,
        ny: Float,
        pressure: Float,
        tilt: Float,
    ): Boolean {
        if (activeMode != DrawingGestureMode.DRAW ||
            activePageIndex != pageIndex ||
            activeDrawingState !== state ||
            !state.isDrawing.value
        ) {
            return false
        }
        state.addPoint(
            x = nx,
            y = ny,
            pressure = if (activeTool == ToolMode.PEN) pressure else 1f,
            tilt = if (activeTool == ToolMode.PEN) tilt else 0f,
        )
        holdTracker.onMove(nx, ny)
        return true
    }

    fun startErase(
        pageIndex: Int,
        state: PdfDrawingState,
        nx: Float,
        ny: Float,
    ): Boolean {
        cancel()
        val gesture =
            EraseGesture(
                pdfDrawingState = state,
                eraserSettings = eraserSettings(),
                eraserPos = eraserPosition(state),
                onGestureStart = { snapshot -> onGestureStart(pageIndex, snapshot) },
                onEraseFinished = { before, after -> onEraseFinished(pageIndex, before, after) },
            )
        gesture.start(nx, ny)
        activeErase = gesture
        activePageIndex = pageIndex
        activeMode = DrawingGestureMode.ERASE
        activeTool = ToolMode.ERASER
        activeDrawingState = state
        return true
    }

    fun moveErase(
        pageIndex: Int,
        state: PdfDrawingState,
        nx: Float,
        ny: Float,
    ): Boolean {
        if (activeMode != DrawingGestureMode.ERASE ||
            activePageIndex != pageIndex ||
            activeDrawingState !== state
        ) {
            return false
        }
        activeErase?.move(nx, ny)
        return true
    }

    fun finish(): DrawingPath? {
        holdTracker.cancel()
        val completed =
            when (activeMode) {
                DrawingGestureMode.DRAW -> finishDraw()
                DrawingGestureMode.ERASE -> {
                    activeErase?.end()
                    null
                }
                DrawingGestureMode.NONE -> null
            }
        reset()
        return completed
    }

    fun cancel() {
        holdTracker.cancel()
        when (activeMode) {
            DrawingGestureMode.DRAW -> activeDrawingState?.discardDrawing()
            DrawingGestureMode.ERASE -> activeErase?.cancel()
            DrawingGestureMode.NONE -> Unit
        }
        reset()
    }

    private fun finishDraw(): DrawingPath? {
        val state = activeDrawingState ?: return null
        val pageIndex = activePageIndex
        val completed = state.finishDrawing()
        if (completed != null) onStrokeFinished(pageIndex, completed)
        return completed
    }

    private fun triggerShapeSnap() {
        if (activeMode != DrawingGestureMode.DRAW) return
        val pageIndex = activePageIndex
        if (pageIndex < 0) return
        val state = activeDrawingState ?: return
        state.snapLiveStrokeToShape(pageAspect(pageIndex))
    }

    private fun reset() {
        activeMode = DrawingGestureMode.NONE
        activePageIndex = -1
        activeTool = ToolMode.NONE
        activeDrawingState = null
        activeErase = null
    }
}
