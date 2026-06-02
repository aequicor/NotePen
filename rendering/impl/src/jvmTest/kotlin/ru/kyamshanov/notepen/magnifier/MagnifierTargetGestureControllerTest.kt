package ru.kyamshanov.notepen.magnifier

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import ru.kyamshanov.notepen.pdfviewer.PdfViewerState
import ru.kyamshanov.notepen.pdfviewer.SpreadMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MagnifierTargetGestureControllerTest {
    @Test
    fun `move target can cross from right spread page to left spread page`() {
        val viewerState =
            PdfViewerState().apply {
                viewportSize = IntSize(1000, 1000)
                pages = listOf(squarePage(0), squarePage(1))
                spreadMode = SpreadMode.SPREAD
            }
        val state =
            MagnifierState().apply {
                enable(
                    onPage = 1,
                    viewportSize = Size(1000f, 1000f),
                    target = Rect(0.2f, 0.2f, 0.4f, 0.4f),
                    selectionSizePx = Size(130f, 130f),
                    panelCenter = Offset(500f, 500f),
                )
            }
        val controller = MagnifierTargetGestureController(state, viewerState)
        val start = Offset(x = 861f, y = 195f)

        assertTrue(controller.onDown(start))
        controller.onMove(start + Offset(x = -300f, y = 0f))
        controller.onUp()

        assertEquals(0, state.pageIndex)
    }

    private fun squarePage(index: Int): PdfPageInfo = PdfPageInfo(pageIndex = index, widthPt = 100f, heightPt = 100f)
}
