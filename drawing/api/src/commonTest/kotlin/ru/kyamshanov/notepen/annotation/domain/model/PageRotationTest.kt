package ru.kyamshanov.notepen.annotation.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageRotationTest {
    private fun assertPointEquals(
        expectedX: Float,
        expectedY: Float,
        actual: DrawingPoint,
    ) {
        assertTrue(
            kotlin.math.abs(expectedX - actual.x) < EPS && kotlin.math.abs(expectedY - actual.y) < EPS,
            "expected ($expectedX, $expectedY) but was (${actual.x}, ${actual.y})",
        )
    }

    @Test
    fun nextQuarterCyclesZeroToThreeAndWraps() {
        assertEquals(1, PageRotation.nextQuarter(0))
        assertEquals(2, PageRotation.nextQuarter(1))
        assertEquals(3, PageRotation.nextQuarter(2))
        assertEquals(0, PageRotation.nextQuarter(3))
    }

    @Test
    fun normalizeQuartersHandlesNegativeAndLarge() {
        assertEquals(0, PageRotation.normalizeQuarters(0))
        assertEquals(3, PageRotation.normalizeQuarters(-1))
        assertEquals(1, PageRotation.normalizeQuarters(5))
        assertEquals(2, PageRotation.normalizeQuarters(-2))
    }

    @Test
    fun effectiveDegreesCombinesNativeAndUser() {
        assertEquals(90, PageRotation.effectiveDegrees(nativeRotationDegrees = 0, userQuarters = 1))
        assertEquals(0, PageRotation.effectiveDegrees(nativeRotationDegrees = 90, userQuarters = 3))
        assertEquals(180, PageRotation.effectiveDegrees(nativeRotationDegrees = 270, userQuarters = 3))
    }

    // Угол 90° CW: верхний край страницы становится правым. Проверяем все 4 угла
    // единичного квадрата (Y вниз). Это «контракт», с которым обязан совпадать
    // поворот растра, иначе штрихи уедут с содержимого.
    @Test
    fun rotatePointCwMapsCornersClockwise() {
        assertPointEquals(1f, 0f, PageRotation.rotatePointCw(DrawingPoint(0f, 0f))) // TL -> TR
        assertPointEquals(1f, 1f, PageRotation.rotatePointCw(DrawingPoint(1f, 0f))) // TR -> BR
        assertPointEquals(0f, 1f, PageRotation.rotatePointCw(DrawingPoint(1f, 1f))) // BR -> BL
        assertPointEquals(0f, 0f, PageRotation.rotatePointCw(DrawingPoint(0f, 1f))) // BL -> TL
    }

    @Test
    fun fourCwRotationsReturnToOrigin() {
        val original = DrawingPoint(0.3f, 0.7f, isNewPath = true, pressure = 0.5f, tilt = 0.2f)
        val back = PageRotation.rotatePoint(original, 4)
        assertPointEquals(original.x, original.y, back)
        // Метаданные точки сохраняются при повороте.
        assertEquals(original.isNewPath, back.isNewPath)
        assertEquals(original.pressure, back.pressure)
        assertEquals(original.tilt, back.tilt)
    }

    @Test
    fun rotatePointTwiceEqualsPointReflection() {
        // 180°: (x, y) -> (1 - x, 1 - y).
        val rotated = PageRotation.rotatePoint(DrawingPoint(0.25f, 0.1f), 2)
        assertPointEquals(0.75f, 0.9f, rotated)
    }

    @Test
    fun rotatePathScalesStrokeWidthByAspectOnQuarterTurn() {
        val path = DrawingPath(points = listOf(DrawingPoint(0.2f, 0.4f)), strokeWidth = 0.01f)
        // Портретная страница A4: aspect = 0.707; после поворота толщина = 0.01 * 0.707.
        val rotated = PageRotation.rotatePathCw(path, pageAspect = 0.707f)
        assertTrue(kotlin.math.abs(rotated.strokeWidth - 0.01f * 0.707f) < EPS)
        assertPointEquals(0.6f, 0.2f, rotated.points.first())
    }

    @Test
    fun rotateExtentCwKeepsPdfRectInvariant() {
        // Прямоугольник самой PDF-страницы [0,1]x[0,1] поворотом не выходит за себя.
        val rotated = PageRotation.rotateExtentCw(PageExtent.Pdf)
        assertTrue(kotlin.math.abs(rotated.left - 0f) < EPS)
        assertTrue(kotlin.math.abs(rotated.top - 0f) < EPS)
        assertTrue(kotlin.math.abs(rotated.right - 1f) < EPS)
        assertTrue(kotlin.math.abs(rotated.bottom - 1f) < EPS)
    }

    @Test
    fun rotateExtentCwMovesAsymmetricMarginToCorrectSide() {
        // Extent, расширенный вниз (bottom = 1.5): после +90° CW нижний край
        // уходит влево (left = -0.5).
        val extent = PageExtent(left = 0f, top = 0f, right = 1f, bottom = 1.5f)
        val rotated = PageRotation.rotateExtentCw(extent)
        assertTrue(kotlin.math.abs(rotated.left - (-0.5f)) < EPS, "left=${rotated.left}")
        assertTrue(kotlin.math.abs(rotated.right - 1f) < EPS, "right=${rotated.right}")
        assertTrue(kotlin.math.abs(rotated.top - 0f) < EPS, "top=${rotated.top}")
        assertTrue(kotlin.math.abs(rotated.bottom - 1f) < EPS, "bottom=${rotated.bottom}")
    }

    private companion object {
        const val EPS = 1e-4f
    }
}
