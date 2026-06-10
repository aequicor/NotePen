package ru.kyamshanov.notepen

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.test.TestScope
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserMode
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.EraserShape
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.drawing.api.PdfDrawingState
import ru.kyamshanov.notepen.drawing.api.ToolMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests that coordinates are clamped to the page extent in [MultiPageDrawingController] and that
 * cross-page cap-hiding (handBoundary extrapolation beyond ny=0/1) is preserved.
 */
class MultiPageDrawingControllerClampTest {
    // -- vertical clamping at document extremities --

    @Test
    fun `onDown above page 0 clamps ny to 0`() {
        val finished = mutableListOf<Pair<Int, DrawingPath>>()
        val controller = stackController(onStrokeFinished = { page, path -> finished += page to path })

        // viewportY < pageTopPx(0) = 0 → above first page
        controller.onDown(Offset(50f, -20f), pressure = 1f, tilt = 0f)
        controller.onUp()

        assertEquals(1, finished.size)
        val pts = finished[0].second.points
        assertTrue(pts.all { it.y >= 0f }, "all points must be clamped to y >= 0")
    }

    @Test
    fun `onMove below last page clamps ny to 1`() {
        val finished = mutableListOf<Pair<Int, DrawingPath>>()
        val controller = stackController(onStrokeFinished = { page, path -> finished += page to path })

        controller.onDown(Offset(50f, 150f), pressure = 1f, tilt = 0f)
        controller.onMove(Offset(50f, 250f), pressure = 1f, tilt = 0f)
        controller.onUp()

        val pts = finished.flatMap { it.second.points }
        assertTrue(pts.all { it.y <= 1f }, "all points must be clamped to y <= 1")
    }

    @Test
    fun `cross-page boundary — old page gets extrapolated ny greater than 1`() {
        // Stroke that starts on page 0 and moves to page 1 — the old page must
        // receive a capped point with ny > 1 (handBoundary cap-hiding mechanism).
        val pointsByPage = mutableMapOf<Int, MutableList<DrawingPath>>()
        val controller =
            stackController(
                onStrokeFinished = { page, path ->
                    pointsByPage.getOrPut(page) { mutableListOf() } += path
                },
            )

        // Start on page 0 (y=50 → ny=0.5 on 100px-tall page)
        controller.onDown(Offset(50f, 50f), pressure = 1f, tilt = 0f)
        // Move to page 1 (y=110 → into the second page territory)
        controller.onMove(Offset(50f, 110f), pressure = 1f, tilt = 0f)
        controller.onUp()

        // Page 0 should have a finished stroke; its last point should have ny > 1
        val page0Paths = pointsByPage[0]
        assertTrue(page0Paths != null && page0Paths.isNotEmpty(), "page 0 must have a stroke")
        val lastY = page0Paths.last().points.last().y
        assertTrue(lastY > 1f, "last point of page-0 stroke must have ny > 1 (cap-hiding); was $lastY")
    }

    // -- isInsidePdfPage respects extent --

    @Test
    fun `isInsidePdfPage returns false outside default extent`() {
        val controller = stackController()
        // x = 1.1, normalised to 1.1 — outside [0,1] default extent
        assertFalse(controller.isInsidePdfPage(Offset(110f, 50f)))
    }

    @Test
    fun `isInsidePdfPage returns true inside widened extent`() {
        val drawingStates = mutableStateMapOf<Int, PdfDrawingState>()
        val state = PdfDrawingState().also { it.expandRight() }
        drawingStates[0] = state
        val controller = stackController(drawingStates = drawingStates)
        // x = 1.1 * basePageWidthPx = 110 → inside widened extent (right = 4/3 ≈ 1.33)
        assertTrue(controller.isInsidePdfPage(Offset(110f, 50f)))
    }

    // -- helpers --

    private fun stackController(
        drawingStates: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, PdfDrawingState> = mutableStateMapOf(),
        onStrokeFinished: (Int, DrawingPath) -> Unit = { _, _ -> },
    ): MultiPageDrawingController =
        MultiPageDrawingController(
            drawingStates = drawingStates,
            geometry = StackGeometry,
            toolMode = { ToolMode.PEN },
            penSettings = { PenSettings(colorArgb = 0xFF000000L, strokeWidth = 0.003f) },
            markerSettings = { MarkerSettings() },
            eraserSettings = {
                EraserSettings(
                    sizeNormalized = 0.05f,
                    mode = EraserMode.POINT,
                    shape = EraserShape.CIRCLE,
                )
            },
            eraserOverride = { false },
            onGestureStart = { _, _ -> },
            onStrokeFinished = onStrokeFinished,
            onEraseFinished = { _, _, _ -> },
            scope = TestScope(),
        )

    /** Two pages stacked vertically, each 100×100px, basePageWidthPx=100. */
    private object StackGeometry : PageLayoutGeometry {
        override val pageCount: Int = 2
        override val basePageWidthPx: Float = 100f
        override val zoom: Float = 1f
        override val pan: Offset = Offset.Zero
        override val isSpread: Boolean = false

        override fun pageTopPx(index: Int): Float = index * 100f

        override fun pageLeftPx(index: Int): Float = 0f

        override fun pdfHeightPx(index: Int): Float = 100f
    }
}

private fun assertFalse(
    value: Boolean,
    message: String = "Expected false but was true",
) = assertTrue(!value, message)
