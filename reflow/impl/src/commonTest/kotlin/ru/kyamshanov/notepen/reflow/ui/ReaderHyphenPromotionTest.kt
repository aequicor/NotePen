package ru.kyamshanov.notepen.reflow.ui

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Ядро промоушена дефисов [promoteHyphens] на скриптованных оракулах (без TextMeasurer).
 * Оракул — детерминированная функция текста варианта (как настоящая раскладка): на один и тот же
 * hyphenated-текст всегда один ответ. Сценарии повторяют физику жадной разбивки: мягкий перенос
 * не имеет ширины, видимый дефис — имеет, поэтому промоушен может сдвинуть разрыв раньше.
 */
class ReaderHyphenPromotionTest {
    @Test
    fun identityWithoutSlotsSkipsOracle() {
        val initial = ReaderHyphenation("abcdef", IntArray(0))
        val calls = mutableListOf<String>()
        val final = promoteHyphens(initial, oracle = scripted(emptyMap(), calls))
        assertSame(initial, final)
        assertTrue(calls.isEmpty(), "оракул не должен вызываться без слотов")
    }

    @Test
    fun promotesSlotAtLineEnd() {
        // "ab§cdef": слот (hyph 2) замыкает строку; с дефисом разрыв не уехал → стабильно.
        val initial = ReaderHyphenation("abcdef", intArrayOf(2))
        val calls = mutableListOf<String>()
        val script =
            mapOf(
                "ab${SOFT_HYPHEN}cdef" to intArrayOf(3),
                "ab${PROMOTED_HYPHEN}cdef" to intArrayOf(3),
            )
        val final = promoteHyphens(initial, oracle = scripted(script, calls))
        assertEquals("ab${PROMOTED_HYPHEN}cdef", final.hyphenated)
        assertContentEquals(booleanArrayOf(true), final.promoted)
        assertEquals(2, calls.size, "ожидалось два прохода: промоушен и подтверждение")
        assertEquals(final.hyphenated, calls.last(), "последний вызов оракула — для финального варианта")
    }

    @Test
    fun blacklistsSlotWhoseDashDoesNotFit() {
        // "ab cd§ef": слот замыкает строку. С дефисом строка не влезает — разрыв уезжает на пробел,
        // а строка с прежним началом (0) больше не рвётся на слоте → слот невалиден (blacklist),
        // текст возвращается к виду без переноса.
        val initial = ReaderHyphenation("ab cdef", intArrayOf(5))
        val calls = mutableListOf<String>()
        val script =
            mapOf(
                "ab cd${SOFT_HYPHEN}ef" to intArrayOf(6),
                "ab cd${PROMOTED_HYPHEN}ef" to intArrayOf(3),
                "ab cdef" to intArrayOf(3),
            )
        val final = promoteHyphens(initial, oracle = scripted(script, calls))
        assertEquals("ab cdef", final.hyphenated)
        assertEquals(0, final.positions.size)
        assertEquals(3, calls.size)
        assertEquals(final.hyphenated, calls.last())
    }

    @Test
    fun promotesIndependentSlotsInOneBatch() {
        // Оба слота замыкают свои строки — промоутятся за один проход, второй подтверждает.
        val initial = ReaderHyphenation("aaa bbb", intArrayOf(2, 6))
        val calls = mutableListOf<String>()
        val script =
            mapOf(
                "aa${SOFT_HYPHEN}a bb${SOFT_HYPHEN}b" to intArrayOf(3, 8),
                "aa${PROMOTED_HYPHEN}a bb${PROMOTED_HYPHEN}b" to intArrayOf(3, 8),
            )
        val final = promoteHyphens(initial, oracle = scripted(script, calls))
        assertEquals("aa${PROMOTED_HYPHEN}a bb${PROMOTED_HYPHEN}b", final.hyphenated)
        assertContentEquals(booleanArrayOf(true, true), final.promoted)
        assertEquals(2, calls.size)
    }

    @Test
    fun cascadeDemotesThenSettles() {
        // Каскад: blacklist слота 0 сдвигает контекст слота 1 → demote → re-promote в новом
        // контексте → дефис снова не влезает → blacklist. Оракул консистентен по тексту.
        // plain "aa bb cc", слоты: 4 (b|b) и 7 (c|c).
        val initial = ReaderHyphenation("aa bb cc", intArrayOf(4, 7))
        val calls = mutableListOf<String>()
        val s = SOFT_HYPHEN
        val d = PROMOTED_HYPHEN
        val script =
            mapOf(
                // Оба слота замыкают строки → промоушен обоих.
                "aa b${s}b c${s}c" to intArrayOf(5, 9),
                // Дефис слота 0 не влез (строка с началом 0 рвётся на пробеле), слот 1 у конца.
                "aa b${d}b c${d}c" to intArrayOf(3, 9),
                // Слот 0 в blacklist; слот 1 (записан со старта 3) теперь посреди последней
                // строки, его прежней строки нет (старты 0 и 6) → demote...
                "aa bb c${d}c" to intArrayOf(6),
                // ...мягкий перенос снова замыкает строку (старт 0) → re-promote...
                "aa bb c${s}c" to intArrayOf(8),
                // ...и повторный оракул "aa bb c-c" (тот же текст → тот же ответ [6]) даёт
                // blacklist: строка со стартом 0 существует, слот её не замыкает.
                "aa bb cc" to intArrayOf(3),
            )
        val final = promoteHyphens(initial, oracle = scripted(script, calls))
        assertEquals("aa bb cc", final.hyphenated)
        assertEquals(0, final.positions.size)
        assertEquals(6, calls.size, "ожидались проходы: promote, blacklist0+refresh1, demote1, re-promote1, blacklist1, стабильный")
        assertEquals("aa bb c${d}c", calls[2])
        assertEquals("aa bb c${s}c", calls[3])
        assertEquals(final.hyphenated, calls.last())
    }

    @Test
    fun capFallsBackToDemoteOnly() {
        // Патологический (нефизичный) оракул: промоушен вечно сдвигает чужие строки так, что
        // записанное начало исчезает → бесконечный promote/demote. Кап (1+4=5) останавливает,
        // demote-only добивание возвращает мягкий перенос (без новых промоушенов).
        // plain "aa bbbb", слот 5 (bb|bb); запись идёт со строки 1 (старт 3), не с нулевой.
        val initial = ReaderHyphenation("aa bbbb", intArrayOf(5))
        val calls = mutableListOf<String>()
        val script =
            mapOf(
                // Слот замыкает строку 1 (старты 0 и 3).
                "aa bb${SOFT_HYPHEN}bb" to intArrayOf(3, 6),
                // С дефисом строки легли иначе: стартов 3 нет (0, 2, 6) → demote, не blacklist.
                "aa bb${PROMOTED_HYPHEN}bb" to intArrayOf(2, 7),
            )
        val final = promoteHyphens(initial, oracle = scripted(script, calls))
        assertEquals("aa bb${SOFT_HYPHEN}bb", final.hyphenated)
        assertContentEquals(intArrayOf(5), final.positions)
        assertContentEquals(booleanArrayOf(false), final.promoted)
        // 5 итераций до капа + 2 вызова demote-only добивания.
        assertEquals(7, calls.size)
        assertEquals(final.hyphenated, calls.last(), "последний вызов оракула — для финального варианта")
    }

    @Test
    fun deterministicForSameOracle() {
        val script =
            mapOf(
                "ab cd${SOFT_HYPHEN}ef" to intArrayOf(6),
                "ab cd${PROMOTED_HYPHEN}ef" to intArrayOf(3),
                "ab cdef" to intArrayOf(3),
            )
        val a = promoteHyphens(ReaderHyphenation("ab cdef", intArrayOf(5)), oracle = scripted(script))
        val b = promoteHyphens(ReaderHyphenation("ab cdef", intArrayOf(5)), oracle = scripted(script))
        assertEquals(a.hyphenated, b.hyphenated)
        assertContentEquals(a.positions, b.positions)
        assertContentEquals(a.promoted, b.promoted)
    }

    /** Оракул по таблице text→ends; незнакомый вариант — громкое падение теста. */
    private fun scripted(
        script: Map<String, IntArray>,
        calls: MutableList<String>? = null,
    ): HyphLineOracle =
        HyphLineOracle { variant ->
            calls?.add(variant.hyphenated)
            script.getValue(variant.hyphenated)
        }
}
