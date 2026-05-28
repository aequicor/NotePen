package ru.kyamshanov.notepen.appsettings

import ru.kyamshanov.notepen.AppContextHolder
import ru.kyamshanov.notepen.appsettings.data.AndroidAppSettingsRepository
import ru.kyamshanov.notepen.appsettings.domain.port.AppSettingsRepository

private val instance: AppSettingsRepository by lazy {
    AndroidAppSettingsRepository(AppContextHolder.context)
}

actual fun defaultAppSettingsRepository(): AppSettingsRepository = instance
