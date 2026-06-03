package ru.kyamshanov.notepen

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.EraserMode
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.model.ToolKind
import ru.kyamshanov.notepen.drawing.api.EraserPosition
import ru.kyamshanov.notepen.drawing.api.PdfDrawingState
import ru.kyamshanov.notepen.drawing.api.ToolMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class DrawingGestureSessionTest {
    @Test
    fun `pen finish commits stroke and calls callback`() =
        runTest {
            val state = PdfDrawingState()
            val finished = mutableListOf<DrawingPath>()
            val session =
                session(
                    onStrokeFinished = { _, path -> finished += path },
                )

            session.startDraw(
                pageIndex = 0,
                state = state,
                nx = 0.1f,
                ny = 0.2f,
                pressure = 0.7f,
                tilt = 0.3f,
                tool = ToolMode.PEN,
            )
            session.addPoint(0, state, 0.2f, 0.3f, pressure = 0.8f, tilt = 0.4f)

            val completed = assertNotNull(session.finish())

            assertEquals(1, state.currentPaths.size)
            assertEquals(1, finished.size)
            assertEquals(completed, finished.single())
            assertEquals(ToolKind.PEN, completed.toolType)
            assertEquals(PEN_COLOR, completed.colorArgb)
            assertNear(PEN_WIDTH, completed.strokeWidth)
            assertNear(0.7f, completed.points.first().pressure)
        }

    @Test
    fun `marker snapshots width color and ignores pressure`() =
        runTest {
            val state = PdfDrawingState()
            var marker = MarkerSettings(colorArgb = MARKER_COLOR, strokeWidth = MARKER_WIDTH)
            val session =
                session(
                    markerSettings = { marker },
                )

            session.startDraw(
                pageIndex = 0,
                state = state,
                nx = 0.1f,
                ny = 0.2f,
                pressure = 0.2f,
                tilt = 0.9f,
                tool = ToolMode.MARKER,
            )
            marker = MarkerSettings(colorArgb = 0x8000BCD4L, strokeWidth = 0.05f)
            session.addPoint(0, state, 0.2f, 0.3f, pressure = 0.1f, tilt = 0.8f)

            val completed = assertNotNull(session.finish())

            assertEquals(ToolKind.MARKER, completed.toolType)
            assertEquals(MARKER_COLOR, completed.colorArgb)
            assertNear(MARKER_WIDTH, completed.strokeWidth)
            assertNear(1f, completed.points.first().pressure)
            assertNear(0f, completed.points.first().tilt)
        }

    @Test
    fun `eraser finish calls callback with before and after snapshots`() =
        runTest {
            val state = PdfDrawingState()
            state.currentPaths.add(
                DrawingPath(
                    points =
                        listOf(
                            DrawingPoint(0.48f, 0.5f, isNewPath = true),
                            DrawingPoint(0.52f, 0.5f),
                        ),
                ),
            )
            val erased = mutableListOf<Pair<List<DrawingPath>, List<DrawingPath>>>()
            val session =
                session(
                    eraserSettings = { EraserSettings(sizeNormalized = 0.1f, mode = EraserMode.OBJECT) },
                    onEraseFinished = { _, before, after -> erased += before to after },
                )

            session.startErase(0, state, 0.5f, 0.5f)
            session.finish()

            assertEquals(1, erased.size)
            assertEquals(1, erased.single().first.size)
            assertEquals(0, erased.single().second.size)
            assertEquals(0, state.currentPaths.size)
        }

    @Test
    fun `cancel discards live stroke without callback`() =
        runTest {
            val state = PdfDrawingState()
            val finished = mutableListOf<DrawingPath>()
            val session =
                session(
                    onStrokeFinished = { _, path -> finished += path },
                )

            session.startDraw(0, state, 0.1f, 0.1f, pressure = 1f, tilt = 0f, tool = ToolMode.PEN)
            session.addPoint(0, state, 0.2f, 0.2f, pressure = 1f, tilt = 0f)
            session.cancel()

            assertFalse(state.isDrawing.value)
            assertEquals(0, state.livePoints.size)
            assertEquals(0, state.currentPaths.size)
            assertEquals(0, finished.size)
        }

    private fun CoroutineScope.session(
        penSettings: () -> PenSettings = { PenSettings(colorArgb = PEN_COLOR, strokeWidth = PEN_WIDTH) },
        markerSettings: () -> MarkerSettings = { MarkerSettings(colorArgb = MARKER_COLOR, strokeWidth = MARKER_WIDTH) },
        eraserSettings: () -> EraserSettings = { EraserSettings() },
        onStrokeFinished: (Int, DrawingPath) -> Unit = { _, _ -> },
        onEraseFinished: (Int, List<DrawingPath>, List<DrawingPath>) -> Unit = { _, _, _ -> },
    ): DrawingGestureSession =
        DrawingGestureSession(
            penSettings = penSettings,
            markerSettings = markerSettings,
            eraserSettings = eraserSettings,
            eraserPosition = { mutableStateOf<EraserPosition?>(null) },
            onGestureStart = { _, _ -> },
            onStrokeFinished = onStrokeFinished,
            onEraseFinished = onEraseFinished,
            pageAspect = { 1f },
            scope = this,
        )

    private fun assertNear(
        expected: Float,
        actual: Float,
        eps: Float = 1e-6f,
    ) {
        kotlin.test.assertTrue(kotlin.math.abs(expected - actual) <= eps, "Expected $expected, got $actual")
    }

    private companion object {
        const val PEN_COLOR: Long = 0xFF336699L
        const val PEN_WIDTH: Float = 0.004f
        const val MARKER_COLOR: Long = 0x80FFEB3BL
        const val MARKER_WIDTH: Float = 0.03f
    }
}
