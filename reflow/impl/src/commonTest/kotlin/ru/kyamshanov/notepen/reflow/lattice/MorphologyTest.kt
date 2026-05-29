package ru.kyamshanov.notepen.reflow.lattice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MorphologyTest {
    @Test
    fun `argbToBinary marks pixels below threshold as dark`() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val gray200 = argb(200, 200, 200) // 200 ≥ 180 default threshold → светлый
        val gray100 = argb(100, 100, 100) // 100 < 180 → тёмный
        val binary = Morphology.argbToBinary(intArrayOf(white, black, gray200, gray100))
        assertFalse(binary[0])
        assertTrue(binary[1])
        assertFalse(binary[2])
        assertTrue(binary[3])
    }

    @Test
    fun `argbToBinary uses min-channel — coloured pixel with one dark channel still counts as dark`() {
        // Анти-aliasing на гранях чёрной линии часто даёт цветные пиксели c одним
        // тёмным каналом (например, ARGB 255,30,30,200 на синем подложке). Min-channel
        // надёжно ловит такие случаи в отличие от усреднённой luma.
        val edge = argb(30, 30, 200) // min=30 < 180 → тёмный
        val binary = Morphology.argbToBinary(intArrayOf(edge))
        assertTrue(binary[0])
    }

    @Test
    fun `findHorizontalRuns ignores runs shorter than minLength`() {
        // 10-pixel dark run; minLength=20 → отсекается (типичный кейс буквы текста).
        val width = 30
        val binary = BooleanArray(width)
        for (i in 5..14) binary[i] = true
        val runs = Morphology.findHorizontalRuns(binary, width = width, height = 1, minLength = 20)
        assertTrue(runs.isEmpty())
    }

    @Test
    fun `findHorizontalRuns returns segment for runs at or above minLength`() {
        val width = 30
        val binary = BooleanArray(width)
        for (i in 5..24) binary[i] = true // 20-pixel run
        val runs = Morphology.findHorizontalRuns(binary, width = width, height = 1, minLength = 20)
        assertEquals(1, runs.size)
        val seg = runs.single()
        assertEquals(5, seg.start)
        assertEquals(24, seg.end)
        assertEquals(0, seg.perpPos)
    }

    @Test
    fun `findVerticalRuns walks columns analogously`() {
        // 20-pixel vertical run в столбце x=3 (rows 5..24).
        val width = 10
        val height = 30
        val binary = BooleanArray(width * height)
        for (y in 5..24) binary[y * width + 3] = true
        val runs = Morphology.findVerticalRuns(binary, width = width, height = height, minLength = 20)
        assertEquals(1, runs.size)
        val seg = runs.single()
        assertEquals(5, seg.start)
        assertEquals(24, seg.end)
        assertEquals(3, seg.perpPos)
    }

    @Test
    fun `clusterToGridLines merges close perpendiculars to a single line at the median`() {
        val segments =
            listOf(
                Morphology.LineSegment(start = 0, end = 100, perpPos = 10),
                Morphology.LineSegment(start = 0, end = 100, perpPos = 11),
                Morphology.LineSegment(start = 0, end = 100, perpPos = 12),
            )
        val lines = Morphology.clusterToGridLines(segments, perpTolerancePx = 2)
        assertEquals(1, lines.size)
        assertEquals(11, lines.single().position)
        assertEquals(0, lines.single().start)
        assertEquals(100, lines.single().end)
    }

    @Test
    fun `clusterToGridLines unions extents inside one cluster`() {
        val segments =
            listOf(
                Morphology.LineSegment(start = 5, end = 50, perpPos = 10),
                Morphology.LineSegment(start = 30, end = 90, perpPos = 11),
            )
        val lines = Morphology.clusterToGridLines(segments, perpTolerancePx = 2)
        assertEquals(1, lines.size)
        assertEquals(5, lines.single().start)
        assertEquals(90, lines.single().end)
    }

    @Test
    fun `clusterToGridLines separates distant perpendiculars`() {
        val segments =
            listOf(
                Morphology.LineSegment(start = 0, end = 100, perpPos = 10),
                Morphology.LineSegment(start = 0, end = 100, perpPos = 50),
            )
        val lines = Morphology.clusterToGridLines(segments, perpTolerancePx = 5)
        assertEquals(2, lines.size)
        assertEquals(10, lines[0].position)
        assertEquals(50, lines[1].position)
    }

    /** ARGB 0xFFAARRGGBB с альфой 0xFF (непрозрачный пиксель PDF-страницы). */
    private fun argb(
        r: Int,
        g: Int,
        b: Int,
    ): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
