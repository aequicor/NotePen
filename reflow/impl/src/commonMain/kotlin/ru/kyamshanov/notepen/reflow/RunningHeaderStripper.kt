package ru.kyamshanov.notepen.reflow

import kotlin.math.roundToInt

/**
 * Удаляет колонтитулы (running headers / footers) из набора страниц до сборки
 * [ReflowDocument]. Колонтитулы детектируются как **повторяющийся текст в
 * одинаковой Y-позиции** через достаточно большую долю страниц книги:
 *
 *  - «Page 23», «Стр. 24» — отличаются числом, но идентичны по позиции и
 *    структуре. Нормализуем цифры в маркер `#`, тогда они кластеризуются.
 *  - Повторяющийся заголовок главы — один и тот же текст из страницы в страницу
 *    в одной зоне.
 *  - Copyright/издатель внизу — тот же текст постоянно внизу.
 *
 * Метод **консервативен**: работает только на документах от
 * [MIN_PAGES_FOR_DETECTION] страниц (на короткие книги нет статистики), смотрит
 * только в [TOP_ZONE_FRAC] / [BOTTOM_ZONE_FRAC] от высоты страницы, и стрипает
 * только то, что встречается в ≥ [DEFAULT_REPETITION_THRESHOLD] доле страниц.
 *
 * Не использует Compose, не зависит от платформы — чистая логика над [RawPage].
 */
internal object RunningHeaderStripper {
    /** Минимум страниц, чтобы детект имел смысл. Меньше — статистика недоверчивая. */
    private const val MIN_PAGES_FOR_DETECTION = 10

    /** Какая доля высоты страницы считается top-zone (header). */
    private const val TOP_ZONE_FRAC = 0.08f

    /** Какая доля высоты страницы считается bottom-zone (footer). */
    private const val BOTTOM_ZONE_FRAC = 0.08f

    /** Доля страниц, на которой кандидат должен появиться, чтобы считаться колонтитулом. */
    private const val DEFAULT_REPETITION_THRESHOLD = 0.5f

    /**
     * Бакетизация yNorm: значение делится на этот шаг и округляется. 0.005 = 0.5%
     * высоты страницы — достаточная точность чтобы одна и та же зона ловилась на
     * каждой странице, но устойчивая к мелким float-расхождениям.
     */
    private const val Y_BUCKET_STEP = 0.005f

    /**
     * Допуск группировки глифов в одну строку колонтитула (в долях кегля): глифы
     * с близким top считаются одной строкой. Совпадает с
     * `ReflowAssembler.LINE_TOLERANCE_FRAC = 0.5` — те же эвристики.
     */
    private const val LINE_GROUPING_TOLERANCE_FRAC = 0.5f

    /**
     * Минимальная длина нормализованной строки кандидата (после trim и
     * digit→`#`). Однобуквенные/двухбуквенные строки в зонах — обычно орнаменты
     * или артефакты экстракции; их кластеризация даёт ложные срабатывания.
     */
    private const val MIN_CANDIDATE_LENGTH = 2

    /**
     * Удаляет колонтитулы из списка страниц. Возвращает страницы с тем же
     * порядком, но с урезанным `glyphs` для тех, где найдены матчи.
     *
     * @param pages исходные страницы; ВСЕ страницы документа, чтобы статистика
     *   повторений была корректной (полу-документ может пропустить колонтитулы,
     *   присутствующие только в одной половине)
     * @param threshold доля страниц, начиная с которой кандидат считается
     *   колонтитулом; по умолчанию [DEFAULT_REPETITION_THRESHOLD]
     */
    fun strip(
        pages: List<RawPage>,
        threshold: Float = DEFAULT_REPETITION_THRESHOLD,
    ): List<RawPage> {
        if (pages.size < MIN_PAGES_FOR_DETECTION) return pages
        val perPageCandidates = pages.map { collectCandidates(it) }
        val keyCounts = HashMap<CandidateKey, Int>()
        perPageCandidates.forEach { candidates ->
            // Set — потому что один и тот же ключ на одной странице (например,
            // page number в разных зонах) считается один раз: важна доля СТРАНИЦ,
            // не вхождений.
            candidates.map { it.key }.toSet().forEach { key ->
                keyCounts[key] = (keyCounts[key] ?: 0) + 1
            }
        }
        val minOccurrences = (pages.size * threshold).roundToInt().coerceAtLeast(MIN_PAGES_FOR_DETECTION)
        val stripKeys = keyCounts.filterValues { it >= minOccurrences }.keys
        if (stripKeys.isEmpty()) return pages

        return pages.mapIndexed { i, page ->
            val toRemove =
                perPageCandidates[i]
                    .filter { it.key in stripKeys }
                    .flatMap { it.glyphIndices }
                    .toHashSet()
            if (toRemove.isEmpty()) {
                page
            } else {
                page.copy(glyphs = page.glyphs.filterIndexed { idx, _ -> idx !in toRemove })
            }
        }
    }

    /**
     * Собирает кандидатов колонтитулов на одной странице: строки в top/bottom
     * зонах с нормализованным текстом и Y-бакетом.
     */
    private fun collectCandidates(page: RawPage): List<Candidate> {
        if (page.glyphs.isEmpty() || page.heightPt <= 0f) return emptyList()
        val topLimit = page.heightPt * TOP_ZONE_FRAC
        val bottomLimit = page.heightPt * (1f - BOTTOM_ZONE_FRAC)
        // Глифы с original-индексами; нужны индексы, чтобы потом удалить их из page.glyphs.
        val zoneGlyphs =
            page.glyphs.withIndex().filter { (_, g) ->
                g.rect.bottom <= topLimit || g.rect.top >= bottomLimit
            }
        if (zoneGlyphs.isEmpty()) return emptyList()
        return groupIntoLines(zoneGlyphs, page.heightPt)
    }

    private fun groupIntoLines(
        zoneGlyphs: List<IndexedValue<RawGlyph>>,
        pageHeightPt: Float,
    ): List<Candidate> {
        val sorted = zoneGlyphs.sortedBy { it.value.rect.top }
        val lines = mutableListOf<MutableList<IndexedValue<RawGlyph>>>()
        var currentTop = Float.NaN
        var currentFont = 0f
        for (g in sorted) {
            val tolerance = (if (currentFont > 0f) currentFont else g.value.fontSizePt) * LINE_GROUPING_TOLERANCE_FRAC
            if (lines.isEmpty() || kotlin.math.abs(g.value.rect.top - currentTop) > tolerance) {
                lines.add(mutableListOf(g))
                currentTop = g.value.rect.top
                currentFont = g.value.fontSizePt
            } else {
                lines.last().add(g)
            }
        }
        return lines.mapNotNull { line ->
            val sortedByX = line.sortedBy { it.value.rect.left }
            val rawText = sortedByX.joinToString("") { it.value.text }.trim()
            if (rawText.length < MIN_CANDIDATE_LENGTH) return@mapNotNull null
            val normalized = normaliseDigits(rawText)
            val yMid = (sortedByX.first().value.rect.top + sortedByX.first().value.rect.bottom) / 2f
            val yNorm = yMid / pageHeightPt
            val yBucket = (yNorm / Y_BUCKET_STEP).roundToInt()
            Candidate(
                key = CandidateKey(yBucket = yBucket, normalisedText = normalized),
                glyphIndices = sortedByX.map { it.index },
            )
        }
    }

    /**
     * Минимум non-digit non-whitespace символов в строке, при котором digit-
     * нормализация **отключается**. «Page 23» имеет 4 буквы — нормализуется
     * (свернёт «Page 23/24/25» в один ключ). «Unique title 1» — 11 букв,
     * нормализация не сработает, и три отдельных уникальных заголовка
     * сохранятся как три отдельных ключа.
     */
    private const val DIGIT_NORMALIZATION_MAX_NON_DIGIT_CHARS = 8

    /**
     * Сворачивает цифры в маркер `#`, если текст короткий (label + number style:
     * «Page 23», «Стр. 24»). Длинные строки с числом внутри (типа заголовка
     * раздела «Глава 1. Имя существительное») нормализацию не получают —
     * иначе агрегация ключей слишком агрессивна и удаляет уникальные заголовки.
     *
     * Возвращает текст в нижнем регистре, чтобы вариации Capitalize/UPPER
     * объединялись.
     */
    private fun normaliseDigits(text: String): String {
        val nonDigitNonSpaceCount = text.count { !it.isDigit() && !it.isWhitespace() }
        if (nonDigitNonSpaceCount > DIGIT_NORMALIZATION_MAX_NON_DIGIT_CHARS) {
            return text.lowercase()
        }
        val out = StringBuilder()
        var inDigits = false
        for (ch in text) {
            if (ch.isDigit()) {
                if (!inDigits) {
                    out.append('#')
                    inDigits = true
                }
            } else {
                out.append(ch)
                inDigits = false
            }
        }
        return out.toString().lowercase()
    }

    /** Ключ кластеризации: позиция (в bucket'ах) + нормализованный текст. */
    private data class CandidateKey(
        val yBucket: Int,
        val normalisedText: String,
    )

    /** Один кандидат колонтитула на одной странице. */
    private data class Candidate(
        val key: CandidateKey,
        val glyphIndices: List<Int>,
    )
}
