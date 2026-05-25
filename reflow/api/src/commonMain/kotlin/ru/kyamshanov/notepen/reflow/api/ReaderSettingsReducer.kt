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
 *   новый кастомный пресет — текущие значения сидируются от уже применённых, а имя
 *   нумеруется от пресета-основы (см. [editActive]);
 * - правка при активном кастомном пресете обновляет его на месте, не плодя копий;
 * - переименование ([renamePreset]) трогает только имя кастома, не настройки;
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
     *   кастомный пресет [ReaderPreset] с id [newPresetId], помещает его первым в
     *   [StoredReaderSettings.userPresets] и делает активным.
     *
     * Имя нового кастома выводится из активного пресета-основы (имя встроенного по
     * [StoredReaderSettings.activePresetId]; без активного — [FALLBACK_FORK_BASE])
     * и нумеруется суффиксом `-N` ([forkName]): первый форк «Ночи» — «Ночь-1»,
     * следующий — «Ночь-2». Так пользователь видит, от чего ответвился пресет.
     *
     * В обоих случаях [settings] (после клампинга) становятся текущими.
     *
     * @param newPresetId стабильный id для нового кастома — генерируется снаружи
     *   (UI), чтобы переход оставался детерминированным
     */
    public fun editActive(
        stored: StoredReaderSettings,
        settings: ReaderSettings,
        newPresetId: String,
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
                    name = forkName(baseName = activeBaseName(stored), existing = stored.userPresets),
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
     * Переименовывает кастомный пресет [id] в [newName]. Переход чистый:
     * - встроенные пресеты не переименовываются (см. [isBuiltinReaderPresetId]) —
     *   состояние возвращается без изменений;
     * - [newName] обрезается по краям; пустое после обрезки имя игнорируется
     *   (возврат без изменений) — у пресета всегда есть видимое имя;
     * - если такое имя уже носит другой кастом, оно дополняется числовым суффиксом
     *   `-N` ([uniqueAmong]), чтобы имена в колесе оставались различимыми.
     *
     * Текущие настройки ([StoredReaderSettings.current]) и подсветка
     * ([StoredReaderSettings.activePresetId]) не меняются.
     */
    public fun renamePreset(
        stored: StoredReaderSettings,
        id: String,
        newName: String,
    ): StoredReaderSettings {
        if (isBuiltinReaderPresetId(id)) return stored
        val target = stored.userPresets.firstOrNull { it.id == id } ?: return stored
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return stored
        val others = stored.userPresets.filterNot { it.id == id }
        val resolved = uniqueAmong(trimmed, others)
        if (resolved == target.name) return stored
        return stored.copy(
            userPresets =
                stored.userPresets.map { preset ->
                    if (preset.id == id) preset.copy(name = resolved) else preset
                },
        )
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
     * Имя пресета-основы для нового форка: имя встроенного пресета, выбранного
     * сейчас ([StoredReaderSettings.activePresetId]); если активного нет (или это
     * не встроенный) — [FALLBACK_FORK_BASE].
     */
    private fun activeBaseName(stored: StoredReaderSettings): String =
        BuiltinReaderPresets.all
            .firstOrNull { it.id == stored.activePresetId }
            ?.name
            ?: FALLBACK_FORK_BASE

    /**
     * Имя нового форка: «[baseName]-N», где N — наименьшее положительное целое,
     * дающее имя, которого ещё нет среди [existing]. Суффикс ставится всегда (даже
     * у первого форка), поэтому первый форк «Ночи» — «Ночь-1», следующий — «Ночь-2».
     */
    private fun forkName(
        baseName: String,
        existing: List<ReaderPreset>,
    ): String {
        val taken = existing.mapTo(mutableSetOf()) { it.name }
        var n = 1
        while ("$baseName-$n" in taken) n++
        return "$baseName-$n"
    }

    /**
     * Возвращает [name], если его не носит ни один из [others], иначе добавляет
     * наименьший свободный числовой суффикс «-N» (-2, -3, …). Используется при
     * переименовании, чтобы не плодить пресеты-тёзки.
     */
    private fun uniqueAmong(
        name: String,
        others: List<ReaderPreset>,
    ): String {
        val taken = others.mapTo(mutableSetOf()) { it.name }
        if (name !in taken) return name
        var n = 2
        while ("$name-$n" in taken) n++
        return "$name-$n"
    }

    /** Имя-основа форка, когда нет активного встроенного пресета. */
    private const val FALLBACK_FORK_BASE = "Моё"
}
