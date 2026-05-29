package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.TextAnchor
import ru.kyamshanov.notepen.reflow.ui.ReaderPagination
import ru.kyamshanov.notepen.reflow.ui.ReaderPagination.BlockLayout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReflowPageLocatorTest {
    private fun docOfPages(vararg pageIndices: Int) =
        ReflowAssembler.assemble(
            pageIndices.map { pageIndex -> page(line("page $pageIndex text", top = 100f), pageIndex = pageIndex) },
        )

    @Test
    fun `maps each page to its first block`() {
        val document = docOfPages(0, 1, 2)
        assertEquals(0, ReflowPageLocator.blockIndexForPage(document, 0))
        assertEquals(1, ReflowPageLocator.blockIndexForPage(document, 1))
        assertEquals(2, ReflowPageLocator.blockIndexForPage(document, 2))
    }

    @Test
    fun `missing page falls back to next available content`() {
        val document = docOfPages(0, 2) // страница 1 без блоков
        assertEquals(1, ReflowPageLocator.blockIndexForPage(document, 1))
    }

    @Test
    fun `page beyond content returns null`() {
        assertNull(ReflowPageLocator.blockIndexForPage(docOfPages(0, 1), 5))
    }

    @Test
    fun `block maps back to its source page`() {
        val document = docOfPages(0, 1, 2)
        assertEquals(0, ReflowPageLocator.pageForBlock(document, 0))
        assertEquals(1, ReflowPageLocator.pageForBlock(document, 1))
        assertEquals(2, ReflowPageLocator.pageForBlock(document, 2))
    }

    @Test
    fun `block index out of range returns null page`() {
        val document = docOfPages(0, 1)
        assertNull(ReflowPageLocator.pageForBlock(document, 99))
        assertNull(ReflowPageLocator.pageForBlock(document, -1))
    }

    /**
     * Defect F — entering reading mode at PDF page N must open the reader near
     * page N, not at the title page (page 0).
     *
     * Replays the FIXED orchestration: PDF page N → [ReflowPageLocator.blockIndexForPage]
     * → seed the durable anchor via [TextAnchor.ofBlock] → the pager's initial page comes
     * from [ReaderPagination.pageForAnchor] on that anchor. The old code seeded only the
     * volatile one-shot and left the durable anchor at [TextAnchor.START], so the pager
     * opened at page 0 — this test would have failed then.
     */
    @Test
    fun `entering reading at PDF page N lands the pager within plus minus one of N`() {
        val document = docOfPages(0, 1, 2, 3, 4, 5)
        // One atomic window per block (each source page contributes exactly one block in
        // this fixture), so the reader page index equals the block index.
        val windows =
            ReaderPagination.pageWindows(
                blocks = document.blocks.map { BlockLayout(heightPx = 20f, lineBottomsPx = emptyList(), breakAfter = true) },
                pageHeightPx = 25f,
                spacingPx = 0f,
            )
        for (targetPage in 0..5) {
            val block =
                ReflowPageLocator.blockIndexForPage(document, targetPage)
                    ?: error("no block for page $targetPage")
            // The fix seeds initialAnchor = TextAnchor.ofBlock(block); the pager reads it
            // via pageForAnchor when it first composes.
            val readerPage = ReaderPagination.pageForAnchor(windows, TextAnchor.ofBlock(block))
            assertTrue(
                abs(readerPage - targetPage) <= 1,
                "PDF page $targetPage should open the reader within ±1 (got reader page $readerPage)",
            )
            if (targetPage > 0) {
                assertTrue(readerPage > 0, "PDF page $targetPage must NOT open at the title page (Defect F)")
            }
        }
    }
}
