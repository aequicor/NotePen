package ru.kyamshanov.notepen.appsettings.domain.model

import kotlinx.serialization.Serializable

/**
 * Глобальные настройки приложения, не зависящие от документа или экрана.
 *
 * @property alwaysOnDisplay не гасить экран, пока приложение активно
 *  (Android: `View.keepScreenOn`; Desktop: no-op). По умолчанию включено.
 */
@Serializable
data class AppSettings(
    val alwaysOnDisplay: Boolean = true,
)
