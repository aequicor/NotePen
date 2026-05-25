package ru.kyamshanov.notepen.reflow.api

/**
 * Чистые переходы состояния [StoredReaderSettings], которыми управляет панель
 * ридера. Вынесено отдельным объектом без зависимостей от Compose — чтобы
 * поведение пресетов и личного пресета «Моё» покрывалось unit-тестами, а слой
 * отображения только вызывал переходы и хранил результат.
 *
 * Главные инварианты:
 * - выбор встроенного пресета не теряет ручные правки (поле [StoredReaderSettings.my]);
 * - любая ручная правка ползунка становится новым снимком «Моё» и снимает
 *   подсветку активного пресета ([StoredReaderSettings.activePresetId] = `null`).
 */
public object ReaderSettingsReducer {
    /**
     * Применяет встроенный/именованный [preset]: подставляет его настройки как
     * текущие и подсвечивает пресет. Личный пресет «Моё» сохраняется нетронутым,
     * поэтому к ручным правкам всегда можно вернуться через [restoreMy].
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
     * Фиксирует ручную правку: [settings] становятся и текущими, и снимком «Моё»
     * (см. [StoredReaderSettings.my]); подсветка активного пресета снимается.
     */
    public fun edit(
        stored: StoredReaderSettings,
        settings: ReaderSettings,
    ): StoredReaderSettings {
        val coerced = settings.coerced()
        return stored.copy(
            current = coerced,
            my = coerced,
            activePresetId = null,
        )
    }

    /**
     * Возвращает текущими настройки личного пресета «Моё». Если пользователь
     * ещё ничего не подкручивал ([StoredReaderSettings.my] == `null`), состояние
     * не меняется.
     */
    public fun restoreMy(stored: StoredReaderSettings): StoredReaderSettings {
        val my = stored.my ?: return stored
        return stored.copy(current = my, activePresetId = null)
    }
}
