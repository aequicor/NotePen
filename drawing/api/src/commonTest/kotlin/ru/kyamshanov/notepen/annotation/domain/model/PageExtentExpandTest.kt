package ru.kyamshanov.notepen.annotation.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageExtentExpandTest {
    @Test
    fun `expandedRight by 1-3 gives right = 4-3`() {
        val ext = PageExtent.Pdf
        val result = ext.expandedRight(1f / 3f)
        assertEquals(4f / 3f, result.right, absoluteTolerance = 1e-6f)
        assertEquals(ext.left, result.left)
        assertEquals(ext.top, result.top)
        assertEquals(ext.bottom, result.bottom)
    }

    @Test
    fun `expandedLeft by 1-3 gives left = -1-3`() {
        val ext = PageExtent.Pdf
        val result = ext.expandedLeft(1f / 3f)
        assertEquals(-1f / 3f, result.left, absoluteTolerance = 1e-6f)
        assertEquals(ext.right, result.right)
    }

    @Test
    fun `expandedRight compounds correctly on second press`() {
        val after1 = PageExtent.Pdf.expandedRight(1f / 3f)
        val after2 = after1.expandedRight(1f / 3f)
        // After first press: right=4/3, width=4/3. After second: right = 4/3 + (4/3)*(1/3) = 4/3 + 4/9 = 16/9
        assertEquals(16f / 9f, after2.right, absoluteTolerance = 1e-5f)
    }

    @Test
    fun `expandedLeft compounds correctly on second press`() {
        val after1 = PageExtent.Pdf.expandedLeft(1f / 3f)
        val after2 = after1.expandedLeft(1f / 3f)
        // After first: left=-1/3, width=4/3. After second: left = -1/3 - (4/3)*(1/3) = -1/3 - 4/9 = -7/9
        assertEquals(-7f / 9f, after2.left, absoluteTolerance = 1e-5f)
    }

    @Test
    fun `invariants hold after expand sequence`() {
        var ext = PageExtent.Pdf
        repeat(5) { ext = ext.expandedLeft(1f / 3f) }
        repeat(5) { ext = ext.expandedRight(1f / 3f) }
        assertTrue(ext.left <= 0f)
        assertTrue(ext.top <= 0f)
        assertTrue(ext.right >= 1f)
        assertTrue(ext.bottom >= 1f)
        assertTrue(ext.width >= 1f)
    }

    @Test
    fun `expanding does not change top or bottom`() {
        val ext = PageExtent.Pdf
        val expanded = ext.expandedLeft(0.5f).expandedRight(0.5f)
        assertEquals(ext.top, expanded.top)
        assertEquals(ext.bottom, expanded.bottom)
    }
}

private fun assertEquals(
    expected: Float,
    actual: Float,
    absoluteTolerance: Float,
) {
    assertTrue(
        kotlin.math.abs(expected - actual) <= absoluteTolerance,
        "Expected $expected but was $actual (tolerance $absoluteTolerance)",
    )
}
