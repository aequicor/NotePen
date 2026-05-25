package ru.kyamshanov.notepen.reflow.ui

import ru.kyamshanov.notepen.reflow.assertAlmostEquals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadingErgonomicsTest {
    @Test
    fun `dim is zero before threshold and ramps to max`() {
        assertEquals(0f, ReadingErgonomics.dimAlpha(0L, 1000L, 1000L, 0.12f))
        assertEquals(0f, ReadingErgonomics.dimAlpha(1000L, 1000L, 1000L, 0.12f))
        assertAlmostEquals(0.06f, ReadingErgonomics.dimAlpha(1500L, 1000L, 1000L, 0.12f))
        assertAlmostEquals(0.12f, ReadingErgonomics.dimAlpha(2000L, 1000L, 1000L, 0.12f))
        assertAlmostEquals(0.12f, ReadingErgonomics.dimAlpha(99_999L, 1000L, 1000L, 0.12f))
    }

    @Test
    fun `rhythm break lands every Nth block`() {
        assertTrue(ReadingErgonomics.isRhythmBreak(9, 10))
        assertFalse(ReadingErgonomics.isRhythmBreak(8, 10))
        assertFalse(ReadingErgonomics.isRhythmBreak(5, 0))
    }
}
