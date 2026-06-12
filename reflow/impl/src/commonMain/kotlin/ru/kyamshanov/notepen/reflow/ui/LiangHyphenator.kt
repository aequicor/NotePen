package ru.kyamshanov.notepen.reflow.ui

/**
 * Алгоритм переноса Кнута-Лиэнга (как в TeX): по набору взвешенных паттернов находит точки переноса
 * в слове. Чистый, без I/O — паттерны и исключения загружаются снаружи (на десктопе из ресурсов
 * `hyph-ru`, см. jvmMain actual для [platformSoftHyphenPositions]).
 *
 * Паттерн — это буквы с цифрами-весами между ними: `а1б` значит «слабо разрешён перенос между а и б»,
 * `.` — граница слова. Для каждой межбуквенной позиции берётся МАКСИМУМ веса по всем подошедшим
 * паттернам; нечётный вес → перенос разрешён. [leftMin]/[rightMin] запрещают переносы у краёв слова
 * (для русского 2/2).
 *
 * @property patterns карта «буквы паттерна (без цифр)» → массив весов длиной (буквы+1)
 * @property exceptions словарь явных переносов: слово → индексы вставки (перекрывают паттерны)
 */
internal class LiangHyphenator(
    private val patterns: Map<String, IntArray>,
    private val exceptions: Map<String, IntArray>,
    private val leftMin: Int = 2,
    private val rightMin: Int = 2,
) {
    /** Индексы вставки переноса в [word] (между word[i-1] и word[i]); сортированы. Пусто, если нет. */
    fun breakPositions(word: String): IntArray {
        val len = word.length
        val lower = word.lowercase()
        val exception = exceptions[lower]
        // Слишком короткое — без переноса; явное исключение перекрывает паттерны при любой длине.
        if (len < leftMin + rightMin || exception != null) return exception ?: EMPTY

        // Окружаем слово границами '.', считаем уровни в межсимвольных позициях.
        val work = ".$lower."
        val levels = IntArray(work.length + 1)
        for (i in work.indices) {
            for (j in i + 1..work.length) {
                val fragment = work.substring(i, j)
                val pat = patterns[fragment] ?: continue
                for (k in pat.indices) {
                    if (pat[k] > levels[i + k]) levels[i + k] = pat[k]
                }
            }
        }

        // Перенос после word[p] (вставка в p+1): уровень в позиции между work-символами p+1 и p+2 →
        // levels[p+2]; нечётный → разрешён. Края отсекаем по leftMin/rightMin.
        val result = ArrayList<Int>()
        for (p in 0 until len - 1) {
            val leftLetters = p + 1
            val rightLetters = len - leftLetters
            if (leftLetters >= leftMin && rightLetters >= rightMin && levels[p + 2] % 2 == 1) {
                result.add(p + 1)
            }
        }
        return result.toIntArray()
    }

    companion object {
        private val EMPTY = IntArray(0)

        /**
         * Разбирает паттерн вида `.а1б2в` в пару «буквы» → «веса». Цифры — веса в межбуквенных позициях,
         * точка `.` — буква-граница. Веса по умолчанию 0, длина массива = число букв + 1.
         */
        fun parsePattern(token: String): Pair<String, IntArray> {
            val letters = StringBuilder()
            val levels = ArrayList<Int>()
            levels.add(0)
            for (ch in token) {
                if (ch.isDigit()) {
                    levels[levels.size - 1] = ch - '0'
                } else {
                    letters.append(ch)
                    levels.add(0)
                }
            }
            return letters.toString() to levels.toIntArray()
        }

        /**
         * Строит [LiangHyphenator] из списка паттернов (toks с цифрами) и слов-исключений (с дефисами
         * в точках переноса). Исключения хранятся в нижнем регистре.
         */
        fun fromTokens(
            patternTokens: List<String>,
            exceptionWords: List<String>,
            leftMin: Int = 2,
            rightMin: Int = 2,
        ): LiangHyphenator {
            val patterns = HashMap<String, IntArray>(patternTokens.size * 2)
            for (tok in patternTokens) {
                val (letters, levels) = parsePattern(tok)
                if (letters.isNotEmpty()) patterns[letters] = levels
            }
            val exceptions = HashMap<String, IntArray>(exceptionWords.size * 2)
            for (word in exceptionWords) {
                val clean = word.replace("-", "").lowercase()
                val positions = ArrayList<Int>()
                var plainIndex = 0
                for (ch in word) {
                    if (ch == '-') positions.add(plainIndex) else plainIndex++
                }
                if (clean.isNotEmpty()) exceptions[clean] = positions.toIntArray()
            }
            return LiangHyphenator(patterns, exceptions, leftMin, rightMin)
        }
    }
}
