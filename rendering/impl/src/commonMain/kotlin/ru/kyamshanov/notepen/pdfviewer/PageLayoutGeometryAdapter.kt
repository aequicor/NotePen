package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.ui.geometry.Offset
import ru.kyamshanov.notepen.PageLayoutGeometry

/**
 * Адаптер [PdfViewerState] к [PageLayoutGeometry] для драйвера рисования.
 *
 * Читает layout/pan/zoom «вживую» на каждый вызов, поэтому одного экземпляра
 * достаточно на всё время жизни вьюера — он всегда отражает актуальное
 * состояние.
 */
fun PdfViewerState.asPageLayoutGeometry(): PageLayoutGeometry {
    val viewerState = this
    return object : PageLayoutGeometry {
        override val pageCount: Int get() = viewerState.layout.pageHeightsPx.size
        override val basePageWidthPx: Float get() = viewerState.layout.basePageWidthPx
        override val zoom: Float get() = viewerState.zoom
        override val pan: Offset get() = viewerState.pan

        override fun pageTopPx(index: Int): Float = viewerState.layout.pageTopsPx[index]

        override fun pdfHeightPx(index: Int): Float = viewerState.layout.pdfHeightsPx[index]
    }
}
