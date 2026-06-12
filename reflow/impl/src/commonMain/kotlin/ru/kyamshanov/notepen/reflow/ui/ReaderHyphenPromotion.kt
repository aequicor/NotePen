package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import ru.kyamshanov.notepen.reflow.api.SourceSpan

/**
 * Промоушен дефисов: мягкие переносы, на которых движок разорвал строки, превращаются в видимые
 * дефисы, УЧАСТВУЮЩИЕ в измерении строки, итерациями раскладки до неподвижной точки.
 *
 * Зачем: Skia (десктоп) принимает U+00AD как точку разрыва, но дефис-глиф не рисует и ширины ему
 * не даёт. Прежняя дорисовка дефиса ПОВЕРХ готовой раскладки работала только при рваном правом
 * крае (align=START) — там справа от слога есть свободное место. При выключке по ширине строка
 * растянута до полного края, и дефису физически нет места. Word решает это внутри разбивщика
 * строк: точка переноса валидна, только если слог+дефис влезают, а дефис меряется как часть
 * строки. Здесь то же самое эмулируется поверх «глухого» движка:
 *
 * 1. Раскладываем текст со всеми мягкими переносами (слоты-кандидаты).
 * 2. Слоты в концах строк промоутим в видимый [PROMOTED_HYPHEN] той же длины — карта смещений
 *    plain↔hyph ключуется позициями и не меняется.
 * 3. Перемеряем. Дефис добавил ширину, разрыв мог уехать раньше:
 *    - какая-то строка по-прежнему начинается там же, но слот её больше не замыкает → дефис в том
 *      же контексте не влез → слот в blacklist (разрыв невалиден — ровно правило Word);
 *    - строки слота с прежним началом больше нет (каскад от слотов выше по тексту) → demote
 *      обратно в мягкий перенос; слот может промоутнуться снова, когда префикс стабилизируется.
 * 4. До неподвижной точки. Сходимость: жадная разбивка строит строки слева направо, судьба слота
 *    зависит только от префикса текста — слоты стабилизируются в порядке текста; blacklist строго
 *    уменьшает множество кандидатов. Кап — страховка; по капу — demote-only добивание (страдает
 *    качество — разрыв без дефиса, но не корректность — дефис посреди строки невозможен).
 *
 * Инварианты результата: видимый дефис стоит ТОЛЬКО в конце строки; последний вызов оракула
 * сделан именно для финального варианта (его раскладка достоверна для финального текста).
 * На Android слотов нет ([platformSoftHyphenPositions] пуст) — identity, ноль итераций.
 */
internal fun interface HyphLineOracle {
    /** HYPH-смещения концов всех строк, кроме последней — `getLineEnd(line, visibleEnd = false)`. */
    fun lineEndsFor(variant: ReaderHyphenation): IntArray
}

/**
 * Гоняет промоушен до неподвижной точки (см. документацию файла). Возвращает финальный вариант;
 * гарантирует, что последний вызов [oracle] был сделан для него.
 */
internal fun promoteHyphens(
    initial: ReaderHyphenation,
    maxIterations: Int = (initial.positions.size + EXTRA_ITERATIONS).coerceAtMost(MAX_ITERATIONS),
    oracle: HyphLineOracle,
): ReaderHyphenation {
    if (!initial.hasSoftHyphens) return initial
    val state = PromotionState(initial)
    var current = initial
    var iterations = 0
    while (iterations < maxIterations && state.advance(current, oracle.lineEndsFor(current))) {
        current = state.build()
        iterations++
    }
    // Выход без изменений == неподвижная точка (последний оракул — для current). Исчерпание
    // капа — патология разбивки: добиваем demote-only, жертвуя дефисами, не корректностью.
    return if (iterations < maxIterations) current else state.demoteOnlyFinish(current, oracle)
}

/** Финальный текст блока (с промоутнутыми дефисами) и раскладка, полученная именно для него. */
internal class ResolvedBlockText(
    val hyphenation: ReaderHyphenation,
    val layout: TextLayoutResult,
)

/**
 * Точка входа measure/render-путей: гоняет промоушен [initial] через реальный обмер (триалы
 * собираются тем же [styledText], что у рендера, — bold/monospace/word-spacing/bionic участвуют
 * в разбивке; подсветки выделений фоновые и метрик не меняют) и возвращает финальный вариант
 * вместе с его раскладкой. Identity-путь (Android/выключенные переносы) — ровно один обмер,
 * как раньше.
 */
internal fun TextMeasurer.resolveHyphenation(
    initial: ReaderHyphenation,
    source: List<SourceSpan>,
    settings: ReflowReaderSettings,
    style: TextStyle,
    widthPx: Int,
): ResolvedBlockText {
    var last: TextLayoutResult? = null

    fun measureVariant(variant: ReaderHyphenation): TextLayoutResult =
        measure(
            styledText(variant, source, emptyList(), settings),
            style,
            constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)),
        ).also { last = it }

    val final =
        if (initial.hasSoftHyphens) {
            promoteHyphens(initial) { variant -> lineEndsOf(measureVariant(variant)) }
        } else {
            measureVariant(initial)
            initial
        }
    // promoteHyphens гарантирует: последний обмер — для финального варианта.
    return ResolvedBlockText(final, requireNotNull(last))
}

private fun lineEndsOf(layout: TextLayoutResult): IntArray =
    IntArray((layout.lineCount - 1).coerceAtLeast(0)) { layout.getLineEnd(it, visibleEnd = false) }

/** Дополнительные итерации сверх числа слотов (промоушен почти всегда сходится за 2-3). */
private const val EXTRA_ITERATIONS = 4

/** Жёсткий потолок итераций на блок — страховка от патологий разбивки. */
private const val MAX_ITERATIONS = 16

/**
 * Изменяемое состояние fixed-point. Все маски индексируются ОРИГИНАЛЬНЫМИ слотами [origin] —
 * blacklist сокращает позиции текущего варианта, но не сдвигает учёт. Начала строк записываются
 * в PLAIN-смещениях: они стабильны между вариантами (hyph-смещения плывут при blacklist'е).
 */
private class PromotionState(
    initial: ReaderHyphenation,
) {
    private val plain = initial.plain
    private val origin = initial.positions
    private val active = BooleanArray(origin.size) { true }
    private val promoted = BooleanArray(origin.size)

    /** PLAIN-начало строки, которую слот замыкал при промоушене; -1 — слот не промоутнут. */
    private val recordedLineStart = IntArray(origin.size) { -1 }

    /** origin-индексы слотов текущего варианта (соответствие m → origin после [build]). */
    private var activeOrigin = IntArray(origin.size) { it }

    /** Собирает вариант из активных слотов; обновляет соответствие activeOrigin. */
    fun build(): ReaderHyphenation {
        val count = active.count { it }
        val positions = IntArray(count)
        val mask = BooleanArray(count)
        val mapping = IntArray(count)
        var m = 0
        for (orig in origin.indices) {
            if (!active[orig]) continue
            positions[m] = origin[orig]
            mask[m] = promoted[orig]
            mapping[m] = orig
            m++
        }
        activeOrigin = mapping
        return ReaderHyphenation(plain, positions, mask)
    }

    /**
     * Один шаг итерации по концам строк [ends] раскладки [current]: промоушен слотов в концах
     * строк, blacklist/demote промоутнутых, выпавших из концов. Возвращает, были ли изменения
     * (нет изменений == неподвижная точка).
     */
    fun advance(
        current: ReaderHyphenation,
        ends: IntArray,
    ): Boolean {
        var changed = false
        val slotAtEnd = BooleanArray(activeOrigin.size)
        // 1) Слоты, замыкающие строки: новые промоутим, у стабильных освежаем запись начала
        // строки (контекст мог сместиться целиком, оставив слот на конце строки — это норма).
        for (line in ends.indices) {
            val m = slotIndexAt(current, ends[line] - 1) ?: continue
            slotAtEnd[m] = true
            val orig = activeOrigin[m]
            if (!promoted[orig]) {
                promoted[orig] = true
                changed = true
            }
            recordedLineStart[orig] = current.hyphToPlain(lineStart(ends, line))
        }
        // 2) Промоутнутые слоты вне концов строк: если строка с записанным началом существует,
        // но слот её больше не замыкает — дефис не влез (blacklist); иначе — каскадный сдвиг
        // сверху (demote, слот ещё может вернуться).
        val startsPlain = lineStartsPlain(current, ends)
        for (m in activeOrigin.indices) {
            val orig = activeOrigin[m]
            if (!promoted[orig] || slotAtEnd[m]) continue
            if (recordedLineStart[orig] in startsPlain) {
                active[orig] = false
            }
            promoted[orig] = false
            recordedLineStart[orig] = -1
            changed = true
        }
        return changed
    }

    /**
     * Добивание по капу: только demote промоутнутых слотов вне концов строк, без новых промоушенов
     * и blacklist'ов. Множество промоутнутых строго убывает — заканчивается максимум за их число
     * шагов. Намеренно мягче blacklist'а: невидимый разрыв без дефиса лучше сдвига целого слова.
     */
    fun demoteOnlyFinish(
        start: ReaderHyphenation,
        oracle: HyphLineOracle,
    ): ReaderHyphenation {
        var current = start
        while (true) {
            val ends = oracle.lineEndsFor(current)
            val slotAtEnd = BooleanArray(activeOrigin.size)
            for (line in ends.indices) {
                val m = slotIndexAt(current, ends[line] - 1) ?: continue
                slotAtEnd[m] = true
            }
            var changed = false
            for (m in activeOrigin.indices) {
                val orig = activeOrigin[m]
                if (!promoted[orig] || slotAtEnd[m]) continue
                promoted[orig] = false
                recordedLineStart[orig] = -1
                changed = true
            }
            if (!changed) return current
            current = build()
        }
    }

    /** Индекс слота варианта [current], чей символ стоит в hyph-смещении [hyphIdx], либо null. */
    private fun slotIndexAt(
        current: ReaderHyphenation,
        hyphIdx: Int,
    ): Int? {
        var lo = 0
        var hi = current.positions.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = current.slotHyphIndex(mid)
            when {
                v == hyphIdx -> return mid
                v < hyphIdx -> lo = mid + 1
                else -> hi = mid - 1
            }
        }
        return null
    }

    /** PLAIN-начала всех строк раскладки (начало строки i — конец строки i-1, нулевой — 0). */
    private fun lineStartsPlain(
        current: ReaderHyphenation,
        ends: IntArray,
    ): Set<Int> {
        val starts = HashSet<Int>(ends.size + 1)
        for (line in 0..ends.size) starts.add(current.hyphToPlain(lineStart(ends, line)))
        return starts
    }
}

/** HYPH-начало строки [line]: конец предыдущей строки (для нулевой — 0). */
private fun lineStart(
    ends: IntArray,
    line: Int,
): Int = if (line == 0) 0 else ends[line - 1]
