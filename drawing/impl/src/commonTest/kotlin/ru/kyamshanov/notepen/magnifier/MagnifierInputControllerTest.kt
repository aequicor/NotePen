package ru.kyamshanov.notepen.magnifier

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.drawing.api.EraserPosition
import ru.kyamshanov.notepen.drawing.api.PdfDrawingState
import ru.kyamshanov.notepen.drawing.api.ToolMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MagnifierInputControllerTest {
    @Test
    fun `pen stroke width is divided by loupe magnification`() =
        runTest {
            val state = PdfDrawingState()
            val geometry =
                FakeMagnifierGeometry(
                    pageCanvasWidthPx = 1000f,
                    segments =
                        listOf(
                            MagnifierPageSegment(
                                pageIndex = 0,
                                targetOnPage = Rect(0f, 0f, 0.25f, 0.2f),
                                panelTopFrac = 0f,
                                panelBottomFrac = 1f,
                            ),
                        ),
                )
            val controller = controller(geometry, mutableMapOf(0 to state))

            controller.onDown(Offset(250f, 50f), PANEL, pressure = 1f, tilt = 0f)

            assertNear(PEN_WIDTH / 2f, state.liveStrokeWidth.value)
        }

    @Test
    fun `panel y selects matching segment and maps point inside it`() =
        runTest {
            val states = mutableMapOf(0 to PdfDrawingState(), 1 to PdfDrawingState())
            val geometry =
                FakeMagnifierGeometry(
                    pageCanvasWidthPx = 1000f,
                    segments =
                        listOf(
                            MagnifierPageSegment(
                                pageIndex = 0,
                                targetOnPage = Rect(0f, 0f, 1f, 0.5f),
                                panelTopFrac = 0f,
                                panelBottomFrac = 0.5f,
                            ),
                            MagnifierPageSegment(
                                pageIndex = 1,
                                targetOnPage = Rect(0f, 0.5f, 1f, 1f),
                                panelTopFrac = 0.5f,
                                panelBottomFrac = 1f,
                            ),
                        ),
                )
            val controller = controller(geometry, states)

            controller.onDown(Offset(30f, 75f), PANEL, pressure = 1f, tilt = 0f)

            assertFalse(states.getValue(0).isDrawing.value)
            assertTrue(states.getValue(1).isDrawing.value)
            val first = states.getValue(1).livePoints.single()
            assertNear(0.06f, first.x)
            assertNear(0.75f, first.y)
        }

    @Test
    fun `cancel discards magnifier live stroke`() =
        runTest {
            val state = PdfDrawingState()
            val finished = mutableListOf<DrawingPath>()
            val controller =
                controller(
                    geometry = singleSegmentGeometry(),
                    states = mutableMapOf(0 to state),
                    onStrokeFinished = { _, path -> finished += path },
                )

            controller.onDown(Offset(20f, 20f), PANEL, pressure = 1f, tilt = 0f)
            controller.onMove(Offset(40f, 40f), PANEL, pressure = 1f, tilt = 0f)
            controller.onCancel()

            assertFalse(state.isDrawing.value)
            assertEquals(0, state.livePoints.size)
            assertEquals(0, state.currentPaths.size)
            assertEquals(0, finished.size)
        }

    @Test
    fun `auto-scroll shifts target after pen-up near panel edge`() =
        runTest {
            val state = PdfDrawingState()
            val geometry =
                singleSegmentGeometry(
                    target = Rect(0f, 0f, 0.2f, 0.2f),
                    autoScrollEnabled = true,
                )
            val controller = controller(geometry, mutableMapOf(0 to state))

            controller.onDown(Offset(250f, 50f), PANEL, pressure = 1f, tilt = 0f)
            controller.onMove(Offset(475f, 50f), PANEL, pressure = 1f, tilt = 0f)
            controller.onUp(PANEL)

            val target = geometry.segments.single().targetOnPage
            assertNear(0.07f, target.left)
            assertNear(0.27f, target.right)
        }

    private fun CoroutineScope.controller(
        geometry: MagnifierGeometry,
        states: MutableMap<Int, PdfDrawingState>,
        toolMode: () -> ToolMode = { ToolMode.PEN },
        onStrokeFinished: (Int, DrawingPath) -> Unit = { _, _ -> },
    ): MagnifierInputController =
        MagnifierInputController(
            geometry = geometry,
            pdfDrawingStateProvider = { pageIndex -> states.getOrPut(pageIndex) { PdfDrawingState() } },
            toolMode = toolMode,
            penSettings = { PenSettings(colorArgb = 0xFF336699L, strokeWidth = PEN_WIDTH) },
            markerSettings = { MarkerSettings() },
            eraserSettings = { EraserSettings() },
            eraserOverride = { false },
            eraserPos = mutableStateOf<EraserPosition?>(null),
            onGestureStart = { _, _ -> },
            onStrokeFinished = onStrokeFinished,
            onEraseFinished = { _, _, _ -> },
            scope = this,
            pageAspect = { 1f },
        )

    private fun singleSegmentGeometry(
        target: Rect = Rect(0f, 0f, 1f, 1f),
        autoScrollEnabled: Boolean = false,
    ): FakeMagnifierGeometry =
        FakeMagnifierGeometry(
            pageCanvasWidthPx = 1000f,
            segments =
                listOf(
                    MagnifierPageSegment(
                        pageIndex = 0,
                        targetOnPage = target,
                        panelTopFrac = 0f,
                        panelBottomFrac = 1f,
                    ),
                ),
            autoScrollEnabled = autoScrollEnabled,
        )

    private fun assertNear(
        expected: Float,
        actual: Float,
        eps: Float = 1e-5f,
    ) {
        assertTrue(abs(expected - actual) <= eps, "Expected $expected, got $actual")
    }

    private class FakeMagnifierGeometry(
        override val pageCanvasWidthPx: Float,
        override var segments: List<MagnifierPageSegment>,
        override val autoScrollEnabled: Boolean = false,
    ) : MagnifierGeometry {
        override fun setSingleSegmentTarget(
            pageIndex: Int,
            targetOnPage: Rect,
        ) {
            segments =
                listOf(
                    MagnifierPageSegment(
                        pageIndex = pageIndex,
                        targetOnPage = targetOnPage,
                        panelTopFrac = 0f,
                        panelBottomFrac = 1f,
                    ),
                )
        }
    }

    private companion object {
        val PANEL = Size(500f, 100f)
        const val PEN_WIDTH: Float = 0.01f
    }
}
