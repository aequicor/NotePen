package ru.kyamshanov.notepen.reflow.ui

import kotlin.math.roundToInt

/**
 * Чистая построчная раскладка по страницам для страничного режима ридера — как в
 * настоящих электронных читалках: страница заполняется строками доверху, а абзац,
 * не влезающий целиком, переносится по границе строки (а не уезжает блоком на
 * следующую страницу, оставляя внизу пустоту).
 *
 * Вынесено из Compose, чтобы покрываться unit-тестами: измерение высот и границ
 * строк — забота слоя отображения, а раскладка детерминирована и проверяема.
 */
internal object ReaderPagination {
    /**
     * Раскладочные метрики одного блока в порядке чтения.
     *
     * @property heightPx высота блока (px), неотрицательная
     * @property lineBottomsPx Y нижних границ строк блока относительно его верха
     *   (по возрастанию; последняя ≈ [heightPx]). Пусто — блок неделимый (картинка,
     *   таблица, разделитель, заголовок): рвать по строкам внутри него нельзя.
     * @property breakAfter можно ли закончить страницу сразу за этим блоком. `false`
     *   у заголовков — чтобы заголовок не повисал сиротой внизу страницы (страница
     *   закончится до него, и он уедет вниз вместе с началом своего абзаца).
     */
    data class BlockLayout(
        val heightPx: Float,
        val lineBottomsPx: List<Float> = emptyList(),
        val breakAfter: Boolean = true,
    )

    /**
     * Окно одной страницы поверх непрерывной колонки блоков.
     *
     * @property startPx Y верхней видимой границы окна в непрерывной колонке
     * @property heightPx высота видимого содержимого (≤ полезной высоты страницы);
     *   обрезается по границе строки, поэтому внизу может остаться зазор меньше строки
     * @property firstBlock индекс блока, с которого начинается окно
     * @property firstBlockOffsetPx прокрутка внутри [firstBlock] до [startPx] (px);
     *   для [androidx.compose.foundation.lazy.LazyListState.scrollToItem]
     */
    data class PageWindow(
        val startPx: Float,
        val heightPx: Float,
        val firstBlock: Int,
        val firstBlockOffsetPx: Int,
    )

    /**
     * Раскладывает блоки по страницам построчно. На каждую страницу помещается столько
     * непрерывного содержимого (по [pageHeightPx]), сколько влезает до ближайшей снизу
     * границы строки; следующая страница продолжает с этой границы (внутри абзаца) или
     * со следующего блока (если разрыв пришёлся на конец блока — межблочный зазор
     * [spacingPx] не показывается вверху новой страницы).
     *
     * Неделимый блок ([BlockLayout.lineBottomsPx] пуст) рвать нельзя: страница либо
     * кончается перед ним, либо включает его целиком. Если такой блок (или одиночная
     * строка) выше страницы — он всё равно займёт страницу с обрезкой по вьюпорту, но
     * раскладка гарантированно продвинется вперёд (без зацикливания).
     *
     * @param blocks метрики блоков в порядке чтения
     * @param pageHeightPx полезная высота страницы (px)
     * @param spacingPx вертикальный зазор между блоками (px), неотрицательный
     * @return окна страниц по порядку; пусто, если [blocks] пуст. Первое окно всегда
     *   начинается с `startPx = 0`.
     */
    fun pageWindows(
        blocks: List<BlockLayout>,
        pageHeightPx: Float,
        spacingPx: Float,
    ): List<PageWindow> {
        if (blocks.isEmpty()) return emptyList()

        // Верх каждого блока в непрерывной колонке (с учётом межблочных зазоров).
        val tops = FloatArray(blocks.size)
        var y = 0f
        blocks.forEachIndexed { i, b ->
            tops[i] = y
            y += b.heightPx.coerceAtLeast(0f) + spacingPx
        }
        val contentHeight = tops.last() + blocks.last().heightPx.coerceAtLeast(0f)
        return if (pageHeightPx <= 0f || contentHeight <= 0f) {
            // Вырожденный случай: весь контент одной страницей (без обрезки).
            listOf(PageWindow(0f, contentHeight.coerceAtLeast(0f), 0, 0))
        } else {
            fillPages(tops, contentHeight, pageHeightPx, buildBreaks(blocks, tops, spacingPx))
        }
    }

    /**
     * Жадно нарезает непрерывную колонку (высотой [contentHeight]) на окна по [pageHeightPx],
     * заканчивая каждую страницу на ближайшей снизу границе из [breaks]. Если ни одна граница
     * не попадает в пределы страницы (строка/блок выше страницы) — режет по высоте страницы,
     * гарантируя продвижение.
     */
    private fun fillPages(
        tops: FloatArray,
        contentHeight: Float,
        pageHeightPx: Float,
        breaks: List<Break>,
    ): List<PageWindow> {
        val windows = mutableListOf<PageWindow>()
        var start = 0f
        val eps = 0.5f
        var more = true
        while (more) {
            val remaining = contentHeight - start
            if (remaining <= pageHeightPx + eps) {
                windows.add(window(start, remaining, tops))
                more = false
            } else {
                val limit = start + pageHeightPx
                val candidate = breaks.lastOrNull { it.endY > start + eps && it.endY <= limit + eps }
                windows.add(window(start, (candidate?.endY ?: limit) - start, tops))
                start = candidate?.nextStartY ?: limit
            }
        }
        return windows
    }

    private fun buildBreaks(
        blocks: List<BlockLayout>,
        tops: FloatArray,
        spacingPx: Float,
    ): List<Break> {
        val breaks = ArrayList<Break>()
        blocks.forEachIndexed { i, b ->
            val top = tops[i]
            val h = b.heightPx.coerceAtLeast(0f)
            if (b.lineBottomsPx.isEmpty()) {
                // Неделимый блок: единственная возможная граница — сразу за ним.
                if (b.breakAfter) breaks.add(Break(endY = top + h, nextStartY = top + h + spacingPx))
            } else {
                b.lineBottomsPx.forEachIndexed { li, lineBottom ->
                    val endY = top + lineBottom
                    if (li == b.lineBottomsPx.lastIndex) {
                        // Конец блока: следующая страница — со следующего блока (без зазора).
                        if (b.breakAfter) breaks.add(Break(endY = endY, nextStartY = top + h + spacingPx))
                    } else {
                        // Разрыв внутри абзаца: продолжаем тот же блок со следующей строки.
                        breaks.add(Break(endY = endY, nextStartY = endY))
                    }
                }
            }
        }
        return breaks
    }

    /** Окно от [start] высотой [height], с предвычисленным первым блоком и смещением. */
    private fun window(
        start: Float,
        height: Float,
        tops: FloatArray,
    ): PageWindow {
        var first = 0
        for (i in tops.indices) {
            if (tops[i] <= start + 0.5f) first = i else break
        }
        return PageWindow(
            startPx = start,
            heightPx = height.coerceAtLeast(0f),
            firstBlock = first,
            firstBlockOffsetPx = (start - tops[first]).coerceAtLeast(0f).roundToInt(),
        )
    }

    /** Кандидат конца страницы: [endY] — низ видимого, [nextStartY] — верх следующей. */
    private data class Break(
        val endY: Float,
        val nextStartY: Float,
    )
}
