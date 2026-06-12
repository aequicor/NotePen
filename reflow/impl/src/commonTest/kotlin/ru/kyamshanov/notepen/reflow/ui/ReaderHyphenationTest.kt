package ru.kyamshanov.notepen.reflow.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Проверяет двустороннюю карту смещений [ReaderHyphenation]: вставку мягких переносов и
 * взаимную обратимость plain↔hyph (стык, на котором публичные PLAIN-смещения встречаются с
 * HYPH-смещениями из [androidx.compose.ui.text.TextLayoutResult]).
 */
class ReaderHyphenationTest {
    @Test
    fun identityWhenNoPositions() {
        val h = ReaderHyphenation("abcd", IntArray(0))
        assertEquals("abcd", h.hyphenated)
        assertEquals(4, h.hyphLength)
        assertTrue(!h.hasSoftHyphens)
        for (p in 0..4) {
            assertEquals(p, h.plainToHyph(p))
            assertEquals(p, h.hyphToPlain(p))
        }
    }

    @Test
    fun insertsSoftHyphensAtPositions() {
        val h = ReaderHyphenation("abcd", intArrayOf(2))
        assertEquals("ab${SOFT_HYPHEN}cd", h.hyphenated)
        assertEquals(5, h.hyphLength)
        assertTrue(h.hasSoftHyphens)
    }

    @Test
    fun plainToHyphAccountsForInsertionsBefore() {
        val h = ReaderHyphenation("abcd", intArrayOf(2))
        assertEquals(0, h.plainToHyph(0))
        // Перенос на позиции 2 стоит перед plain[2]; plain-смещение 2 (перед 'c') → hyph 3.
        assertEquals(3, h.plainToHyph(2))
        assertEquals(5, h.plainToHyph(4)) // конец
    }

    @Test
    fun roundTripPlainHyphPlain() {
        val h = ReaderHyphenation("программирование", intArrayOf(3, 5, 8, 11, 13))
        for (p in 0..h.plain.length) {
            assertEquals(p, h.hyphToPlain(h.plainToHyph(p)), "round-trip failed at plain $p")
        }
    }

    @Test
    fun hyphenatedLengthMatchesMap() {
        val word = "распределение"
        val h = ReaderHyphenation(word, intArrayOf(3, 6, 9, 11))
        assertEquals(h.hyphenated.length, h.hyphLength)
        assertEquals(word.length, h.plain.length)
        assertEquals(word, h.hyphenated.replace(SOFT_HYPHEN.toString(), ""))
    }

    @Test
    fun multiplePositionsMapConsistently() {
        val h = ReaderHyphenation("abcdef", intArrayOf(2, 4))
        assertEquals("ab${SOFT_HYPHEN}cd${SOFT_HYPHEN}ef", h.hyphenated)
        // plain end maps to hyph end and back.
        assertEquals(8, h.plainToHyph(6))
        assertEquals(6, h.hyphToPlain(8))
        for (p in 0..6) assertEquals(p, h.hyphToPlain(h.plainToHyph(p)))
    }

    @Test
    fun promotedSlotRendersVisibleHyphenWithSameMap() {
        val plain = "abcdef"
        val positions = intArrayOf(2, 4)
        val shy = ReaderHyphenation(plain, positions)
        val mixed = ReaderHyphenation(plain, positions, booleanArrayOf(true, false))
        // Слот 0 промоутнут в видимый дефис, слот 1 остался мягким переносом.
        assertEquals("ab${PROMOTED_HYPHEN}cd${SOFT_HYPHEN}ef", mixed.hyphenated)
        assertEquals(shy.hyphLength, mixed.hyphenated.length)
        // Карта не зависит от значения символа в слоте: конвертации совпадают с непромоутнутой.
        for (p in 0..plain.length) {
            assertEquals(shy.plainToHyph(p), mixed.plainToHyph(p), "plainToHyph diverged at $p")
            assertEquals(p, mixed.hyphToPlain(mixed.plainToHyph(p)), "round-trip failed at $p")
        }
    }

    @Test
    fun slotHyphIndexPointsAtInsertedChar() {
        val h = ReaderHyphenation("abcdef", intArrayOf(2, 4), booleanArrayOf(true, false))
        assertEquals(PROMOTED_HYPHEN, h.hyphenated[h.slotHyphIndex(0)])
        assertEquals(SOFT_HYPHEN, h.hyphenated[h.slotHyphIndex(1)])
    }

    @Test
    fun reducedPositionsKeepInvariants() {
        // Blacklist слота 0: позиции [2,4] → [4]; карта пересчитывается и остаётся обратимой.
        val reduced = ReaderHyphenation("abcdef", intArrayOf(4), booleanArrayOf(true))
        assertEquals("abcd${PROMOTED_HYPHEN}ef", reduced.hyphenated)
        assertEquals(7, reduced.hyphLength)
        for (p in 0..6) assertEquals(p, reduced.hyphToPlain(reduced.plainToHyph(p)))
    }
}
