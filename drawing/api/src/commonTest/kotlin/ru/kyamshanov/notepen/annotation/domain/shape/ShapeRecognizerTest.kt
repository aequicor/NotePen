package ru.kyamshanov.notepen.annotation.domain.shape

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShapeRecognizerTest {
    private val aspectA4 = 595f / 842f
    private val rng = Random(seed = 42)

    private fun jitter(amp: Float): Float = (rng.nextFloat() - 0.5f) * 2f * amp

    @Test
    fun straightishStrokeRecognizedAsLine() {
        val pts =
            List(40) { i ->
                val t = i / 39f
                DrawingPoint(
                    x = 0.1f + 0.6f * t + jitter(0.002f),
                    y = 0.2f + 0.4f * t + jitter(0.002f),
                )
            }
        val shape = ShapeRecognizer.recognize(pts, aspectA4)
        assertTrue(shape is RecognizedShape.Line, "expected Line, got $shape")
    }

    @Test
    fun noisyCircleRecognizedAsEllipse() {
        val cx = 0.5f
        val cy = 0.5f
        val rx = 0.20f
        val ry = 0.20f * aspectA4 // визуально круг на A4
        val n = 80
        val pts =
            List(n + 1) { i ->
                val theta = (i.toFloat() / n) * 2f * PI.toFloat()
                DrawingPoint(
                    x = cx + rx * cos(theta) + jitter(0.006f),
                    y = cy + ry * sin(theta) + jitter(0.006f),
                )
            }
        val shape = ShapeRecognizer.recognize(pts, aspectA4)
        assertTrue(shape is RecognizedShape.Ellipse, "expected Ellipse, got $shape")
    }

    @Test
    fun closedRectangleStrokeRecognizedAsRectangle() {
        // Замкнутый прямоугольник со срезанными «дрожащими» сторонами.
        val left = 0.2f
        val right = 0.7f
        val top = 0.3f
        val bottom = 0.6f
        val perSide = 30
        val pts = ArrayList<DrawingPoint>()
        val corners =
            listOf(
                left to top,
                right to top,
                right to bottom,
                left to bottom,
                left to top,
            )
        for (side in 0 until 4) {
            val (sx, sy) = corners[side]
            val (ex, ey) = corners[side + 1]
            for (i in 0 until perSide) {
                val t = i.toFloat() / perSide
                pts.add(
                    DrawingPoint(
                        x = sx + (ex - sx) * t + jitter(0.004f),
                        y = sy + (ey - sy) * t + jitter(0.004f),
                    ),
                )
            }
        }
        pts.add(DrawingPoint(x = left, y = top))
        val shape = ShapeRecognizer.recognize(pts, aspectA4)
        assertTrue(shape is RecognizedShape.Rectangle, "expected Rectangle, got $shape")
    }

    @Test
    fun shortZigzagNotRecognized() {
        val pts =
            listOf(
                DrawingPoint(0.1f, 0.1f),
                DrawingPoint(0.2f, 0.3f),
                DrawingPoint(0.1f, 0.5f),
                DrawingPoint(0.2f, 0.7f),
                DrawingPoint(0.1f, 0.9f),
            )
        assertNull(ShapeRecognizer.recognize(pts, aspectA4))
    }

    @Test
    fun tooFewPointsRejected() {
        val pts =
            listOf(
                DrawingPoint(0.1f, 0.1f),
                DrawingPoint(0.9f, 0.9f),
            )
        assertNull(ShapeRecognizer.recognize(pts, aspectA4))
    }
}
