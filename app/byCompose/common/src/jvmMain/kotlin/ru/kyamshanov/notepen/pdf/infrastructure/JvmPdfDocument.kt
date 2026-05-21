package ru.kyamshanov.notepen.pdf.infrastructure

import kotlinx.coroutines.CancellationException
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocumentInfo

/**
 * JVM-реализация [PdfDocument] поверх Apache PDFBox.
 *
 * Доступ к [PDDocument]/[PDFRenderer] не thread-safe — всё взаимодействие
 * идёт через [useRenderer], который сериализуется с [close] на общем мониторе.
 */
internal class JvmPdfDocument(
    private val renderer: PDFRenderer,
    private val document: PDDocument,
    override val info: PdfDocumentInfo,
) : PdfDocument {

    private val lock = Any()

    @Volatile
    private var closed = false

    /**
     * Выполняет [block] под общим монитором документа.
     *
     * Если документ уже закрыт — выбрасывает [CancellationException], чтобы
     * вызывающий корутин-скоуп тихо отменил работу.
     */
    internal fun <R> useRenderer(block: (PDFRenderer) -> R): R = synchronized(lock) {
        if (closed) throw CancellationException("PDF document is closed")
        block(renderer)
    }

    /**
     * Выполняет [block] над [PDDocument] под тем же монитором, что и [useRenderer].
     *
     * Если документ уже закрыт — выбрасывает [CancellationException].
     */
    internal fun <R> useDocument(block: (PDDocument) -> R): R = synchronized(lock) {
        if (closed) throw CancellationException("PDF document is closed")
        block(document)
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            document.close()
        }
    }
}
