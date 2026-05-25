package ru.kyamshanov.notepen.reflow.ui

/**
 * Чистая раскладка блоков по страницам для страничного режима ридера.
 *
 * Вынесено из Compose, чтобы покрываться unit-тестами: измерение высот — забота
 * слоя отображения, а сама укладка детерминирована и проверяема.
 */
internal object ReaderPagination {
    /**
     * Жадно раскладывает блоки по страницам в порядке чтения: на странице
     * помещается столько идущих подряд блоков, сколько влезает по [pageHeightPx]
     * с зазором [spacingPx] между соседними блоками. Блок выше страницы занимает
     * свою страницу целиком (обрезку по вьюпорту делает рендер).
     *
     * @param blockHeightsPx высоты блоков в порядке чтения (px), неотрицательные
     * @param pageHeightPx полезная высота страницы (px)
     * @param spacingPx вертикальный зазор между блоками (px)
     * @return список страниц; каждая — непустой список индексов блоков. Пусто, если
     *   [blockHeightsPx] пуст.
     */
    fun paginate(
        blockHeightsPx: List<Float>,
        pageHeightPx: Float,
        spacingPx: Float,
    ): List<List<Int>> {
        if (blockHeightsPx.isEmpty()) return emptyList()
        if (pageHeightPx <= 0f) return listOf(blockHeightsPx.indices.toList())

        val pages = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        var used = 0f
        blockHeightsPx.forEachIndexed { index, rawHeight ->
            val height = rawHeight.coerceAtLeast(0f)
            val withGap = if (current.isEmpty()) height else height + spacingPx
            if (current.isNotEmpty() && used + withGap > pageHeightPx) {
                pages.add(current)
                current = mutableListOf()
                used = 0f
            }
            used += if (current.isEmpty()) height else height + spacingPx
            current.add(index)
        }
        if (current.isNotEmpty()) pages.add(current)
        return pages
    }
}
