package ru.kyamshanov.notepen.annotation.domain

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StrokeSimplifierTest {

    @Test
    fun straightLineCollapsesToEndpoints() {
        val line = (0..99).map { i ->
            DrawingPoint(x = i / 99f, y = i / 99f, isNewPath = i == 0)
        }
        val result = StrokeSimplifier.simplify(line)
        assertEquals(2, result.size)
        assertEquals(line.first(), result.first())
        assertEquals(line.last(), result.last())
    }

    @Test
    fun preservesFirstPointFlagAndEndpoints() {
        val zigzag = listOf(
            DrawingPoint(0f, 0f, isNewPath = true, pressure = 0.5f, tilt = 0.1f),
            DrawingPoint(0.25f, 0.5f),
            DrawingPoint(0.5f, 0f),
            DrawingPoint(0.75f, 0.5f),
            DrawingPoint(1f, 0f, pressure = 0.9f),
        )
        val result = StrokeSimplifier.simplify(zigzag)
        assertTrue(result.first().isNewPath)
        assertEquals(0.5f, result.first().pressure)
        assertEquals(zigzag.last(), result.last())
        // Острые повороты зигзага должны сохраниться.
        assertTrue(result.size >= 4)
    }

    @Test
    fun keepsPointsWherePressureVariesOnStraightLine() {
        // Геометрически прямая линия, но с «горбом» нажатия в середине — точки
        // вокруг пика давления должны сохраниться, иначе профиль толщины станет ступенчатым.
        val line = (0..40).map { i ->
            val t = i / 40f
            val pressure = 1f - kotlin.math.abs(t - 0.5f) * 2f // 0 на концах, 1 в центре
            DrawingPoint(x = t, y = 0f, isNewPath = i == 0, pressure = pressure)
        }
        val result = StrokeSimplifier.simplify(line)
        assertTrue(result.size > 2, "профиль нажатия должен удержать промежуточные точки")
        val peak = result.maxByOrNull { it.pressure }
        assertTrue((peak?.pressure ?: 0f) > 0.9f, "пик давления должен сохраниться")
    }

    @Test
    fun shortListsReturnedUnchanged() {
        val single = listOf(DrawingPoint(0.1f, 0.2f, isNewPath = true))
        assertSame(single, StrokeSimplifier.simplify(single))

        val pair = listOf(DrawingPoint(0f, 0f), DrawingPoint(1f, 1f))
        assertSame(pair, StrokeSimplifier.simplify(pair))

        assertSame(emptyList(), StrokeSimplifier.simplify(emptyList()))
    }
}
