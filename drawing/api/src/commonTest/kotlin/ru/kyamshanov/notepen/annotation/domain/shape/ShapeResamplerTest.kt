package ru.kyamshanov.notepen.annotation.domain.shape

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShapeResamplerTest {
    @Test
    fun lineHasFirstPointMarked() {
        val line =
            RecognizedShape.Line(
                start = DrawingPoint(0.1f, 0.2f),
                end = DrawingPoint(0.8f, 0.9f),
            )
        val pts = ShapeResampler.toPoints(line, basePressure = 0.7f, baseTilt = 0.2f)
        assertTrue(pts.size > 8)
        assertTrue(pts.first().isNewPath)
        assertTrue(pts.drop(1).none { it.isNewPath })
        assertEquals(0.7f, pts.first().pressure)
    }

    @Test
    fun ellipseIsClosed() {
        val ellipse = RecognizedShape.Ellipse(cx = 0.5f, cy = 0.5f, rx = 0.2f, ry = 0.15f)
        val pts = ShapeResampler.toPoints(ellipse, basePressure = 1f, baseTilt = 0f)
        assertTrue(pts.size > 32)
        val dx = abs(pts.first().x - pts.last().x)
        val dy = abs(pts.first().y - pts.last().y)
        assertTrue(dx < 1e-3f && dy < 1e-3f, "ellipse must close on itself")
    }

    @Test
    fun rectangleIsClosed() {
        val rect = RecognizedShape.Rectangle(left = 0.2f, top = 0.3f, right = 0.7f, bottom = 0.6f)
        val pts = ShapeResampler.toPoints(rect, basePressure = 1f, baseTilt = 0f)
        assertTrue(pts.size > 32)
        assertTrue(pts.first().isNewPath)
        assertEquals(rect.left, pts.first().x)
        assertEquals(rect.top, pts.first().y)
        assertEquals(rect.left, pts.last().x)
        assertEquals(rect.top, pts.last().y)
    }
}
