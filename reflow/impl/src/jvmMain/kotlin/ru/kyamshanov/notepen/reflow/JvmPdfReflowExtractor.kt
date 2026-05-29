package ru.kyamshanov.notepen.reflow

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.graphics.image.PDImage
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import ru.kyamshanov.notepen.reflow.api.PageBitmapProvider
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import ru.kyamshanov.notepen.reflow.lattice.LatticeTableRefiner
import java.awt.geom.Point2D
import java.io.File

/**
 * JVM-реализация [PdfReflowExtractor] поверх Apache PDFBox 3.x.
 *
 * Текст со страниц извлекается через [PDFTextStripper] (позиции глифов), а
 * сборка в заголовки/абзацы и порядок чтения делегируются платформенно-
 * нейтральному [ReflowAssembler].
 *
 * Встроенные растровые изображения собираются через [PDFGraphicsStreamEngine]
 * как [ru.kyamshanov.notepen.reflow.api.ReflowBlock.Figure]. Векторные таблицы
 * и формулы как изображения не детектируются.
 *
 * @param ioDispatcher диспетчер для блокирующего парсинга; не должен быть Main-диспетчером
 */
class JvmPdfReflowExtractor(
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
                // Vector-path Lattice работает без растеризации: если PDF
                // нарисовал рамки таблиц векторно (большинство нативных PDF),
                // refiner подменит low-conf Figure'ы на Tables прямо здесь.
                // На сканированных PDF (нет vectorLines) — no-op.
                val afterLattice = LatticeTableRefiner.refineFromVectorLines(assembled, rawPages)
                // Tagged PDF: автор разметил структуру → промоутим совпадающие
                // Paragraph'ы в Heading'и с правильным уровнем. На untagged PDF —
                // no-op.
                applyTaggedHeadings(document, afterLattice)
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
                // 1) Дешёвый vector-path сначала: если PDF имеет нарисованные
                //    рамки таблиц векторно — recover'им их без растеризации.
                val afterVector = LatticeTableRefiner.refineFromVectorLines(assembled, rawPages)
                // 2) Для оставшихся fallback Figures (без vector lines / vector
                //    refinement не сработал) пробуем растровый путь через
                //    PageBitmapProvider. Растеризация значительно дороже.
                val afterRaster =
                    LatticeTableRefiner.refine(
                        document = afterVector,
                        rawPages = rawPages,
                        renderPage = pageBitmaps,
                    )
                applyTaggedHeadings(document, afterRaster)
            }
        }

    /**
     * Промоутит совпадающие Paragraph'ы в Heading'и из struct tree, если PDF
     * tagged. Wrap в `runCatching` — кривой StructTree не должен ломать extract.
     */
    private fun applyTaggedHeadings(
        document: PDDocument,
        reflow: ReflowDocument,
    ): ReflowDocument =
        runCatching {
            if (!TaggedPdfHeadings.isTagged(document)) return@runCatching reflow
            val headings = TaggedPdfHeadings.collectHeadings(document)
            TaggedPdfHeadings.promoteMatchingParagraphs(reflow, headings)
        }.getOrDefault(reflow)

    private fun openDocument(path: String): PDDocument {
        val file = File(path)
        require(file.exists() && file.canRead()) { "PDF file not found or not readable: $path" }
        return Loader.loadPDF(file)
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
     * Один проход stream-engine по странице: собираем и встроенные изображения,
     * и векторные линии (кандидаты в грид сетки таблиц). Раньше делали два
     * прохода ради одного collector'а изображений; теперь второй пайплайн —
     * Lattice via vector — присоединяется бесплатно.
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
     * Stream-engine, собирающий за один проход:
     *  - встроенные растровые изображения (через CTM в [drawImage]) — как раньше;
     *  - векторные «грид-линии» из path-operator'ов (`moveTo`/`lineTo` →
     *    `strokePath`/`closePath`/`appendRectangle`+stroke), отфильтрованные до
     *    близких к горизонтальным или вертикальным.
     *
     * Сегменты, формирующие прямоугольники таблиц, эмитятся отдельными отрезками
     * — постпроцессинг (LatticeTableDetector) кластеризует их в грид. Сегменты,
     * близкие к диагональным (например, тени или подписи) — отбрасываются.
     *
     * Координаты конвертируются из PDF user-space (origin bottom-left) в
     * top-left того же масштаба, как у [RawGlyph.rect].
     */
    private class GraphicsCollector(
        page: PDPage,
        private val pageHeightPt: Float,
    ) : PDFGraphicsStreamEngine(page) {
        val images = mutableListOf<ReflowRect>()
        val vectorLines = mutableListOf<VectorLine>()

        /** Накопленные сегменты текущего path'а; коммитятся в [vectorLines] на stroke. */
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
            p0: Point2D,
            p1: Point2D,
            p2: Point2D,
            p3: Point2D,
        ) {
            // Прямоугольник = 4 ребра. Эмитим их как 4 сегмента, чтобы strokePath
            // их подхватил.
            addSegment(p0.x.toFloat(), p0.y.toFloat(), p1.x.toFloat(), p1.y.toFloat())
            addSegment(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat())
            addSegment(p2.x.toFloat(), p2.y.toFloat(), p3.x.toFloat(), p3.y.toFloat())
            addSegment(p3.x.toFloat(), p3.y.toFloat(), p0.x.toFloat(), p0.y.toFloat())
        }

        override fun clip(windingRule: Int) {}

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
            // Кривые — не грид-линии; не запоминаем как сегмент, но обновляем позицию.
            currentX = x3
            currentY = y3
        }

        override fun getCurrentPoint(): Point2D = Point2D.Float(currentX, currentY)

        override fun closePath() {
            pendingSegments.clear()
        }

        override fun endPath() {
            pendingSegments.clear()
        }

        override fun strokePath() {
            commitPending()
        }

        override fun fillPath(windingRule: Int) {
            // Заливка без обводки — не грид-линия, dropping.
            pendingSegments.clear()
        }

        override fun fillAndStrokePath(windingRule: Int) {
            commitPending()
        }

        override fun shadingFill(shadingName: COSName) {}

        private fun commitPending() {
            for (s in pendingSegments) {
                classify(s)?.let { vectorLines += it }
            }
            pendingSegments.clear()
        }

        /**
         * Классифицирует сегмент как [VectorLine.isHorizontal] / vertical или
         * отбрасывает. Допуск 1°: |dy| / max(|dx|, ε) ≤ tan(1°) ≈ 0.018 → horizontal.
         * `perpPos`-координата конвертируется в top-left origin.
         */
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
            /** tg(1°) ≈ 0.0175. Допуск отклонения от оси для классификации линии. */
            const val AXIS_ALIGN_TANGENT = 0.018f

            /** Минимальная длина сегмента в pt: 3 pt отсекает «точки» от декоров. */
            const val MIN_SEGMENT_LENGTH_PT = 3f
        }
    }

    private companion object {
        /** Сколько первых страниц сканировать в [probe] для классификации. */
        const val PROBE_PAGE_LIMIT = 5
    }
}
