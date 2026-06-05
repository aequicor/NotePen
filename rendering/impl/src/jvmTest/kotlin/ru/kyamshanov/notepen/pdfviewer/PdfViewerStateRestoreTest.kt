package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.ui.unit.IntSize
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class PdfViewerStateRestoreTest {
    @Test
    fun `pending initial state restores horizontal pan at high zoom`() {
        val state = PdfViewerState()

        state.applyInitialState(
            scalePercent = 400,
            pageIndex = 0,
            pageOffsetPx = 0,
            panXPx = -700f,
        )
        state.viewportSize = IntSize(width = 1_000, height = 800)
        state.pages = listOf(PdfPageInfo(pageIndex = 0, widthPt = 100f, heightPt = 100f))

        state.applyPendingInitialScrollIfNeeded()

        assertEquals(400, state.scalePercent)
        assertEquals(-700f, state.pan.x)
        assertEquals(0, state.firstVisiblePageIndex)
        assertEquals(0, state.firstVisiblePageOffsetPx)
    }
}
