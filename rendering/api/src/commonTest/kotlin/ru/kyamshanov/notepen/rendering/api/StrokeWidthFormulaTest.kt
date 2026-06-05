package ru.kyamshanov.notepen.rendering.api

import kotlin.test.Test
import kotlin.test.assertTrue

class StrokeWidthFormulaTest {
    @Test
    fun `segment width keeps a visible floor for weak pressure`() {
        val baseWidthPx = 10f

        val downWidth = computeSegmentWidth(baseWidthPx = baseWidthPx, pressure = 0f, tilt = 0f)
        val weakWidth = computeSegmentWidth(baseWidthPx = baseWidthPx, pressure = 0.05f, tilt = 0f)
        val fullWidth = computeSegmentWidth(baseWidthPx = baseWidthPx, pressure = 1f, tilt = 0f)

        assertTrue(downWidth > RenderingConstants.MIN_RENDERED_STROKE_PX)
        assertTrue(weakWidth > downWidth)
        assertTrue(fullWidth > weakWidth)
    }
}
