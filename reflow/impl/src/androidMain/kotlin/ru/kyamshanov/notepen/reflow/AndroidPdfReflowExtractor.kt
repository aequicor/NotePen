package ru.kyamshanov.notepen.reflow

import android.content.Context
import android.graphics.Path
import android.graphics.PointF
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.reflow.api.PageBitmapProvider
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import ru.kyamshanov.notepen.reflow.lattice.LatticeTableRefiner
import java.io.File

/**
 * Android-реализация [PdfReflowExtractor] поверх PdfBox-Android (порт Apache
 * PDFBox 2.x; встроенный [android.graphics.pdf.PdfRenderer] текст не отдаёт).
 *
 * Принимает те же форматы `path`, что и
 * [ru.kyamshanov.notepen.pdf.infrastructure.AndroidPdfDocumentLoader]:
 * абсолютный путь, `file://…` и `content://…` URI.
 *
 * Сборка делегируется платформенно-нейтральному [ReflowAssembler]. Встроенные
 * растровые изображения собираются через [PDFGraphicsStreamEngine] как
 * [ru.kyamshanov.notepen.reflow.api.ReflowBlock.Figure].
 *
 * @param context контекст приложения (для ContentResolver и инициализации шрифтов PdfBox-Android)
 * @param ioDispatcher диспетчер для блокирующего парсинга; не должен быть Main-диспетчером
 */
class AndroidPdfReflowExtractor(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : PdfReflowExtractor {
    override suspend fun probe(path: String): PdfContentKind =
        withContext(ioDispatcher) {
            openDocument(path).use { document ->
                val limit = minOf(PROBE_PAGE_LIMIT, document.numberOfPages)
                ReflowAssembler.classify((0 until limit).map { extractPage(document, it) })
            }
        }

    override suspend fun extract(path: String): ReflowDocument =
        withContext(ioDispatcher) {
            openDocument(path).use { document ->
                ReflowAssembler.assemble((0 until document.numberOfPages).map { extractPage(document, it) })
            }
        }

    override suspend fun extractWithLattice(
        path: String,
        pageBitmaps: PageBitmapProvider,
    ): ReflowDocument =
        withContext(ioDispatcher) {
            openDocument(path).use { document ->
                // RawPage'и держим до конца, чтобы Lattice мог их переиспользовать
                // (мап глифов в ячейки по их координатам). Второй проход extract
                // дороже, чем разовое удержание ~10–100 КБ RawPage в памяти.
                val rawPages = (0 until document.numberOfPages).map { extractPage(document, it) }
                val assembled = ReflowAssembler.assemble(rawPages)
                LatticeTableRefiner.refine(
                    document = assembled,
                    rawPages = rawPages,
                    renderPage = pageBitmaps,
                )
            }
        }

    private fun openDocument(path: String): PDDocument {
        PDFBoxResourceLoader.init(context.applicationContext)
        val uri = Uri.parse(path)
        val stream =
            when (uri.scheme) {
                null, "file" -> {
                    val file = File(uri.path ?: path)
                    require(file.exists() && file.canRead()) { "PDF file not found or not readable: $path" }
                    file.inputStream()
                }

                else ->
                    context.contentResolver.openInputStream(uri)
                        ?: throw IllegalArgumentException("Cannot open stream for: $path")
            }
        return stream.use { PDDocument.load(it) }
    }

    private fun extractPage(
        document: PDDocument,
        pageIndex: Int,
    ): RawPage {
        val page = document.getPage(pageIndex)
        val box = page.mediaBox
        val glyphs = mutableListOf<RawGlyph>()
        val stripper =
            object : PDFTextStripper() {
                override fun writeString(
                    text: String,
                    textPositions: List<TextPosition>,
                ) {
                    val chunk = textPositions.mapNotNull { it.toGlyph() }
                    // PDFBox emits one writeString per word and signals the inter-word break
                    // by a separate call, not a space glyph. So insert a blank glyph at the
                    // boundary — otherwise words run together in PDFs whose text layer has no
                    // explicit space glyphs and whose word gap is below the spacing threshold.
                    // A same-line boundary becomes a word space; a new line places it at the
                    // line start, where buildLine ignores it.
                    chunk.firstOrNull()?.let { first ->
                        if (glyphs.isNotEmpty()) glyphs.add(first.copy(text = " "))
                    }
                    glyphs.addAll(chunk)
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
            images = collectImageRegions(page),
        )
    }

    /** Прямоугольники встроенных растровых изображений страницы (в пунктах, top-left). */
    private fun collectImageRegions(page: PDPage): List<ReflowRect> =
        runCatching { ImageRegionCollector(page).also { it.processPage(page) }.regions }
            .getOrDefault(emptyList())

    private fun TextPosition.toGlyph(): RawGlyph? {
        val text = unicode?.takeIf { it.isNotEmpty() } ?: return null
        val fontName = font?.name
        return RawGlyph(
            text = text,
            rect =
                ReflowRect(
                    left = xDirAdj,
                    top = yDirAdj - heightDir,
                    right = xDirAdj + widthDirAdj,
                    bottom = yDirAdj,
                ),
            fontSizePt = fontSizeInPt,
            spaceWidthPt = widthOfSpace,
            bold = FontStyles.isBold(fontName),
            italic = FontStyles.isItalic(fontName),
            monospace = FontStyles.isMonospace(fontName),
        )
    }

    /**
     * Stream-engine PdfBox-Android, собирающий размещение встроенных растровых
     * изображений (через CTM в [drawImage]); прочие операции игнорируются.
     */
    private class ImageRegionCollector(
        page: PDPage,
    ) : PDFGraphicsStreamEngine(page) {
        val regions = mutableListOf<ReflowRect>()
        private val pageHeight = page.mediaBox.height

        override fun drawImage(pdImage: PDImage) {
            val m = graphicsState.currentTransformationMatrix
            regions +=
                FigureGeometry.imageRectFromCtm(
                    scaleX = m.scaleX,
                    shearY = m.shearY,
                    shearX = m.shearX,
                    scaleY = m.scaleY,
                    translateX = m.translateX,
                    translateY = m.translateY,
                    pageHeightPt = pageHeight,
                )
        }

        override fun appendRectangle(
            p0: PointF,
            p1: PointF,
            p2: PointF,
            p3: PointF,
        ) {}

        override fun clip(windingRule: Path.FillType) {}

        override fun moveTo(
            x: Float,
            y: Float,
        ) {}

        override fun lineTo(
            x: Float,
            y: Float,
        ) {}

        override fun curveTo(
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            x3: Float,
            y3: Float,
        ) {}

        override fun getCurrentPoint(): PointF = PointF(0f, 0f)

        override fun closePath() {}

        override fun endPath() {}

        override fun strokePath() {}

        override fun fillPath(windingRule: Path.FillType) {}

        override fun fillAndStrokePath(windingRule: Path.FillType) {}

        override fun shadingFill(shadingName: COSName) {}
    }

    private companion object {
        /** Сколько первых страниц сканировать в [probe] для классификации. */
        const val PROBE_PAGE_LIMIT = 5
    }
}
