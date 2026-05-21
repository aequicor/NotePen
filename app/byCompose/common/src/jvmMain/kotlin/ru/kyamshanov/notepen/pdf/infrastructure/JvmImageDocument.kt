package ru.kyamshanov.notepen.pdf.infrastructure

import ru.kyamshanov.notepen.pdf.domain.model.ImageBackedDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocumentInfo

/**
 * JVM-реализация [ImageBackedDocument]: декодированное изображение как документ из одной страницы.
 *
 * Пиксели хранятся в памяти, поэтому [close] не освобождает внешних ресурсов.
 */
internal class JvmImageDocument(
    override val widthPx: Int,
    override val heightPx: Int,
    override val pixels: IntArray,
    override val info: PdfDocumentInfo,
) : ImageBackedDocument {

    override fun close() = Unit
}
