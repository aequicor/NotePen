package ru.kyamshanov.notepen.reflow

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import java.io.File

/**
 * Android-реализация [PdfReflowExtractor] поверх PdfBox-Android (порт Apache
 * PDFBox 2.x; встроенный [android.graphics.pdf.PdfRenderer] текст не отдаёт).
 *
 * Принимает те же форматы `path`, что и
 * [ru.kyamshanov.notepen.pdf.infrastructure.AndroidPdfDocumentLoader]:
 * абсолютный путь, `file://…` и `content://…` URI.
 *
 * Сборка делегируется платформенно-нейтральному [ReflowAssembler]. Извлечение
 * нетекстовых областей ([ru.kyamshanov.notepen.reflow.api.ReflowBlock.Figure])
 * пока не реализовано — это следующий инкремент.
 *
 * @param context контекст приложения (для ContentResolver и инициализации шрифтов PdfBox-Android)
 * @param ioDispatcher диспетчер для блокирующего парсинга; не должен быть Main-диспетчером
 */
class AndroidPdfReflowExtractor(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : PdfReflowExtractor {

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
        PDFBoxResourceLoader.init(context.applicationContext)
        val uri = Uri.parse(path)
        val stream = when (uri.scheme) {
            null, "file" -> {
                val file = File(uri.path ?: path)
                require(file.exists() && file.canRead()) { "PDF file not found or not readable: $path" }
                file.inputStream()
            }

            else -> context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open stream for: $path")
        }
        return stream.use { PDDocument.load(it) }
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
        val text = unicode?.takeUnless { it.isBlank() } ?: return null
        return RawGlyph(
            text = text,
            rect = ReflowRect(
                left = xDirAdj,
                top = yDirAdj - heightDir,
                right = xDirAdj + widthDirAdj,
                bottom = yDirAdj,
            ),
            fontSizePt = fontSizeInPt,
        )
    }

    private companion object {
        /** Сколько первых страниц сканировать в [probe] для классификации. */
        const val PROBE_PAGE_LIMIT = 5
    }
}
