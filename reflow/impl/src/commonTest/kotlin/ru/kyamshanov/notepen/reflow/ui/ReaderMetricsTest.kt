package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.ui.unit.dp
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

    @Test
    fun clampVerticalMargins_keepsMarginsThatLeaveEnoughContent() {
        // 100 + 100 = 200 ≤ 70% от 1000 → без изменений.
        assertEquals(100.dp to 100.dp, clampVerticalMargins(100.dp, 100.dp, 1000.dp))
    }

    @Test
    fun clampVerticalMargins_scalesDownProportionallyOnOverflow() {
        // 400 + 400 = 800 > 700 (70% от 1000) → масштаб 700/800 = 0.875.
        assertEquals(350.dp to 350.dp, clampVerticalMargins(400.dp, 400.dp, 1000.dp))
        // Асимметрия сохраняется: 600 + 200 = 800 → 525 + 175 = 700.
        assertEquals(525.dp to 175.dp, clampVerticalMargins(600.dp, 200.dp, 1000.dp))
    }

    @Test
    fun clampVerticalMargins_handlesZeroViewport() {
        // Нулевой вьюпорт: maxCombined = 0, поля схлопываются в ноль (без деления на ноль).
        assertEquals(0.dp to 0.dp, clampVerticalMargins(50.dp, 50.dp, 0.dp))
    }
}
