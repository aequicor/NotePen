package ru.kyamshanov.notepen.reflow.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReaderSettingsReducerTest {
    @Test
    fun applyPreset_setsCurrentAndActiveId() {
        val stored = StoredReaderSettings(current = ReaderSettings(), activePresetId = null)

        val next = ReaderSettingsReducer.applyPreset(stored, BuiltinReaderPresets.night)

        assertEquals(BuiltinReaderPresets.night.settings, next.current)
        assertEquals(BuiltinReaderPresets.night.id, next.activePresetId)
    }

    @Test
    fun editActive_fromBuiltin_forksCustomFirstAndActive() {
        val stored = StoredReaderSettings(activePresetId = BuiltinReaderPresets.comfort.id)
        val edited = stored.current.copy(fontSizeSp = 22f, bionic = true)

        val next = ReaderSettingsReducer.editActive(stored, edited, newPresetId = "id-1", defaultName = "Моё")

        assertEquals(edited, next.current)
        assertEquals(1, next.userPresets.size)
        // Кастом — первый в списке и активен.
        assertEquals("id-1", next.userPresets.first().id)
        assertEquals("id-1", next.activePresetId)
        assertEquals("Моё", next.userPresets.first().name)
        assertEquals(edited, next.userPresets.first().settings)
    }

    @Test
    fun editActive_withoutActivePreset_alsoForksCustom() {
        val stored = StoredReaderSettings(activePresetId = null)
        val edited = stored.current.copy(theme = ReaderTheme.SEPIA)

        val next = ReaderSettingsReducer.editActive(stored, edited, newPresetId = "id-1", defaultName = "Моё")

        assertEquals("id-1", next.activePresetId)
        assertEquals(1, next.userPresets.size)
        assertEquals(edited, next.userPresets.first().settings)
    }

    @Test
    fun editActive_putsNewCustomBeforeExistingOnes() {
        val existing = ReaderPreset(id = "old", name = "Старое", settings = ReaderSettings(fontSizeSp = 15f))
        val stored =
            StoredReaderSettings(
                userPresets = listOf(existing),
                activePresetId = BuiltinReaderPresets.night.id,
            )
        val edited = stored.current.copy(fontSizeSp = 20f)

        val next = ReaderSettingsReducer.editActive(stored, edited, newPresetId = "new", defaultName = "Моё")

        assertEquals(listOf("new", "old"), next.userPresets.map { it.id })
        assertEquals("new", next.activePresetId)
    }

    @Test
    fun editActive_onActiveCustom_updatesInPlaceWithoutDuplicating() {
        val custom = ReaderPreset(id = "c1", name = "Моё", settings = ReaderSettings(fontSizeSp = 18f))
        val stored = StoredReaderSettings(userPresets = listOf(custom), activePresetId = "c1")
        val edited = custom.settings.copy(fontSizeSp = 21f, bionic = true)

        val next = ReaderSettingsReducer.editActive(stored, edited, newPresetId = "should-not-be-used", defaultName = "Моё")

        // Тот же кастом обновлён на месте, новый не создан.
        assertEquals(1, next.userPresets.size)
        assertEquals("c1", next.activePresetId)
        assertEquals(edited, next.userPresets.first().settings)
        assertEquals("Моё", next.userPresets.first().name)
        assertEquals(edited, next.current)
    }

    @Test
    fun editActive_namesAreUnique() {
        val first =
            ReaderSettingsReducer.editActive(
                StoredReaderSettings(activePresetId = BuiltinReaderPresets.comfort.id),
                ReaderSettings(fontSizeSp = 20f),
                newPresetId = "id-1",
                defaultName = "Моё",
            )
        // Переключаемся на встроенный, затем форкаем второй кастом.
        val onBuiltin = ReaderSettingsReducer.applyPreset(first, BuiltinReaderPresets.night)
        val second =
            ReaderSettingsReducer.editActive(
                onBuiltin,
                onBuiltin.current.copy(fontSizeSp = 22f),
                newPresetId = "id-2",
                defaultName = "Моё",
            )
        val third =
            ReaderSettingsReducer.editActive(
                ReaderSettingsReducer.applyPreset(second, BuiltinReaderPresets.compact),
                ReaderSettings(fontSizeSp = 23f),
                newPresetId = "id-3",
                defaultName = "Моё",
            )

        val names = third.userPresets.map { it.name }
        assertEquals(setOf("Моё", "Моё 2", "Моё 3"), names.toSet())
        // Все имена различны.
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun editActive_coercesOutOfRangeValues() {
        val stored = StoredReaderSettings()

        val next = ReaderSettingsReducer.editActive(stored, stored.current.copy(fontSizeSp = 999f), "id-1", "Моё")

        assertEquals(ReaderSettings.MAX_FONT_SP, next.current.fontSizeSp)
        assertEquals(ReaderSettings.MAX_FONT_SP, next.userPresets.first().settings.fontSizeSp)
    }

    @Test
    fun deletePreset_active_clearsActiveIdAndKeepsCurrent() {
        val custom = ReaderPreset(id = "c1", name = "Моё", settings = ReaderSettings(fontSizeSp = 18f))
        val current = ReaderSettings(fontSizeSp = 18f, theme = ReaderTheme.SEPIA)
        val stored = StoredReaderSettings(current = current, userPresets = listOf(custom), activePresetId = "c1")

        val next = ReaderSettingsReducer.deletePreset(stored, "c1")

        assertTrue(next.userPresets.isEmpty())
        assertNull(next.activePresetId)
        // Удаление не дёргает текущие настройки.
        assertEquals(current, next.current)
    }

    @Test
    fun deletePreset_inactive_keepsActiveId() {
        val a = ReaderPreset(id = "a", name = "A", settings = ReaderSettings())
        val b = ReaderPreset(id = "b", name = "B", settings = ReaderSettings())
        val stored = StoredReaderSettings(userPresets = listOf(a, b), activePresetId = "a")

        val next = ReaderSettingsReducer.deletePreset(stored, "b")

        assertEquals(listOf("a"), next.userPresets.map { it.id })
        assertEquals("a", next.activePresetId)
    }

    @Test
    fun deletePreset_builtinId_isNoOp() {
        val custom = ReaderPreset(id = "c1", name = "Моё", settings = ReaderSettings())
        val stored =
            StoredReaderSettings(
                userPresets = listOf(custom),
                activePresetId = BuiltinReaderPresets.night.id,
            )

        val next = ReaderSettingsReducer.deletePreset(stored, BuiltinReaderPresets.night.id)

        assertSame(stored, next)
    }
}
