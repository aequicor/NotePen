package ru.kyamshanov.notepen.pdf.infrastructure

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocumentInfo

/**
 * JVM-реализация [PdfDocument] поверх Apache PDFBox.
 *
 * [renderer] — не thread-safe; все вызовы должны быть защищены через [synchronized] на этом объекте.
 *
 * Закрытие документа откладывается до завершения всех активных рендерингов.
 * [close] идемпотентен и безопасен для вызова с любого потока.
 */
internal class JvmPdfDocument(
    internal val renderer: PDFRenderer,
    private val document: PDDocument,
    override val info: PdfDocumentInfo,
) : PdfDocument {

    private val lock = Any()
    private var closeRequested = false
    private var renderCount = 0

    /**
     * Сигнализирует о начале рендеринга.
     * Возвращает `false`, если документ уже закрывается — в этом случае рендеринг нельзя начинать.
     */
    internal fun beginRender(): Boolean = synchronized(lock) {
        if (closeRequested) return@synchronized false
        renderCount++
        true
    }

    /**
     * Сигнализирует о завершении рендеринга.
     * Если [close] уже был вызван и это был последний активный рендеринг, закрывает документ.
     */
    internal fun endRender() {
        val doClose = synchronized(lock) {
            renderCount--
            closeRequested && renderCount == 0
        }
        if (doClose) document.close()
    }

    /**
     * Освобождает все ресурсы документа.
     * Если рендеринг активен — фактическое закрытие откладывается до его завершения.
     * Идемпотентен.
     */
    override fun close() {
        val doClose = synchronized(lock) {
            if (closeRequested) return@synchronized false
            closeRequested = true
            renderCount == 0
        }
        if (doClose) document.close()
    }
}
