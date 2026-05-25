package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import ru.kyamshanov.notepen.reflow.api.SourceSpan
import kotlin.math.abs

/**
 * Платформенно-нейтральная сборка [ReflowDocument] из сырых постраничных
 * данных ([RawPage]). Вся эвристика порядка чтения и группировки сосредоточена
 * здесь, чтобы покрываться unit-тестами и переиспользоваться обоими
 * экстракторами (PDFBox на JVM, PdfBox-Android на Android).
 *
 * Геометрия группировки (зазоры строк/абзацев) считается в **пунктах PDF**
 * (как и `bodyFont`), а итоговые [SourceSpan.bounds] / [ReflowBlock.Figure]
 * нормализуются в `[0..1]` относительно страницы — одно и то же из глифа, но в
 * разных системах координат.
 *
 * Этапы:
 *  1. классификация типа содержимого ([classify]);
 *  2. для каждой страницы — группировка глифов в строки, строк в абзацы и
 *     заголовки, вставка нетекстовых областей как фигур;
 *  3. провенанс: на каждый исходный ран глифов — [SourceSpan] с диапазоном
 *     символов в итоговом тексте блока.
 */
internal object ReflowAssembler {
    /** Кегль строки относительно основного, начиная с которого она — заголовок. */
    private const val HEADING_RATIO = 1.2f

    /** Порог смены кегля (доля основного), разрывающий абзац. */
    private const val FONT_CHANGE_FRAC = 0.15f

    /** Вертикальный зазор (доля основного кегля) между строками, разрывающий абзац. */
    private const val PARA_GAP_FACTOR = 0.7f

    /**
     * Минимальный шаг строк (baseline-to-baseline, доля основного кегля), с
     * которого начинается разрыв абзаца, — нижняя граница на случай, когда
     * медианный шаг документа недоступен/вырожден (мало строк). Обычная вёрстка
     * книги идёт с шагом ~1.15–1.45 кегля (внутри абзаца; сгенерированный PDF —
     * 1.25), а разрыв абзаца добавляет заметную выноску — в измеренном PDF-гайде
     * (15 pt) абзацный шаг ≈1.60 кегля (24 pt) против внутриабзацного ≈1.20
     * (18 pt). Порог берётся между этими кластерами (1.5), чтобы и не дробить
     * перенос строк, и отделять абзацы при недоступной медиане.
     */
    private const val MIN_PARA_PITCH_FRAC = 1.5f

    /**
     * Абсолютная надбавка к **типичному внутриабзацному** шагу (доля основного
     * кегля), начиная с которой шаг считается разрывом абзаца, когда медиана
     * надёжна. Разрыв — это обычная выноска плюс заметный зазор: в измеренном
     * PDF абзацный шаг (≈24 pt) превышает типичный (≈18 pt) на ≈6 pt ≈ 0.4 кегля,
     * тогда как самый плотный список даёт лишь +1.5–2.3 pt (≤0.15 кегля). Порог
     * +0.30 кегля проходит между ними (типичный 18 → порог 22.5 pt: на 2.2 pt
     * выше плотного списка, на 1.5 pt ниже абзацного разрыва). Аддитивная (а не
     * множительная) надбавка устойчива к величине выноски: разрыв всегда
     * прибавляет абсолютный зазор поверх обычного шага.
     */
    private const val PARA_PITCH_MARGIN_FRAC = 0.30f

    /**
     * Квантиль (доля) шага строк, берущийся за **типичный внутриабзацный**:
     * разрывы абзацев и зазоры вокруг заголовков образуют верхний хвост
     * распределения, поэтому медиана (0.5) может смещаться вверх в документах с
     * множеством коротких одностраничных абзацев. Нижний квантиль попадает на
     * самый частый (и самый малый) повторяющийся шаг — обычную межстрочную
     * выноску — и не завышается хвостом разрывов.
     */
    private const val INTRA_PITCH_QUANTILE = 0.4f

    /**
     * Сколько образцов межстрочного шага нужно, чтобы медиане можно было верить
     * (иначе работает только кегельный порог [MIN_PARA_PITCH_FRAC]).
     */
    private const val MIN_PITCH_SAMPLES = 4

    /** Допуск группировки глифов в одну строку (доля основного кегля). */
    private const val LINE_TOLERANCE_FRAC = 0.5f

    /** Горизонтальный зазор (доля кегля строки), трактуемый как пробел между словами (когда ширина пробела неизвестна). */
    private const val SPACE_FACTOR = 0.25f

    /** Доля ширины пробела шрифта, начиная с которой зазор считается межсловным (при известной ширине). */
    private const val SPACE_WIDTH_FRACTION = 0.5f

    /** Дефисы, снимаемые при переносе слова на конце строки: ASCII, типографский (‐), мягкий. */
    private const val HYPHEN_CHARS = "-‐­"

    /** Знаки препинания, примыкающие к предыдущему слову: перед ними пробел не ставится. */
    private const val TRAILING_PUNCT = ",.;:!?)]}»…"

    private const val HEADING_LEVEL_1_RATIO = 1.8f
    private const val HEADING_LEVEL_2_RATIO = 1.4f

    /** Горизонтальный зазор (доля кегля), начиная с которого это граница колонок таблицы. */
    private const val COLUMN_GAP_FACTOR = 1.5f

    /** Допуск выравнивания левых краёв ячеек в одну колонку (доля кегля). */
    private const val COLUMN_ALIGN_FACTOR = 1.0f

    /** Минимум «колоночных» строк (≥2 сегмента), чтобы счесть блок таблицей. */
    private const val MIN_TABLE_ROWS = 2

    /** Сколько подряд не-колоночных строк (переносы ячеек) допускается между строками таблицы. */
    private const val MAX_TABLE_GAP_LINES = 2

    /**
     * Классифицирует тип содержимого по наличию текстового слоя на страницах.
     */
    fun classify(pages: List<RawPage>): PdfContentKind {
        if (pages.isEmpty()) return PdfContentKind.IMAGE_ONLY
        val textPages = pages.count { it.glyphs.isNotEmpty() }
        return when (textPages) {
            0 -> PdfContentKind.IMAGE_ONLY
            pages.size -> PdfContentKind.TEXT_BASED
            else -> PdfContentKind.HYBRID
        }
    }

    /**
     * Собирает [ReflowDocument] из всех страниц: блоки идут в порядке чтения,
     * страницы конкатенируются (постраничная вёрстка снимается).
     */
    fun assemble(pages: List<RawPage>): ReflowDocument {
        val bodyFont = medianFontSize(pages.flatMap { it.glyphs })
        val pageLines = pages.map { it to groupLines(it.glyphs, bodyFont, it) }
        // Шаг строк (baseline-to-baseline) стабилен к вариации высоты глиф-бокса
        // между шрифтами/версиями PDFBox, в отличие от зазора по нижнему краю
        // бокса, — поэтому разрыв абзаца считаем по нему.
        val typicalPitch = typicalLinePitch(pageLines.map { it.second })
        val blocks = pageLines.flatMap { (page, lines) -> buildPageBlocks(page, lines, bodyFont, typicalPitch) }
        return ReflowDocument(kind = classify(pages), blocks = blocks)
    }

    private fun buildPageBlocks(
        page: RawPage,
        lines: List<Line>,
        bodyFont: Float,
        typicalPitch: Float,
    ): List<ReflowBlock> {
        // Таблицы вытаскиваем до сборки абзацев: их строки не должны слипнуться в текст.
        val tables =
            detectTableRanges(lines, page.widthPt, bodyFont).mapNotNull { range ->
                buildTable(lines.subList(range.first, range.last + 1), page.widthPt, bodyFont)?.let { range to it }
            }
        val inTable = BooleanArray(lines.size)
        tables.forEach { (range, _) -> for (index in range) inTable[index] = true }

        val items =
            buildList {
                tables.forEach { (range, table) -> add(Item.Table(table, lines[range.first].top)) }
                lines.forEachIndexed { index, line -> if (!inTable[index]) add(Item.Text(line)) }
                page.images
                    .filter {
                        !FigureGeometry.isFullPage(it, page.widthPt, page.heightPt) &&
                            !FigureGeometry.isTooSmall(it, page.widthPt, page.heightPt)
                    }
                    .forEach { add(Item.Image(it)) }
            }.sortedBy { it.top }

        val builder = BlockBuilder(page.pageIndex, page.widthPt, page.heightPt, bodyFont, typicalPitch)
        items.forEach { item ->
            when (item) {
                is Item.Text -> builder.addLine(item.line)
                is Item.Image -> builder.addImage(item.rect)
                is Item.Table -> builder.addTable(item.table)
            }
        }
        return builder.build()
    }

    /**
     * Диапазоны строк, образующих таблицы: подряд идущие «колоночные» строки
     * (≥2 сегмента, разделённых большими зазорами). Между ними допускаются
     * переносы ячеек (одиночные сегменты) — до [MAX_TABLE_GAP_LINES] строк.
     */
    private fun detectTableRanges(
        lines: List<Line>,
        pageWidthPt: Float,
        fontSize: Float,
    ): List<IntRange> {
        if (lines.size < MIN_TABLE_ROWS || fontSize <= 0f) return emptyList()
        val columnGapPt = fontSize * COLUMN_GAP_FACTOR
        val columnar = lines.map { it.toSegments(pageWidthPt, columnGapPt).size >= 2 }
        val ranges = mutableListOf<IntRange>()
        var i = 0
        while (i < lines.size) {
            if (!columnar[i]) {
                i++
                continue
            }
            var last = i
            var columnarCount = 1
            var j = i + 1
            while (j < lines.size) {
                when {
                    columnar[j] -> {
                        last = j
                        columnarCount++
                        j++
                    }

                    j - last <= MAX_TABLE_GAP_LINES -> j++ // перенос ячейки между строками таблицы
                    else -> break
                }
            }
            if (columnarCount >= MIN_TABLE_ROWS) {
                ranges += i..last
                i = last + 1
            } else {
                i++
            }
        }
        return ranges
    }

    /**
     * Собирает [ReflowBlock.Table]: границы колонок выводятся из строк с явными
     * зазорами, а ячейки режутся **по позиции каждого глифа** относительно этих
     * границ — поэтому широкая ячейка, вплотную подошедшая к соседней колонке (без
     * заметного зазора), всё равно делится правильно. Ряды — по вертикальным
     * зазорам (как абзацы), перенос ячейки склеивается. `null`, если колонок < 2.
     */
    private fun buildTable(
        lines: List<Line>,
        pageWidthPt: Float,
        fontSize: Float,
    ): ReflowBlock.Table? {
        if (pageWidthPt <= 0f || fontSize <= 0f) return null
        val tolNorm = fontSize * COLUMN_ALIGN_FACTOR / pageWidthPt
        val gapSegments = lines.map { it.toSegments(pageWidthPt, fontSize * COLUMN_GAP_FACTOR) }
        val columns = columnLefts(gapSegments.flatten(), tolNorm)
        if (columns.size < 2) return null
        val columnSegments = lines.map { it.toColumnSegments(columns, tolNorm) }
        val rows =
            groupTableRows(lines, columnSegments, fontSize).map { rowSegments ->
                val cellSegments = List(columns.size) { mutableListOf<Segment>() }
                rowSegments.forEach { cellSegments[it.columnIndex].add(it.segment) }
                ReflowBlock.TableRow(
                    cells =
                        cellSegments.map { segments ->
                            val built = buildCell(segments)
                            ReflowBlock.TableCell(built.text, built.source)
                        },
                )
            }
        return if (rows.size >= MIN_TABLE_ROWS) ReflowBlock.Table(rows) else null
    }

    /** Левые края колонок: кластеризует левые края сегментов с допуском [tolNorm]. */
    private fun columnLefts(
        segments: List<Segment>,
        tolNorm: Float,
    ): List<Float> {
        val lefts = segments.map { it.leftNorm }.sorted()
        val edges = mutableListOf<Float>()
        for (left in lefts) {
            if (edges.isEmpty() || left - edges.last() > tolNorm) edges += left
        }
        return edges
    }

    /** Группирует строки таблицы в ряды по вертикальному зазору (перенос ячейки — тот же ряд). */
    private fun groupTableRows(
        lines: List<Line>,
        lineSegments: List<List<IndexedSegment>>,
        fontSize: Float,
    ): List<List<IndexedSegment>> {
        val rows = mutableListOf<MutableList<IndexedSegment>>()
        var prevBottom = 0f
        lines.forEachIndexed { index, line ->
            val isNewRow = rows.isEmpty() || (line.top - prevBottom) > fontSize * PARA_GAP_FACTOR
            if (isNewRow) rows.add(mutableListOf())
            rows.last().addAll(lineSegments[index])
            prevBottom = line.bottom
        }
        return rows
    }

    /** Текст ячейки + провенанс: сегменты (в т.ч. перенесённые) склеиваются пробелом. */
    private fun buildCell(segments: List<Segment>): BuiltText {
        val sb = StringBuilder()
        val spans = mutableListOf<SourceSpan>()
        for (segment in segments) {
            if (sb.isNotEmpty()) sb.append(' ')
            segment.pieces.forEachIndexed { index, piece ->
                if (index > 0 && segment.spaceBefore[index]) sb.append(' ')
                val start = sb.length
                sb.append(piece.text)
                spans += SourceSpan(piece.pageIndex, start, sb.length, piece.bounds, piece.bold, piece.monospace)
            }
        }
        return BuiltText(sb.toString(), spans)
    }

    private fun groupLines(
        glyphs: List<RawGlyph>,
        bodyFont: Float,
        page: RawPage,
    ): List<Line> {
        if (glyphs.isEmpty()) return emptyList()
        val reference = if (bodyFont > 0f) bodyFont else medianFontSize(glyphs)
        val tolerance = reference * LINE_TOLERANCE_FRAC
        val sorted = glyphs.sortedBy { it.rect.top }
        val lines = mutableListOf<MutableList<RawGlyph>>()
        var lineTop = sorted.first().rect.top
        for (glyph in sorted) {
            if (lines.isEmpty() || glyph.rect.top - lineTop > tolerance) {
                lines += mutableListOf(glyph)
                lineTop = glyph.rect.top
            } else {
                lines.last() += glyph
            }
        }
        return lines.map { buildLine(it, page) }
    }

    /**
     * Строит [Line] из глифов одной строки: каждый глиф становится
     * [SourcePiece] с нормализованным прямоугольником; [Line.spaceBefore]
     * помечает позиции, перед которыми по горизонтальному зазору нужен пробел.
     * Геометрия строки ([Line.top]/[Line.bottom]/[Line.fontSize]) остаётся в
     * пунктах для последующей группировки в абзацы.
     */
    private fun buildLine(
        glyphs: List<RawGlyph>,
        page: RawPage,
    ): Line {
        val sorted = glyphs.sortedBy { it.rect.left }
        val fontSize = medianFontSize(sorted)
        val pieces = ArrayList<SourcePiece>(sorted.size)
        val spaceBefore = ArrayList<Boolean>(sorted.size)
        var prevRight: Float? = null
        var pendingSpace = false
        for (glyph in sorted) {
            // Пробел PDFBox отдаёт отдельным глифом — это надёжная граница слова;
            // держим его как разделитель, но не как фрагмент-провенанс.
            if (glyph.text.isBlank()) {
                pendingSpace = true
                prevRight = glyph.rect.right
                continue
            }
            val gap = prevRight?.let { glyph.rect.left - it }
            // Порог пробела — по ширине пробела шрифта (если известна), иначе по кеглю.
            // Так настоящий межсловный зазор отличается от широкого трекинга букв
            // (в сканах буквы внутри слова могут стоять с заметными зазорами).
            val spaceThreshold =
                if (glyph.spaceWidthPt > 0f) glyph.spaceWidthPt * SPACE_WIDTH_FRACTION else fontSize * SPACE_FACTOR
            // Знаки препинания примыкают к предыдущему слову, даже если глиф стоит
            // с заметным зазором (типично для PDF) — иначе получается «слово ,».
            // Исключение — моноширинные глифы: в коде «.» начинает токен
            // (`.bodyAsText()`), и пробел перед ним нужно сохранить.
            val attachesToPrev =
                !glyph.monospace &&
                    glyph.text.firstOrNull()?.let { it in TRAILING_PUNCT } == true
            val needsSpace =
                pieces.isNotEmpty() && !attachesToPrev &&
                    (pendingSpace || (gap != null && gap > spaceThreshold))
            spaceBefore += needsSpace
            pieces +=
                SourcePiece(
                    text = glyph.text,
                    pageIndex = page.pageIndex,
                    bounds = glyph.rect.normalised(page.widthPt, page.heightPt),
                    bold = glyph.bold,
                    monospace = glyph.monospace,
                )
            pendingSpace = false
            prevRight = glyph.rect.right
        }
        return Line(
            pieces = pieces,
            spaceBefore = spaceBefore,
            top = sorted.minOf { it.rect.top },
            bottom = sorted.maxOf { it.rect.bottom },
            fontSize = fontSize,
        )
    }

    private fun medianFontSize(glyphs: List<RawGlyph>): Float {
        if (glyphs.isEmpty()) return 0f
        val sizes = glyphs.map { it.fontSizePt }.sorted()
        return sizes[sizes.size / 2]
    }

    /**
     * Типичный внутриабзацный шаг строк (baseline-to-baseline, в пунктах) по
     * всем парам соседних строк всех страниц. Это нормальная межстрочная
     * выноска документа; разрыв абзаца — шаг, заметно её превышающий.
     *
     * Берётся не медиана, а нижний квантиль [INTRA_PITCH_QUANTILE]: разрывы
     * абзацев и зазоры у заголовков сидят в верхнем хвосте распределения и
     * завысили бы медиану в документах с множеством коротких абзацев, тогда как
     * нижний квантиль устойчиво попадает на самый частый малый шаг — обычную
     * выноску. `0`, если строк недостаточно для надёжной оценки.
     */
    private fun typicalLinePitch(pageLines: List<List<Line>>): Float {
        val pitches = mutableListOf<Float>()
        for (lines in pageLines) {
            for (i in 1 until lines.size) {
                val pitch = lines[i].top - lines[i - 1].top
                if (pitch > 0f) pitches += pitch
            }
        }
        if (pitches.size < MIN_PITCH_SAMPLES) return 0f
        pitches.sort()
        val index = (pitches.size * INTRA_PITCH_QUANTILE).toInt().coerceIn(0, pitches.size - 1)
        return pitches[index]
    }

    private fun headingLevelForRatio(ratio: Float): Int =
        when {
            ratio >= HEADING_LEVEL_1_RATIO -> 1
            ratio >= HEADING_LEVEL_2_RATIO -> 2
            else -> 3
        }

    /** Нормализует прямоугольник из пунктов в доли `[0..1]` страницы. */
    private fun ReflowRect.normalised(
        widthPt: Float,
        heightPt: Float,
    ): ReflowRect =
        if (widthPt <= 0f || heightPt <= 0f) {
            this
        } else {
            ReflowRect(left / widthPt, top / heightPt, right / widthPt, bottom / heightPt)
        }

    /**
     * Накопитель блоков одной колонки: преобразует поток строк/изображений в
     * [ReflowBlock.Heading] / [ReflowBlock.Paragraph] / [ReflowBlock.Figure],
     * склеивая строки в абзацы, снимая переносы по дефису и собирая провенанс
     * ([SourceSpan]) в координатах итогового текста блока.
     */
    private class BlockBuilder(
        private val pageIndex: Int,
        private val widthPt: Float,
        private val heightPt: Float,
        private val bodyFont: Float,
        private val typicalPitch: Float,
    ) {
        private val blocks = mutableListOf<ReflowBlock>()
        private val pending = mutableListOf<Line>()

        /** 0 — в накоплении абзац основного текста; >0 — заголовок этого уровня. */
        private var pendingHeadingLevel = 0

        /** true — накапливается элемент списка (строка началась с маркера). */
        private var pendingList = false

        fun addImage(rect: ReflowRect) {
            flush()
            blocks += ReflowBlock.Figure(pageIndex, rect.normalised(widthPt, heightPt))
        }

        fun addTable(table: ReflowBlock.Table) {
            flush()
            blocks += table
        }

        fun addLine(line: Line) {
            val level = headingLevelOf(line)
            if (level > 0) {
                if (pending.isNotEmpty() && pendingHeadingLevel == level) {
                    pending += line
                } else {
                    flush()
                    pendingHeadingLevel = level
                    pending += line
                }
                return
            }
            // Маркер списка всегда начинает новый блок-элемент: соседние пункты
            // стоят плотно и иначе слиплись бы в один абзац.
            if (line.startsListItem()) {
                flush()
                pendingList = true
                pending += line
                return
            }
            if (pending.isNotEmpty() && breaksParagraph(line)) flush()
            pendingHeadingLevel = 0
            pending += line
        }

        fun build(): List<ReflowBlock> {
            flush()
            return blocks
        }

        private fun headingLevelOf(line: Line): Int =
            if (bodyFont > 0f && line.fontSize >= bodyFont * HEADING_RATIO) {
                headingLevelForRatio(line.fontSize / bodyFont)
            } else {
                0
            }

        /**
         * Разрыв абзаца — по **шагу строк** (baseline-to-baseline,
         * `line.top - last.top`), а не по зазору между боксами глифов: высота
         * глиф-бокса (`heightDir`) меняется от шрифта и версии PDFBox, из-за чего
         * нормальная межстрочная выноска (например, 1.25 кегля в
         * сгенерированном PDF) ложно превышала прежний бокс-порог и каждая строка
         * становилась отдельным абзацем. Шаг же остаётся стабильным.
         *
         * Порог — `max` от двух оценок, чтобы и не дробить перенос строк, и
         * отделять абзацы при умеренном межабзацном зазоре:
         *  - **аддитивный** относительно типичного шага документа (когда тот
         *    надёжен): `typicalPitch + margin·bodyFont`. Разрыв — обычная выноска
         *    плюс заметный абсолютный зазор; надбавка устойчива к величине самой
         *    выноски. Прежний множительный порог (×1.5 медианы ≈ 1.8 кегля) был
         *    слишком высок и сливал абзацы с умеренным зазором (в измеренном
         *    PDF-гайде разрыв ≈1.6 кегля при выноске ≈1.2);
         *  - **кегельная нижняя граница** [MIN_PARA_PITCH_FRAC] — когда типичный
         *    шаг недоступен (мало строк): отделяет абзац и в сканах/нативных PDF.
         */
        private fun breaksParagraph(line: Line): Boolean {
            if (pendingHeadingLevel > 0) return true
            val last = pending.last()
            val pitch = line.top - last.top
            val additiveThreshold = if (typicalPitch > 0f) typicalPitch + bodyFont * PARA_PITCH_MARGIN_FRAC else 0f
            val fontThreshold = bodyFont * MIN_PARA_PITCH_FRAC
            val pitchThreshold = maxOf(additiveThreshold, fontThreshold)
            val fontJump = bodyFont > 0f && abs(line.fontSize - last.fontSize) > bodyFont * FONT_CHANGE_FRAC
            return pitch > pitchThreshold || fontJump
        }

        private fun flush() {
            if (pending.isEmpty()) {
                pendingList = false
                return
            }
            // Элемент списка склеивается как абзац (перенос строк/дефис), но
            // эмитится отдельным типом блока для отступа в ридере.
            val built = if (pendingHeadingLevel > 0) buildHeading(pending) else buildParagraph(pending)
            if (built.text.isNotEmpty()) {
                blocks +=
                    when {
                        pendingHeadingLevel > 0 -> ReflowBlock.Heading(built.text, pendingHeadingLevel, built.source)
                        pendingList -> ReflowBlock.ListItem(built.text, built.source)
                        else -> ReflowBlock.Paragraph(built.text, built.source)
                    }
            }
            pending.clear()
            pendingHeadingLevel = 0
            pendingList = false
        }

        /** Абзац: межстрочный пробел, со снятием переноса по дефису. */
        private fun buildParagraph(lines: List<Line>): BuiltText {
            val sb = StringBuilder()
            val spans = mutableListOf<SourceSpan>()
            for (line in lines) {
                if (line.pieces.isEmpty()) continue
                if (sb.isNotEmpty()) {
                    if (isSoftHyphen(sb)) {
                        // Строка кончается «буква+дефис»: слово перенесено — соединяем
                        // без пробела. Мягкий перенос (следующая строка со строчной)
                        // — дефис убираем; составной (`Plugin-Name`) — оставляем.
                        if (line.startsLowercase()) {
                            sb.deleteAt(sb.length - 1)
                            shrinkLastSpan(spans)
                        }
                    } else {
                        sb.append(' ')
                    }
                }
                appendPieces(line, sb, spans)
            }
            return BuiltText(sb.toString(), spans.filter { it.charStart < it.charEnd })
        }

        /** Заголовок: всегда межстрочный пробел, без логики переноса. */
        private fun buildHeading(lines: List<Line>): BuiltText {
            val sb = StringBuilder()
            val spans = mutableListOf<SourceSpan>()
            for (line in lines) {
                if (line.pieces.isEmpty()) continue
                if (sb.isNotEmpty()) sb.append(' ')
                appendPieces(line, sb, spans)
            }
            return BuiltText(sb.toString(), spans.filter { it.charStart < it.charEnd })
        }

        /**
         * Дописывает раны строки в [sb], вставляя внутристрочные пробелы по
         * [Line.spaceBefore], и фиксирует [SourceSpan] на каждый ран. Разделители
         * (пробелы) не покрываются ни одним спаном.
         */
        private fun appendPieces(
            line: Line,
            sb: StringBuilder,
            spans: MutableList<SourceSpan>,
        ) {
            line.pieces.forEachIndexed { index, piece ->
                if (index > 0 && line.spaceBefore[index]) sb.append(' ')
                val start = sb.length
                sb.append(piece.text)
                spans += SourceSpan(piece.pageIndex, start, sb.length, piece.bounds, piece.bold, piece.monospace)
            }
        }

        /** Укорачивает последний спан на 1 символ (снятый дефис в конце буфера). */
        private fun shrinkLastSpan(spans: MutableList<SourceSpan>) {
            if (spans.isEmpty()) return
            val last = spans.removeAt(spans.size - 1)
            spans += last.copy(charEnd = last.charEnd - 1)
        }

        private fun isSoftHyphen(sb: StringBuilder): Boolean = sb.length >= 2 && sb.last() in HYPHEN_CHARS && sb[sb.length - 2].isLetter()
    }

    private data class SourcePiece(
        val text: String,
        val pageIndex: Int,
        val bounds: ReflowRect,
        val bold: Boolean = false,
        val monospace: Boolean = false,
    )

    private data class BuiltText(
        val text: String,
        val source: List<SourceSpan>,
    )

    private data class Line(
        val pieces: List<SourcePiece>,
        val spaceBefore: List<Boolean>,
        val top: Float,
        val bottom: Float,
        val fontSize: Float,
    ) {
        fun startsLowercase(): Boolean = pieces.firstOrNull()?.text?.firstOrNull()?.isLowerCase() == true

        /**
         * Строка открывает элемент списка: начинается с маркера-буллета
         * (`•`, `-`, `*`… — см. [BULLET_CHARS]) либо с нумерации вида `1.` / `2)`.
         * За маркером должна идти буква — начало текста пункта. Так `1.` (пункт)
         * отличается и от `3.14` (дробь, дальше цифра), и от перенесённой строки
         * `3), то …` (закрывающая скобка выражения вроде `mutableListOf(1, 2, 3)`,
         * дальше пунктуация). Пробелы тут не помогают — их глифы отбрасываются в
         * [buildLine].
         */
        fun startsListItem(): Boolean {
            val lead =
                buildString {
                    for (piece in pieces) {
                        append(piece.text)
                        if (length >= LIST_MARKER_SCAN) break
                    }
                }.trimStart()
            if (lead.isEmpty()) return false
            if (lead[0] in BULLET_CHARS) return true
            val digits = lead.takeWhile { it.isDigit() }
            if (digits.isEmpty() || digits.length >= lead.length || lead[digits.length] !in NUMBER_MARKERS) {
                return false
            }
            val afterMarker = lead.getOrNull(digits.length + 1)
            return afterMarker == null || afterMarker.isLetter()
        }

        /**
         * Делит строку на сегменты по горизонтальным зазорам шире [columnGapPt]
         * (пунктов) — кандидаты в ячейки таблицы. Без больших зазоров — один сегмент.
         */
        fun toSegments(
            pageWidthPt: Float,
            columnGapPt: Float,
        ): List<Segment> {
            if (pieces.isEmpty()) return emptyList()
            val segments = mutableListOf<Segment>()
            var start = 0
            for (i in 1 until pieces.size) {
                val gapPt = (pieces[i].bounds.left - pieces[i - 1].bounds.right) * pageWidthPt
                if (gapPt > columnGapPt) {
                    segments += segmentOf(start, i)
                    start = i
                }
            }
            segments += segmentOf(start, pieces.size)
            return segments
        }

        /**
         * Режет строку на сегменты по принадлежности глифов колонкам [columns]
         * (новый сегмент — там, где глиф попадает в другую колонку). Так ячейки
         * делятся по позиции, даже если между ними нет заметного зазора.
         */
        fun toColumnSegments(
            columns: List<Float>,
            tolNorm: Float,
        ): List<IndexedSegment> {
            if (pieces.isEmpty()) return emptyList()
            val segments = mutableListOf<IndexedSegment>()
            var start = 0
            var currentColumn = columnOf(pieces[0].bounds.left, columns, tolNorm)
            for (i in 1 until pieces.size) {
                val column = columnOf(pieces[i].bounds.left, columns, tolNorm)
                if (column != currentColumn) {
                    segments += IndexedSegment(segmentOf(start, i), currentColumn)
                    start = i
                    currentColumn = column
                }
            }
            segments += IndexedSegment(segmentOf(start, pieces.size), currentColumn)
            return segments
        }

        private fun segmentOf(
            from: Int,
            to: Int,
        ): Segment =
            Segment(
                pieces = pieces.subList(from, to),
                spaceBefore = spaceBefore.subList(from, to),
                leftNorm = pieces[from].bounds.left,
            )
    }

    /** Часть строки между большими зазорами — кандидат в ячейку колонки. */
    private data class Segment(
        val pieces: List<SourcePiece>,
        val spaceBefore: List<Boolean>,
        val leftNorm: Float,
    )

    /** Сегмент с известным индексом колонки. */
    private data class IndexedSegment(
        val segment: Segment,
        val columnIndex: Int,
    )

    private sealed interface Item {
        val top: Float

        data class Text(val line: Line) : Item {
            override val top: Float get() = line.top
        }

        data class Image(val rect: ReflowRect) : Item {
            override val top: Float get() = rect.top
        }

        data class Table(val table: ReflowBlock.Table, override val top: Float) : Item
    }
}

/** Сколько первых символов строки сканировать на маркер списка. */
private const val LIST_MARKER_SCAN = 6

/**
 * Символы-буллеты, открывающие элемент списка. Тире `—`/`–` сюда НЕ входят: в
 * русском тексте это знак предложения (в т.ч. в начале перенесённой строки), а
 * не маркер списка — иначе абзац ложно становится пунктом списка.
 */
private val BULLET_CHARS = "•‣◦▪●·-*".toSet()

/** Разделители после номера в нумерованном списке (`1.`, `2)`). */
private const val NUMBER_MARKERS = ".)"

/** Индекс колонки для левого края [left]: самая правая граница `columns`, не превышающая `left` (+ допуск). */
private fun columnOf(
    left: Float,
    columns: List<Float>,
    tolNorm: Float,
): Int {
    var column = 0
    for (i in columns.indices) {
        if (left + tolNorm >= columns[i]) column = i else break
    }
    return column
}
