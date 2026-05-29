package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import ru.kyamshanov.notepen.reflow.api.SourceSpan
import ru.kyamshanov.notepen.reflow.segmentation.XyCutSegmenter
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
    /**
     * Минимальный кегль строки относительно основного для сигнала «крупный шрифт»
     * в heading ensemble. Снижен с 1.2 до 1.15: раньше использовался как
     * единственный жёсткий критерий, теперь — один из 4 сигналов (ensemble требует
     * ≥2, см. [HEADING_ENSEMBLE_MIN_SIGNALS]).
     */
    private const val HEADING_RATIO = 1.15f

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

    /** Unicode soft hyphen U+00AD — типографская подсказка, что слово можно перенести. */
    private const val SOFT_HYPHEN = '­'

    /** Знаки препинания, примыкающие к предыдущему слову: перед ними пробел не ставится. */
    private const val TRAILING_PUNCT = ",.;:!?)]}»…"

    private const val HEADING_LEVEL_1_RATIO = 1.8f
    private const val HEADING_LEVEL_2_RATIO = 1.4f

    /**
     * Доля typicalPitch, начиная с которой зазор перед строкой считается «над
     * заголовком». 1.5× — мягче breaksParagraph (тот аддитивный, см.
     * PARA_PITCH_MARGIN_FRAC); ensemble требует ≥2 сигналов, так что отдельный
     * gap-сигнал может и не закрепить heading — это допустимо.
     */
    private const val HEADING_GAP_FACTOR = 1.5f

    /**
     * Сколько secondary-сигналов из 3 нужно подкрепить mandatory-сигнал «крупный
     * кегль» ([HEADING_RATIO]), чтобы строка стала заголовком:
     * boldMajority, gapAbove≥[HEADING_GAP_FACTOR]×typicalPitch, noTerminalPunct.
     *
     * Подход: font-ratio ≥ 1.15 — обязателен (sentence-style секций без
     * увеличения кегля в реальных PDF почти не бывает; их попытка детекции даёт
     * слишком много false positives на bold-inline в обычном абзаце). Плюс ≥1
     * secondary — отсекает «слегка крупнее» лишённые типографских признаков
     * строки, которые раньше детектились как heading на одном font-ratio.
     */
    private const val HEADING_SECONDARY_SIGNALS_MIN = 1

    /**
     * Терминальные знаки конца предложения: после них строка скорее завершает
     * абзац, а не открывает раздел. Двоеточие и точка с запятой — пограничные:
     * иногда подзаголовок-определение (`Note:`), иногда конец списка-фразы;
     * включаем, потому что в типичных PDF это чаще конец предложения.
     */
    private const val SENTENCE_TERMINATORS = ".!?…:;"

    /**
     * Минимальная длина строки (после trim) для эмиссии Heading. Огрызки в 1 символ
     * («Ф», «\», «'») почти всегда — OCR-мусор, на TOC бесполезен.
     */
    private const val HEADING_MIN_LENGTH = 2

    /**
     * Минимальное число «слов» в строке-кандидате, начиная с которого считаем
     * долю одиночных букв (F-3): «В х о д н а я» = 7 слов, >50% одиночные →
     * это OCR с расставленными буквами, не Heading.
     */
    private const val HEADING_SPACED_OUT_MIN_WORDS = 3

    /** Общий разделитель пробелов/таб'ов для разбиения на слова. */
    private val WHITESPACE_REGEX = Regex("\\s+")

    /**
     * Доля моноширинных глифов в строке, начиная с которой строка считается
     * «строкой кода» (а не «текстом с inline-code-fragment'ом»). 0.8 — высокий
     * порог, отсекает обычные абзацы с одним inline `code` (Markdown-style).
     */
    private const val CODE_LINE_MONOSPACE_FRACTION = 0.8f

    /**
     * Минимум моноширинных строк подряд для эмиссии Code-блока. Меньшее
     * количество — обычно подсказки в скобках или одиночные кодовые строки в
     * абзаце; они остаются inline через `SourceSpan(monospace=true)`.
     */
    private const val CODE_MIN_CONSECUTIVE_LINES = 2

    /**
     * Минимальная доля letter-or-digit в строке Heading. Ниже — текст состоит
     * преимущественно из пунктуации/спецсимволов («/», «.», «—»), это не реальный
     * заголовок, а строка-разделитель.
     */
    private const val HEADING_MIN_ALPHA_RATIO = 0.6f

    /** Горизонтальный зазор (доля кегля), начиная с которого это граница колонок таблицы. */
    private const val COLUMN_GAP_FACTOR = 1.5f

    /**
     * Допуск выравнивания левых краёв ячеек в одну колонку (доля кегля). 0.5 — половина
     * кегля; tighter, чем «целая буква» (1.0): отсекает sloppy alignments, при которых
     * соседние абзацы случайно вырастают в «таблицу».
     */
    private const val COLUMN_ALIGN_FACTOR = 0.5f

    /** Минимум «колоночных» строк (≥2 сегмента), чтобы счесть блок таблицей. */
    private const val MIN_TABLE_ROWS = 2

    /**
     * Минимум рядов для «широкой» таблицы (8+ колонок). Reasoning: legitimate wide
     * tables практически всегда имеют 3+ ряда (header + data); 2-рядные wide
     * candidates обычно — pseudo-row из выровненных по индентам глоссариев /
     * упражнений учебника.
     */
    private const val MIN_TABLE_ROWS_WIDE = 3

    /** Доля bold-ячеек в первой строке, начиная с которой таблица имеет header. */
    private const val TABLE_HEADER_BOLD_RATIO = 0.5f

    /**
     * Множитель кегля для drop cap: глифы ≥ этого множителя × body-font, в строке
     * только один символ — декоративная буквица, не Heading.
     */
    private const val DROP_CAP_RATIO = 3f

    /** Дефолтный XY-cut порог зазора (0.02 = 2% стороны страницы) — для HYBRID/OCR. */
    private const val XY_CUT_DEFAULT_MIN_GAP_FRACTION = 0.02f

    /**
     * Более строгий порог для TEXT_BASED: 10% ширины страницы. Столбцы таблиц
     * имеют gap ~ font-size = 1–2% страницы; пограничные wide-cell prose
     * false-positive — до ~7%. Multi-column journals обычно имеют gutter
     * ≥10%, поэтому порог 0.10 ловит реальные 2-колоночные layouts и не
     * разрезает таблицы / wide-cell prose в один столбец.
     */
    private const val XY_CUT_TEXT_BASED_MIN_GAP_FRACTION = 0.10f

    /** Минимум непустых ячеек в строке, чтобы статус header имел смысл. */
    private const val TABLE_HEADER_MIN_NON_EMPTY = 2

    /**
     * Доля кегля строки, при которой глиф считается super/subscript-кандидатом:
     * `glyph.fontSizePt < line.fontSize × этот множитель`. 0.7 = сильно меньше —
     * отсекает шрифт-вариации primary text (book/heading) и ловит only真ные
     * sup/sub'ы.
     */
    private const val SUBSCRIPT_FONT_RATIO = 0.7f

    /**
     * Сдвиг baseline (доля кегля строки): superscript глиф имеет
     * `bottom < median - delta`, subscript — `bottom > median + delta`. 0.25 —
     * мягкий порог; меньшее значение даёт ложноположительные на anti-aliased
     * выравнивании в OCR.
     */
    private const val SUBSCRIPT_BASELINE_DELTA = 0.25f

    /** Порог по числу колонок, выше которого начинается «широкая» политика (см. [MIN_TABLE_ROWS_WIDE]). */
    private const val WIDE_TABLE_COLS_THRESHOLD = 8

    /**
     * Минимальная допустимая доля непустых ячеек. Ниже — сетка считается разрежённой
     * (случайно выровненные сегменты, а не реальная таблица), confidence
     * принудительно зануляется. 0.5 — компромисс: реальные грамматические таблицы
     * с optional ячейками держатся выше; pseudo-tables из упражнений (где почти
     * каждая ячейка пустая, кроме первой) обнуляются.
     */
    private const val FILL_RATIO_HARD_MIN = 0.5f

    /** Сколько подряд не-колоночных строк (переносы ячеек) допускается между строками таблицы. */
    private const val MAX_TABLE_GAP_LINES = 2

    /**
     * Порог числа глифов, ниже которого XY-cut сегментацию пропускаем: на маленьких
     * страницах (заглушки, единичные строки) recursive split не даёт пользы и тратит
     * время впустую. ≈ 20 глифов = типичная короткая строка/заголовок.
     */
    private const val MIN_GLYPHS_FOR_XY_CUT = 20

    /**
     * Допуск выравнивания левых краёв подряд идущих list-маркеров (нормализованный
     * к ширине страницы): отступы в пределах этого порога считаются одним уровнем
     * вложенности. ≈ 1.5% ширины страницы — пограничный между типичной шириной
     * буквы (1–1.5%) и заметным indent'ом nested-списка (3–5%).
     */
    private const val LIST_INDENT_TOLERANCE_NORM = 0.015f

    /**
     * Полный credit `rowFactor` — c этого числа строк таблица перестаёт штрафоваться
     * за «короткость». 2-строчная (key-value) тоже легитимна, поэтому базис rowFactor
     * непустой (см. [computeTableConfidence]).
     */
    private const val TABLE_FULL_CREDIT_ROWS = 4

    /**
     * Граница средней длины ячейки (символов), на которой `lengthPenalty` обнуляется.
     * Реальные таблицы — короткие cells (5–30 символов); 100+ — почти всегда «втянутый»
     * абзац из соседней колонки на wide-cell/OCR случае.
     */
    private const val TABLE_MAX_TYPICAL_CELL_CHARS = 100f

    /**
     * Нижняя граница средней длины **непустой** ячейки (символов): OCR / column-gap
     * noise нарезает прозу пословно/побуквенно ("Лр", "т", "икл", "ъ"), и средняя
     * содержательная ячейка получается < 2 символов. Реальные таблицы имеют хотя бы
     * двухбуквенные ячейки ("Yes"/"No"/"5%"/"FB2"), и среднее у них ≥ 2.0. Таблица с
     * меньшим средним отбрасывается (`buildTable` возвращает null), и диапазон строк
     * деградирует в обычные параграфы — текст хотя бы читается потоком, а не пляшет
     * по сетке. См. F-1 (OCR-каша на учебнике Барановской).
     */
    private const val TABLE_MIN_AVG_CELL_CHARS = 2.0f

    /**
     * Максимальная доля «фрагментных» (≤ [TABLE_FRAGMENT_CELL_CHARS] символов) ячеек
     * среди непустых: OCR/column-gap noise часто даёт `"(О|с|нова|на|в|1997"` —
     * среднее 2.3, но 4 из 6 ячеек ≤ 2 chars. Real grammar/glossary table с
     * single-char header (например, кириллический алфавит «А|Б|В | Аист|Барс|Волк»)
     * даёт ≤ 50% фрагментов; пограничный порог 0.6 фильтрует noise, оставляя
     * подобные легитимные tables.
     */
    private const val TABLE_FRAGMENT_RATIO_LIMIT = 0.6f
    private const val TABLE_FRAGMENT_CELL_CHARS = 2

    /**
     * Граница confidence, ниже которой таблица заменяется на [ReflowBlock.Figure]-кроп
     * исходной страницы (см. пост-пасс в [buildPageBlocks]). 0.4 — компромисс между
     * сохранением 2-строчных key-value таблиц с короткими ячейками (conf ~0.7) и
     * отсечением широких false-positive с длинной прозой в ячейках (conf < 0.4).
     */
    private const val TABLE_CONFIDENCE_THRESHOLD = 0.4f

    /**
     * Жёсткий потолок числа колонок: при таком и более колонок «таблица»
     * гарантированно считается false positive — реальных таблиц с 30+ колонок
     * в типичных документах не бывает (на учебнике Барановской наблюдаемые
     * false positives имели 30–56 колонок: pseudo-row aligned exercise-текст).
     *
     * Реализуется как бинарная отсечка `colsPenalty = if (cols >= LIMIT) 0 else 1` —
     * мягкая линейная пеналь (12/20 или 15/25) уносила в Figure-фолбэк сотни
     * легитимных таблиц с conf 0.4–0.5 (~340K символов теряли searchability).
     * Бинарная отсечка сохраняет промежуточные ширины и фильтрует только явные
     * аномалии.
     */
    private const val TABLE_COLS_HARD_LIMIT = 30

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
        val kind = classify(pages)
        // Колонтитулы (running headers/footers) убираем ДО всего остального: они
        // не должны попадать ни в порядок чтения (XY-cut), ни в детект таблиц,
        // ни в abzac-сборку. Stripper консервативный — на коротких документах
        // ничего не делает.
        val cleanedPages = RunningHeaderStripper.strip(pages)
        val bodyFont = medianFontSize(cleanedPages.flatMap { it.glyphs })
        // XY-cut сегментация ДО groupLines — только для OCR/HYBRID PDF, где
        // text-layer noisy и groupLines склеивает глифы разных колонок одной Y
        // в одну Line (с перепутанным порядком «leftcol1 rightcol1»). Для
        // TEXT_BASED PDF не применяем: PDFTextStripper там даёт чистую
        // последовательность, плюс table-extraction (detectTableRanges) видит
        // table-column gaps теми же сигналами, что XY-cut, и сегментация
        // разрезала бы таблицы по колонкам, ломая их.
        // TEXT_BASED: XY-cut пока выключен — на чистом native PDF column gaps
        // таблиц/выровненных абзацев trigger'ят сегментацию, и таблицы режутся
        // по колонкам. Нужен detect-таблицы-сначала + exclude-zone в XY-cut'е
        // (не landed). Пока возвращаем как было: TEXT_BASED идёт без сегментации,
        // OCR/HYBRID — через XY-cut.
        val flatPages =
            if (kind == PdfContentKind.TEXT_BASED) cleanedPages else cleanedPages.flatMap { segmentPage(it) }
        // Footnotes: вынимаем small-font блоки из нижней зоны страниц. Возвращаем
        // (pageWithoutFootnotes, footnoteBlockOrNull). Извлечение before groupLines:
        // footnote-глифы не должны участвовать в подсчёте typicalPitch / bodyFont.
        val pagesWithFootnotes =
            flatPages.map { p ->
                val split = FootnoteExtractor.extractFootnote(p, bodyFont)
                split.page to split.footnote
            }
        val pageLines = pagesWithFootnotes.map { (p, _) -> p to groupLines(p.glyphs, bodyFont, p) }
        // Шаг строк (baseline-to-baseline) стабилен к вариации высоты глиф-бокса
        // между шрифтами/версиями PDFBox, в отличие от зазора по нижнему краю
        // бокса, — поэтому разрыв абзаца считаем по нему.
        val typicalPitch = typicalLinePitch(pageLines.map { it.second })
        val blocks =
            pageLines.flatMapIndexed { i, pair ->
                val (page, lines) = pair
                val pageBlocks = buildPageBlocks(page, lines, bodyFont, typicalPitch)
                // Footnote страницы приклеиваем в конец её блоков, перед переходом к
                // следующей странице. Так блок «принадлежит» правильной странице в
                // потоке чтения.
                val footnote = pagesWithFootnotes[i].second
                if (footnote != null && footnote.text.isNotEmpty()) pageBlocks + footnote else pageBlocks
            }
        // Cross-page table continuation: подряд идущие Tables с одинаковым числом
        // колонок склеиваются в одну. Срабатывает на multi-page tables (длинные
        // спецификации, библиографии в табличной форме).
        val joinedBlocks = joinContinuedTables(blocks)
        // Dehyphenation across page boundaries: если последний Paragraph
        // страницы заканчивается на дефис + буква, а следующий Paragraph
        // начинается со строчной буквы — это перенос слова через page break.
        // Склеиваем тексты, убирая дефис.
        val dehyphenated = dehyphenateAdjacentParagraphs(joinedBlocks)
        return ReflowDocument(kind = kind, blocks = dehyphenated)
    }

    /**
     * Пост-пасс: для каждой пары подряд идущих [ReflowBlock.Paragraph] / [ListItem],
     * где первый кончается на дефис (соединяющий буквы), а второй начинается
     * со строчной буквы, — склеиваем их в один блок с удалением дефиса.
     *
     * Узкая эвристика: только Paragraph→Paragraph и ListItem→ListItem (последний —
     * редко, обычно перенос предложения в списке). Heading→Paragraph и прочие
     * комбинации не трогаем.
     *
     * Spans второго блока сдвигаются на длину первого после слияния (с поправкой
     * на удалённый дефис).
     */
    private fun dehyphenateAdjacentParagraphs(blocks: List<ReflowBlock>): List<ReflowBlock> {
        if (blocks.size < 2) return blocks
        val out = mutableListOf<ReflowBlock>()
        var i = 0
        while (i < blocks.size) {
            val curr = blocks[i]
            val next = blocks.getOrNull(i + 1)
            if (next != null && shouldDehyphenateAcross(curr, next)) {
                out += mergeAcrossHyphen(curr as ReflowBlock.Paragraph, next as ReflowBlock.Paragraph)
                i += 2
            } else {
                out += curr
                i++
            }
        }
        return out
    }

    /**
     * `true`, если оба блока — Paragraph, первый кончается на дефис+буква (т.е.
     * перенос слова в смысле [isSoftHyphenSeparator]), а второй начинается со
     * строчной буквы.
     */
    private fun shouldDehyphenateAcross(
        a: ReflowBlock,
        b: ReflowBlock,
    ): Boolean {
        if (a !is ReflowBlock.Paragraph || b !is ReflowBlock.Paragraph) return false
        val aText = a.text.trimEnd()
        if (aText.length < 2) return false
        val last = aText.last()
        val prev = aText[aText.length - 2]
        if (last !in HYPHEN_CHARS || !prev.isLetter()) return false
        val bFirst = b.text.firstOrNull { !it.isWhitespace() } ?: return false
        return bFirst.isLowerCase()
    }

    private fun mergeAcrossHyphen(
        a: ReflowBlock.Paragraph,
        b: ReflowBlock.Paragraph,
    ): ReflowBlock.Paragraph {
        // Убираем последний символ (дефис) у первого, объединяем тексты, сдвигаем
        // спаны второго.
        val trimmedA = a.text.trimEnd()
        val noHyphenA = trimmedA.dropLast(1)
        val merged = noHyphenA + b.text.trimStart()
        val shift = noHyphenA.length - (b.text.length - b.text.trimStart().length)
        val newSpans =
            a.source +
                b.source.map { span ->
                    span.copy(charStart = span.charStart + shift, charEnd = span.charEnd + shift)
                }
        return ReflowBlock.Paragraph(text = merged, source = newSpans)
    }

    /**
     * Сливает подряд идущие [ReflowBlock.Table] с одинаковым числом колонок: чаще
     * всего это таблица, разбитая страничным разрывом исходного PDF на два Table-
     * блока. Сливаем агрессивно по равенству cols — column-lefts проверки
     * пропускаем, потому что разные страницы могут иметь чуть смещённую раскладку
     * (page margins), но столбцов столько же.
     *
     * Confidence результата — `min(parts)` (консервативно: одна слабая половина
     * понижает общую). Если в первой части был header — у объединения header
     * сохраняется на первой строке, остальные `isHeader=false` (даже если в
     * новой части тоже определилась header — на cross-page таблице вторая
     * шапка повторяет первую, её опускаем).
     */
    private fun joinContinuedTables(blocks: List<ReflowBlock>): List<ReflowBlock> {
        if (blocks.size < 2) return blocks
        val out = mutableListOf<ReflowBlock>()
        var i = 0
        while (i < blocks.size) {
            val curr = blocks[i]
            if (curr !is ReflowBlock.Table) {
                out += curr
                i++
                continue
            }
            val cols = curr.rows.firstOrNull()?.cells?.size ?: 0
            var j = i + 1
            val parts = mutableListOf(curr)
            // Жадно гребём следующие Tables с тем же числом колонок. Прерываемся,
            // как только встретили НЕ-Table.
            while (j < blocks.size) {
                val next = blocks[j] as? ReflowBlock.Table ?: break
                val nextCols = next.rows.firstOrNull()?.cells?.size ?: 0
                if (nextCols != cols || cols == 0) break
                parts += next
                j++
            }
            if (parts.size == 1) {
                out += curr
            } else {
                out += mergeTableParts(parts)
            }
            i = j
        }
        return out
    }

    private fun mergeTableParts(parts: List<ReflowBlock.Table>): ReflowBlock.Table {
        val allRows = mutableListOf<ReflowBlock.TableRow>()
        val firstHeaderIdx = parts.first().rows.indexOfFirst { it.isHeader }
        for ((partIdx, part) in parts.withIndex()) {
            for ((rowIdx, row) in part.rows.withIndex()) {
                // Header сохраняем только из первой части (на первой её позиции).
                val keepHeader = partIdx == 0 && rowIdx == firstHeaderIdx && firstHeaderIdx >= 0
                allRows += if (keepHeader) row else row.copy(isHeader = false)
            }
        }
        val conf = parts.minOf { it.confidence }
        return ReflowBlock.Table(rows = allRows, confidence = conf)
    }

    /**
     * Делит [page] на регионы XY-cut'ом. Однорегионная страница возвращается как
     * есть (нет лишних аллокаций); многорегионная — как список sub-RawPage'ей в
     * порядке чтения. Images распределяются по регионам по midpoint картинки:
     * ищем регион, чей bbox содержит центр изображения (или ближайший по
     * Manhattan-расстоянию, если ни один не содержит). Это нужно для multi-column
     * страниц: figure в правой колонке должна попасть в правую sub-RawPage,
     * иначе при flow-reading она «выпрыгнет» в неправильное место текста.
     */
    private fun segmentPage(
        page: RawPage,
        minGapFraction: Float = XY_CUT_DEFAULT_MIN_GAP_FRACTION,
    ): List<RawPage> {
        val regions =
            if (page.glyphs.size >= MIN_GLYPHS_FOR_XY_CUT) {
                XyCutSegmenter.segment(
                    glyphRects = page.glyphs.map { it.rect },
                    pageWidthPt = page.widthPt,
                    pageHeightPt = page.heightPt,
                    minGapFraction = minGapFraction,
                )
            } else {
                emptyList()
            }
        if (regions.size <= 1) return listOf(page)
        val regionBounds = regions.map { glyphIndices -> regionBounds(glyphIndices, page.glyphs) }
        val imagesPerRegion = assignImagesToRegions(page.images, regionBounds)
        return regions.mapIndexed { regionIndex, glyphIndices ->
            page.copy(
                glyphs = glyphIndices.map { page.glyphs[it] },
                images = imagesPerRegion[regionIndex],
            )
        }
    }

    /**
     * Bbox региона = объединение прямоугольников его глифов. Региону без глифов
     * (теоретически невозможно — XyCutSegmenter не эмитит пустые) даём пустой
     * вырожденный rect, который assignImagesToRegions исключит из «contains».
     */
    private fun regionBounds(
        glyphIndices: List<Int>,
        glyphs: List<RawGlyph>,
    ): ReflowRect {
        if (glyphIndices.isEmpty()) {
            return ReflowRect(0f, 0f, 0f, 0f)
        }
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        for (i in glyphIndices) {
            val r = glyphs[i].rect
            if (r.left < left) left = r.left
            if (r.top < top) top = r.top
            if (r.right > right) right = r.right
            if (r.bottom > bottom) bottom = r.bottom
        }
        return ReflowRect(left, top, right, bottom)
    }

    /**
     * Распределяет [images] по регионам по midpoint: для каждой картинки находим
     * регион, чей bbox содержит её центр; если ни один не содержит — берём с
     * минимальным расстоянием от центра картинки до bbox региона (по Manhattan).
     * Возвращает [List<List<ReflowRect>>] длины `regions.size`.
     */
    private fun assignImagesToRegions(
        images: List<ReflowRect>,
        regions: List<ReflowRect>,
    ): List<List<ReflowRect>> {
        val buckets = List(regions.size) { mutableListOf<ReflowRect>() }
        for (img in images) {
            val midX = (img.left + img.right) / 2f
            val midY = (img.top + img.bottom) / 2f
            val containerIdx =
                regions.indexOfFirst { r ->
                    midX in r.left..r.right && midY in r.top..r.bottom && (r.right > r.left) && (r.bottom > r.top)
                }
            val targetIdx =
                if (containerIdx >= 0) {
                    containerIdx
                } else {
                    var bestIdx = 0
                    var bestDist = Float.POSITIVE_INFINITY
                    regions.forEachIndexed { idx, r ->
                        val dx = maxOf(r.left - midX, 0f, midX - r.right)
                        val dy = maxOf(r.top - midY, 0f, midY - r.bottom)
                        val dist = dx + dy
                        if (dist < bestDist) {
                            bestDist = dist
                            bestIdx = idx
                        }
                    }
                    bestIdx
                }
            buckets[targetIdx].add(img)
        }
        return buckets
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
                tables.forEach { (range, table) ->
                    val tableLines = lines.subList(range.first, range.last + 1)
                    add(tableOrFallbackItem(table, tableLines, page, lines[range.first].top))
                }
                lines.forEachIndexed { index, line -> if (!inTable[index]) add(Item.Text(line)) }
                page.images
                    .filter {
                        !FigureGeometry.isFullPage(it, page.widthPt, page.heightPt) &&
                            !FigureGeometry.isTooSmall(it, page.widthPt, page.heightPt)
                    }.forEach { add(Item.Image(it)) }
            }.sortedBy { it.top }

        val builder = BlockBuilder(page.pageIndex, page.widthPt, page.heightPt, bodyFont, typicalPitch)
        items.forEach { item ->
            when (item) {
                is Item.Text -> builder.addLine(item.line)
                is Item.Image -> builder.addImage(item.rect)
                is Item.Table -> builder.addTable(item.table)
                is Item.PrebuiltFigure -> builder.addFigure(item.figure)
            }
        }
        return builder.build()
    }

    /**
     * Решение «таблица или Figure-фолбэк» для одного диапазона: low-confidence таблица
     * заменяется на crop исходной страницы (см. [tableAsFigureFallback]). Вынесено из
     * лямбды [buildList], чтобы цикломатическая сложность [buildPageBlocks] не росла.
     * Если bounds вычислить не удалось (теоретически невозможно) — оставляем Table.
     */
    private fun tableOrFallbackItem(
        table: ReflowBlock.Table,
        tableLines: List<Line>,
        page: RawPage,
        top: Float,
    ): Item {
        val figure =
            if (table.confidence < TABLE_CONFIDENCE_THRESHOLD) tableAsFigureFallback(tableLines, page) else null
        return if (figure != null) Item.PrebuiltFigure(figure, top) else Item.Table(table, top)
    }

    /**
     * Stream-fallback для low-confidence таблицы: собирает [ReflowBlock.Figure] из union
     * bbox'ов всех глифов table-range. Координаты bbox остаются в нормализованной
     * `[0..1]`-системе страницы (как у обычных [ReflowBlock.Figure]); aspectRatio —
     * по реальным размерам в PDF-точках, чтобы высота crop'а была детерминирована.
     *
     * `null` означает «нет глифов в диапазоне» — теоретически невозможно (table-range
     * не строится без сегментов), но защищаемся от пустых строк.
     */
    private fun tableAsFigureFallback(
        tableLines: List<Line>,
        page: RawPage,
    ): ReflowBlock.Figure? {
        val bounds = unionGlyphBoundsNorm(tableLines) ?: return null
        val widthPt = bounds.width * page.widthPt
        val heightPt = bounds.height * page.heightPt
        val aspectRatio = if (heightPt > 0f) widthPt / heightPt else 1f
        // wasTableFallback=true — маркер для LatticeTableRefiner: эта Figure пришла
        // из Stream-fallback, и если на странице есть нарисованная сетка, её можно
        // попробовать восстановить в Table.
        return ReflowBlock.Figure(
            pageIndex = page.pageIndex,
            bounds = bounds,
            aspectRatio = aspectRatio,
            wasTableFallback = true,
        )
    }

    /** Union bbox'ов всех глифов в строках (нормализованные `[0..1]`-координаты). */
    private fun unionGlyphBoundsNorm(lines: List<Line>): ReflowRect? {
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        var seen = false
        lines.forEach { line ->
            line.pieces.forEach { piece ->
                seen = true
                if (piece.bounds.left < left) left = piece.bounds.left
                if (piece.bounds.top < top) top = piece.bounds.top
                if (piece.bounds.right > right) right = piece.bounds.right
                if (piece.bounds.bottom > bottom) bottom = piece.bounds.bottom
            }
        }
        return if (seen) ReflowRect(left, top, right, bottom) else null
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
        if (rows.size < MIN_TABLE_ROWS) return null
        // Wide-tables (8+ cols) с <3 рядов почти всегда — pseudo-row из выровненных
        // упражнений / глоссариев; legitimate wide tables имеют header + data.
        if (columns.size >= WIDE_TABLE_COLS_THRESHOLD && rows.size < MIN_TABLE_ROWS_WIDE) return null
        // OCR-noise guard (F-1): средняя длина непустой ячейки ниже [TABLE_MIN_AVG_CELL_CHARS]
        // ИЛИ доля «фрагментных» (≤[TABLE_FRAGMENT_CELL_CHARS]) ячеек выше
        // [TABLE_FRAGMENT_RATIO_LIMIT] — column-gap detector почти всегда разрезал
        // обычную прозу по глифам (учебник Барановской: "(О", "с", "нова", "на", "в",
        // "1997"...). Считаем по non-empty cells, чтобы редкие легитимные таблицы с
        // optional пустыми ячейками не наказывались. Возврат null отдаёт диапазон
        // обратно в абзацный сборщик — пусть пляшущий OCR хотя бы читается строкой.
        run {
            val nonEmptyLengths = rows.flatMap { row -> row.cells.map { it.text.length }.filter { it > 0 } }
            if (nonEmptyLengths.isNotEmpty()) {
                val avg = nonEmptyLengths.sum().toFloat() / nonEmptyLengths.size
                if (avg < TABLE_MIN_AVG_CELL_CHARS) return null
                val fragments = nonEmptyLengths.count { it <= TABLE_FRAGMENT_CELL_CHARS }
                if (fragments.toFloat() / nonEmptyLengths.size > TABLE_FRAGMENT_RATIO_LIMIT) return null
            }
        }
        // Header detection: первая строка с ≥TABLE_HEADER_BOLD_RATIO долей bold-ячеек
        // — таблица заголовка. На остальных строках isHeader=false default.
        val firstRow = rows.first()
        val isFirstHeader = isHeaderRow(firstRow)
        val finalRows =
            if (!isFirstHeader || rows.isEmpty()) {
                rows
            } else {
                listOf(firstRow.copy(isHeader = true)) + rows.drop(1)
            }
        return ReflowBlock.Table(rows = finalRows, confidence = computeTableConfidence(finalRows))
    }

    /**
     * `true`, если первая строка таблицы выглядит как header: непустых ячеек
     * минимум 2, и доля ячеек, в которых **большинство spans** полужирные,
     * ≥ [TABLE_HEADER_BOLD_RATIO]. Reasoning: legitimate headers — это «Name»,
     * «Type», «Value» bold-ом; обычные данные — нет.
     */
    private fun isHeaderRow(row: ReflowBlock.TableRow): Boolean {
        val nonEmpty = row.cells.filter { it.text.isNotBlank() }
        if (nonEmpty.size < TABLE_HEADER_MIN_NON_EMPTY) return false
        val boldCells =
            nonEmpty.count { cell ->
                val spans = cell.source
                spans.isNotEmpty() && spans.count { it.bold } * 2 >= spans.size
            }
        return boldCells.toFloat() / nonEmpty.size >= TABLE_HEADER_BOLD_RATIO
    }

    /**
     * Агрегатная уверенность Stream-детектора, `[0..1]`. Три фактора, перемноженные:
     *  - **rowFactor** — масштаб от числа строк; базис 0.5 (легитимные key-value
     *    таблицы тоже считаем) + до 0.5 за «полноту» к [TABLE_FULL_CREDIT_ROWS];
     *  - **fillRatio** — доля непустых ячеек: разрежённая сетка обычно означает
     *    случайно выровненные сегменты, а не реальную таблицу;
     *  - **lengthPenalty** — средняя длина ячейки: реальные таблицы — короткие
     *    cells (5–40 символов), длинные — почти всегда «втянутый» абзац из соседней
     *    колонки на OCR/wide-cell случае.
     *
     * Column-alignment tightness не учитываем: цена считать стандартное отклонение
     * на этапе сборки выше, чем полезность сигнала (колонки уже отсечены по
     * [COLUMN_ALIGN_FACTOR]). Если defect (a) не закроется — добавим в Slice 2.
     */
    private fun computeTableConfidence(rows: List<ReflowBlock.TableRow>): Float {
        val totalCells = rows.sumOf { it.cells.size }
        if (totalCells == 0) return 0f
        val nonEmpty = rows.sumOf { row -> row.cells.count { it.text.isNotBlank() } }
        val fillRatio = nonEmpty.toFloat() / totalCells
        val meanCellChars = rows.sumOf { row -> row.cells.sumOf { it.text.length } }.toFloat() / totalCells
        val rowFactor = 0.5f + 0.5f * (rows.size / TABLE_FULL_CREDIT_ROWS.toFloat()).coerceAtMost(1f)
        val lengthPenalty = 1f - (meanCellChars / TABLE_MAX_TYPICAL_CELL_CHARS).coerceIn(0f, 1f)
        val cols = rows.first().cells.size
        // Бинарная отсечка по числу колонок: только явные аномалии (30+) обнуляются.
        val colsPenalty = if (cols >= TABLE_COLS_HARD_LIMIT) 0f else 1f
        // Бинарная отсечка по fillRatio: разрежённая сетка (50% и менее non-empty)
        // — почти всегда случайно выровненные сегменты. Soft factor (fillRatio
        // multiply) уже даёт градиент сверху, hard cutoff отсекает совсем разреженные.
        val fillPenalty = if (fillRatio < FILL_RATIO_HARD_MIN) 0f else 1f
        return rowFactor * fillRatio * lengthPenalty * colsPenalty * fillPenalty
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
                spans +=
                    SourceSpan(
                        pageIndex = piece.pageIndex,
                        charStart = start,
                        charEnd = sb.length,
                        bounds = piece.bounds,
                        bold = piece.bold,
                        monospace = piece.monospace,
                        italic = piece.italic,
                        superscript = piece.superscript,
                        subscript = piece.subscript,
                    )
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
        // Baseline для super/subscript-детекции: медиана `rect.bottom` среди
        // глифов primary-кегля. Глифы заметно меньшего кегля и со сдвигом по Y
        // — super/sub.
        val primaryBottoms = sorted.filter { it.fontSizePt >= fontSize * 0.9f }.map { it.rect.bottom }
        val medianBottom = if (primaryBottoms.isEmpty()) 0f else primaryBottoms.sorted()[primaryBottoms.size / 2]
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
                pieces.isNotEmpty() &&
                    !attachesToPrev &&
                    (pendingSpace || (gap != null && gap > spaceThreshold))
            spaceBefore += needsSpace
            val isSmallFont = fontSize > 0f && glyph.fontSizePt < fontSize * SUBSCRIPT_FONT_RATIO
            val bottomDelta = glyph.rect.bottom - medianBottom
            val isSuperscript = isSmallFont && bottomDelta < -fontSize * SUBSCRIPT_BASELINE_DELTA
            val isSubscript = isSmallFont && bottomDelta > fontSize * SUBSCRIPT_BASELINE_DELTA
            pieces +=
                SourcePiece(
                    text = glyph.text,
                    pageIndex = page.pageIndex,
                    bounds = glyph.rect.normalised(page.widthPt, page.heightPt),
                    bold = glyph.bold,
                    italic = glyph.italic,
                    monospace = glyph.monospace,
                    superscript = isSuperscript,
                    subscript = isSubscript,
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

        /** Уровень вложенности накапливаемого list-элемента (см. [computeListLevel]). */
        private var pendingListLevel = 0

        /**
         * true — накапливается code-блок (подряд идущие моноширинные строки).
         * Имеет приоритет над heading/list/paragraph в [addLine].
         */
        private var pendingCode = false

        /**
         * Нормализованный отступ маркера последнего эмитированного list-элемента
         * (для сравнения с новым). `null` — list-flow прерван (после non-list
         * блока / перед первым list-элементом страницы), уровень начинается с 0.
         */
        private var lastListIndent: Float? = null

        /** Уровень последнего эмитированного list-элемента (см. [lastListIndent]). */
        private var lastListLevel: Int = 0

        /**
         * Низ предыдущей текстовой строки (PDF-пункты), для подсчёта вертикального
         * зазора как сигнала heading ensemble (см. [countHeadingSignals]). `null`
         * означает «первая строка страницы / блока» — gap-сигнал не срабатывает.
         */
        private var lastLineBottom: Float? = null

        fun addImage(rect: ReflowRect) {
            flush()
            // aspectRatio считаем по исходным PDF-точкам ДО нормализации: после
            // нормализации `bounds.width/bounds.height` отражает пропорцию относительно
            // страницы, а не самой картинки.
            val ratio = if (rect.height > 0f) rect.width / rect.height else 1f
            blocks += ReflowBlock.Figure(pageIndex, rect.normalised(widthPt, heightPt), ratio)
        }

        fun addTable(table: ReflowBlock.Table) {
            flush()
            blocks += table
        }

        /**
         * Кладёт уже собранную [ReflowBlock.Figure] прямо в блоки (в отличие от
         * [addImage], которой надо денормализовать координаты). Нужна для
         * Stream-фолбэка low-confidence таблиц на crop исходной страницы.
         */
        fun addFigure(figure: ReflowBlock.Figure) {
            flush()
            blocks += figure
        }

        fun addLine(line: Line) {
            // lastLineBottom обновляем ДО раннего return: следующая строка должна
            // видеть актуальный нижний край этой как «предыдущей».
            lastLineBottom = line.bottom
            // Code-block detection: моноширинная строка добавляется в накапливаемый
            // pendingCode-run; не-моноширинная сбрасывает run и переходит к обычной
            // обработке. Проверяем ДО list/heading: код имеет приоритет над list-
            // паттернами вроде «1: foo» (часто встречается в коде).
            if (isMonospaceDominant(line)) {
                if (!pendingCode) flush()
                pendingCode = true
                pending += line
                return
            } else if (pendingCode) {
                flush()
            }
            // List-маркер выигрывает над heading: типографски «Упр. 47.» или «1)
            // Item» могут пройти heading-ensemble (font + bold + no period), но
            // семантически это enumerated items, а не section headings. Проверяем
            // list-pattern первым, чтобы они не «угоняли» heading-классификацию.
            if (line.startsListItem()) {
                flush()
                pendingList = true
                pendingListLevel = computeListLevel(line)
                pending += line
                return
            }
            val level = headingLevelOf(line, lastLineBottom)
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
            if (pending.isNotEmpty() && breaksParagraph(line)) flush()
            pendingHeadingLevel = 0
            pending += line
        }

        fun build(): List<ReflowBlock> {
            flush()
            return blocks
        }

        /**
         * Heading-уровень строки: требуется mandatory-сигнал «крупный кегль»
         * (`fontSize ≥ bodyFont × HEADING_RATIO`) плюс хотя бы
         * [HEADING_SECONDARY_SIGNALS_MIN] secondary-сигналов из 3 (bold majority,
         * gap above, no terminal punctuation). Calibre-стиль без CRF.
         *
         * `0` означает «не заголовок» — будет абзацем или элементом списка по
         * стандартной логике [addLine].
         */
        private fun headingLevelOf(
            line: Line,
            previousLineBottom: Float?,
        ): Int {
            // Drop cap эвристика: одиночный гигантский глиф (≥DROP_CAP_RATIO × body)
            // НЕ heading — это декоративная капитель начала абзаца. Возвращаем 0;
            // addLine положит эту «строку» в обычный pending pool, и она склеится
            // с продолжением абзаца как часть его текста.
            if (looksLikeDropCap(line)) return 0
            val fontRatioMet = bodyFont > 0f && line.fontSize >= bodyFont * HEADING_RATIO
            val secondary = if (fontRatioMet) countSecondaryHeadingSignals(line, previousLineBottom) else 0
            return if (fontRatioMet && secondary >= HEADING_SECONDARY_SIGNALS_MIN) {
                headingLevelForRatio(line.fontSize / bodyFont)
            } else {
                0
            }
        }

        /**
         * Drop cap (буквица): одиночный глиф минимум в [DROP_CAP_RATIO] раз
         * крупнее body-font'а, без хвоста (длина текста строки = 1). Книги-беллетристика
         * часто открывают абзац декоративной заглавной буквой высотой в 3–4 строки;
         * heading-detector ошибочно классифицирует её как L1.
         */
        private fun looksLikeDropCap(line: Line): Boolean {
            if (bodyFont <= 0f) return false
            if (line.fontSize < bodyFont * DROP_CAP_RATIO) return false
            val text = line.pieces.joinToString("") { it.text }
            return text.length == 1 && text.first().isLetter()
        }

        /**
         * Подсчитывает secondary-сигналы heading'а (font-ratio проверяется отдельно
         * в [headingLevelOf]):
         *  1. bold majority — большинство кусков строки полужирным;
         *  2. gapAbove ≥ [HEADING_GAP_FACTOR] × typicalPitch — заметный
         *     вертикальный отрыв от предыдущей строки;
         *  3. no terminal punctuation — строка не оканчивается на `.!?…:;`.
         */
        private fun countSecondaryHeadingSignals(
            line: Line,
            previousLineBottom: Float?,
        ): Int {
            var signals = 0
            if (line.pieces.isNotEmpty() && line.pieces.count { it.bold } * 2 >= line.pieces.size) signals++
            if (previousLineBottom != null &&
                typicalPitch > 0f &&
                line.top - previousLineBottom >= typicalPitch * HEADING_GAP_FACTOR
            ) {
                signals++
            }
            val lastChar = line.pieces.lastOrNull()?.text?.trimEnd()?.lastOrNull()
            if (lastChar != null && lastChar !in SENTENCE_TERMINATORS) signals++
            return signals
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
                pendingCode = false
                return
            }
            // Code-блок собирается отдельно: переносы строк сохраняются как '\n',
            // моноширинный стиль рендера и без heading/list демоушн'ов.
            if (pendingCode) {
                emitCode()
                return
            }
            // Элемент списка склеивается как абзац (перенос строк/дефис), но
            // эмитится отдельным типом блока для отступа в ридере.
            val emitAsHeading = pendingHeadingLevel > 0
            val built = if (emitAsHeading) buildHeading(pending) else buildParagraph(pending)
            // Демоушн «мусорных» heading'ов в Paragraph: на OCR-шумных PDF (Барановская)
            // ensemble иногда срабатывает на одно-символьных огрызках («Ф», «\», «V»)
            // или OCR-искажённых строках («Лр тиклъ»). Эти строки technically «крупный
            // кегль + gap», но не несут навигационной ценности; UX страдает от мусорных
            // entries в TOC. Sanity check: ≥2 letter-or-digit char, ≥60% алфавитных.
            val headingPassesSanity = emitAsHeading && isAcceptableHeading(built.text)
            val effectiveHeading = emitAsHeading && headingPassesSanity
            val emittedListItem = pendingList && built.text.isNotEmpty()
            if (built.text.isNotEmpty()) {
                blocks +=
                    when {
                        effectiveHeading -> ReflowBlock.Heading(built.text, pendingHeadingLevel, built.source)
                        pendingList -> ReflowBlock.ListItem(built.text, built.source, level = pendingListLevel)
                        else -> ReflowBlock.Paragraph(built.text, built.source)
                    }
            }
            // List-state: после list-item обновляем «последний», после non-list
            // (или пустого) — обнуляем, чтобы следующий list-элемент начал с 0.
            if (emittedListItem) {
                lastListIndent = pending.firstOrNull()?.pieces?.firstOrNull()?.bounds?.left ?: lastListIndent
                lastListLevel = pendingListLevel
            } else {
                lastListIndent = null
                lastListLevel = 0
            }
            pending.clear()
            pendingHeadingLevel = 0
            pendingList = false
            pendingListLevel = 0
        }

        /**
         * Уровень вложенности новой list-строки по отступу её маркера. Сравниваем
         * с [lastListIndent]: глубже → +1, мельче → -1 (clamp ≥ 0), в пределах
         * [LIST_INDENT_TOLERANCE_NORM] → тот же. Первый list-элемент потока — 0.
         */
        private fun computeListLevel(line: Line): Int {
            val indent = line.pieces.firstOrNull()?.bounds?.left
            val prevIndent = lastListIndent
            return if (indent == null || prevIndent == null) {
                0
            } else {
                when {
                    indent > prevIndent + LIST_INDENT_TOLERANCE_NORM -> lastListLevel + 1
                    indent < prevIndent - LIST_INDENT_TOLERANCE_NORM -> (lastListLevel - 1).coerceAtLeast(0)
                    else -> lastListLevel
                }
            }
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
                        // без пробела. U+00AD (typesetter's soft hyphen) — это hint от
                        // вёрстки «здесь можно переносить», не пунктуация → всегда
                        // убираем. ASCII/Unicode hyphen — двусмысленно: lowercase следующая
                        // → soft перенос, убираем; uppercase → составное слово
                        // (`Plugin-Name`), оставляем.
                        val unicodeSoftHyphen = sb.last() == SOFT_HYPHEN
                        if (unicodeSoftHyphen || line.startsLowercase()) {
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

        /**
         * Sanity-фильтр для эмиссии Heading: отсев одно-символьных огрызков и
         * OCR-мусора (символы вроде «\», «'», «V», одинокий слог). Heading'и идут в
         * TOC ридера, мусор там создаёт шум. Критерии:
         *  - длина после `trim()` ≥ [HEADING_MIN_LENGTH] (2);
         *  - доля букв/цифр ≥ [HEADING_MIN_ALPHA_RATIO] (0.6);
         *  - содержит хотя бы один letter-or-digit;
         *  - не «расставленные пробелами буквы» (F-3): ≥3 «слов», >50% — одиночные
         *    буквы. Это сигнатура OCR'д заголовков в условно-широком кегле
         *    («В х о д н а я» вместо «Входная»), где каждая буква оказалась
         *    собственным глифом с большим зазором. Heading в TOC из такого
         *    становится нечитаемым шумом — лучше показать как Paragraph.
         *
         * Если строка не проходит — flush demote'ит её в Paragraph (текст всё ещё
         * виден в потоке чтения, просто без heading-стиля и без TOC-entry).
         */
        private fun isAcceptableHeading(text: String): Boolean {
            val trimmed = text.trim()
            val letterDigits = trimmed.count { it.isLetterOrDigit() }
            val alphaRatio = if (trimmed.isEmpty()) 0f else letterDigits.toFloat() / trimmed.length
            val baselineOk =
                trimmed.length >= HEADING_MIN_LENGTH &&
                    letterDigits > 0 &&
                    alphaRatio >= HEADING_MIN_ALPHA_RATIO
            if (!baselineOk) return false
            val words = trimmed.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
            val spacedOut =
                words.size >= HEADING_SPACED_OUT_MIN_WORDS &&
                    words.count { it.length == 1 && it[0].isLetter() } * 2 > words.size
            return !spacedOut
        }

        /**
         * Эмитит накопленный Code-блок: переносы строк сохраняются как '\n',
         * пробелы внутри строки — по spaceBefore. Эмиссия требует
         * ≥[CODE_MIN_CONSECUTIVE_LINES] строк, иначе откат к Paragraph (одиночная
         * моноширинная строка чаще — встроенный путь к файлу / variable name,
         * рендерится через monospace SourceSpan и не нуждается в Code-блоке).
         */
        private fun emitCode() {
            if (pending.size < CODE_MIN_CONSECUTIVE_LINES) {
                val built = buildParagraph(pending)
                if (built.text.isNotEmpty()) {
                    blocks += ReflowBlock.Paragraph(built.text, built.source)
                }
            } else {
                val built = buildCode(pending)
                if (built.text.isNotEmpty()) {
                    blocks += ReflowBlock.Code(built.text, built.source)
                }
            }
            pending.clear()
            pendingHeadingLevel = 0
            pendingList = false
            pendingListLevel = 0
            pendingCode = false
            lastListIndent = null
            lastListLevel = 0
        }

        /** Доля моноширинных глифов в строке (по числу pieces). */
        private fun isMonospaceDominant(line: Line): Boolean {
            if (line.pieces.isEmpty()) return false
            val mono = line.pieces.count { it.monospace }
            return mono.toFloat() / line.pieces.size >= CODE_LINE_MONOSPACE_FRACTION
        }

        /** Code-блок: между строками — '\n', spans сохраняют монотонные offsets. */
        private fun buildCode(lines: List<Line>): BuiltText {
            val sb = StringBuilder()
            val spans = mutableListOf<SourceSpan>()
            for (line in lines) {
                if (line.pieces.isEmpty()) continue
                if (sb.isNotEmpty()) sb.append('\n')
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
                spans +=
                    SourceSpan(
                        pageIndex = piece.pageIndex,
                        charStart = start,
                        charEnd = sb.length,
                        bounds = piece.bounds,
                        bold = piece.bold,
                        monospace = piece.monospace,
                        italic = piece.italic,
                        superscript = piece.superscript,
                        subscript = piece.subscript,
                    )
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
        val italic: Boolean = false,
        val monospace: Boolean = false,
        val superscript: Boolean = false,
        val subscript: Boolean = false,
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
        fun startsLowercase(): Boolean =
            pieces
                .firstOrNull()
                ?.text
                ?.firstOrNull()
                ?.isLowerCase() == true

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
            if (matchesRussianListPrefix(lead)) return true
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

        data class Text(
            val line: Line,
        ) : Item {
            override val top: Float get() = line.top
        }

        data class Image(
            val rect: ReflowRect,
        ) : Item {
            override val top: Float get() = rect.top
        }

        data class Table(
            val table: ReflowBlock.Table,
            override val top: Float,
        ) : Item

        /**
         * Заранее собранная фигура — не нуждается в нормализации (в отличие от [Image]).
         * Используется для подмены low-confidence таблицы её crop'ом исходной страницы:
         * union bbox'ов глифов уже посчитан в нормализованных координатах.
         */
        data class PrebuiltFigure(
            val figure: ReflowBlock.Figure,
            override val top: Float,
        ) : Item
    }
}

/** Сколько первых символов строки сканировать на маркер списка. */
private const val LIST_MARKER_SCAN = 16

/**
 * Символы-буллеты, открывающие элемент списка. Тире `—`/`–` сюда НЕ входят: в
 * русском тексте это знак предложения (в т.ч. в начале перенесённой строки), а
 * не маркер списка — иначе абзац ложно становится пунктом списка.
 */
private val BULLET_CHARS = "•‣◦▪●·-*".toSet()

/**
 * Русские префиксы-маркеры элементов списка (упражнения/задания в учебниках):
 * `Упр. N.`, `Упражнение N.`, `Задание N.` и т.п. Lowercase для case-insensitive
 * сравнения. После префикса требуем non-letter boundary, чтобы не сматчить
 * слова с теми же буквенными началами (например, «упрямый» не должен пройти
 * как «упр»).
 */
private val RUSSIAN_LIST_PREFIXES = listOf("упр", "упражнение", "задание", "задача", "пример")

/** Разделители после номера в нумерованном списке (`1.`, `2)`). */
private const val NUMBER_MARKERS = ".)"

/**
 * Проверяет, начинается ли строка с русского списочного префикса вида
 * `Упр. N.`, `Упражнение N.`, `Задание N.` (см. [RUSSIAN_LIST_PREFIXES]).
 * Требования:
 *  - префикс на lowercase-сопоставлении;
 *  - сразу после префикса non-letter boundary (точка/пробел) — отсекает «упрямый»;
 *  - после префикса (опц. пунктуация) — цифры, затем `.` или `)`.
 *
 * Используется в [Line.startsListItem]; spaces в [Line.pieces] уже удалены
 * экстрактором, поэтому `lead` обычно вида `"Упр.47.Раскрой"`.
 */
private fun matchesRussianListPrefix(lead: String): Boolean {
    val leadLower = lead.lowercase()
    return RUSSIAN_LIST_PREFIXES.any { prefix -> prefixMatchesNumberedItem(lead, leadLower, prefix) }
}

/**
 * Проверяет одну запись из [RUSSIAN_LIST_PREFIXES]: `prefix` совпадает с началом
 * [lead] (case-insensitive), сразу после префикса non-letter boundary
 * (`упр` не должен сматчить `упрямый`), затем — опциональная пунктуация и цифры,
 * затем терминатор из [NUMBER_MARKERS]. Возвращает `true` только при полном
 * совпадении паттерна.
 */
private fun prefixMatchesNumberedItem(
    lead: String,
    leadLower: String,
    prefix: String,
): Boolean {
    val afterPrefix = if (leadLower.startsWith(prefix)) lead.substring(prefix.length) else null
    if (afterPrefix == null || afterPrefix.isEmpty() || afterPrefix[0].isLetter()) return false
    val trimmed = afterPrefix.dropWhile { it == '.' || it == ' ' }
    val digits = trimmed.takeWhile { it.isDigit() }
    val terminator = trimmed.getOrNull(digits.length)
    return digits.isNotEmpty() && (terminator == null || terminator in NUMBER_MARKERS)
}

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
