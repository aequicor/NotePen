package ru.kyamshanov.notepen.pdf.domain

import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class PdfPageInfoTest {
    @Test
    fun aspectRatioNoRotation() {
        val page = PdfPageInfo(pageIndex = 0, widthPt = 612f, heightPt = 792f)
        assertEquals(612f / 792f, page.aspectRatio)
    }

    @Test
    fun aspectRatioSwapsFor90() {
        val page = PdfPageInfo(pageIndex = 0, widthPt = 612f, heightPt = 792f, rotation = 90)
        assertEquals(792f / 612f, page.aspectRatio)
    }

    @Test
    fun aspectRatioSwapsFor270() {
        val page = PdfPageInfo(pageIndex = 0, widthPt = 612f, heightPt = 792f, rotation = 270)
        assertEquals(792f / 612f, page.aspectRatio)
    }

    @Test
    fun aspectRatioUnchangedFor180() {
        val page = PdfPageInfo(pageIndex = 0, widthPt = 612f, heightPt = 792f, rotation = 180)
        assertEquals(612f / 792f, page.aspectRatio)
    }
}
