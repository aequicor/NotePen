package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.ui.CachedLayout
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReflowLayoutBinaryFormatTest {
    private fun roundtrip(layout: CachedLayout): CachedLayout {
        val bytes =
            ByteArrayOutputStream().apply {
                ReflowLayoutBinaryFormat.write(layout, this)
            }.toByteArray()
        return ReflowLayoutBinaryFormat.read(ByteArrayInputStream(bytes))
    }

    @Test
    fun emptyLayoutRoundtrips() {
        val empty = CachedLayout(textHeights = emptyMap(), textLineBottoms = emptyMap())
        val out = roundtrip(empty)
        assertEquals(empty, out)
    }

    @Test
    fun heightsRoundtrip() {
        val layout =
            CachedLayout(
                textHeights = mapOf(0 to 120, 7 to 4096, 9999 to 1),
                textLineBottoms = emptyMap(),
            )
        assertEquals(layout, roundtrip(layout))
    }

    @Test
    fun lineBottomsRoundtrip() {
        val layout =
            CachedLayout(
                textHeights = mapOf(0 to 60, 1 to 180),
                textLineBottoms =
                    mapOf(
                        0 to listOf(60f),
                        1 to listOf(60f, 120f, 180f),
                    ),
            )
        assertEquals(layout, roundtrip(layout))
    }

    @Test
    fun mixedLayoutWithGapsInIndicesRoundtrip() {
        val layout =
            CachedLayout(
                textHeights = (0..50 step 7).associateWith { it * 10 + 80 },
                textLineBottoms =
                    mapOf(
                        7 to listOf(40f, 80f),
                        21 to listOf(40f, 80f, 120f, 160f),
                        35 to emptyList(),
                    ),
                figureHeights = mapOf(3 to 1828, 14 to 940),
            )
        assertEquals(layout, roundtrip(layout))
    }

    @Test
    fun figureHeightsRoundtrip() {
        val layout =
            CachedLayout(
                textHeights = emptyMap(),
                textLineBottoms = emptyMap(),
                figureHeights = mapOf(0 to 100, 5 to 250, 99 to 9001),
            )
        assertEquals(layout, roundtrip(layout))
    }

    @Test
    fun badMagicFails() {
        val garbage = ByteArray(16) { 0 }
        assertFailsWith<IllegalArgumentException> {
            ReflowLayoutBinaryFormat.read(ByteArrayInputStream(garbage))
        }
    }
}
