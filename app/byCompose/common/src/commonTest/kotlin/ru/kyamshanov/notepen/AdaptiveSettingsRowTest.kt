package ru.kyamshanov.notepen

import kotlin.test.Test
import kotlin.test.assertContentEquals

class AdaptiveSettingsRowTest {
    @Test
    fun `greedyFit - all slots fit`() {
        // TC-01: budget=600-32=568; 100+120+12+80+12=324 <= 568
        val fits =
            greedyFit(
                naturalWidths = listOf(100, 120, 80),
                maxWidth = 600,
                gapPx = 12,
                paddingPx = 32,
                iconButtonWidthPx = 40,
            )
        assertContentEquals(booleanArrayOf(true, true, true), fits)
    }

    @Test
    fun `greedyFit - no slot fits`() {
        // TC-02: budget=50-32=18; even first slot (100) exceeds budget
        val fits =
            greedyFit(
                naturalWidths = listOf(100, 120, 80),
                maxWidth = 50,
                gapPx = 12,
                paddingPx = 32,
                iconButtonWidthPx = 40,
            )
        assertContentEquals(booleanArrayOf(false, false, false), fits)
    }

    @Test
    fun `greedyFit - only first slot fits`() {
        // TC-03: budget=200-32=168; slot0=100 fits(budget=68); slot1=200+12=212 > 68
        val fits =
            greedyFit(
                naturalWidths = listOf(100, 200, 80),
                maxWidth = 200,
                gapPx = 12,
                paddingPx = 32,
                iconButtonWidthPx = 40,
            )
        assertContentEquals(booleanArrayOf(true, false, false), fits)
    }

    @Test
    fun `greedyFit - first two slots fit`() {
        // TC-04: budget=350-32=318; slot0=100(budget=218); slot1=120+12=132(budget=86); slot2=200+12=212 > 86
        val fits =
            greedyFit(
                naturalWidths = listOf(100, 120, 200),
                maxWidth = 350,
                gapPx = 12,
                paddingPx = 32,
                iconButtonWidthPx = 40,
            )
        assertContentEquals(booleanArrayOf(true, true, false), fits)
    }

    @Test
    fun `greedyFit - exact boundary first fits second collapses`() {
        // TC-05: budget=200-32=168; slot0=100 fits(budget=68); slot1=100+12=112 > 68 → false
        val fits =
            greedyFit(
                naturalWidths = listOf(100, 100),
                maxWidth = 200,
                gapPx = 12,
                paddingPx = 32,
                iconButtonWidthPx = 40,
            )
        assertContentEquals(booleanArrayOf(true, false), fits)
    }

    @Test
    fun `greedyFit - empty list returns empty array`() {
        val fits =
            greedyFit(
                naturalWidths = emptyList(),
                maxWidth = 600,
                gapPx = 12,
                paddingPx = 32,
                iconButtonWidthPx = 40,
            )
        assertContentEquals(booleanArrayOf(), fits)
    }

    @Test
    fun `greedyFit - single slot fits exactly`() {
        // budget=100-32=68; slot0=68 fits exactly
        val fits =
            greedyFit(
                naturalWidths = listOf(68),
                maxWidth = 100,
                gapPx = 12,
                paddingPx = 32,
                iconButtonWidthPx = 40,
            )
        assertContentEquals(booleanArrayOf(true), fits)
    }

    @Test
    fun `greedyFit - single slot one px too wide collapses`() {
        // budget=100-32=68; slot0=69 > 68 → false
        val fits =
            greedyFit(
                naturalWidths = listOf(69),
                maxWidth = 100,
                gapPx = 12,
                paddingPx = 32,
                iconButtonWidthPx = 40,
            )
        assertContentEquals(booleanArrayOf(false), fits)
    }
}
