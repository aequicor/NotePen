package ru.kyamshanov.notepen.annotation.domain

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StrokeStairSmootherTest {
    @Test
    fun smoothsPixelStairDiagonal() {
        val stair =
            listOf(
                DrawingPoint(0f, 0f, isNewPath = true, pressure = 0.4f),
                DrawingPoint(0.01f, 0f, pressure = 0.5f),
                DrawingPoint(0.01f, 0.01f, pressure = 0.6f),
                DrawingPoint(0.02f, 0.01f, pressure = 0.7f),
                DrawingPoint(0.02f, 0.02f, pressure = 0.8f),
                DrawingPoint(0.03f, 0.02f, pressure = 0.9f),
                DrawingPoint(0.03f, 0.03f, pressure = 1f),
            )

        assertTrue(StrokeStairSmoother.looksLikePixelStairs(stair))

        val result = StrokeStairSmoother.smoothIfStairStepped(stair)

        assertEquals(stair.first(), result.first())
        assertEquals(stair.last(), result.last())
        assertEquals(stair.map { it.pressure }, result.map { it.pressure })
        assertFalse(result[1].y == stair[1].y && result[2].x == stair[2].x)
    }

    @Test
    fun leavesAlreadySmoothDiagonalUntouched() {
        val smooth =
            (0..12).map { i ->
                val t = i / 12f
                DrawingPoint(t, t * 0.8f, isNewPath = i == 0)
            }

        assertFalse(StrokeStairSmoother.looksLikePixelStairs(smooth))
        assertSame(smooth, StrokeStairSmoother.smoothIfStairStepped(smooth))
    }

    @Test
    fun keepsLowPressureTailSamples() {
        val stroke =
            listOf(
                DrawingPoint(0f, 0f, isNewPath = true, pressure = 0.7f),
                DrawingPoint(0.1f, 0.1f, pressure = 0.8f),
                DrawingPoint(0.2f, 0.2f, pressure = 0.75f),
                DrawingPoint(0.35f, 0.05f, pressure = 0.02f),
            )

        val result = StrokeStairSmoother.cleanPenStroke(stroke)

        assertEquals(stroke, result)
    }

    @Test
    fun keepsLowPressureStartSamples() {
        val stroke =
            listOf(
                DrawingPoint(0f, 0f, isNewPath = true, pressure = 0.02f),
                DrawingPoint(0.05f, 0.2f, pressure = 0.08f),
                DrawingPoint(0.1f, 0.4f, pressure = 0.7f),
                DrawingPoint(0.2f, 0.5f, pressure = 0.8f),
            )

        val result = StrokeStairSmoother.cleanPenStroke(stroke)

        assertEquals(stroke, result)
    }
}
