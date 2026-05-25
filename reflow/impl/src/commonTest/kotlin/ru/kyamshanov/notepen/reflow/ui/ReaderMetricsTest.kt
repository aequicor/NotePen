package ru.kyamshanov.notepen.reflow.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderMetricsTest {
    @Test
    fun percent_spansFirstToLastBlock() {
        assertEquals(0, ReaderProgress.percent(firstVisibleBlock = 0, totalBlocks = 10))
        assertEquals(100, ReaderProgress.percent(firstVisibleBlock = 9, totalBlocks = 10))
        assertEquals(50, ReaderProgress.percent(firstVisibleBlock = 5, totalBlocks = 11))
    }

    @Test
    fun percent_handlesDegenerateCounts() {
        assertEquals(0, ReaderProgress.percent(firstVisibleBlock = 0, totalBlocks = 0))
        assertEquals(100, ReaderProgress.percent(firstVisibleBlock = 0, totalBlocks = 1))
        // Выход за границы зажимается в 0..100.
        assertEquals(100, ReaderProgress.percent(firstVisibleBlock = 99, totalBlocks = 10))
    }

    @Test
    fun minutesLeft_roundsUpAndStaysNonNegative() {
        assertEquals(0, ReaderProgress.minutesLeft(remainingChars = 0))
        assertEquals(1, ReaderProgress.minutesLeft(remainingChars = 1, charsPerMinute = 1000))
        assertEquals(1, ReaderProgress.minutesLeft(remainingChars = 1000, charsPerMinute = 1000))
        assertEquals(2, ReaderProgress.minutesLeft(remainingChars = 1001, charsPerMinute = 1000))
    }

    @Test
    fun columnWidth_scalesWithCharsAndFontSize() {
        assertEquals(627f, columnWidthValue(columnChars = 66, fontSizeValue = 19f))
        assertEquals(350f, columnWidthValue(columnChars = 50, fontSizeValue = 14f))
    }
}
