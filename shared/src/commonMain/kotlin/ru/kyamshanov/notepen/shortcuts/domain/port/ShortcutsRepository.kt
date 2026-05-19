package ru.kyamshanov.notepen.shortcuts.domain.port

import ru.kyamshanov.notepen.shortcuts.domain.model.ShortcutsSettings

/**
 * Порт хранения пользовательских настроек шорткатов.
 *
 * Реализации:
 * - JVM (desktop) — JSON-файл в пользовательском конфиг-каталоге;
 * - прочие платформы — in-memory NoOp, возвращающий значения по умолчанию.
 *
 * Реализация должна быть thread-safe для одновременного `load`/`save` из
 * разных корутин: настройки могут меняться из UI thread'а, а грузиться при
 * старте приложения с произвольного диспетчера.
 */
interface ShortcutsRepository {
    /** Загрузить настройки. При отсутствии файла/ошибке — вернуть defaults. */
    suspend fun load(): ShortcutsSettings

    /** Перезаписать настройки. */
    suspend fun save(settings: ShortcutsSettings)
}
