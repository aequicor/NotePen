package ru.kyamshanov.notepen.reflow

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Проверяет, что страховочные лимиты reflow-разбора PDF отвергают документы с
 * непомерным числом страниц или гигантскими размерами страницы
 * [PdfTooLargeException] — до выделения буферов, то есть без риска OOM.
 */
class PdfReflowLimitsTest {
    @Test
    fun `accepts a normal page count and dimensions`() {
        // A4 ≈ 595×842 pt, типичная книга — десятки/сотни страниц.
        PdfReflowLimits.requirePageCount(300)
        PdfReflowLimits.requirePageDimensions(pageIndex = 0, widthPt = 595f, heightPt = 842f)
    }

    @Test
    fun `rejects an excessive page count`() {
        assertFailsWith<PdfTooLargeException> {
            PdfReflowLimits.requirePageCount(PdfReflowLimits.MAX_PAGES + 1)
        }
    }

    @Test
    fun `rejects an oversized page width`() {
        assertFailsWith<PdfTooLargeException> {
            PdfReflowLimits.requirePageDimensions(
                pageIndex = 3,
                widthPt = PdfReflowLimits.MAX_PAGE_DIMENSION_PT + 1f,
                heightPt = 842f,
            )
        }
    }

    @Test
    fun `rejects an oversized page height`() {
        assertFailsWith<PdfTooLargeException> {
            PdfReflowLimits.requirePageDimensions(
                pageIndex = 3,
                widthPt = 595f,
                heightPt = PdfReflowLimits.MAX_PAGE_DIMENSION_PT + 1f,
            )
        }
    }
}
