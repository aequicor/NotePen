package ru.kyamshanov.notepen.reflow.api

/**
 * Чистые переходы состояния [StoredReaderSettings], которыми управляет панель
 * ридера. Вынесено отдельным объектом без зависимостей от Compose — чтобы
 * поведение пресетов покрывалось unit-тестами, а слой отображения только вызывал
 * переходы и хранил результат.
 *
 * Объект полностью детерминирован: новые идентификаторы пресетов он не генерирует,
 * а получает параметром от вызывающего ([editActive]) — это и делает переходы
 * воспроизводимыми в тестах.
 *
 * Главные инварианты:
 * - правка ползунка при активном встроенном пресете (или без активного) форкает
 *   новый кастомный пресет — текущие значения сидируются от уже применённых;
 * - правка при активном кастомном пресете обновляет его на месте, не плодя копий;
 * - удаление активного кастома снимает подсветку ([StoredReaderSettings.activePresetId]
 *   = `null`), но не трогает текущие настройки ([StoredReaderSettings.current]).
 */
public object ReaderSettingsReducer {
    /**
     * Применяет встроенный/именованный [preset]: подставляет его настройки как
     * текущие и подсвечивает пресет.
     */
    public fun applyPreset(
        stored: StoredReaderSettings,
        preset: ReaderPreset,
    ): StoredReaderSettings =
        stored.copy(
            current = preset.settings.coerced(),
            activePresetId = preset.id,
        )

    /**
     * Фиксирует ручную правку с семантикой «форк/обновление кастома»:
     * - если активен существующий кастомный пресет (его id есть в
     *   [StoredReaderSettings.userPresets]) — обновляет его настройки на месте и
     *   оставляет активным;
     * - иначе (активен встроенный пресет или активного нет) — создаёт новый
     *   кастомный пресет [ReaderPreset] с id [newPresetId] и уникальным именем на
     *   основе [defaultName], помещает его первым в [StoredReaderSettings.userPresets]
     *   и делает активным.
     *
     * В обоих случаях [settings] (после клампинга) становятся текущими.
     *
     * @param newPresetId стабильный id для нового кастома — генерируется снаружи
     *   (UI), чтобы переход оставался детерминированным
     * @param defaultName базовое имя нового кастома; уникальность среди уже
     *   существующих кастомов обеспечивается суффиксом (« 2», « 3», …)
     */
    public fun editActive(
        stored: StoredReaderSettings,
        settings: ReaderSettings,
        newPresetId: String,
        defaultName: String,
    ): StoredReaderSettings {
        val coerced = settings.coerced()
        val activeCustom = stored.userPresets.firstOrNull { it.id == stored.activePresetId }
        return if (activeCustom != null) {
            stored.copy(
                current = coerced,
                userPresets =
                    stored.userPresets.map { preset ->
                        if (preset.id == activeCustom.id) preset.copy(settings = coerced) else preset
                    },
            )
        } else {
            val new =
                ReaderPreset(
                    id = newPresetId,
                    name = uniqueName(defaultName, stored.userPresets),
                    settings = coerced,
                )
            stored.copy(
                current = coerced,
                userPresets = listOf(new) + stored.userPresets,
                activePresetId = newPresetId,
            )
        }
    }

    /**
     * Удаляет кастомный пресет [id] из [StoredReaderSettings.userPresets]. Если он
     * был активным — снимает подсветку ([StoredReaderSettings.activePresetId] =
     * `null`), но текущие настройки ([StoredReaderSettings.current]) не меняет,
     * чтобы вид не «прыгал» при удалении.
     *
     * Встроенные пресеты не удаляются: если [id] — встроенный
     * (см. [isBuiltinReaderPresetId]), состояние возвращается без изменений.
     */
    public fun deletePreset(
        stored: StoredReaderSettings,
        id: String,
    ): StoredReaderSettings {
        if (isBuiltinReaderPresetId(id)) return stored
        return stored.copy(
            userPresets = stored.userPresets.filterNot { it.id == id },
            activePresetId = if (stored.activePresetId == id) null else stored.activePresetId,
        )
    }

    /**
     * Возвращает [base], если такого имени ещё нет среди [existing], иначе
     * добавляет наименьший свободный числовой суффикс: «Моё», «Моё 2», «Моё 3», …
     */
    private fun uniqueName(
        base: String,
        existing: List<ReaderPreset>,
    ): String {
        val taken = existing.mapTo(mutableSetOf()) { it.name }
        if (base !in taken) return base
        var n = 2
        while ("$base $n" in taken) n++
        return "$base $n"
    }
}
