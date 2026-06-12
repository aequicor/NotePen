package ru.kyamshanov.notepen.reflow.ui

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Проверяет чистый алгоритм Кнута-Лиэнга [LiangHyphenator] на синтетических паттернах (без данных
 * `hyph-ru` — те проверяются на jvm). Покрывает разбор паттернов, веса (нечётный → перенос),
 * исключения и границы leftMin/rightMin.
 */
class LiangHyphenatorTest {
    @Test
    fun parsePattern_extractsLettersAndLevels() {
        val (letters, levels) = LiangHyphenator.parsePattern("а1б")
        assertEquals("аб", letters)
        assertContentEquals(intArrayOf(0, 1, 0), levels)
    }

    @Test
    fun parsePattern_keepsBoundaryDotAndTrailingDigit() {
        val (letters, levels) = LiangHyphenator.parsePattern(".до3п")
        assertEquals(".доп", letters)
        assertContentEquals(intArrayOf(0, 0, 0, 3, 0), levels)
    }

    @Test
    fun oddLevelMeansBreak() {
        val h = LiangHyphenator.fromTokens(listOf("а1б"), emptyList(), leftMin = 1, rightMin = 1)
        // "баба": паттерн «аб» (уровень 1) даёт разрыв между а(1) и б(2).
        assertContentEquals(intArrayOf(2), h.breakPositions("баба"))
    }

    @Test
    fun evenLevelDoesNotBreak() {
        val h = LiangHyphenator.fromTokens(listOf("а2б"), emptyList(), leftMin = 1, rightMin = 1)
        assertTrue(h.breakPositions("баба").isEmpty())
    }

    @Test
    fun exceptionsOverridePatterns() {
        val h = LiangHyphenator.fromTokens(listOf("а1б"), listOf("ба-ба"), leftMin = 1, rightMin = 1)
        // Исключение «ба-ба» задаёт разрыв после 2-й буквы явно.
        assertContentEquals(intArrayOf(2), h.breakPositions("баба"))
    }

    @Test
    fun leftRightMinSuppressEdgeBreaks() {
        val h = LiangHyphenator.fromTokens(listOf("а1б"), emptyList(), leftMin = 2, rightMin = 2)
        // «аба» (3 буквы): любой перенос оставил бы <2 букв с краю — запрещено.
        assertTrue(h.breakPositions("аба").isEmpty())
    }

    @Test
    fun tooShortWordNotHyphenated() {
        val h = LiangHyphenator.fromTokens(listOf("а1б"), emptyList(), leftMin = 2, rightMin = 2)
        assertTrue(h.breakPositions("аб").isEmpty())
    }
}
