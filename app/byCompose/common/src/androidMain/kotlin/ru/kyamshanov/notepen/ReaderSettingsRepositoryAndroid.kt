package ru.kyamshanov.notepen

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.reflow.api.ReaderSettingsRepository
import ru.kyamshanov.notepen.reflow.api.StoredReaderSettings

/**
 * Android implementation of [ReaderSettingsRepository].
 *
 * Stores settings in `context.filesDir/reader_settings.json`; atomic write via
 * a temp file + rename, guarded by an in-process [Mutex].
 */
class ReaderSettingsRepositoryAndroid(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ReaderSettingsRepository {
    private val settingsFile get() = java.io.File(context.filesDir, "reader_settings.json")
    private val mutex = Mutex()

    override suspend fun load(): StoredReaderSettings =
        withContext(Dispatchers.IO) {
            try {
                val text =
                    settingsFile.takeIf { it.exists() }?.readText()
                        ?: return@withContext StoredReaderSettings()
                json.decodeFromString<StoredReaderSettings>(text)
            } catch (_: Exception) {
                StoredReaderSettings()
            }
        }

    override suspend fun save(settings: StoredReaderSettings) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val tmp = java.io.File(context.filesDir, "reader_settings.tmp.json")
                tmp.writeText(json.encodeToString(StoredReaderSettings.serializer(), settings))
                tmp.renameTo(settingsFile)
                Unit
            }
        }
}

actual fun createReaderSettingsRepository(): ReaderSettingsRepository =
    ReaderSettingsRepositoryAndroid(AppContextHolder.context)
