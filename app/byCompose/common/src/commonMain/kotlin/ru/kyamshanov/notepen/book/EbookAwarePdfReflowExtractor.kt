package ru.kyamshanov.notepen.book

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import kotlin.time.TimeSource

private val extractLogger = KotlinLogging.logger {}

/**
 * [PdfReflowExtractor]-декоратор для книг (EPUB/FB2): режим reflow-чтения берёт
 * структуру прямо из [BookReflowProvider] — reflow-документа, собранного
 * рендерером при верстке книги в PDF (истинные заголовки/абзацы/списки/цитаты,
 * без эвристик). Если сайдкара нет (старый кеш) или источник — комикс,
 * откатывается на извлечение текста из PDF через базовый [delegate].
 *
 * Геометрия согласована: [ru.kyamshanov.notepen.reflow.api.SourceSpan] из
 * рендерера нормализованы к тем же страницам PDF, на которых рисует editor,
 * поэтому штрихи сопоставляются с текстом тем же [ru.kyamshanov.notepen.reflow.StrokeTextMapper].
 *
 * @param delegate базовый извлекатель reflow-содержимого (из PDF)
 * @param converter конвертер книги → PDF (определяет формат, печёт PDF)
 * @param reflow поставщик reflow-документа книги (сайдкар рядом с кешем PDF)
 */
class EbookAwarePdfReflowExtractor(
    private val delegate: PdfReflowExtractor,
    private val converter: EbookToPdfConverter,
    private val reflow: BookReflowProvider,
) : PdfReflowExtractor {
    override suspend fun probe(path: String): PdfContentKind = delegate.probe(resolve(path))

    override suspend fun extract(path: String): ReflowDocument {
        val mark = TimeSource.Monotonic.markNow()
        return if (converter.canConvert(path)) {
            val sidecar = reflow.reflowFor(path)
            if (sidecar != null) {
                extractLogger.info {
                    "PdfReflow: extract branch=sidecar blocks=${sidecar.blocks.size} " +
                        "total=${mark.elapsedNow().inWholeMilliseconds}ms"
                }
                sidecar
            } else {
                val pdfPath = converter.ensurePdf(path)
                val ensureMs = mark.elapsedNow().inWholeMilliseconds
                val extractMark = TimeSource.Monotonic.markNow()
                val doc = delegate.extract(pdfPath)
                extractLogger.info {
                    "PdfReflow: extract branch=book-delegate blocks=${doc.blocks.size} " +
                        "ensurePdf=${ensureMs}ms delegate=${extractMark.elapsedNow().inWholeMilliseconds}ms " +
                        "total=${mark.elapsedNow().inWholeMilliseconds}ms"
                }
                doc
            }
        } else {
            val doc = delegate.extract(path)
            extractLogger.info {
                "PdfReflow: extract branch=raw blocks=${doc.blocks.size} " +
                    "total=${mark.elapsedNow().inWholeMilliseconds}ms"
            }
            doc
        }
    }

    private suspend fun resolve(path: String): String = if (converter.canConvert(path)) converter.ensurePdf(path) else path
}
