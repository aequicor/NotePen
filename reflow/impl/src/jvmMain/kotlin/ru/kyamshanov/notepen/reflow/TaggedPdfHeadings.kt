package ru.kyamshanov.notepen.reflow

import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSString
import org.apache.pdfbox.pdmodel.PDDocument
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.SourceSpan

/**
 * Извлекает структуру заголовков из tagged PDF (`StructTreeRoot`) и пост-пассом
 * промоутит совпадающие Paragraph'ы в Heading'и нужного уровня.
 *
 * **Зачем**: tagged PDF (научные публикации, корпоративные документы — ~30%
 * native PDF в сети) содержат явную иерархию структуры от автора. Heuristic
 * ensemble в [ReflowAssembler] промахивается на heading'ах без font-jump
 * (например, серифные scientific PDFs с heading в обычном кегле + bold).
 *
 * **Подход (этап 1, без MCID-биндинга)**: собираем тексты всех H1–H6 и Title
 * элементов из struct tree; затем ищем матчи в текстах Paragraph'ов
 * документа (case-insensitive, после нормализации пробелов) и промоутим. Не
 * идеально (может промахнуться на разбитом тексте), но даёт значимый сигнал
 * там, где heuristic'и не справляются.
 *
 * **Этап 2 (TODO)**: настоящий MCID binding для точной разметки — тогда
 * полностью замещает heuristic heading-detection на tagged-документах.
 */
internal object TaggedPdfHeadings {
    /**
     * Возвращает `true`, если у документа есть [StructTreeRoot] с kids — то есть
     * автор реально размечал структуру (а не просто оставил пустой root).
     */
    fun isTagged(document: PDDocument): Boolean {
        val root = document.documentCatalog?.structureTreeRoot ?: return false
        return rootKidsCount(root.cosObject as? COSDictionary) > 0
    }

    /**
     * Собирает плоский список заголовков из struct tree в порядке обхода
     * (depth-first). Каждая запись = (level, normalisedText). Уровень — из
     * тега `Hn`; `Title` маппится в H1.
     */
    fun collectHeadings(document: PDDocument): List<TaggedHeading> {
        val root = document.documentCatalog?.structureTreeRoot ?: return emptyList()
        val rootDict = root.cosObject as? COSDictionary ?: return emptyList()
        val out = mutableListOf<TaggedHeading>()
        traverse(rootDict, depth = 0, sink = out)
        return out
    }

    /**
     * Пост-пасс над [document]: для каждого heading из tagged-структуры ищет
     * первый Paragraph с совпадающим (после нормализации) текстом и промоутит
     * его в [ReflowBlock.Heading]. Без матча — silent skip.
     *
     * Сохраняет порядок блоков и оригинальные [SourceSpan]'ы.
     */
    fun promoteMatchingParagraphs(
        document: ReflowDocument,
        headings: List<TaggedHeading>,
    ): ReflowDocument {
        if (headings.isEmpty()) return document
        val byNormalized =
            headings
                .groupBy { it.normalisedText }
                .mapValues { it.value.first() } // ambiguous matches → берём первый
        val blocks = document.blocks.toMutableList()
        val claimed = HashSet<String>()
        for (i in blocks.indices) {
            val p = blocks[i] as? ReflowBlock.Paragraph ?: continue
            val key = normalise(p.text)
            if (key in claimed) continue
            val h = byNormalized[key] ?: continue
            blocks[i] =
                ReflowBlock.Heading(
                    text = p.text,
                    level = h.level,
                    source = p.source,
                )
            claimed += key
        }
        return ReflowDocument(kind = document.kind, blocks = blocks)
    }

    /**
     * Рекурсивный обход struct tree. `K` (kids) бывает COSDictionary (одиночный
     * дочерний элемент), COSArray (список), COSName/COSInteger (marked content
     * — листья без текстового вклада на этом уровне).
     */
    private fun traverse(
        node: COSDictionary,
        depth: Int,
        sink: MutableList<TaggedHeading>,
    ) {
        if (depth > MAX_TREE_DEPTH) return
        val type = (node.getCOSName(COSName.S) ?: node.getCOSName(COSName.TYPE))?.name ?: ""
        val level = parseHeadingLevel(type)
        if (level > 0) {
            val text = collectActualText(node).trim()
            if (text.length >= MIN_HEADING_TEXT_LENGTH) {
                sink += TaggedHeading(level = level, normalisedText = normalise(text))
            }
        }
        when (val kids = node.getDictionaryObject(COSName.K)) {
            is COSArray ->
                kids.iterator().forEach { obj ->
                    if (obj is COSDictionary) traverse(obj, depth + 1, sink)
                }
            is COSDictionary -> traverse(kids, depth + 1, sink)
            else -> Unit // листья (marked content) — текст из ActualText или ничего
        }
    }

    /**
     * Текстовое содержимое элемента: предпочтительно `ActualText` или `Alt`
     * (если автор задал), иначе пытаемся достать из `Title`. На leaf-узлах
     * (без явного текста) возвращает «», и caller это игнорирует.
     */
    private fun collectActualText(node: COSDictionary): String {
        val actual = (node.getDictionaryObject(COSName.ACTUAL_TEXT) as? COSString)?.string
        if (!actual.isNullOrBlank()) return actual
        val alt = (node.getDictionaryObject(COSName.ALT) as? COSString)?.string
        if (!alt.isNullOrBlank()) return alt
        val title = (node.getDictionaryObject(COSName.T) as? COSString)?.string
        if (!title.isNullOrBlank()) return title
        return ""
    }

    /** «H1»→1, «H2»→2, … «H6»→6, «Title»→1, иначе 0. */
    private fun parseHeadingLevel(type: String): Int {
        if (type.equals("Title", ignoreCase = true)) return 1
        if (type.length == 2 && type.startsWith('H')) {
            val digit = type[1]
            if (digit in '1'..'6') return digit - '0'
        }
        return 0
    }

    /** Нормализация для матчинга текста: trim, lowercase, collapse whitespace. */
    private fun normalise(text: String): String = text.trim().lowercase().replace(Regex("\\s+"), " ")

    /** Сколько kids у root dict. Используется для дешёвой проверки «реально tagged». */
    private fun rootKidsCount(rootDict: COSDictionary?): Int {
        val k = rootDict?.getDictionaryObject(COSName.K) ?: return 0
        return when (k) {
            is COSArray -> k.size()
            is COSDictionary -> 1
            else -> 0
        }
    }

    /** Один заголовок из struct tree. */
    data class TaggedHeading(
        val level: Int,
        val normalisedText: String,
    )

    /** Глубина обхода — защита от циклов в кривых PDF. */
    private const val MAX_TREE_DEPTH = 32

    /**
     * Минимальная длина текста заголовка для матчинга. Tagged PDF могут содержать
     * пустые/служебные H1, которые матчатся с любым пустым Paragraph (опасно).
     */
    private const val MIN_HEADING_TEXT_LENGTH = 3
}
