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
                val rawPages = (0 until document.numberOfPages).map { extractPage(document, it) }
                val assembled = ReflowAssembler.assemble(rawPages)
                LatticeTableRefiner.refineFromVectorLines(assembled, rawPages)
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
                val afterVector = LatticeTableRefiner.refineFromVectorLines(assembled, rawPages)
                LatticeTableRefiner.refine(
                    document = afterVector,
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
        val (images, vectorLines) = collectGraphics(page, box.height)
        return RawPage(
            pageIndex = pageIndex,
            widthPt = box.width,
            heightPt = box.height,
            glyphs = glyphs,
            images = images,
            vectorLines = vectorLines,
        )
    }

    /**
     * Один проход stream-engine: изображения + векторные грид-линии (см.
     * [LatticeTableRefiner.refineFromVectorLines]).
     */
    private fun collectGraphics(
        page: PDPage,
        pageHeightPt: Float,
    ): Pair<List<ReflowRect>, List<VectorLine>> =
        runCatching {
            val collector = GraphicsCollector(page, pageHeightPt)
            collector.processPage(page)
            collector.images to collector.vectorLines
        }.getOrDefault(emptyList<ReflowRect>() to emptyList())

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
     * Stream-engine PdfBox-Android: за один проход собирает встроенные
     * изображения + векторные грид-линии (см. JVM-аналог в JvmPdfReflowExtractor).
     */
    private class GraphicsCollector(
        page: PDPage,
        private val pageHeightPt: Float,
    ) : PDFGraphicsStreamEngine(page) {
        val images = mutableListOf<ReflowRect>()
        val vectorLines = mutableListOf<VectorLine>()

        private val pendingSegments = mutableListOf<PendingSegment>()
        private var currentX = 0f
        private var currentY = 0f

        override fun drawImage(pdImage: PDImage) {
            val m = graphicsState.currentTransformationMatrix
            images +=
                FigureGeometry.imageRectFromCtm(
                    scaleX = m.scaleX,
                    shearY = m.shearY,
                    shearX = m.shearX,
                    scaleY = m.scaleY,
                    translateX = m.translateX,
                    translateY = m.translateY,
                    pageHeightPt = pageHeightPt,
                )
        }

        override fun appendRectangle(
            p0: PointF,
            p1: PointF,
            p2: PointF,
            p3: PointF,
        ) {
            addSegment(p0.x, p0.y, p1.x, p1.y)
            addSegment(p1.x, p1.y, p2.x, p2.y)
            addSegment(p2.x, p2.y, p3.x, p3.y)
            addSegment(p3.x, p3.y, p0.x, p0.y)
        }

        override fun clip(windingRule: Path.FillType) {}

        override fun moveTo(
            x: Float,
            y: Float,
        ) {
            currentX = x
            currentY = y
        }

        override fun lineTo(
            x: Float,
            y: Float,
        ) {
            addSegment(currentX, currentY, x, y)
            currentX = x
            currentY = y
        }

        private fun addSegment(
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
        ) {
            pendingSegments += PendingSegment(x1, y1, x2, y2)
        }

        override fun curveTo(
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            x3: Float,
            y3: Float,
        ) {
            currentX = x3
            currentY = y3
        }

        override fun getCurrentPoint(): PointF = PointF(currentX, currentY)

        override fun closePath() {
            pendingSegments.clear()
        }

        override fun endPath() {
            pendingSegments.clear()
        }

        override fun strokePath() {
            commitPending()
        }

        override fun fillPath(windingRule: Path.FillType) {
            pendingSegments.clear()
        }

        override fun fillAndStrokePath(windingRule: Path.FillType) {
            commitPending()
        }

        override fun shadingFill(shadingName: COSName) {}

        private fun commitPending() {
            for (s in pendingSegments) {
                classify(s)?.let { vectorLines += it }
            }
            pendingSegments.clear()
        }

        private fun classify(s: PendingSegment): VectorLine? {
            val dx = kotlin.math.abs(s.x2 - s.x1)
            val dy = kotlin.math.abs(s.y2 - s.y1)
            val length = kotlin.math.max(dx, dy)
            if (length < MIN_SEGMENT_LENGTH_PT) return null
            return when {
                dy <= AXIS_ALIGN_TANGENT * dx ->
                    VectorLine(
                        isHorizontal = true,
                        start = kotlin.math.min(s.x1, s.x2),
                        end = kotlin.math.max(s.x1, s.x2),
                        perpPos = pageHeightPt - (s.y1 + s.y2) / 2f,
                    )
                dx <= AXIS_ALIGN_TANGENT * dy ->
                    VectorLine(
                        isHorizontal = false,
                        start = pageHeightPt - kotlin.math.max(s.y1, s.y2),
                        end = pageHeightPt - kotlin.math.min(s.y1, s.y2),
                        perpPos = (s.x1 + s.x2) / 2f,
                    )
                else -> null
            }
        }

        private data class PendingSegment(
            val x1: Float,
            val y1: Float,
            val x2: Float,
            val y2: Float,
        )

        private companion object {
            const val AXIS_ALIGN_TANGENT = 0.018f
            const val MIN_SEGMENT_LENGTH_PT = 3f
        }
    }

    private companion object {
        /** Сколько первых страниц сканировать в [probe] для классификации. */
        const val PROBE_PAGE_LIMIT = 5
    }
}
