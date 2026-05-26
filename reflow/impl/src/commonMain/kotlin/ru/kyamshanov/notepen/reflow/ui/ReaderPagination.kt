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
     * «Держать со следующим» ([keepWithNext]): если блок с пометкой оказался бы
     * последним на странице (заголовок — а его абзац уехал на следующую), он
     * переносится на следующую страницу вместе с преемником, чтобы заголовок не
     * висел сиротой внизу. Перенос на один уровень: если связка не влезает и на
     * чистую страницу, блок остаётся на месте (иначе зациклились бы).
     *
     * @param blockHeightsPx высоты блоков в порядке чтения (px), неотрицательные
     * @param pageHeightPx полезная высота страницы (px)
     * @param spacingPx вертикальный зазор между блоками (px)
     * @param keepWithNext по индексу блока — «не оставлять последним на странице»
     *   (обычно заголовки); короче [blockHeightsPx] или пустой — считается `false`
     * @return список страниц; каждая — непустой список индексов блоков. Пусто, если
     *   [blockHeightsPx] пуст.
     */
    fun paginate(
        blockHeightsPx: List<Float>,
        pageHeightPx: Float,
        spacingPx: Float,
        keepWithNext: List<Boolean> = emptyList(),
    ): List<List<Int>> {
        if (blockHeightsPx.isEmpty()) return emptyList()
        if (pageHeightPx <= 0f) return listOf(blockHeightsPx.indices.toList())

        fun heightAt(i: Int): Float = blockHeightsPx[i].coerceAtLeast(0f)
        fun keepAt(i: Int): Boolean = keepWithNext.getOrElse(i) { false }

        val pages = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        var used = 0f
        blockHeightsPx.indices.forEach { index ->
            val height = heightAt(index)
            val withGap = if (current.isEmpty()) height else height + spacingPx
            if (current.isNotEmpty() && used + withGap > pageHeightPx) {
                val prev = current.last()
                if (keepAt(prev) && current.size > 1) {
                    // Заголовок остался бы последним — уносим его на новую страницу
                    // вместе с этим блоком (он не один на странице, перенос безопасен).
                    current.removeAt(current.lastIndex)
                    pages.add(current)
                    current = mutableListOf(prev)
                    used = heightAt(prev)
                } else {
                    pages.add(current)
                    current = mutableListOf()
                    used = 0f
                }
            }
            used += if (current.isEmpty()) height else height + spacingPx
            current.add(index)
        }
        if (current.isNotEmpty()) pages.add(current)
        return pages
    }
}
