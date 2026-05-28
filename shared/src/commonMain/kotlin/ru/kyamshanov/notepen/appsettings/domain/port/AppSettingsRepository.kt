package ru.kyamshanov.notepen.appsettings.domain.port

import kotlinx.coroutines.flow.StateFlow
import ru.kyamshanov.notepen.appsettings.domain.model.AppSettings

/**
 * Порт хранения [AppSettings]. Реализации:
 * - JVM (desktop) — JSON-файл в каталоге данных приложения;
 * - Android — JSON-файл в `context.filesDir`.
 *
 * Реализация должна быть thread-safe: настройки могут одновременно читаться из
 * нескольких корутин и переписываться UI-потоком.
 */
interface AppSettingsRepository {
    /** Текущее значение. Эмитит загруженные настройки и затем все изменения. */
    val settings: StateFlow<AppSettings>

    /** Загрузить настройки. При отсутствии файла/ошибке — вернуть defaults. */
    suspend fun load(): AppSettings

    /** Перезаписать настройки. */
    suspend fun save(settings: AppSettings)
}
