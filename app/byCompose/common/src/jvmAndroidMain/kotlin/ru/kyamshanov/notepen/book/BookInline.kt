package ru.kyamshanov.notepen.book

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Обходит инлайн-содержимое [element] и собирает [RichText]: текстовые узлы
 * превращаются в [InlineSpan] с накопленным начертанием, `br` — в пробел.
 * Последовательности пробелов схлопываются, края обрезаются.
 *
 * Маппинг тегов покрывает и HTML (EPUB), и FB2: `b`/`strong` → полужирный,
 * `i`/`em`/`emphasis` → курсив, `code`/`kbd`/`samp` → моноширинный,
 * `sup`/`sub` → индексы, `a` → ссылка.
 */
internal fun inlineSpansOf(element: Element): RichText {
    val raw = mutableListOf<InlineSpan>()
    collectInline(element, InlineStyle(), raw)
    return normalizeWhitespace(raw)
}

/** Активные инлайн-стили при обходе дерева. */
private data class InlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val superscript: Boolean = false,
    val subscript: Boolean = false,
    val link: Boolean = false,
) {
    fun with(tag: String): InlineStyle = when (tag) {
        "b", "strong" -> copy(bold = true)
        "i", "em", "emphasis", "cite", "dfn", "var" -> copy(italic = true)
        "code", "kbd", "samp", "tt" -> copy(code = true)
        "sup" -> copy(superscript = true)
        "sub" -> copy(subscript = true)
        "a" -> copy(link = true)
        else -> this
    }
}

private fun collectInline(node: Node, style: InlineStyle, out: MutableList<InlineSpan>) {
    for (child in node.childNodes()) {
        when (child) {
            is TextNode -> child.wholeText.takeIf { it.isNotEmpty() }?.let { text ->
                out.add(
                    InlineSpan(
                        text = text,
                        bold = style.bold,
                        italic = style.italic,
                        code = style.code,
                        superscript = style.superscript,
                        subscript = style.subscript,
                        link = style.link,
                    ),
                )
            }

            is Element -> if (child.normalName() == "br") {
                out.add(InlineSpan(text = " "))
            } else {
                collectInline(child, style.with(child.normalName()), out)
            }

            else -> Unit
        }
    }
}

/** Схлопывает пробелы внутри и между фрагментами, обрезая края. */
private fun normalizeWhitespace(spans: List<InlineSpan>): RichText {
    val result = mutableListOf<InlineSpan>()
    var pendingSpace = false
    var seenContent = false
    for (span in spans) {
        val sb = StringBuilder()
        for (ch in span.text) {
            if (ch.isWhitespace()) {
                if (seenContent) pendingSpace = true
            } else {
                if (pendingSpace) {
                    sb.append(' ')
                    pendingSpace = false
                }
                sb.append(ch)
                seenContent = true
            }
        }
        if (sb.isNotEmpty()) result.add(span.copy(text = sb.toString()))
    }
    return result
}
