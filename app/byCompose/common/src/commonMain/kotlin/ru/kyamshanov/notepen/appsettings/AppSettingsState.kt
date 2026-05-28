package ru.kyamshanov.notepen.appsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import ru.kyamshanov.notepen.appsettings.domain.model.AppSettings
import ru.kyamshanov.notepen.appsettings.domain.port.AppSettingsRepository

/**
 * Платформенно-специфичная фабрика [AppSettingsRepository]. JVM — JSON-файл в
 * каталоге данных приложения; Android — JSON-файл в `context.filesDir`.
 *
 * Реализация должна возвращать процесс-синглтон: репозиторий держит in-memory
 * стейт-флоу, который должны видеть все наблюдатели в приложении.
 */
expect fun defaultAppSettingsRepository(): AppSettingsRepository

/**
 * Глобальные настройки приложения как Compose-state. Первая композиция —
 * подписка на репозиторий; значение приходит из последнего эмита `settings`
 * (репозиторий сам эмитит дефолты при старте).
 */
@Composable
fun rememberAppSettings(repository: AppSettingsRepository = remember { defaultAppSettingsRepository() }): AppSettings {
    val state by repository.settings.collectAsState()
    return state
}
