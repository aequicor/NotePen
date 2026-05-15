package ru.kyamshanov.notepen

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import java.io.File

/**
 * Android-реализация [PdfManager] поверх [android.graphics.pdf.PdfRenderer].
 *
 * Временный мост до Commit 4, когда [PdfManager] будет заменён на domain-порты
 * [ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader] /
 * [ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer].
 */
internal class AndroidPdfManager(private val filePath: String) : PdfManager {

    private val pfd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)

    private val pdfRenderer: PdfRenderer = PdfRenderer(pfd)

    override val metadata: PdfInfo by lazy { buildPdfInfo() }

    private fun buildPdfInfo(): PdfInfo {
        val pages = (0 until pdfRenderer.pageCount).map { index ->
            val page = pdfRenderer.openPage(index)
            val info = PdfPageInfo(
                pageNumber = index,
                width = page.width.toFloat(),
                height = page.height.toFloat(),
            )
            page.close()
            info
        }
        return PdfInfo(
            filePath = filePath,
            pageCount = pdfRenderer.pageCount,
            pages = pages,
            hasUniformPageSizes = pages.map { it.width to it.height }.toSet().size == 1,
        )
    }

    @Synchronized
    override fun renderPage(pageIndex: Int, viewSize: IntSize): ImageBitmap? {
        if (pageIndex < 0 || pageIndex >= pdfRenderer.pageCount) return null
        val bitmap = Bitmap.createBitmap(viewSize.width, viewSize.height, Bitmap.Config.ARGB_8888)
        val page = pdfRenderer.openPage(pageIndex)
        return try {
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap.asImageBitmap()
        } finally {
            page.close()
        }
    }

    override fun close() {
        pdfRenderer.close()
        pfd.close()
    }
}
