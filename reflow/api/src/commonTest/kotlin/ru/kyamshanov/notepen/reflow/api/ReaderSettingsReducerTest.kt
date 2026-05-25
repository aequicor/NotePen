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

        val next = ReaderSettingsReducer.editActive(stored, edited, newPresetId = "id-1")

        assertEquals(edited, next.current)
        assertEquals(1, next.userPresets.size)
        // Кастом — первый в списке и активен.
        assertEquals("id-1", next.userPresets.first().id)
        assertEquals("id-1", next.activePresetId)
        // Имя нумеруется от пресета-основы (активный встроенный «Комфорт»).
        assertEquals("Комфорт-1", next.userPresets.first().name)
        assertEquals(edited, next.userPresets.first().settings)
    }

    @Test
    fun editActive_withoutActivePreset_alsoForksCustom() {
        val stored = StoredReaderSettings(activePresetId = null)
        val edited = stored.current.copy(theme = ReaderTheme.SEPIA)

        val next = ReaderSettingsReducer.editActive(stored, edited, newPresetId = "id-1")

        assertEquals("id-1", next.activePresetId)
        assertEquals(1, next.userPresets.size)
        // Без активной основы имя падает на запасное «Моё-1».
        assertEquals("Моё-1", next.userPresets.first().name)
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

        val next = ReaderSettingsReducer.editActive(stored, edited, newPresetId = "new")

        assertEquals(listOf("new", "old"), next.userPresets.map { it.id })
        assertEquals("new", next.activePresetId)
        assertEquals("Ночь-1", next.userPresets.first().name)
    }

    @Test
    fun editActive_onActiveCustom_updatesInPlaceWithoutDuplicating() {
        val custom = ReaderPreset(id = "c1", name = "Ночь-1", settings = ReaderSettings(fontSizeSp = 18f))
        val stored = StoredReaderSettings(userPresets = listOf(custom), activePresetId = "c1")
        val edited = custom.settings.copy(fontSizeSp = 21f, bionic = true)

        val next = ReaderSettingsReducer.editActive(stored, edited, newPresetId = "should-not-be-used")

        // Тот же кастом обновлён на месте, новый не создан.
        assertEquals(1, next.userPresets.size)
        assertEquals("c1", next.activePresetId)
        assertEquals(edited, next.userPresets.first().settings)
        assertEquals("Ночь-1", next.userPresets.first().name)
        assertEquals(edited, next.current)
    }

    @Test
    fun editActive_twoForksOfSameBuiltin_getSequentialSuffixes() {
        // Первый форк «Ночи».
        val first =
            ReaderSettingsReducer.editActive(
                StoredReaderSettings(activePresetId = BuiltinReaderPresets.night.id),
                ReaderSettings(fontSizeSp = 20f),
                newPresetId = "id-1",
            )
        // Повторно встаём на «Ночь» и форкаем снова.
        val onNightAgain = ReaderSettingsReducer.applyPreset(first, BuiltinReaderPresets.night)
        val second =
            ReaderSettingsReducer.editActive(
                onNightAgain,
                onNightAgain.current.copy(fontSizeSp = 22f),
                newPresetId = "id-2",
            )

        val names = second.userPresets.map { it.name }.toSet()
        assertEquals(setOf("Ночь-1", "Ночь-2"), names)
    }

    @Test
    fun editActive_forksFromDifferentBuiltins_useEachBaseName() {
        val first =
            ReaderSettingsReducer.editActive(
                StoredReaderSettings(activePresetId = BuiltinReaderPresets.comfort.id),
                ReaderSettings(fontSizeSp = 20f),
                newPresetId = "id-1",
            )
        val second =
            ReaderSettingsReducer.editActive(
                ReaderSettingsReducer.applyPreset(first, BuiltinReaderPresets.night),
                first.current.copy(fontSizeSp = 22f),
                newPresetId = "id-2",
            )
        val third =
            ReaderSettingsReducer.editActive(
                ReaderSettingsReducer.applyPreset(second, BuiltinReaderPresets.compact),
                ReaderSettings(fontSizeSp = 23f),
                newPresetId = "id-3",
            )

        assertEquals(setOf("Комфорт-1", "Ночь-1", "Компактно-1"), third.userPresets.map { it.name }.toSet())
    }

    @Test
    fun editActive_coercesOutOfRangeValues() {
        val stored = StoredReaderSettings()

        val next = ReaderSettingsReducer.editActive(stored, stored.current.copy(fontSizeSp = 999f), "id-1")

        assertEquals(ReaderSettings.MAX_FONT_SP, next.current.fontSizeSp)
        assertEquals(ReaderSettings.MAX_FONT_SP, next.userPresets.first().settings.fontSizeSp)
    }

    @Test
    fun renamePreset_custom_changesNameOnly() {
        val custom = ReaderPreset(id = "c1", name = "Ночь-1", settings = ReaderSettings(fontSizeSp = 18f))
        val current = ReaderSettings(fontSizeSp = 18f, theme = ReaderTheme.SEPIA)
        val stored = StoredReaderSettings(current = current, userPresets = listOf(custom), activePresetId = "c1")

        val next = ReaderSettingsReducer.renamePreset(stored, "c1", "Вечер")

        assertEquals("Вечер", next.userPresets.first().name)
        // Настройки и подсветка не трогаются.
        assertEquals(custom.settings, next.userPresets.first().settings)
        assertEquals("c1", next.activePresetId)
        assertEquals(current, next.current)
    }

    @Test
    fun renamePreset_trimsWhitespace() {
        val custom = ReaderPreset(id = "c1", name = "Старое", settings = ReaderSettings())
        val stored = StoredReaderSettings(userPresets = listOf(custom))

        val next = ReaderSettingsReducer.renamePreset(stored, "c1", "  Вечер  ")

        assertEquals("Вечер", next.userPresets.first().name)
    }

    @Test
    fun renamePreset_blankName_isNoOp() {
        val custom = ReaderPreset(id = "c1", name = "Старое", settings = ReaderSettings())
        val stored = StoredReaderSettings(userPresets = listOf(custom))

        val next = ReaderSettingsReducer.renamePreset(stored, "c1", "   ")

        assertSame(stored, next)
    }

    @Test
    fun renamePreset_collidingName_getsSuffix() {
        val a = ReaderPreset(id = "a", name = "Вечер", settings = ReaderSettings())
        val b = ReaderPreset(id = "b", name = "Старое", settings = ReaderSettings())
        val stored = StoredReaderSettings(userPresets = listOf(a, b))

        // Переименование b в уже занятое «Вечер» уводит его в «Вечер-2».
        val next = ReaderSettingsReducer.renamePreset(stored, "b", "Вечер")

        assertEquals("Вечер-2", next.userPresets.first { it.id == "b" }.name)
        assertEquals("Вечер", next.userPresets.first { it.id == "a" }.name)
    }

    @Test
    fun renamePreset_sameName_isNoOp() {
        val custom = ReaderPreset(id = "c1", name = "Вечер", settings = ReaderSettings())
        val stored = StoredReaderSettings(userPresets = listOf(custom))

        val next = ReaderSettingsReducer.renamePreset(stored, "c1", "Вечер")

        assertSame(stored, next)
    }

    @Test
    fun renamePreset_builtinId_isNoOp() {
        val custom = ReaderPreset(id = "c1", name = "Старое", settings = ReaderSettings())
        val stored = StoredReaderSettings(userPresets = listOf(custom))

        val next = ReaderSettingsReducer.renamePreset(stored, BuiltinReaderPresets.night.id, "Ночь!")

        assertSame(stored, next)
    }

    @Test
    fun renamePreset_unknownId_isNoOp() {
        val custom = ReaderPreset(id = "c1", name = "Старое", settings = ReaderSettings())
        val stored = StoredReaderSettings(userPresets = listOf(custom))

        val next = ReaderSettingsReducer.renamePreset(stored, "nope", "Вечер")

        assertSame(stored, next)
    }

    @Test
    fun deletePreset_active_clearsActiveIdAndKeepsCurrent() {
        val custom = ReaderPreset(id = "c1", name = "Ночь-1", settings = ReaderSettings(fontSizeSp = 18f))
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
        val custom = ReaderPreset(id = "c1", name = "Ночь-1", settings = ReaderSettings())
        val stored =
            StoredReaderSettings(
                userPresets = listOf(custom),
                activePresetId = BuiltinReaderPresets.night.id,
            )

        val next = ReaderSettingsReducer.deletePreset(stored, BuiltinReaderPresets.night.id)

        assertSame(stored, next)
    }
}
