package ru.kyamshanov.notepen.book

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.port.PdfExporter

/**
 * [PdfExporter]-декоратор для EPUB: экспорт рукописных пометок выполняется поверх
 * PDF-версии книги. Если [sourcePdfPath] указывает на EPUB, он подменяется на
 * путь к сконвертированному PDF (тот же кеш, что и при открытии), после чего
 * вызывается базовый [delegate].
 *
 * @param delegate базовый экспортёр PDF
 * @param converter конвертер EPUB → PDF
 */
class EbookAwarePdfExporter(
    private val delegate: PdfExporter,
    private val converter: EbookToPdfConverter,
) : PdfExporter {
    override suspend fun export(
        sourcePdfPath: String,
        annotations: Map<Int, List<DrawingPath>>,
        outputPath: String,
    ): Result<Unit> {
        val effectiveSource =
            if (converter.canConvert(sourcePdfPath)) {
                runCatching { converter.ensurePdf(sourcePdfPath) }
                    .getOrElse { return Result.failure(it) }
            } else {
                sourcePdfPath
            }
        return delegate.export(effectiveSource, annotations, outputPath)
    }
}
