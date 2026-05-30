package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.NormalizedRect
import ru.kyamshanov.notepen.annotation.domain.model.PageNote
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import ru.kyamshanov.notepen.reflow.api.SourceSpan
import ru.kyamshanov.notepen.reflow.api.TextAnchor

/**
 * Сопоставление рукописных штрихов с переверстанным текстом и обратно — то, что
 * позволяет аннотациям «оставаться» при переключении между постраничным и
 * reflow-видом.
 *
 * Чистая логика: и точки штриха ([DrawingPoint]), и [SourceSpan.bounds] заданы в
 * одной нормализованной системе `[0..1]` относительно страницы, поэтому пересчёт
 * координат не нужен.
 */
public object StrokeTextMapper {
    /** Сколько символов префикса/суффикса вокруг цитаты сохраняем в [PageNote.context]. */
    private const val CONTEXT_CHARS = 32

    /**
     * Прямое сопоставление: какие диапазоны текста перекрывает штрих [path],
     * нарисованный на странице [pageIndex].
     *
     * Берёт ограничивающий прямоугольник штриха, пересекает его с
     * [SourceSpan.bounds] этой страницы и сливает перекрытые диапазоны символов
     * (в т.ч. через один символ-разделитель). Анкеры упорядочены по блоку, затем
     * по началу диапазона.
     *
     * @param document reflow-документ
     * @param pageIndex страница, к которой привязан штрих (штрихи хранятся
     *   постранично, а [SourceSpan.bounds] нормализованы на свою страницу)
     * @param path рукописный штрих в нормализованных координатах страницы
     * @return перекрытые диапазоны текста; пусто, если штрих пуст или ничего не задел
     */
    public fun anchorsFor(
        document: ReflowDocument,
        pageIndex: Int,
        path: DrawingPath,
    ): List<TextAnchor> {
        val box = boundingBox(path.points) ?: return emptyList()
        val anchors = mutableListOf<TextAnchor>()
        document.blocks.forEachIndexed { blockIndex, block ->
            val covered =
                block
                    .sourceSpans()
                    .filter { it.pageIndex == pageIndex && intersects(box, it.bounds) }
                    .map { it.charStart to it.charEnd }
            mergeRanges(covered).forEach { (start, end) ->
                anchors += TextAnchor(blockIndex, start, end)
            }
        }
        return anchors
    }

    /**
     * Обратное сопоставление: исходные области страницы для диапазона текста
     * [anchor] — для отрисовки аннотации в постраничном виде.
     *
     * @return [SourceSpan]-ы блока [TextAnchor.blockIndex], пересекающиеся с
     *   диапазоном анкера (каждый несёт `pageIndex` + нормализованные `bounds`)
     */
    public fun spansFor(
        document: ReflowDocument,
        anchor: TextAnchor,
    ): List<SourceSpan> {
        val block = document.blocks.getOrNull(anchor.blockIndex) ?: return emptyList()
        return block.sourceSpans().filter { it.charStart < anchor.charEnd && it.charEnd > anchor.charStart }
    }

    /**
     * Снаппинг штриха к словам: какие слово-выровненные области перекрывает
     * штрих [path], нарисованный на странице [pageIndex]. Это геометрия, которую
     * «липкий маркер» сохраняет как истину (см. [NormalizedRect]).
     *
     * Берёт перекрытые [SourceSpan]-ы (как [anchorsFor]) и сливает их по строкам в
     * непрерывные полосы (по одной на строку текста), чтобы выделение легло ровно.
     *
     * @return нормализованные области выделения; пусто, если штрих ничего не задел
     */
    public fun snapToWords(
        document: ReflowDocument,
        pageIndex: Int,
        path: DrawingPath,
    ): List<NormalizedRect> {
        val box = boundingBox(path.points) ?: return emptyList()
        val covered =
            document.blocks
                .flatMap { it.sourceSpans() }
                .filter { it.pageIndex == pageIndex && intersects(box, it.bounds) }
                .map { it.bounds }
        return mergeIntoLines(covered).map { NormalizedRect(it.left, it.top, it.right, it.bottom) }
    }

    /**
     * Обратное сопоставление для сохранённого выделения: какие диапазоны текста
     * перекрывают области [rects] на странице [pageIndex]. Пересчитывается на лету
     * при входе в режим чтения — поэтому всегда согласовано с текущей экстракцией
     * (хранимого индекса блока, который мог бы «уехать», нет).
     *
     * @return перекрытые диапазоны текста, упорядоченные по блоку и началу диапазона
     */
    public fun anchorsForRects(
        document: ReflowDocument,
        pageIndex: Int,
        rects: List<NormalizedRect>,
    ): List<TextAnchor> {
        if (rects.isEmpty()) return emptyList()
        val boxes = rects.map { ReflowRect(it.left, it.top, it.right, it.bottom) }
        val anchors = mutableListOf<TextAnchor>()
        document.blocks.forEachIndexed { blockIndex, block ->
            val covered =
                block
                    .sourceSpans()
                    .filter { span -> span.pageIndex == pageIndex && boxes.any { intersects(it, span.bounds) } }
                    .map { it.charStart to it.charEnd }
            mergeRanges(covered).forEach { (start, end) ->
                anchors += TextAnchor(blockIndex, start, end)
            }
        }
        return anchors
    }

    /**
     * Геометрия для «липкого выделения», созданного из текстового выделения в режиме
     * чтения: для каждого [TextAnchor] берёт его [SourceSpan]-ы ([spansFor]), группирует
     * по исходной странице и сливает в полосы по строкам ([mergeIntoLines]). Так выделение
     * по диапазону символов превращается в те же слово-выровненные [NormalizedRect], что
     * хранит StickyHighlight (одна страница может дать несколько полос; один блок,
     * собранный с разных страниц, корректно разносится по страницам).
     *
     * @return области выделения по нулевому индексу страницы; пусто, если ничего не покрыто
     */
    public fun selectionRectsByPage(
        document: ReflowDocument,
        anchors: List<TextAnchor>,
    ): Map<Int, List<NormalizedRect>> {
        if (anchors.isEmpty()) return emptyMap()
        val boundsByPage = mutableMapOf<Int, MutableList<ReflowRect>>()
        anchors.forEach { anchor ->
            spansFor(document, anchor).forEach { span ->
                boundsByPage.getOrPut(span.pageIndex) { mutableListOf() }.add(span.bounds)
            }
        }
        return boundsByPage.mapValues { (_, bounds) ->
            mergeIntoLines(bounds).map { NormalizedRect(it.left, it.top, it.right, it.bottom) }
        }
    }

    /**
     * Ре-анкоринг заметки ([PageNote]) на текущую экстракцию. Сначала по геометрии
     * ([anchorsForRects] на [PageNote.rects] — источнике истины, как у StickyHighlight);
     * если геометрия больше ничего не задевает (текст переизвлекли иначе) — откат к
     * поиску [PageNote.quote] ([anchorsForQuote]).
     *
     * @return диапазоны текста для [note]; пусто, если ни геометрия, ни цитата не нашлись
     */
    public fun anchorsForNote(
        document: ReflowDocument,
        note: PageNote,
    ): List<TextAnchor> {
        val byGeometry = anchorsForRects(document, note.pageIndex, note.rects)
        if (byGeometry.isNotEmpty()) return byGeometry
        return anchorsForQuote(document, note.quote)
    }

    /**
     * Откат TextQuoteSelector: первое вхождение первой строки [quote] в текст любого
     * блока. Достаточно, чтобы поставить маркер; полная геометрия восстанавливается
     * через [spansFor] по полученному анкеру.
     *
     * @return один анкер на найденное вхождение; пусто, если цитата пуста или не найдена
     */
    public fun anchorsForQuote(
        document: ReflowDocument,
        quote: String,
    ): List<TextAnchor> {
        val needle = quote.substringBefore('\n').trim()
        if (needle.isEmpty()) return emptyList()
        val hit =
            document.blocks.withIndex().firstNotNullOfOrNull { (blockIndex, block) ->
                val at = block.blockText().indexOf(needle)
                if (at >= 0) TextAnchor(blockIndex, at, at + needle.length) else null
            }
        return hit?.let { listOf(it) }.orEmpty()
    }

    /**
     * `(quote, context)` для [PageNote], создаваемой из выделения [anchors] в режиме
     * чтения. `quote` — подстроки текста блоков по анкерам, склеенные через `"\n"` (тот
     * же join, что видит пользователь в выделении). `context` — до [CONTEXT_CHARS]
     * символов префикса перед первым анкером и суффикса после последнего, в форме
     * `"prefix…suffix"` (W3C TextQuoteSelector — для устойчивого ре-поиска).
     */
    public fun quoteForAnchors(
        document: ReflowDocument,
        anchors: List<TextAnchor>,
    ): Pair<String, String> {
        val parts = mutableListOf<String>()
        anchors.forEach { anchor ->
            val text = document.blocks.getOrNull(anchor.blockIndex)?.blockText().orEmpty()
            if (text.isEmpty()) return@forEach
            val start = anchor.charStart.coerceIn(0, text.length)
            val end = anchor.charEnd.coerceIn(start, text.length)
            if (end > start) parts += text.substring(start, end)
        }
        val quote = parts.joinToString("\n")
        if (quote.isEmpty()) return "" to ""
        val first = anchors.first()
        val last = anchors.last()
        val firstText = document.blocks.getOrNull(first.blockIndex)?.blockText().orEmpty()
        val lastText = document.blocks.getOrNull(last.blockIndex)?.blockText().orEmpty()
        val prefixEnd = first.charStart.coerceIn(0, firstText.length)
        val suffixStart = last.charEnd.coerceIn(0, lastText.length)
        val prefix = firstText.substring((prefixEnd - CONTEXT_CHARS).coerceAtLeast(0), prefixEnd)
        val suffix = lastText.substring(suffixStart, (suffixStart + CONTEXT_CHARS).coerceAtMost(lastText.length))
        val context = if (prefix.isEmpty() && suffix.isEmpty()) "" else "$prefix…$suffix"
        return quote to context
    }

    /** Текст текстонесущего блока (для цитаты/контекста заметки); пусто для не-текстовых. */
    private fun ReflowBlock.blockText(): String =
        when (this) {
            is ReflowBlock.Paragraph -> text
            is ReflowBlock.Heading -> text
            is ReflowBlock.ListItem -> text
            is ReflowBlock.Blockquote -> text
            is ReflowBlock.Code -> text
            is ReflowBlock.Footnote -> text
            is ReflowBlock.Table -> ""
            is ReflowBlock.Figure -> ""
            ReflowBlock.Divider -> ""
        }

    private fun ReflowBlock.sourceSpans(): List<SourceSpan> =
        when (this) {
            is ReflowBlock.Paragraph -> source
            is ReflowBlock.Heading -> source
            is ReflowBlock.ListItem -> source
            is ReflowBlock.Blockquote -> source
            // Ячейки таблицы индексируют каждая свой текст, не единый .text блока —
            // ре-анкоринг штрихов в таблицы пока не поддержан.
            is ReflowBlock.Table -> emptyList()
            is ReflowBlock.Figure -> emptyList()
            is ReflowBlock.Code -> source
            is ReflowBlock.Footnote -> source
            ReflowBlock.Divider -> emptyList()
        }

    private fun boundingBox(points: List<DrawingPoint>): ReflowRect? {
        val first = points.firstOrNull() ?: return null
        var left = first.x
        var top = first.y
        var right = first.x
        var bottom = first.y
        for (point in points) {
            if (point.x < left) left = point.x
            if (point.x > right) right = point.x
            if (point.y < top) top = point.y
            if (point.y > bottom) bottom = point.y
        }
        return ReflowRect(left, top, right, bottom)
    }

    /** Пересечение прямоугольников; касание рёбер не считается (строгие сравнения). */
    private fun intersects(
        a: ReflowRect,
        b: ReflowRect,
    ): Boolean = a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

    /** Сливает перекрывающиеся и смежные (зазор ≤ 1 символ-разделитель) диапазоны. */
    private fun mergeRanges(ranges: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf<Pair<Int, Int>>()
        var currentStart = sorted.first().first
        var currentEnd = sorted.first().second
        for (i in 1 until sorted.size) {
            val (start, end) = sorted[i]
            if (start - currentEnd <= 1) {
                if (end > currentEnd) currentEnd = end
            } else {
                merged += currentStart to currentEnd
                currentStart = start
                currentEnd = end
            }
        }
        merged += currentStart to currentEnd
        return merged
    }

    /**
     * Сливает области слов в полосы по строкам: области, чьи вертикальные диапазоны
     * пересекаются, считаются одной строкой и объединяются в один прямоугольник
     * (от левого края первого слова до правого края последнего). Даёт ровную полосу
     * на строку вместо набора отдельных слов-прямоугольников.
     */
    private fun mergeIntoLines(rects: List<ReflowRect>): List<ReflowRect> {
        if (rects.isEmpty()) return emptyList()
        val sorted = rects.sortedWith(compareBy({ it.top }, { it.left }))
        val lines = mutableListOf<ReflowRect>()
        var left = sorted.first().left
        var top = sorted.first().top
        var right = sorted.first().right
        var bottom = sorted.first().bottom
        for (i in 1 until sorted.size) {
            val r = sorted[i]
            val sameLine = r.top < bottom && r.bottom > top
            if (sameLine) {
                if (r.left < left) left = r.left
                if (r.top < top) top = r.top
                if (r.right > right) right = r.right
                if (r.bottom > bottom) bottom = r.bottom
            } else {
                lines += ReflowRect(left, top, right, bottom)
                left = r.left
                top = r.top
                right = r.right
                bottom = r.bottom
            }
        }
        lines += ReflowRect(left, top, right, bottom)
        return lines
    }
}
