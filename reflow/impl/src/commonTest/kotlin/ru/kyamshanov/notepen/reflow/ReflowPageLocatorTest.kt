package ru.kyamshanov.notepen.reflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
