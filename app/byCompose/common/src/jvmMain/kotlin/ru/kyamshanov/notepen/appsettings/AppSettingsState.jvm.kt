package ru.kyamshanov.notepen.appsettings

import ru.kyamshanov.notepen.appsettings.data.JvmAppSettingsRepository
import ru.kyamshanov.notepen.appsettings.domain.port.AppSettingsRepository

private val instance: AppSettingsRepository by lazy { JvmAppSettingsRepository() }

actual fun defaultAppSettingsRepository(): AppSettingsRepository = instance
