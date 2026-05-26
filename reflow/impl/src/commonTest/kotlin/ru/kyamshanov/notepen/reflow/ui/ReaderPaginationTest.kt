package ru.kyamshanov.notepen.reflow.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderPaginationTest {
    @Test
    fun greedyPackingWithoutKeepWithNext() {
        val pages =
            ReaderPagination.paginate(
                blockHeightsPx = listOf(40f, 40f, 40f, 40f),
                pageHeightPx = 100f,
                spacingPx = 0f,
            )
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), pages)
    }

    @Test
    fun headingIsNotLeftOrphanAtPageBottom() {
        // para(60) + heading(20) fit page 0 (80<=100); para(60) overflows → without keep the
        // heading (index 1) would be the last block on page 0. keepWithNext must push it down.
        val pages =
            ReaderPagination.paginate(
                blockHeightsPx = listOf(60f, 20f, 60f),
                pageHeightPx = 100f,
                spacingPx = 0f,
                keepWithNext = listOf(false, true, false),
            )
        val headingPage = pages.indexOfFirst { 1 in it }
        assertTrue(pages[headingPage].last() != 1, "heading must not be orphaned at page bottom")
        assertTrue(2 in pages[headingPage], "heading must travel with its following block")
        assertEquals(listOf(0, 1, 2), pages.flatten(), "no block dropped or duplicated")
    }

    @Test
    fun fallsBackWhenKeptPairExceedsAPage() {
        // heading(20) + para(200): the pair can't fit even on a fresh page — accept the split,
        // don't loop forever moving the heading.
        val pages =
            ReaderPagination.paginate(
                blockHeightsPx = listOf(20f, 200f),
                pageHeightPx = 100f,
                spacingPx = 0f,
                keepWithNext = listOf(true, false),
            )
        assertEquals(listOf(0, 1), pages.flatten())
    }

    @Test
    fun everyBlockAppearsExactlyOnce() {
        val heights = List(20) { (it % 5 + 1) * 18f }
        val keep = List(20) { it % 4 == 0 }
        val flat = ReaderPagination.paginate(heights, 130f, 6f, keep).flatten()
        assertEquals(heights.indices.toList(), flat)
    }
}
