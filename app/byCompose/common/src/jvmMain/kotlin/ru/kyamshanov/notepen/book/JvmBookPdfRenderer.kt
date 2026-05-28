package ru.kyamshanov.notepen.book

import org.apache.fontbox.ttf.TrueTypeCollection
import org.apache.fontbox.ttf.TrueTypeFont
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import ru.kyamshanov.notepen.reflow.api.SourceSpan
import java.awt.Color
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Верстает [BookContent] в PDF на JVM.
 *
 * Текст пишется НАСТОЯЩИМ векторным слоем через PDFBox ([PDPageContentStream]),
 * а не растрируется: страницы остаются чёткими при любом зуме на HiDPI, а
 * текстовый слой делает книгу доступной для reflow-чтения (см.
 * [ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor]).
 *
 * Для покрытия кириллицы и латиницы встроен Unicode-засечный шрифт **PT Serif**
 * (ParaType Free Font License — разрешает встраивание в документы и бандлинг с
 * продуктами; перераспространяем) — он лежит JVM-ресурсом `/fonts/PTSerif.ttc`
 * и встраивается в PDF подмножеством (subset). Четыре начертания
 * (Regular/Bold/Italic/BoldItalic) берутся реальными гранями коллекции:
 * PDFBox не синтезирует жирность/курсив. Моноширинный инлайн-код рисуется тем же
 * засечным шрифтом — у стандартных PDF-шрифтов (Courier) нет кириллицы, а
 * покрытие важнее точного начертания.
 *
 * Авторские шрифты книги ([BookContent.fonts]) используются для фрагмента, только
 * если та грань целиком покрывает его глифы; иначе фрагмент остаётся на PT Serif.
 *
 * Координаты: PDFBox считает начало текста СНИЗУ-слева (y растёт вверх). Внутри
 * верстка ведётся сверху-вниз в «пикселях» прежнего растра (A4 @150 dpi,
 * [PAGE_WIDTH]×[PAGE_HEIGHT]); при выводе значения переводятся в пункты через
 * [PT_PER_PX], а вертикаль зеркалится: `y_pt = (PAGE_HEIGHT − cursorPx) · PT_PER_PX`.
 * Так физическая раскладка (поля, кегли, отступы) совпадает с прежней растровой.
 */
object JvmBookPdfRenderer {
    private const val PAGE_WIDTH = 1240 // A4 @150dpi
    private const val PAGE_HEIGHT = 1754
    private const val MARGIN = 104

    /**
     * Множитель перевода «пикселей» прежнего растра (150 dpi) в пункты PDF
     * (72 dpi): `72/150`. A4 шириной 1240 px ↦ 595.2 pt ≈ [PDRectangle.A4].
     */
    private const val PT_PER_PX = 72f / 150f

    private const val BODY_SIZE = 30f

    /**
     * Втяжка первой строки абзаца ("красная строка") — 1.5 кегля основного
     * текста, конвенциональный книжный отступ. Применяется только к
     * [ContentBlock.Paragraph]; заголовки, списки, цитаты и таблицы рисуются без
     * неё (см. [PageComposer.render]).
     */
    private val PARAGRAPH_FIRST_LINE_INDENT = (BODY_SIZE * 1.5f).toInt()
    private const val BLOCKQUOTE_INDENT = 64
    private const val LIST_INDENT_STEP = 40
    private const val LINE_FACTOR = 1.42f
    private const val PARAGRAPH_GAP = 18
    private const val HEADING_GAP_BEFORE = 30
    private const val HEADING_GAP_AFTER = 12
    private const val RULE_GAP = 26
    private const val IMAGE_GAP = 22

    /** Доля кегля для смещения и уменьшения над-/подстрочных индексов. */
    private const val SCRIPT_SCALE = 0.6f
    private const val SUPERSCRIPT_RISE = 0.33f
    private const val SUBSCRIPT_RISE = -0.16f
    private const val RULE_LINE_WIDTH_PX = 2f
    private const val UNDERLINE_OFFSET_PX = 4f
    private const val UNDERLINE_WIDTH_PX = 1.5f

    private val HEADING_SIZES = floatArrayOf(58f, 48f, 42f, 38f, 34f, 32f)
    private val LINK_COLOR = Color(0x1A, 0x0D, 0xAB)
    private val QUOTE_COLOR = Color(0x44, 0x44, 0x44)
    private val RULE_COLOR = Color(0xBB, 0xBB, 0xBB)

    /** Имя ресурса со встроенным шрифтом PT Serif (TrueType Collection). */
    private const val FONT_RESOURCE = "/fonts/PTSerif.ttc"

    // PostScript-имена граней внутри PTSerif.ttc (подтверждены при сборке шрифта).
    private const val FACE_REGULAR = "PTSerif-Regular"
    private const val FACE_BOLD = "PTSerif-Bold"
    private const val FACE_ITALIC = "PTSerif-Italic"
    private const val FACE_BOLD_ITALIC = "PTSerif-BoldItalic"

    /**
     * @param book книга для верстки
     * @param output файл назначения PDF (создаётся/перезаписывается)
     * @return оглавление + reflow-документ, собранные в этом же проходе верстки
     */
    fun render(
        book: BookContent,
        output: File,
    ): BookRenderResult {
        PDDocument().use { doc ->
            // Коллекция граней PT Serif должна оставаться открытой до doc.save():
            // subset граней встраивается именно при сохранении (PDFBox не закрывает
            // TrueTypeFont, загруженный из коллекции, — это наша ответственность).
            openBundledFonts(doc).use { bundled ->
                val composer =
                    PageComposer(
                        doc = doc,
                        bundled = bundled,
                        bookFonts = loadBookFonts(doc, book.fonts),
                    )
                book.metadata.title
                    ?.takeIf { it.isNotBlank() }
                    ?.let { title ->
                        val laid = composer.heading(level = 1, text = title)
                        composer.add(ReflowBlock.Heading(text = laid.text, level = 1, source = laid.spans))
                    }
                book.metadata.author
                    ?.takeIf { it.isNotBlank() }
                    ?.let { author ->
                        val laid = composer.paragraph(listOf(InlineSpan(author)), italic = true)
                        composer.add(ReflowBlock.Paragraph(text = laid.text, source = laid.spans))
                    }
                for (block in book.blocks) composer.render(block)
                composer.finish()

                if (doc.numberOfPages == 0) doc.addPage(PDPage(PDRectangle.A4))
                output.parentFile?.mkdirs()
                doc.save(output)
                return BookRenderResult(toc = composer.outline(), reflow = composer.reflow())
            }
        }
    }

    /**
     * Грань шрифта: встраиваемый [PDFont] для рисования и [TrueTypeFont] (или
     * `null` для нетипизированных фолбэков) для проверки покрытия глифов до
     * вызова `showText` — иначе [PDType0Font.encode] бросит исключение на
     * непокрытом символе.
     */
    private class Face(
        val font: PDFont,
        private val ttf: TrueTypeFont?,
    ) {
        /** Покрывает ли грань кодовую точку [codePoint] (есть глиф в unicode-cmap). */
        fun covers(codePoint: Int): Boolean {
            val lookup = ttf?.unicodeCmapLookup ?: return false
            return lookup.getGlyphId(codePoint) != 0
        }
    }

    /** Четыре начертания встроенного PT Serif, удерживающие открытую коллекцию. */
    private class BundledFonts(
        private val collection: TrueTypeCollection,
        val regular: Face,
        val bold: Face,
        val italic: Face,
        val boldItalic: Face,
    ) : AutoCloseable {
        /** Грань PT Serif под запрошенное начертание. */
        fun face(
            bold: Boolean,
            italic: Boolean,
        ): Face =
            when {
                bold && italic -> boldItalic
                bold -> this.bold
                italic -> this.italic
                else -> regular
            }

        override fun close() {
            collection.close()
        }
    }

    /** Открывает встроенный PT Serif и загружает 4 грани как встраиваемые subset-шрифты. */
    private fun openBundledFonts(doc: PDDocument): BundledFonts {
        val stream =
            requireNotNull(JvmBookPdfRenderer::class.java.getResourceAsStream(FONT_RESOURCE)) {
                "Bundled font resource missing: $FONT_RESOURCE"
            }
        val collection = TrueTypeCollection(stream)

        fun face(name: String): Face {
            val ttf = collection.getFontByName(name)
            return Face(font = PDType0Font.load(doc, ttf, true), ttf = ttf)
        }

        // Регуляр обязателен и обязан покрывать кириллицу — иначе верстка молча
        // потеряла бы текст; падаем явно.
        val regular = face(FACE_REGULAR)
        require(regular.covers(CYRILLIC_PROBE)) {
            "Bundled font $FONT_RESOURCE does not cover Cyrillic"
        }
        return BundledFonts(
            collection = collection,
            regular = regular,
            bold = face(FACE_BOLD),
            italic = face(FACE_ITALIC),
            boldItalic = face(FACE_BOLD_ITALIC),
        )
    }

    /**
     * Загружает встроенные шрифты книги как встраиваемые [PDType0Font]; нечитаемые
     * (например, WOFF) и не-TrueType пропускает. Каждая грань удерживает свой
     * [TrueTypeFont] для проверки покрытия.
     */
    private fun loadBookFonts(
        doc: PDDocument,
        fonts: List<ByteArray>,
    ): List<Face> =
        fonts.mapNotNull { bytes ->
            runCatching {
                val ttf = org.apache.fontbox.ttf.TTFParser().parse(org.apache.pdfbox.io.RandomAccessReadBuffer(bytes))
                Face(font = PDType0Font.load(doc, ttf, true), ttf = ttf)
            }.getOrNull()
        }

    /** Одно «слово» с пробельным хвостом и оформлением — единица переноса строк. */
    private data class StyledChunk(
        val text: String,
        val face: Face,
        val color: Color,
        val size: Float,
        val rise: Float,
        val underline: Boolean,
        val bold: Boolean,
        val monospace: Boolean,
    )

    /** Накопитель страниц с курсором верстки сверху вниз (в «пикселях» A4 @150dpi). */
    private class PageComposer(
        private val doc: PDDocument,
        private val bundled: BundledFonts,
        private val bookFonts: List<Face>,
    ) {
        private val tocEntries = mutableListOf<TocEntry>()
        private val contentBottom = PAGE_HEIGHT - MARGIN
        private val contentWidth = PAGE_WIDTH - 2 * MARGIN
        private var pageIndex = -1
        private var stream: PDPageContentStream? = null
        private var cursorY = MARGIN

        // Параллельно растровой верстке копим reflow-блоки: их .text и провенанс
        // ([SourceSpan]) рождаются из той же раскладки слов по строкам, поэтому
        // координаты совпадают со штрихами editor'а и пере-извлечение из PDF не нужно.
        private val reflowBlocks = mutableListOf<ReflowBlock>()
        private var blockText = StringBuilder()
        private var blockSpans = mutableListOf<SourceSpan>()

        init {
            newPage()
        }

        fun finish() {
            stream?.close()
            stream = null
        }

        fun outline(): List<TocEntry> = tocEntries.toList()

        /** Готовый reflow-документ, собранный из тех же блоков, что легли в PDF. */
        fun reflow(): ReflowDocument = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = reflowBlocks.toList())

        /** Добавляет блок в reflow-поток в порядке верстки. */
        fun add(block: ReflowBlock) {
            reflowBlocks.add(block)
        }

        /** Текст блока и его провенанс, накопленные при верстке (для reflow). */
        data class BlockLayout(
            val text: String,
            val spans: List<SourceSpan>,
        )

        fun render(block: ContentBlock) {
            when (block) {
                is ContentBlock.Heading -> {
                    val laid = heading(block.level, block.text)
                    add(ReflowBlock.Heading(text = laid.text, level = block.level, source = laid.spans))
                }
                is ContentBlock.Paragraph -> {
                    val laid = paragraph(block.text, firstLineIndent = PARAGRAPH_FIRST_LINE_INDENT)
                    add(ReflowBlock.Paragraph(text = laid.text, source = laid.spans))
                }
                is ContentBlock.Blockquote -> {
                    val laid = paragraph(block.text, italic = true, indent = BLOCKQUOTE_INDENT)
                    add(ReflowBlock.Blockquote(text = laid.text, source = laid.spans))
                }
                is ContentBlock.ListItem -> {
                    val laid =
                        paragraph(listOf(InlineSpan(markerFor(block))) + block.text, indent = (block.level + 1) * LIST_INDENT_STEP)
                    add(ReflowBlock.ListItem(text = laid.text, source = laid.spans))
                }
                is ContentBlock.Table -> {
                    // На PDF таблица кладётся построчно абзацами (геометрия для editor);
                    // в reflow отдаём настоящую сетку — её ячейки без провенанса
                    // (ре-анкоринг штрихов в таблицы пока не поддержан, см. StrokeTextMapper).
                    block.rows.forEach { row -> paragraph(listOf(InlineSpan(row.joinToString("   |   ")))) }
                    add(
                        ReflowBlock.Table(
                            rows =
                                block.rows.map { row ->
                                    ReflowBlock.TableRow(cells = row.map { ReflowBlock.TableCell(text = it) })
                                },
                        ),
                    )
                }
                is ContentBlock.Image -> image(block)?.let { add(it) }
                ContentBlock.HorizontalRule -> {
                    rule()
                    add(ReflowBlock.Divider)
                }
                ContentBlock.PageBreak -> if (cursorY > MARGIN) newPage()
            }
        }

        fun heading(
            level: Int,
            text: String,
        ): BlockLayout {
            beginBlock()
            cursorY += HEADING_GAP_BEFORE
            val size = HEADING_SIZES[(level - 1).coerceIn(0, HEADING_SIZES.lastIndex)]
            val lineHeight = (size * LINE_FACTOR).toInt()
            if (cursorY + lineHeight > contentBottom) newPage()
            tocEntries.add(TocEntry(level = level, title = text, pageIndex = pageIndex))
            val chunks = chunksFor(listOf(InlineSpan(text, bold = true)), size, Color.BLACK, italicBase = false)
            drawChunks(chunks, indent = 0, firstLineIndent = 0)
            cursorY += HEADING_GAP_AFTER
            return finishBlock()
        }

        /**
         * @param indent сдвиг всего блока вправо (цитаты, втяжка списков)
         * @param firstLineIndent дополнительная втяжка только первой строки
         *   ("красная строка"); для не-абзацных блоков — 0
         */
        fun paragraph(
            spans: RichText,
            italic: Boolean = false,
            indent: Int = 0,
            firstLineIndent: Int = 0,
        ): BlockLayout {
            beginBlock()
            val color = if (italic) QUOTE_COLOR else Color.BLACK
            val chunks = chunksFor(spans, BODY_SIZE, color, italicBase = italic)
            drawChunks(chunks, indent, firstLineIndent)
            cursorY += PARAGRAPH_GAP
            return finishBlock()
        }

        private fun beginBlock() {
            blockText = StringBuilder()
            blockSpans = mutableListOf()
        }

        private fun finishBlock(): BlockLayout = BlockLayout(text = blockText.toString(), spans = blockSpans.toList())

        /**
         * Разбивает [RichText] на «слова» ([StyledChunk]) с уже выбранной гранью,
         * цветом, кеглем и над-/подстрочным смещением — единицы переноса строк.
         * Каждое слово несёт свой пробельный хвост, чтобы при переносе пробел между
         * словами не терялся (важно для извлечения текста reflow: между словами
         * должен быть пробел).
         */
        private fun chunksFor(
            spans: RichText,
            baseSize: Float,
            baseColor: Color,
            italicBase: Boolean,
        ): List<StyledChunk> {
            val chunks = mutableListOf<StyledChunk>()
            for (span in spans) {
                if (span.text.isEmpty()) continue
                val italic = span.italic || italicBase
                val face = faceFor(span.text, bold = span.bold, italic = italic)
                val color = if (span.link) LINK_COLOR else baseColor
                val size = if (span.superscript || span.subscript) baseSize * SCRIPT_SCALE else baseSize
                val rise =
                    when {
                        span.superscript -> baseSize * SUPERSCRIPT_RISE
                        span.subscript -> baseSize * SUBSCRIPT_RISE
                        else -> 0f
                    }
                // Переводы строк схлопнуты ещё парсером; \n страхуемся как пробел.
                val normalized = span.text.replace('\n', ' ')
                // Граница фрагмента не должна терять пробел: если фрагмент
                // начинается с пробела, дотягиваем его в хвост предыдущего слова
                // (иначе слова соседних фрагментов слиплись бы — "важныйи").
                if (normalized.firstOrNull() == ' ') chunks.appendTrailingSpace()
                val words = normalized.split(' ').filter { it.isNotEmpty() }
                words.forEachIndexed { i, word ->
                    // Пробел в хвосте слова, если за ним ещё слово в этом фрагменте
                    // или сам фрагмент кончается пробелом (граница со следующим).
                    val spaced = i < words.lastIndex || normalized.lastOrNull() == ' '
                    chunks.add(
                        StyledChunk(
                            text = sanitize(if (spaced) "$word " else word, face),
                            face = face,
                            color = color,
                            size = size,
                            rise = rise,
                            underline = span.link,
                            bold = span.bold,
                            monospace = span.code,
                        ),
                    )
                }
            }
            return chunks
        }

        /** Добавляет одиночный пробел в хвост последнего слова, если его там ещё нет. */
        private fun MutableList<StyledChunk>.appendTrailingSpace() {
            val last = lastOrNull() ?: return
            if (last.text.endsWith(' ')) return
            this[lastIndex] = last.copy(text = "${last.text} ")
        }

        /**
         * Раскладывает слова в строки по ширине ([contentWidth] минус втяжки),
         * измеряя их встроенными метриками PDFBox, и рисует построчно с переносом
         * страниц. Первая строка получает дополнительную втяжку [firstLineIndent].
         */
        private fun drawChunks(
            chunks: List<StyledChunk>,
            indent: Int,
            firstLineIndent: Int,
        ) {
            if (chunks.isEmpty()) return
            val left = (MARGIN + indent).toFloat()
            val fullWidth = (contentWidth - indent).toFloat()
            var line = mutableListOf<StyledChunk>()
            var lineWidth = 0f
            var firstLine = true

            fun flush() {
                if (line.isEmpty()) return
                val lineLeft = if (firstLine) left + firstLineIndent else left
                drawLine(line, lineLeft)
                firstLine = false
                line = mutableListOf()
                lineWidth = 0f
            }

            for (chunk in chunks) {
                val available = (if (firstLine) fullWidth - firstLineIndent else fullWidth)
                val width = widthOf(chunk)
                if (line.isNotEmpty() && lineWidth + width > available) flush()
                line.add(chunk)
                lineWidth += width
            }
            flush()
        }

        /** Рисует одну готовую строку слов с общей базовой линией; продвигает курсор. */
        private fun drawLine(
            line: List<StyledChunk>,
            lineLeft: Float,
        ) {
            val maxSize = line.maxOf { it.size }
            val lineHeight = (maxSize * LINE_FACTOR).toInt()
            if (cursorY + lineHeight > contentBottom) newPage()
            val cs = stream ?: return
            // Базовая линия: верх строки + восходящая часть (приближаем кеглем).
            val baselinePx = cursorY + maxSize
            val baselinePt = (PAGE_HEIGHT - baselinePx) * PT_PER_PX
            cs.beginText()
            // Одна установка матрицы на строку; showText сам продвигает перо по
            // ширине каждого слова, поэтому слова встают подряд без ручных сдвигов.
            cs.setTextMatrix(org.apache.pdfbox.util.Matrix.getTranslateInstance(lineLeft * PT_PER_PX, baselinePt))
            for (chunk in line) {
                if (chunk.text.isEmpty()) continue
                cs.setFont(chunk.face.font, chunk.size * PT_PER_PX)
                cs.setNonStrokingColor(chunk.color)
                cs.setTextRise(chunk.rise * PT_PER_PX)
                cs.showText(chunk.text)
                cs.setTextRise(0f)
            }
            cs.endText()
            drawUnderlines(cs, line, lineLeft, baselinePt)
            recordSpans(line, lineLeft, lineTop = cursorY, lineHeight = lineHeight)
            cursorY += lineHeight
        }

        /**
         * Накапливает текст строки в [blockText] и эмитит по [SourceSpan] на каждое
         * слово: нормализованные `[0..1]` границы слова на текущей странице
         * (та же система, что у штрихов editor'а) + диапазон символов в тексте блока.
         * Перо двигается по тем же ширинам слов, что и `showText`, поэтому границы
         * совпадают с нарисованным текстом (ср. [drawUnderlines]).
         */
        private fun recordSpans(
            line: List<StyledChunk>,
            lineLeft: Float,
            lineTop: Int,
            lineHeight: Int,
        ) {
            var penPx = lineLeft
            val top = lineTop.toFloat() / PAGE_HEIGHT
            val bottom = (lineTop + lineHeight).toFloat() / PAGE_HEIGHT
            for (chunk in line) {
                val width = widthOf(chunk)
                val start = blockText.length
                blockText.append(chunk.text)
                val end = blockText.length
                if (chunk.text.isNotBlank()) {
                    blockSpans.add(
                        SourceSpan(
                            pageIndex = pageIndex,
                            charStart = start,
                            charEnd = end,
                            bounds =
                                ReflowRect(
                                    left = penPx / PAGE_WIDTH,
                                    top = top,
                                    right = (penPx + width) / PAGE_WIDTH,
                                    bottom = bottom,
                                ),
                            bold = chunk.bold,
                            monospace = chunk.monospace,
                        ),
                    )
                }
                penPx += width
            }
        }

        /** Подчёркивает ссылочные слова строки тонкой линией под базовой. */
        private fun drawUnderlines(
            cs: PDPageContentStream,
            line: List<StyledChunk>,
            lineLeft: Float,
            baselinePt: Float,
        ) {
            var penPx = lineLeft
            val y = baselinePt - UNDERLINE_OFFSET_PX * PT_PER_PX
            for (chunk in line) {
                val width = widthOf(chunk)
                if (chunk.underline && chunk.text.isNotBlank()) {
                    cs.setStrokingColor(chunk.color)
                    cs.setLineWidth(UNDERLINE_WIDTH_PX * PT_PER_PX)
                    cs.moveTo(penPx * PT_PER_PX, y)
                    cs.lineTo((penPx + width) * PT_PER_PX, y)
                    cs.stroke()
                }
                penPx += width
            }
        }

        /** Ширина слова в «пикселях» по встроенным метрикам грани. */
        private fun widthOf(chunk: StyledChunk): Float {
            if (chunk.text.isEmpty()) return 0f
            val width1000 = runCatching { chunk.face.font.getStringWidth(chunk.text) }.getOrDefault(0f)
            return width1000 / 1000f * chunk.size
        }

        /**
         * Грань под фрагмент: сперва авторский шрифт книги, если он ПОЛНОСТЬЮ
         * покрывает текст (иначе латинский шрифт «съел» бы кириллицу), затем
         * PT Serif нужного начертания.
         */
        private fun faceFor(
            text: String,
            bold: Boolean,
            italic: Boolean,
        ): Face {
            val book = bookFonts.firstOrNull { face -> text.codePoints().allMatch(face::covers) }
            return book ?: bundled.face(bold = bold, italic = italic)
        }

        /**
         * Заменяет в [text] кодовые точки, не покрытые гранью [face], на пробел —
         * иначе `showText` бросил бы [IllegalArgumentException] (см.
         * [PDType0Font]/`PDCIDFontType2.encode`). PT Serif покрывает кириллицу и
         * латиницу; редкие символы (эмодзи) деградируют в пробел, текст не теряется.
         */
        private fun sanitize(
            text: String,
            face: Face,
        ): String {
            if (text.codePoints().allMatch { it == ' '.code || face.covers(it) }) return text
            val sb = StringBuilder(text.length)
            text.codePoints().forEach { cp ->
                if (cp == ' '.code || face.covers(cp)) sb.appendCodePoint(cp) else sb.append(' ')
            }
            return sb.toString()
        }

        private fun image(block: ContentBlock.Image): ReflowBlock.Figure? {
            val decoded = runCatching { ImageIO.read(ByteArrayInputStream(block.data)) }.getOrNull() ?: return null
            val maxHeight = contentBottom - MARGIN
            var width = decoded.width
            var height = decoded.height
            if (width > contentWidth) {
                height = height * contentWidth / width
                width = contentWidth
            }
            if (height > maxHeight) {
                width = width * maxHeight / height
                height = maxHeight
            }
            if (cursorY + height > contentBottom) newPage()
            return stream?.let { cs ->
                val xObject = LosslessFactory.createFromImage(doc, decoded)
                val xPx = MARGIN + (contentWidth - width) / 2
                // Низ изображения в PDF-координатах (y вверх): верх = cursorY (сверху-вниз).
                val bottomPt = (PAGE_HEIGHT - (cursorY + height)) * PT_PER_PX
                cs.drawImage(xObject, xPx * PT_PER_PX, bottomPt, width * PT_PER_PX, height * PT_PER_PX)
                // Врезка для reflow: тот же прямоугольник, нормализованный к странице —
                // ридер покажет её кропом запечённой PDF-страницы (см. FigureView).
                val figure =
                    ReflowBlock.Figure(
                        pageIndex = pageIndex,
                        bounds =
                            ReflowRect(
                                left = xPx.toFloat() / PAGE_WIDTH,
                                top = cursorY.toFloat() / PAGE_HEIGHT,
                                right = (xPx + width).toFloat() / PAGE_WIDTH,
                                bottom = (cursorY + height).toFloat() / PAGE_HEIGHT,
                            ),
                        aspectRatio = if (height > 0) width.toFloat() / height else 1f,
                    )
                cursorY += height + IMAGE_GAP
                figure
            }
        }

        private fun rule() {
            if (cursorY + RULE_GAP > contentBottom) newPage()
            cursorY += RULE_GAP / 2
            val cs = stream ?: return
            val y = (PAGE_HEIGHT - cursorY) * PT_PER_PX
            cs.setStrokingColor(RULE_COLOR)
            cs.setLineWidth(RULE_LINE_WIDTH_PX * PT_PER_PX)
            cs.moveTo(MARGIN * PT_PER_PX, y)
            cs.lineTo((PAGE_WIDTH - MARGIN) * PT_PER_PX, y)
            cs.stroke()
            cursorY += RULE_GAP / 2
        }

        private fun newPage() {
            stream?.close()
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            pageIndex = doc.numberOfPages - 1
            stream = PDPageContentStream(doc, page)
            cursorY = MARGIN
        }

        private fun markerFor(item: ContentBlock.ListItem): String = if (item.ordered) "${item.ordinal}. " else "•  "
    }

    /** Кириллический пробник покрытия шрифта: «П» (U+041F). */
    private const val CYRILLIC_PROBE = 0x041F
}
