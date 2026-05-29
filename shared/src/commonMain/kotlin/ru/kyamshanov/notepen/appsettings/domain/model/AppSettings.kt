package ru.kyamshanov.notepen.appsettings.domain.model

import kotlinx.serialization.Serializable

/**
 * Глобальные настройки приложения, не зависящие от документа или экрана.
 *
 * @property alwaysOnDisplay не гасить экран, пока приложение активно
 *  (Android: `View.keepScreenOn`; Desktop: no-op). По умолчанию включено.
 * @property openLibraryAtStartup автоматически подключать сохранённые
 *  библиотеки ([ru.kyamshanov.notepen.library.api.LibraryRegistry.savedConnections])
 *  при запуске приложения. По умолчанию выключено — старый JSON без этого поля
 *  десериализуется в `false` (обратная совместимость).
 */
@Serializable
data class AppSettings(
    val alwaysOnDisplay: Boolean = true,
    val openLibraryAtStartup: Boolean = false,
)
