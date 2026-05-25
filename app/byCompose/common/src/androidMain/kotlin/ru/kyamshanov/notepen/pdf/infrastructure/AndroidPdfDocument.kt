package ru.kyamshanov.notepen.pdf.infrastructure

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocumentInfo

/**
 * Android-реализация [PdfDocument] поверх [android.graphics.pdf.PdfRenderer].
 *
 * [renderer] — не thread-safe; все вызовы должны быть защищены через [synchronized] на этом объекте.
 */
internal class AndroidPdfDocument(
    internal val renderer: PdfRenderer,
    private val pfd: ParcelFileDescriptor,
    override val info: PdfDocumentInfo,
) : PdfDocument {
    override fun close() {
        renderer.close()
        pfd.close()
    }
}
