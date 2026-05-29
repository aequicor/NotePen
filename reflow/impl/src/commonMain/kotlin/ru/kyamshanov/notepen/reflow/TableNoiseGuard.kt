package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.ReflowBlock

/**
 * Общий OCR-noise фильтр для таблиц — единая точка истины для обоих путей сборки
 * таблиц: Stream-детектора в [ReflowAssembler.buildTable] и Lattice-рефайнера
 * [ru.kyamshanov.notepen.reflow.lattice.LatticeTableRefiner].
 *
 * Мотивация (F-1 → F-7 → F-8): на OCR-сканах (учебник Барановской, стр. 1–3) и Stream-,
 * и Lattice-путь нарезают обычную прозу по глифам — «(О|с|нова|на|в|1997|году)»,
 * «This|is|a|book.» — и строят полностраничные посимвольные/послоговые сетки.
 *
 *  - **F-1** (avg<2 / fragment-ratio): ловит чисто-побуквенный шум. Но OCR часто
 *    даёт фрагменты по 4–10 символов («анов», «глийс», «упражнений») со средним >2 и
 *    долей ≤2-символьных ячеек <0.6 — такие сетки проскакивали (conf 0.6–0.73).
 *  - **F-8** добавляет два сигнала, откалиброванных на реальных данных (8 фантомных
 *    таблиц Барановской против 20 легитимных таблиц thesis-фикстуры, нулевой
 *    false-positive):
 *      * **wide shredded grid** — ≥[WIDE_GRID_MIN_COLS] колонок при средней ячейке
 *        короче [WIDE_GRID_MAX_AVG] и доле фрагментов >[WIDE_GRID_MIN_FRAG_RATIO]:
 *        целая страница прозы, разрезанная на 18–23 «колонки» по глифам. Реальные
 *        широкие таблицы (thesis: 16–21 колонка) имеют avg ≥ 9.7 — не задеваются;
 *        числовая широкая таблица не задевается, т.к. числа дают низкую fragment-ratio.
 *      * **spaced-letter prose** — доля «ячеек из расставленных букв» ≥
 *        [SPACED_PROSE_MIN_RATIO] при средней букв-на-ячейку < [SPACED_PROSE_MAX_LETTER_AVG]:
 *        OCR letter-spacing «У Д К», ««В а с», «(О с». Legit таблицы с подобной
 *        долей (thesis [0]: 0.33) спасает высокая letter-density (12.2 букв/ячейка).
 *
 * При срабатывании Stream-путь возвращает диапазон в абзацный сборщик, а Lattice —
 * сохраняет исходный Figure-кроп (оба строго лучше сломанной таблицы).
 */
internal object TableNoiseGuard {
    /** Нижняя граница средней длины **непустой** ячейки (символов, с внутр. пробелами). */
    const val MIN_AVG_CELL_CHARS: Float = 2.0f

    /** Максимальная доля «фрагментных» (≤ [FRAGMENT_CELL_CHARS]) ячеек среди непустых. */
    const val FRAGMENT_RATIO_LIMIT: Float = 0.6f

    /** Длина (символов), при которой непустая ячейка считается «фрагментом». */
    const val FRAGMENT_CELL_CHARS: Int = 2

    /** Минимум колонок, чтобы таблица считалась «широкой» и проверялась как shredded grid. */
    private const val WIDE_GRID_MIN_COLS: Int = 12

    /** Потолок средней длины ячейки для широкой таблицы, выше которого она — настоящая. */
    private const val WIDE_GRID_MAX_AVG: Float = 6.0f

    /** Минимальная доля фрагментов в широкой таблице, чтобы признать её shredded. */
    private const val WIDE_GRID_MIN_FRAG_RATIO: Float = 0.35f

    /** Минимальная доля «расставленных-буквами» ячеек для spaced-letter prose. */
    private const val SPACED_PROSE_MIN_RATIO: Float = 0.3f

    /** Потолок средней «букв на ячейку» для spaced-letter prose (выше — настоящий текст). */
    private const val SPACED_PROSE_MAX_LETTER_AVG: Float = 4.0f

    /** Доля single-letter токенов в ячейке, при которой она — «расставленные буквы». */
    private const val SINGLE_LETTER_TOKEN_MAJORITY: Float = 0.5f

    private val WHITESPACE = Regex("\\s+")

    /**
     * `true`, если строки таблицы выглядят как OCR-каша (см. сигналы в KDoc объекта).
     * Считаем по непустым (trimmed) ячейкам. Пустая таблица шумом не считается —
     * это решают другие гарды (минимум строк/колонок).
     */
    fun isOcrNoiseTable(rows: List<ReflowBlock.TableRow>): Boolean {
        val cells = rows.flatMap { it.cells }.map { it.text.trim() }.filter { it.isNotEmpty() }
        if (cells.isEmpty()) return false
        val metrics = computeMetrics(cells, rows)
        return metrics.avgLen < MIN_AVG_CELL_CHARS ||
            metrics.fragmentRatio > FRAGMENT_RATIO_LIMIT ||
            isWideShreddedGrid(metrics) ||
            isSpacedLetterProse(metrics)
    }

    private fun isWideShreddedGrid(m: Metrics): Boolean =
        m.maxCols >= WIDE_GRID_MIN_COLS &&
            m.avgLen < WIDE_GRID_MAX_AVG &&
            m.fragmentRatio > WIDE_GRID_MIN_FRAG_RATIO

    private fun isSpacedLetterProse(m: Metrics): Boolean =
        m.spacedLetterRatio >= SPACED_PROSE_MIN_RATIO &&
            m.letterAvg < SPACED_PROSE_MAX_LETTER_AVG

    private fun computeMetrics(
        cells: List<String>,
        rows: List<ReflowBlock.TableRow>,
    ): Metrics {
        val lengths = cells.map { it.length }
        val avgLen = lengths.sum().toFloat() / cells.size
        val fragmentRatio = lengths.count { it <= FRAGMENT_CELL_CHARS }.toFloat() / cells.size
        val letterAvg = cells.sumOf { c -> c.count { it.isLetter() } }.toFloat() / cells.size
        val spaced = cells.count { isSpacedLetterCell(it) }
        val spacedLetterRatio = spaced.toFloat() / cells.size
        val maxCols = rows.maxOfOrNull { it.cells.size } ?: 0
        return Metrics(avgLen, fragmentRatio, letterAvg, spacedLetterRatio, maxCols)
    }

    /** Ячейка из «расставленных букв»: ≥2 токена, и большинство — одиночные буквы. */
    private fun isSpacedLetterCell(text: String): Boolean {
        val tokens = text.split(WHITESPACE).filter { it.isNotEmpty() }
        if (tokens.size < 2) return false
        val single = tokens.count { tok -> tok.count { it.isLetter() } == 1 && tok.length <= FRAGMENT_CELL_CHARS }
        return single >= tokens.size * SINGLE_LETTER_TOKEN_MAJORITY
    }

    private data class Metrics(
        val avgLen: Float,
        val fragmentRatio: Float,
        val letterAvg: Float,
        val spacedLetterRatio: Float,
        val maxCols: Int,
    )
}
