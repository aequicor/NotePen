package ru.kyamshanov.notepen.reflow.ui

import ru.kyamshanov.notepen.reflow.api.TextAnchor
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

    @Test
    fun pageForAnchorPicksLastWindowAtOrBeforeBlock() {
        // 10 строк × 10px по странице 30px → 4 окна с firstBlock = 0 (один блок, рвётся).
        // Якорь на блок 0 → страница 0.
        val singleBlock =
            ReaderPagination.pageWindows(
                blocks = listOf(splittable(lines = 10, lineHeight = 10f)),
                pageHeightPx = 30f,
                spacingPx = 0f,
            )
        assertEquals(0, ReaderPagination.pageForAnchor(singleBlock, TextAnchor.ofBlock(0)))

        // Несколько блоков: каждое окно открывается своим блоком.
        val multi =
            ReaderPagination.pageWindows(
                blocks =
                    listOf(
                        atomic(height = 25f),
                        atomic(height = 25f),
                        atomic(height = 25f),
                    ),
                pageHeightPx = 30f,
                spacingPx = 0f,
            )
        assertEquals(0, ReaderPagination.pageForAnchor(multi, TextAnchor.ofBlock(0)))
        assertEquals(1, ReaderPagination.pageForAnchor(multi, TextAnchor.ofBlock(1)))
        assertEquals(2, ReaderPagination.pageForAnchor(multi, TextAnchor.ofBlock(2)))
    }

    @Test
    fun pageWithinBlockForYWalksWindowsOfSameBlock() {
        // 10 строк × 10px → 4 окна одного блока с firstBlockOffsetPx 0,30,60,90.
        val windows =
            ReaderPagination.pageWindows(
                blocks = listOf(splittable(lines = 10, lineHeight = 10f)),
                pageHeightPx = 30f,
                spacingPx = 0f,
            )
        // targetY=25 попадает в первую страницу (диапазон 0..30 окна 0).
        assertEquals(0, ReaderPagination.pageWithinBlockForY(windows, basePage = 0, blockIndex = 0, targetY = 25f))
        // targetY=35 попадает на вторую страницу (offset=30 ≤ 35 < 60).
        assertEquals(1, ReaderPagination.pageWithinBlockForY(windows, basePage = 0, blockIndex = 0, targetY = 35f))
        // targetY=85 → третья страница.
        assertEquals(2, ReaderPagination.pageWithinBlockForY(windows, basePage = 0, blockIndex = 0, targetY = 85f))
    }

    @Test
    fun pageWithinBlockForYStopsAtBlockBoundary() {
        // Два атомарных блока: окно 0 — block 0, окно 1 — block 1.
        val windows =
            ReaderPagination.pageWindows(
                blocks = listOf(atomic(height = 20f), atomic(height = 20f)),
                pageHeightPx = 25f,
                spacingPx = 0f,
            )
        // Любой targetY в block 0: лимит обхода — этот же блок, остаёмся на 0.
        assertEquals(0, ReaderPagination.pageWithinBlockForY(windows, basePage = 0, blockIndex = 0, targetY = 999f))
    }

    @Test
    fun pageForAnchorClampsOutOfRangeAndEmpty() {
        assertEquals(0, ReaderPagination.pageForAnchor(emptyList(), TextAnchor.ofBlock(42)))

        val windows =
            ReaderPagination.pageWindows(
                blocks = listOf(atomic(height = 20f), atomic(height = 20f)),
                pageHeightPx = 25f,
                spacingPx = 0f,
            )
        // Block за пределами документа клампится на последнее окно — иначе после ре-пагинации
        // pagerState.scrollToPage улетал бы в out-of-bounds и крашился.
        assertEquals(windows.lastIndex, ReaderPagination.pageForAnchor(windows, TextAnchor.ofBlock(999)))
    }
}
