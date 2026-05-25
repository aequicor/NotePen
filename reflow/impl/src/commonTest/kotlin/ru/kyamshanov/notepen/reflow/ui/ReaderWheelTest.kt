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
    fun customPresetsFirst_thenBuiltins() {
        val custom = ReaderPreset(id = "c1", name = "Моё", settings = ReaderSettings())
        val stored = StoredReaderSettings(userPresets = listOf(custom))

        val elements = readerWheelElements(stored)

        // Кастомный пресет — первый и удаляемый.
        val first = elements.first()
        assertTrue(first is ReaderWheelElement.Preset && first.deletable)
        assertEquals("c1", first.key)

        // Далее — встроенные пресеты в порядке BuiltinReaderPresets.all, неудаляемые.
        // Тюнер «Настроить» в колесо больше не входит — он закреплён отдельной кнопкой.
        val rest = elements.drop(1)
        assertEquals(BuiltinReaderPresets.all.map { it.id }, rest.map { it.key })
        assertTrue(rest.all { it is ReaderWheelElement.Preset && !it.deletable })
    }

    @Test
    fun noCustomPresets_justBuiltins() {
        val elements = readerWheelElements(StoredReaderSettings())

        assertEquals(BuiltinReaderPresets.all.size, elements.size)
        assertEquals(BuiltinReaderPresets.all.first().id, elements.first().key)
        assertTrue(elements.all { it is ReaderWheelElement.Preset })
    }
}
