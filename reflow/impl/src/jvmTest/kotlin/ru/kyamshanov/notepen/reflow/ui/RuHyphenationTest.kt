package ru.kyamshanov.notepen.reflow.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Проверяет РЕАЛЬНЫЙ десктопный перенос (Кнут-Лиэнг + паттерны `hyph-ru` из ресурсов) через
 * [platformSoftHyphenPositions]. Контрольные слова — известные слогоразбиения, в т.ч. те, что
 * правило-ориентированный слогодел ломал (отменяет, хитрость, проблема).
 */
class RuHyphenationTest {
    private fun hyphenate(text: String): String {
        val pos = platformSoftHyphenPositions(text)
        val sb = StringBuilder()
        var prev = 0
        for (p in pos) {
            sb.append(text, prev, p)
            sb.append('-')
            prev = p
        }
        sb.append(text, prev, text.length)
        return sb.toString()
    }

    @Test
    fun classicWords() {
        assertEquals("мо-ло-ко", hyphenate("молоко"))
        assertEquals("ма-ши-на", hyphenate("машина"))
        assertEquals("ком-пью-тер", hyphenate("компьютер"))
    }

    @Test
    fun previouslyBrokenClustersNowCorrect() {
        // Правило-ориентированный давал «отм-еняет», «хитр-ость», «проб-лемой».
        val otmenyaet = hyphenate("отменяет")
        assertTrue("отм-" !in otmenyaet, otmenyaet)
        val hitrost = hyphenate("хитрость")
        assertTrue("хитр-" !in hitrost, hitrost)
        val problema = hyphenate("проблема")
        assertTrue("проб-" !in problema, problema)
    }

    @Test
    fun longWordHasMultipleBreaksAndRoundTrips() {
        val h = hyphenate("программирование")
        assertTrue(h.count { it == '-' } >= 4, h)
        assertEquals("программирование", h.replace("-", ""))
    }

    @Test
    fun latinUntouched() {
        assertEquals("Observable", hyphenate("Observable"))
        assertEquals("это Observable", hyphenate("это Observable").replace("-", ""))
    }

    @Test
    fun monosyllabicNotHyphenated() {
        assertEquals("дом", hyphenate("дом"))
        assertEquals("стол", hyphenate("стол"))
    }
}
