package ru.kyamshanov.notepen

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.annotation.domain.model.StoredToolPresets
import ru.kyamshanov.notepen.annotation.domain.port.ToolPresetsRepository

/**
 * Android implementation of [ToolPresetsRepository].
 *
 * Stores presets in `context.filesDir/tool_presets.json`; atomic write via a
 * temp file + rename, guarded by an in-process [Mutex].
 */
class ToolPresetsRepositoryAndroid(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ToolPresetsRepository {

    private val presetsFile get() = java.io.File(context.filesDir, "tool_presets.json")
    private val mutex = Mutex()

    override suspend fun load(): StoredToolPresets = withContext(Dispatchers.IO) {
        try {
            val text = presetsFile.takeIf { it.exists() }?.readText()
                ?: return@withContext StoredToolPresets()
            json.decodeFromString<StoredToolPresets>(text)
        } catch (_: Exception) {
            StoredToolPresets()
        }
    }

    override suspend fun save(presets: StoredToolPresets) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val tmp = java.io.File(context.filesDir, "tool_presets.tmp.json")
            tmp.writeText(json.encodeToString(StoredToolPresets.serializer(), presets))
            tmp.renameTo(presetsFile)
            Unit
        }
    }
}

actual fun createToolPresetsRepository(): ToolPresetsRepository =
    ToolPresetsRepositoryAndroid(AppContextHolder.context)
