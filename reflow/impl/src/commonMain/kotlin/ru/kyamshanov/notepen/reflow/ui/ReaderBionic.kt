package ru.kyamshanov.notepen.reflow.ui

/**
 * Bionic reading: вычисление «ведущих» символов слов, которые выделяются
 * полужирным как точки фиксации взгляда.
 *
 * Сознательно НЕ дефолт: помогает части людей (в т. ч. с СДВГ), другим — мешает.
 * Логика чистая (без Compose), чтобы покрывалась unit-тестами.
 */
internal object ReaderBionic {
    /**
     * Диапазоны ведущих букв каждого слова в [text] (включительно, по индексам
     * символов). Слова — максимальные пробеги [Char.isLetter]; не-буквы (пробелы,
     * пунктуация, цифры) пропускаются. Доля выделения ≈ 40% длины слова.
     */
    fun boldRanges(text: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var i = 0
        val n = text.length
        while (i < n) {
            while (i < n && !text[i].isLetter()) i++
            if (i >= n) break
            val wordStart = i
            while (i < n && text[i].isLetter()) i++
            val lead = leadCount(i - wordStart)
            if (lead > 0) ranges.add(wordStart..(wordStart + lead - 1))
        }
        return ranges
    }

    /** Сколько ведущих букв выделить для слова длиной [wordLen]. */
    private fun leadCount(wordLen: Int): Int =
        when {
            wordLen <= 1 -> wordLen
            wordLen <= 3 -> 1
            else -> ((wordLen * LEAD_NUMERATOR) + LEAD_DENOMINATOR - 1) / LEAD_DENOMINATOR
        }

    private const val LEAD_NUMERATOR = 2
    private const val LEAD_DENOMINATOR = 5
}
