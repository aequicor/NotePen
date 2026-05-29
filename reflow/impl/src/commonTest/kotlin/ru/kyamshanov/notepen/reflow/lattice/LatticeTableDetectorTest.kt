package ru.kyamshanov.notepen.reflow.lattice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LatticeTableDetectorTest {
    @Test
    fun `detect finds three-by-three grid in synthetic bitmap`() {
        val width = 200
        val height = 200
        // Сетка: 4 горизонтальные линии (Y=10,80,150,190) и 4 вертикальные
        // (X=10,70,140,190), все обрезаны по противоположной оси на [10..190].
        val hYs = listOf(10, 80, 150, 190)
        val vXs = listOf(10, 70, 140, 190)
        val pixels =
            bitmap(width, height) { x, y ->
                (y in hYs && x in 10..190) || (x in vXs && y in 10..190)
            }
        val grid = assertNotNull(LatticeTableDetector.detect(pixels, width, height))
        assertEquals(4, grid.horizontalLines.size)
        assertEquals(4, grid.verticalLines.size)
        assertEquals(9, grid.cells.size, "3 строки × 3 колонки = 9 ячеек")
        // Bbox таблицы — от первой до последней грид-линии.
        assertEquals(10, grid.bounds.left)
        assertEquals(10, grid.bounds.top)
        assertEquals(191, grid.bounds.right)
        assertEquals(191, grid.bounds.bottom)
    }

    @Test
    fun `detect returns null on text-only bitmap`() {
        val width = 200
        val height = 100
        // «Буквы»: тёмные блоки 6×6 px — короче minLength (5% от 200 = 10 px).
        val pixels =
            bitmap(width, height) { x, y ->
                (y in 20..25 && x in 30..35) ||
                    (y in 20..25 && x in 50..55) ||
                    (y in 50..55 && x in 30..35)
            }
        assertNull(LatticeTableDetector.detect(pixels, width, height))
    }

    @Test
    fun `detect returns null when only horizontal lines are present`() {
        val width = 200
        val height = 100
        // 2 горизонтальных линии, ни одной вертикальной — таблицу собрать нельзя.
        val pixels =
            bitmap(width, height) { x, y ->
                y in listOf(20, 60) && x in 10..190
            }
        assertNull(LatticeTableDetector.detect(pixels, width, height))
    }

    @Test
    fun `detect returns null on empty image`() {
        assertNull(LatticeTableDetector.detect(IntArray(0), 0, 0))
    }

    @Test
    fun `detect returns null when only single grid line per axis`() {
        val width = 200
        val height = 200
        // По одной линии на ось — недостаточно (нужно ≥2 для рамки ячейки).
        val pixels =
            bitmap(width, height) { x, y ->
                (y == 50 && x in 10..190) || (x == 100 && y in 10..190)
            }
        assertNull(LatticeTableDetector.detect(pixels, width, height))
    }

    /**
     * Синтетический ARGB-битмап: [draw]`(x, y)` → `true` рисует чёрный пиксель,
     * иначе белый. Альфа всегда `0xFF` (страница непрозрачная).
     */
    private fun bitmap(
        width: Int,
        height: Int,
        draw: (x: Int, y: Int) -> Boolean,
    ): IntArray {
        val arr = IntArray(width * height)
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        for (y in 0 until height) {
            for (x in 0 until width) {
                arr[y * width + x] = if (draw(x, y)) black else white
            }
        }
        return arr
    }
}
