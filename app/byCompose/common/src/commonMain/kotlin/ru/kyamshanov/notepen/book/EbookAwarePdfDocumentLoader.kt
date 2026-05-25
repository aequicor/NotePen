package ru.kyamshanov.notepen.book

import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader

/**
 * [PdfDocumentLoader]-декоратор, прозрачно открывающий EPUB: если путь
 * указывает на EPUB, файл сначала верстается в PDF через [converter], а затем
 * открывается базовым [delegate]; для всех прочих форматов вызов проксируется
 * без изменений.
 *
 * @param delegate базовый загрузчик (PDF/изображения)
 * @param converter конвертер EPUB → PDF
 */
class EbookAwarePdfDocumentLoader(
    private val delegate: PdfDocumentLoader,
    private val converter: EbookToPdfConverter,
) : PdfDocumentLoader {

    override suspend fun load(path: String): PdfDocument {
        val effectivePath = if (converter.canConvert(path)) converter.ensurePdf(path) else path
        return delegate.load(effectivePath)
    }
}
