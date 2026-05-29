package ru.kyamshanov.notepen.reflow.segmentation

import ru.kyamshanov.notepen.reflow.api.ReflowRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XyCutSegmenterTest {
    @Test
    fun `empty input yields no regions`() {
        assertEquals(emptyList(), XyCutSegmenter.segment(emptyList(), pageWidthPt = 600f, pageHeightPt = 800f))
    }

    @Test
    fun `single glyph yields one region with that glyph`() {
        val rects = listOf(rect(left = 100f, top = 100f, right = 110f, bottom = 110f))
        val regions = XyCutSegmenter.segment(rects, pageWidthPt = 600f, pageHeightPt = 800f)
        assertEquals(listOf(listOf(0)), regions)
    }

    @Test
    fun `dense single column yields one region with all glyphs`() {
        // Все глифы в одной плотной колонке, без разрывов выше порога 2% × 800 = 16 pt.
        val rects =
            (0..9).map { i ->
                rect(left = 100f, top = 100f + i * 12f, right = 200f, bottom = 110f + i * 12f)
            }
        val regions = XyCutSegmenter.segment(rects, pageWidthPt = 600f, pageHeightPt = 800f)
        assertEquals(1, regions.size, "Одна колонка без разрывов = один регион")
        assertEquals(rects.indices.toList().sorted(), regions.single().sorted())
    }

    @Test
    fun `two-column layout splits vertically and emits left then right`() {
        // Левая колонка X∈[50,200], правая X∈[400,550]. Зазор 200 pt > порога 2% × 600 = 12 pt.
        val leftCol =
            (0..4).map { i ->
                rect(left = 50f, top = 100f + i * 12f, right = 200f, bottom = 110f + i * 12f)
            }
        val rightCol =
            (5..9).map { i ->
                rect(left = 400f, top = 100f + (i - 5) * 12f, right = 550f, bottom = 110f + (i - 5) * 12f)
            }
        val regions = XyCutSegmenter.segment(leftCol + rightCol, pageWidthPt = 600f, pageHeightPt = 800f)
        assertEquals(2, regions.size, "Две колонки = два региона")
        // Левая колонка — индексы 0..4; правая — 5..9.
        assertEquals(setOf(0, 1, 2, 3, 4), regions[0].toSet())
        assertEquals(setOf(5, 6, 7, 8, 9), regions[1].toSet())
    }

    @Test
    fun `horizontal gap between blocks emits top region before bottom`() {
        // Два горизонтальных блока, разделённых пустой полосой 100..200 (gap 100 pt > 16 pt).
        // Верхний — индексы 0..2, нижний — 3..5.
        val top = (0..2).map { i -> rect(left = 100f, top = 50f + i * 10f, right = 300f, bottom = 60f + i * 10f) }
        val bottom = (3..5).map { i -> rect(left = 100f, top = 250f + (i - 3) * 10f, right = 300f, bottom = 260f + (i - 3) * 10f) }
        val regions = XyCutSegmenter.segment(top + bottom, pageWidthPt = 600f, pageHeightPt = 800f)
        assertEquals(2, regions.size)
        // Reading order: top first.
        assertEquals(setOf(0, 1, 2), regions[0].toSet())
        assertEquals(setOf(3, 4, 5), regions[1].toSet())
    }

    @Test
    fun `two-column with paragraph break inside left column produces three regions in correct order`() {
        // Левая колонка содержит два абзаца, разделённых горизонтальным зазором >16 pt.
        // Правая — один абзац.
        // Ожидаемый порядок чтения: left-top → left-bottom → right.
        val leftTop = (0..2).map { i -> rect(left = 50f, top = 50f + i * 12f, right = 200f, bottom = 60f + i * 12f) }
        val leftBot = (3..5).map { i -> rect(left = 50f, top = 200f + (i - 3) * 12f, right = 200f, bottom = 210f + (i - 3) * 12f) }
        val right = (6..9).map { i -> rect(left = 400f, top = 50f + (i - 6) * 12f, right = 550f, bottom = 60f + (i - 6) * 12f) }
        val regions =
            XyCutSegmenter.segment(
                leftTop + leftBot + right,
                pageWidthPt = 600f,
                pageHeightPt = 800f,
            )
        assertEquals(3, regions.size, "Левая разделена на два абзаца + правая = 3 региона")
        // Первый шаг — вертикальный разрез между колонками; затем внутри левой —
        // горизонтальный между абзацами. Reading order: left-top, left-bot, right.
        assertEquals(setOf(0, 1, 2), regions[0].toSet())
        assertEquals(setOf(3, 4, 5), regions[1].toSet())
        assertEquals(setOf(6, 7, 8, 9), regions[2].toSet())
    }

    @Test
    fun `narrow gap below threshold is not cut`() {
        // Зазор 5 pt между двумя «колонками» (X 150→155) ниже порога 2% × 600 = 12 pt → не режется.
        val a = (0..2).map { i -> rect(left = 50f, top = 100f + i * 12f, right = 150f, bottom = 110f + i * 12f) }
        val b = (3..5).map { i -> rect(left = 155f, top = 100f + (i - 3) * 12f, right = 250f, bottom = 110f + (i - 3) * 12f) }
        val regions = XyCutSegmenter.segment(a + b, pageWidthPt = 600f, pageHeightPt = 800f)
        assertEquals(1, regions.size, "Зазор ниже порога — не делим, один регион")
        assertTrue(regions.single().size == 6)
    }

    /** Удобный конструктор [ReflowRect] в pt-координатах. */
    private fun rect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): ReflowRect = ReflowRect(left, top, right, bottom)
}
