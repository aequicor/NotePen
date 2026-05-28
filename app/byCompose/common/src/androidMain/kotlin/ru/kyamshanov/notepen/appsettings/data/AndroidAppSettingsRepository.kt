package ru.kyamshanov.notepen.appsettings.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.appsettings.domain.model.AppSettings
import ru.kyamshanov.notepen.appsettings.domain.port.AppSettingsRepository
import java.io.File

/**
 * Android-реализация [AppSettingsRepository]. Хранит настройки в
 * `context.filesDir/app_settings.json`, atomic-rename через temp-файл.
 */
class AndroidAppSettingsRepository(
    private val context: Context,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        },
) : AppSettingsRepository {
    private val mutex = Mutex()
    private val state = MutableStateFlow(AppSettings())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { state.value = load() }
    }

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    private val settingsFile get() = File(context.filesDir, "app_settings.json")

    override suspend fun load(): AppSettings =
        withContext(Dispatchers.IO) {
            try {
                val text = settingsFile.takeIf { it.exists() }?.readText() ?: return@withContext AppSettings()
                json.decodeFromString(AppSettings.serializer(), text)
            } catch (_: Exception) {
                AppSettings()
            }
        }

    override suspend fun save(settings: AppSettings) {
        state.value = settings
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val tmp = File(context.filesDir, "app_settings.tmp.json")
                tmp.writeText(json.encodeToString(AppSettings.serializer(), settings))
                tmp.renameTo(settingsFile)
            }
        }
    }
}
