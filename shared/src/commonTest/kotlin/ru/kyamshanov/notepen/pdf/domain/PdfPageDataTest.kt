package ru.kyamshanov.notepen.pdf.domain

import ru.kyamshanov.notepen.pdf.domain.model.PdfPageData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PdfPageDataTest {
    @Test
    fun equalsIsStructuralOverPixels() {
        val a = PdfPageData(2, 2, intArrayOf(1, 2, 3, 4))
        val b = PdfPageData(2, 2, intArrayOf(1, 2, 3, 4))
        assertEquals(a, b)
    }

    @Test
    fun notEqualsWhenPixelsDiffer() {
        val a = PdfPageData(2, 2, intArrayOf(1, 2, 3, 4))
        val b = PdfPageData(2, 2, intArrayOf(1, 2, 3, 0))
        assertNotEquals(a, b)
    }

    @Test
    fun notEqualsWhenDimensionsDiffer() {
        val a = PdfPageData(2, 2, intArrayOf(1, 2, 3, 4))
        val b = PdfPageData(4, 1, intArrayOf(1, 2, 3, 4))
        assertNotEquals(a, b)
    }

    @Test
    fun hashCodeConsistentWithEquals() {
        val a = PdfPageData(2, 2, intArrayOf(1, 2, 3, 4))
        val b = PdfPageData(2, 2, intArrayOf(1, 2, 3, 4))
        assertEquals(a.hashCode(), b.hashCode())
    }
}
