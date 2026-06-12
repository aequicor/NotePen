package ru.kyamshanov.notepen.reflow.ui

/**
 * Десктоп (Skiko): родной `Hyphens.Auto` не рисует переносы, поэтому расставляем мягкие переносы сами
 * алгоритмом Кнута-Лиэнга по паттернам `hyph-ru` (LPPL, А. И. Лебедев — см. resources/hyphenation/NOTICE.txt),
 * а `ReaderHyphenPromotion` превращает разорванные слоты в настоящие дефисы. Латиницу не трогаем
 * (паттерны только русские).
 *
 * Переносим лишь кириллические «слова» (макс. серии кириллических букв); индексы вставки — в исходной
 * строке. [LiangHyphenator] и распарсенные паттерны кэшируются лениво (загрузка ресурсов — один раз).
 */
internal actual fun platformSoftHyphenPositions(text: String): IntArray {
    val hyphenator = RuHyphenatorHolder.instance ?: return EMPTY
    val out = ArrayList<Int>()
    var i = 0
    val n = text.length
    while (i < n) {
        if (!isCyrillicLetter(text[i])) {
            i++
            continue
        }
        var j = i
        while (j < n && isCyrillicLetter(text[j])) j++
        for (pos in hyphenator.breakPositions(text.substring(i, j))) {
            out.add(i + pos)
        }
        i = j
    }
    return if (out.isEmpty()) EMPTY else out.toIntArray()
}

private val EMPTY = IntArray(0)

private fun isCyrillicLetter(c: Char): Boolean {
    val l = c.lowercaseChar()
    return l in 'а'..'я' || l == 'ё'
}

/** Ленивая, потокобезопасная загрузка переносчика из ресурсов `hyph-ru`. */
private object RuHyphenatorHolder {
    val instance: LiangHyphenator? by lazy { load() }

    private fun load(): LiangHyphenator? {
        val patterns = readLines("/hyphenation/hyph-ru.pat.txt") ?: return null
        val exceptions = readLines("/hyphenation/hyph-ru.hyp.txt").orEmpty()
        return LiangHyphenator.fromTokens(patterns, exceptions, leftMin = 2, rightMin = 2)
    }

    private fun readLines(resource: String): List<String>? =
        javaClass.getResourceAsStream(resource)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            reader.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
}
