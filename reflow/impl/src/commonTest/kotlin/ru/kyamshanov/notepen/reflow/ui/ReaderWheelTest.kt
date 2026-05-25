package ru.kyamshanov.notepen.reflow.ui

import ru.kyamshanov.notepen.reflow.api.BuiltinReaderPresets
import ru.kyamshanov.notepen.reflow.api.ReaderPreset
import ru.kyamshanov.notepen.reflow.api.ReaderSettings
import ru.kyamshanov.notepen.reflow.api.StoredReaderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderWheelTest {
    @Test
    fun customPresetsFirst_thenBuiltins_thenTuner() {
        val custom = ReaderPreset(id = "c1", name = "Моё", settings = ReaderSettings())
        val stored = StoredReaderSettings(userPresets = listOf(custom))

        val elements = readerWheelElements(stored)

        // Кастомный пресет — первый и удаляемый.
        val first = elements.first()
        assertTrue(first is ReaderWheelElement.Preset && first.deletable)
        assertEquals("c1", first.key)

        // Тюнер — последний элемент колеса.
        assertTrue(elements.last() is ReaderWheelElement.Tuner)

        // Между ними — встроенные пресеты в порядке BuiltinReaderPresets.all, неудаляемые.
        val middle = elements.drop(1).dropLast(1)
        assertEquals(BuiltinReaderPresets.all.map { it.id }, middle.map { it.key })
        assertTrue(middle.all { it is ReaderWheelElement.Preset && !it.deletable })
    }

    @Test
    fun noCustomPresets_justBuiltinsAndTuner() {
        val elements = readerWheelElements(StoredReaderSettings())

        assertEquals(BuiltinReaderPresets.all.size + 1, elements.size)
        assertEquals(BuiltinReaderPresets.all.first().id, elements.first().key)
        assertTrue(elements.last() is ReaderWheelElement.Tuner)
    }
}
