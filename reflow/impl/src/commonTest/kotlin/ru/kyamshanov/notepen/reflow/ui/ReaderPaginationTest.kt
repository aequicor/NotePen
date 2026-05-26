package ru.kyamshanov.notepen.reflow.ui

import ru.kyamshanov.notepen.reflow.ui.ReaderPagination.BlockLayout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderPaginationTest {
    /** Делимый текстовый блок из [lines] строк высотой [lineHeight] каждая. */
    private fun splittable(
        lines: Int,
        lineHeight: Float,
        breakAfter: Boolean = true,
    ) = BlockLayout(
        heightPx = lines * lineHeight,
        lineBottomsPx = (1..lines).map { it * lineHeight },
        breakAfter = breakAfter,
    )

    /** Неделимый блок (картинка/таблица/заголовок). */
    private fun atomic(
        height: Float,
        breakAfter: Boolean = true,
    ) = BlockLayout(heightPx = height, lineBottomsPx = emptyList(), breakAfter = breakAfter)

    @Test
    fun fillsPageToLineBoundary() {
        // Длинный абзац рвётся по строкам: каждая страница заполнена доверху, без пустот.
        val windows =
            ReaderPagination.pageWindows(
                blocks = listOf(splittable(lines = 10, lineHeight = 10f)),
                pageHeightPx = 30f,
                spacingPx = 0f,
            )
        assertEquals(listOf(0f, 30f, 60f, 90f), windows.map { it.startPx })
        assertEquals(listOf(30f, 30f, 30f, 10f), windows.map { it.heightPx })
        assertTrue(windows.all { it.firstBlock == 0 }, "один блок тянется через все страницы")
        assertEquals(listOf(0, 30, 60, 90), windows.map { it.firstBlockOffsetPx })
    }

    @Test
    fun exactFitProducesSinglePage() {
        val windows =
            ReaderPagination.pageWindows(
                blocks = listOf(splittable(lines = 3, lineHeight = 10f)),
                pageHeightPx = 30f,
                spacingPx = 0f,
            )
        assertEquals(1, windows.size, "ровно влезает — без лишней пустой страницы")
        assertEquals(0f, windows.single().startPx)
        assertEquals(30f, windows.single().heightPx)
    }

    @Test
    fun headingIsNotOrphanedAtPageBottom() {
        // para(20) на 0..20; heading(15, breakAfter=false) на 20..35; para(50) на 35..85; страница 40.
        val blocks =
            listOf(
                splittable(lines = 2, lineHeight = 10f),
                atomic(height = 15f, breakAfter = false),
                splittable(lines = 5, lineHeight = 10f),
            )
        val windows = ReaderPagination.pageWindows(blocks, pageHeightPx = 40f, spacingPx = 0f)
        // Страница 0 кончается до заголовка — он не повисает сиротой внизу.
        val page0End = windows[0].startPx + windows[0].heightPx
        assertTrue(page0End <= 20.5f, "страница 0 кончается у верха заголовка")
        // Заголовок открывает следующую страницу вместе с началом своего абзаца.
        assertEquals(1, windows[1].firstBlock, "заголовок ведёт страницу 1")
    }

    @Test
    fun oversizedAtomicBlockIsTiledNotLost() {
        // Картинка выше страницы: показывается срезами по страницам, прогресс гарантирован,
        // текст/контент не теряется (старое поведение обрезало и теряло остаток).
        val windows =
            ReaderPagination.pageWindows(
                blocks = listOf(atomic(height = 100f)),
                pageHeightPx = 30f,
                spacingPx = 0f,
            )
        assertEquals(listOf(0f, 30f, 60f, 90f), windows.map { it.startPx })
        assertEquals(100f, windows.last().startPx + windows.last().heightPx, "покрывает весь блок")
    }

    @Test
    fun windowsTileContentContinuously() {
        val blocks =
            listOf(
                splittable(lines = 4, lineHeight = 12f),
                atomic(height = 40f),
                splittable(lines = 6, lineHeight = 12f),
            )
        val spacing = 6f
        val windows = ReaderPagination.pageWindows(blocks, pageHeightPx = 50f, spacingPx = spacing)
        val contentHeight =
            blocks.sumOf { it.heightPx.toDouble() }.toFloat() + spacing * (blocks.size - 1)
        assertEquals(0f, windows.first().startPx, "начинается сверху")
        assertTrue(
            abs(windows.last().let { it.startPx + it.heightPx } - contentHeight) <= 0.5f,
            "последняя страница доходит до конца контента",
        )
        windows.zipWithNext().forEach { (a, b) ->
            assertTrue(a.heightPx <= 50.5f, "страница не выше полезной высоты")
            assertTrue(b.startPx >= a.startPx + a.heightPx - 0.5f, "контент не показан дважды")
        }
    }

    @Test
    fun emptyDocumentHasNoPages() {
        assertTrue(ReaderPagination.pageWindows(emptyList(), 100f, 0f).isEmpty())
    }
}
