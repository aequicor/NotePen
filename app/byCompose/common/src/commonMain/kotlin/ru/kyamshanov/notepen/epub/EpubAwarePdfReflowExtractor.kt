package ru.kyamshanov.notepen.epub

import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowDocument

/**
 * [PdfReflowExtractor]-декоратор для EPUB: режим reflow-чтения работает поверх
 * PDF-версии книги. Если путь указывает на EPUB, он подменяется на путь к
 * сконвертированному PDF (тот же кеш, что и при открытии), после чего вызывается
 * базовый [delegate].
 *
 * @param delegate базовый извлекатель reflow-содержимого
 * @param converter конвертер EPUB → PDF
 */
class EpubAwarePdfReflowExtractor(
    private val delegate: PdfReflowExtractor,
    private val converter: EpubToPdfConverter,
) : PdfReflowExtractor {

    override suspend fun probe(path: String): PdfContentKind =
        delegate.probe(resolve(path))

    override suspend fun extract(path: String): ReflowDocument =
        delegate.extract(resolve(path))

    private suspend fun resolve(path: String): String =
        if (converter.isEpub(path)) converter.ensurePdf(path) else path
}
