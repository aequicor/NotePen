package ru.kyamshanov.notepen.reflow.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ReaderSettingsReducerTest {
    @Test
    fun applyPreset_setsCurrentAndActiveId_andPreservesMy() {
        val my = ReaderSettings(fontSizeSp = 23f)
        val stored = StoredReaderSettings(current = ReaderSettings(), my = my, activePresetId = null)

        val next = ReaderSettingsReducer.applyPreset(stored, BuiltinReaderPresets.night)

        assertEquals(BuiltinReaderPresets.night.settings, next.current)
        assertEquals(BuiltinReaderPresets.night.id, next.activePresetId)
        // Ручные правки «Моё» не теряются при выборе встроенного пресета.
        assertEquals(my, next.my)
    }

    @Test
    fun edit_storesAsMyAndClearsActivePreset() {
        val stored = StoredReaderSettings(activePresetId = BuiltinReaderPresets.comfort.id)
        val edited = stored.current.copy(fontSizeSp = 22f, bionic = true)

        val next = ReaderSettingsReducer.edit(stored, edited)

        assertEquals(edited, next.current)
        assertEquals(edited, next.my)
        assertNull(next.activePresetId)
    }

    @Test
    fun edit_coercesOutOfRangeValues() {
        val stored = StoredReaderSettings()

        val next = ReaderSettingsReducer.edit(stored, stored.current.copy(fontSizeSp = 999f))

        assertEquals(ReaderSettings.MAX_FONT_SP, next.current.fontSizeSp)
        assertEquals(ReaderSettings.MAX_FONT_SP, next.my?.fontSizeSp)
    }

    @Test
    fun restoreMy_withoutMy_isNoOp() {
        val stored = StoredReaderSettings(activePresetId = BuiltinReaderPresets.night.id, my = null)

        val next = ReaderSettingsReducer.restoreMy(stored)

        assertSame(stored, next)
    }

    @Test
    fun restoreMy_appliesMyAndClearsActivePreset() {
        val my = ReaderSettings(fontSizeSp = 18f, theme = ReaderTheme.SEPIA)
        val stored =
            StoredReaderSettings(
                current = BuiltinReaderPresets.night.settings,
                my = my,
                activePresetId = BuiltinReaderPresets.night.id,
            )

        val next = ReaderSettingsReducer.restoreMy(stored)

        assertEquals(my, next.current)
        assertNull(next.activePresetId)
        assertEquals(my, next.my)
    }
}
