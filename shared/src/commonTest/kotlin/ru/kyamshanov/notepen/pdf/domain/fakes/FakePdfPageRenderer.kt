package ru.kyamshanov.notepen.pdf.domain.fakes

import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageData
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer

internal class FakePdfPageRenderer : PdfPageRenderer {
    override suspend fun renderPage(
        document: PdfDocument,
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
        rotationQuarters: Int,
        cropLeftN: Float,
        cropTopN: Float,
        cropRightN: Float,
        cropBottomN: Float,
    ): PdfPageData {
        if (pageIndex < 0 || pageIndex >= document.info.pageCount) {
            throw IndexOutOfBoundsException("pageIndex $pageIndex out of [0, ${document.info.pageCount})")
        }
        return PdfPageData(
            widthPx = widthPx,
            heightPx = heightPx,
            pixels = IntArray(widthPx * heightPx) { 0xFF_FF_FF_FF.toInt() },
        )
    }

    override suspend fun documentTextLineHeight(document: PdfDocument): Float? = null
}
