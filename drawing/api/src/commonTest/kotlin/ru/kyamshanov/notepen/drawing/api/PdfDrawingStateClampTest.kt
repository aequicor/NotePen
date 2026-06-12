package ru.kyamshanov.notepen.drawing.api

import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfDrawingStateClampTest {
    // -- X-clamping in startDrawing / addPoint --

    @Test
    fun `startDrawing beyond right edge clamps to right`() {
        val state = PdfDrawingState()
        state.startDrawing(x = 1.5f, y = 0.5f)
        assertEquals(1f, state.livePoints.first().x)
    }

    @Test
    fun `startDrawing beyond left edge clamps to left`() {
        val state = PdfDrawingState()
        state.startDrawing(x = -0.5f, y = 0.5f)
        assertEquals(0f, state.livePoints.first().x)
    }

    @Test
    fun `addPoint beyond right edge clamps to right`() {
        val state = PdfDrawingState()
        state.startDrawing(x = 0.5f, y = 0.5f)
        state.addPoint(x = 2.0f, y = 0.6f)
        assertEquals(1f, state.livePoints.last().x)
    }

    @Test
    fun `addPoint with widened extent clamps to new right`() {
        val state = PdfDrawingState()
        state.expandRight()
        val newRight = state.extent.value.right
        state.startDrawing(x = 0.5f, y = 0.5f)
        state.addPoint(x = newRight + 0.5f, y = 0.6f)
        assertEquals(newRight, state.livePoints.last().x, absoluteTolerance = 1e-6f)
    }

    @Test
    fun `drawing at boundary does not move extent`() {
        val state = PdfDrawingState()
        val extentBefore = state.extent.value
        state.startDrawing(x = 0.99f, y = 0.5f)
        state.addPoint(x = 1.01f, y = 0.6f)
        state.finishDrawing()
        assertEquals(extentBefore, state.extent.value, "extent must not grow from clamped input")
    }

    @Test
    fun `y coordinates are not clamped (needed for cross-page stitching)`() {
        val state = PdfDrawingState()
        state.startDrawing(x = 0.5f, y = 1.2f)
        assertEquals(1.2f, state.livePoints.first().y)

        state.addPoint(x = 0.5f, y = -0.3f)
        assertEquals(-0.3f, state.livePoints.last().y)
    }

    // -- expand API --

    @Test
    fun `expandRight changes extent right boundary`() {
        val state = PdfDrawingState()
        val vBefore = state.historyVersion.value
        state.expandRight()
        assertEquals(4f / 3f, state.extent.value.right, absoluteTolerance = 1e-6f)
        assertTrue(state.historyVersion.value > vBefore, "historyVersion must be bumped")
    }

    @Test
    fun `expandLeft changes extent left boundary`() {
        val state = PdfDrawingState()
        state.expandLeft()
        assertEquals(-1f / 3f, state.extent.value.left, absoluteTolerance = 1e-6f)
    }

    @Test
    fun `expand does not clear extent to Pdf`() {
        val state = PdfDrawingState()
        state.expandRight()
        state.expandLeft()
        assertFalse(state.extent.value == PageExtent.Pdf)
    }

    // -- LiveStrokeSample listener sees clamped X --

    @Test
    fun `live stroke listener receives clamped x value`() {
        val state = PdfDrawingState()
        val samples = mutableListOf<LiveStrokeSample>()
        state.addLiveStrokeListener { samples += it }

        state.startDrawing(x = 1.5f, y = 0.5f)
        assertEquals(1f, samples.first().current.x)

        state.addPoint(x = 2.0f, y = 0.6f)
        assertEquals(1f, samples.last().current.x)
    }
}

private fun assertEquals(
    expected: Float,
    actual: Float,
    absoluteTolerance: Float,
    message: String = "Expected $expected but was $actual (tolerance $absoluteTolerance)",
) {
    assertTrue(kotlin.math.abs(expected - actual) <= absoluteTolerance, message)
}
