package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument

/**
 * Сопоставление страниц исходного PDF с позицией в reflow-документе: по индексу
 * страницы находит блок, к которому нужно прокрутить ридер (например, по тапу в
 * списке страниц).
 */
public object ReflowPageLocator {

    /**
     * Индекс блока [ReflowDocument.blocks], с которого начинается страница
     * [pageIndex]. Если на самой странице блоков нет (например, скан без текста),
     * возвращает первый блок ближайшей следующей страницы; `null`, если контента
     * после [pageIndex] нет.
     *
     * Блоки идут в порядке чтения, а страницы конкатенируются по возрастанию,
     * поэтому исходные страницы блоков не убывают — хватает одного прохода.
     */
    public fun blockIndexForPage(document: ReflowDocument, pageIndex: Int): Int? {
        var fallback: Int? = null
        document.blocks.forEachIndexed { index, block ->
            val page = block.sourcePage() ?: return@forEachIndexed
            when {
                page == pageIndex -> return index
                page > pageIndex && fallback == null -> fallback = index
            }
        }
        return fallback
    }

    /**
     * Исходная страница блока [blockIndex] (или ближайшего соседнего блока с
     * известной страницей). Обратное к [blockIndexForPage] — для синхронизации
     * текущей страницы при прокрутке ридера. `null`, если [blockIndex] вне
     * диапазона блоков.
     */
    public fun pageForBlock(document: ReflowDocument, blockIndex: Int): Int? {
        val blocks = document.blocks
        if (blockIndex !in blocks.indices) return null
        for (i in blockIndex downTo 0) {
            blocks[i].sourcePage()?.let { return it }
        }
        for (i in blockIndex + 1 until blocks.size) {
            blocks[i].sourcePage()?.let { return it }
        }
        return null
    }

    private fun ReflowBlock.sourcePage(): Int? = when (this) {
        is ReflowBlock.Paragraph -> source.minOfOrNull { it.pageIndex }
        is ReflowBlock.Heading -> source.minOfOrNull { it.pageIndex }
        is ReflowBlock.Figure -> pageIndex
    }
}
