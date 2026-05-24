package ru.kyamshanov.notepen.reflow

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import java.io.File

/**
 * JVM-реализация [PdfReflowExtractor] поверх Apache PDFBox 3.x.
 *
 * Текст со страниц извлекается через [PDFTextStripper] (позиции глифов), а
 * сборка в заголовки/абзацы и порядок чтения делегируются платформенно-
 * нейтральному [ReflowAssembler].
 *
 * Извлечение нетекстовых областей ([ru.kyamshanov.notepen.reflow.api.ReflowBlock.Figure])
 * пока не реализовано — `images` страниц пусты; это следующий инкремент.
 *
 * @param ioDispatcher диспетчер для блокирующего парсинга; не должен быть Main-диспетчером
 */
class JvmPdfReflowExtractor(private val ioDispatcher: CoroutineDispatcher) : PdfReflowExtractor {

    override suspend fun probe(path: String): PdfContentKind = withContext(ioDispatcher) {
        openDocument(path).use { document ->
            val limit = minOf(PROBE_PAGE_LIMIT, document.numberOfPages)
            ReflowAssembler.classify((0 until limit).map { extractPage(document, it) })
        }
    }

    override suspend fun extract(path: String): ReflowDocument = withContext(ioDispatcher) {
        openDocument(path).use { document ->
            ReflowAssembler.assemble((0 until document.numberOfPages).map { extractPage(document, it) })
        }
    }

    private fun openDocument(path: String): PDDocument {
        val file = File(path)
        require(file.exists() && file.canRead()) { "PDF file not found or not readable: $path" }
        return Loader.loadPDF(file)
    }

    private fun extractPage(document: PDDocument, pageIndex: Int): RawPage {
        val box = document.getPage(pageIndex).mediaBox
        val glyphs = mutableListOf<RawGlyph>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: List<TextPosition>) {
                textPositions.forEach { position -> position.toGlyph()?.let(glyphs::add) }
            }
        }
        stripper.sortByPosition = true
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.getText(document)
        return RawPage(
            pageIndex = pageIndex,
            widthPt = box.width,
            heightPt = box.height,
            glyphs = glyphs,
            images = emptyList(),
        )
    }

    private fun TextPosition.toGlyph(): RawGlyph? {
        val text = unicode?.takeIf { it.isNotEmpty() } ?: return null
        return RawGlyph(
            text = text,
            rect = ReflowRect(
                left = xDirAdj,
                top = yDirAdj - heightDir,
                right = xDirAdj + widthDirAdj,
                bottom = yDirAdj,
            ),
            fontSizePt = fontSizeInPt,
            spaceWidthPt = widthOfSpace,
        )
    }

    private companion object {
        /** Сколько первых страниц сканировать в [probe] для классификации. */
        const val PROBE_PAGE_LIMIT = 5
    }
}
