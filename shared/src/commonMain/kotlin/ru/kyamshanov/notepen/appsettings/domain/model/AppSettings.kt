package ru.kyamshanov.notepen.appsettings.domain.model

import kotlinx.serialization.Serializable

/**
 * Режим ориентации экрана, не зависящий от физического поворота устройства.
 * `AUTO` — следовать системе (датчик/автоповорот); `LANDSCAPE`/`PORTRAIT` —
 * залочить ориентацию (на Android с разрешённым переворотом на 180°). На
 * десктопе понятия «ориентация окна» нет — там применение no-op.
 */
@Serializable
enum class ScreenOrientationMode { AUTO, LANDSCAPE, PORTRAIT }

/**
 * Глобальные настройки приложения, не зависящие от документа или экрана.
 *
 * @property alwaysOnDisplay не гасить экран, пока приложение активно
 *  (Android: `View.keepScreenOn`; Desktop: no-op). По умолчанию включено.
 * @property openLibraryAtStartup автоматически подключать сохранённые
 *  библиотеки ([ru.kyamshanov.notepen.library.api.LibraryRegistry.savedConnections])
 *  при запуске приложения. По умолчанию выключено — старый JSON без этого поля
 *  десериализуется в `false` (обратная совместимость).
 * @property orientation ориентация экрана в режиме редактора (глобальная, для всех
 *  документов). По умолчанию [ScreenOrientationMode.AUTO] — старый JSON без поля
 *  десериализуется в `AUTO`. Режим чтения хранит свою ориентацию отдельно
 *  (`ReaderSettings.orientation`).
 */
@Serializable
data class AppSettings(
    val alwaysOnDisplay: Boolean = true,
    val openLibraryAtStartup: Boolean = false,
    val orientation: ScreenOrientationMode = ScreenOrientationMode.AUTO,
)
