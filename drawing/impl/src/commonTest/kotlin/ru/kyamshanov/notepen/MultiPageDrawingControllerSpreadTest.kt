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
import ru.kyamshanov.notepen.drawing.api.ToolMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiPageDrawingControllerSpreadTest {
    @Test
    fun `spread hit-test writes left column into left page`() {
        val finished = mutableListOf<Pair<Int, DrawingPath>>()
        val controller =
            controller(
                onStrokeFinished = { page, path -> finished += page to path },
            )

        controller.onDown(Offset(50f, 50f), pressure = 1f, tilt = 0f)
        controller.onMove(Offset(60f, 55f), pressure = 1f, tilt = 0f)
        controller.onUp()

        assertEquals(1, finished.size)
        assertEquals(0, finished.single().first)
        assertTrue(finished.single().second.points.all { it.x in 0f..1f })
    }

    @Test
    fun `spread hit-test writes right column into right page`() {
        val finished = mutableListOf<Pair<Int, DrawingPath>>()
        val controller =
            controller(
                onStrokeFinished = { page, path -> finished += page to path },
            )

        controller.onDown(Offset(150f, 50f), pressure = 1f, tilt = 0f)
        controller.onMove(Offset(160f, 55f), pressure = 1f, tilt = 0f)
        controller.onUp()

        assertEquals(1, finished.size)
        assertEquals(1, finished.single().first)
        assertTrue(finished.single().second.points.all { it.x in 0f..1f })
    }

    private fun controller(onStrokeFinished: (Int, DrawingPath) -> Unit): MultiPageDrawingController =
        MultiPageDrawingController(
            drawingStates = mutableStateMapOf(),
            geometry = SpreadGeometry,
            toolMode = { ToolMode.PEN },
            penSettings = { PenSettings(colorArgb = 0xFF1E88E5L, strokeWidth = 0.003f) },
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

    private object SpreadGeometry : PageLayoutGeometry {
        override val pageCount: Int = 2
        override val basePageWidthPx: Float = 100f
        override val zoom: Float = 1f
        override val pan: Offset = Offset.Zero
        override val isSpread: Boolean = true

        override fun pageTopPx(index: Int): Float = 0f

        override fun pageLeftPx(index: Int): Float = if (index == 0) 0f else 116f

        override fun pdfHeightPx(index: Int): Float = 100f
    }
}
