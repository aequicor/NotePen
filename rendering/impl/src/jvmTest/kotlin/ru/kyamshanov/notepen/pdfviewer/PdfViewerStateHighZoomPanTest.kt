package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PdfViewerStateHighZoomPanTest {
    @Test
    fun `high zoom drag keeps committed pan unchanged until commit`() {
        val state = highZoomState()
        val initialPan = state.pan
        val delta = Offset(x = -120f, y = -80f)

        state.beginTransientPanGesture()
        state.transientPanGestureBy(delta)

        assertEquals(initialPan, state.pan)
        assertEquals(delta, state.gestureTranslation)
        assertEquals(initialPan + delta, state.effectivePan)
        assertTrue(state.isVisualTransformActive)

        state.commitPinchGesture()

        assertEquals(initialPan + delta, state.pan)
        assertEquals(Offset.Zero, state.gestureTranslation)
        assertEquals(state.pan, state.effectivePan)
        assertFalse(state.isVisualTransformActive)
    }

    @Test
    fun `high zoom wheel scroll keeps committed pan unchanged until commit`() {
        val state = highZoomState()
        val initialPan = state.pan
        val delta = Offset(x = 0f, y = -150f)

        state.transientWheelScrollBy(delta)

        assertEquals(initialPan, state.pan)
        assertEquals(delta, state.gestureTranslation)
        assertEquals(initialPan + delta, state.effectivePan)
        assertTrue(state.isVisualTransformActive)

        state.commitPinchGesture()

        assertEquals(initialPan + delta, state.pan)
        assertEquals(Offset.Zero, state.gestureTranslation)
        assertEquals(state.pan, state.effectivePan)
        assertFalse(state.isVisualTransformActive)
    }

    @Test
    fun `normal drag mutates committed pan immediately`() {
        val state = lowZoomState()
        val initialPan = state.pan
        val delta = Offset(x = -120f, y = -80f)

        state.beginPanGesture()
        state.panGestureBy(delta)

        assertEquals(Offset.Zero, state.gestureTranslation)
        assertFalse(state.isVisualTransformActive)
        assertNotEquals(initialPan, state.pan)
        assertWithin(delta, state.pan - initialPan)
    }

    private fun highZoomState(): PdfViewerState =
        stateAtScale(scalePercent = 400).also {
            assertTrue(it.renderScalePercent >= 300)
        }

    private fun lowZoomState(): PdfViewerState =
        stateAtScale(scalePercent = 100).also {
            assertTrue(it.renderScalePercent < 300)
        }

    private fun stateAtScale(scalePercent: Int): PdfViewerState =
        PdfViewerState().apply {
            viewportSize = IntSize(width = 1000, height = 1000)
            pages = listOf(squarePage())
            applyPendingInitialScrollIfNeeded()
            setScalePercent(scalePercent)
        }

    private fun squarePage(): PdfPageInfo = PdfPageInfo(pageIndex = 0, widthPt = 1000f, heightPt = 1000f)

    private fun assertWithin(
        expected: Offset,
        actual: Offset,
    ) {
        assertTrue(abs(actual.x - expected.x) < EPSILON, "expected x=${expected.x}, actual x=${actual.x}")
        assertTrue(abs(actual.y - expected.y) < EPSILON, "expected y=${expected.y}, actual y=${actual.y}")
    }

    private companion object {
        const val EPSILON = 1e-3f
    }
}
