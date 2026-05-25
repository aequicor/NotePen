package ru.kyamshanov.notepen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.annotation.domain.model.StoredToolPresets
import ru.kyamshanov.notepen.annotation.domain.port.ToolPresetsRepository
import ru.kyamshanov.notepen.mainscreen.infrastructure.getAppDataDir

/**
 * Desktop (JVM) implementation of [ToolPresetsRepository].
 *
 * Stores presets in `$HOME/.notepen/tool_presets.json`. Two-level locking mirrors
 * [ru.kyamshanov.notepen.mainscreen.infrastructure.FileHistoryRepositoryDesktop]:
 * an in-process [Mutex] plus a cross-process [java.nio.channels.FileLock], all
 * file work confined to [Dispatchers.IO].
 */
class ToolPresetsRepositoryDesktop(
    private val dataDir: java.io.File = getAppDataDir(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ToolPresetsRepository {
    private val presetsFile get() = java.io.File(dataDir, "tool_presets.json")
    private val lockFile get() = java.io.File(dataDir, "tool_presets.lock")
    private val inProcessMutex = Mutex()

    override suspend fun load(): StoredToolPresets =
        withContext(Dispatchers.IO) {
            try {
                val text =
                    presetsFile.takeIf { it.exists() }?.readText()
                        ?: return@withContext StoredToolPresets()
                json.decodeFromString<StoredToolPresets>(text)
            } catch (_: Exception) {
                StoredToolPresets()
            }
        }

    override suspend fun save(presets: StoredToolPresets) {
        inProcessMutex.withLock {
            withContext(Dispatchers.IO) {
                java.io.RandomAccessFile(lockFile, "rw").use { raf ->
                    raf.channel.use { channel ->
                        val lock = channel.lock()
                        try {
                            writeAtomic(presets)
                        } finally {
                            lock.release()
                        }
                    }
                }
            }
        }
    }

    private fun writeAtomic(presets: StoredToolPresets) {
        val tmp = java.io.File(dataDir, "tool_presets.tmp.json")
        tmp.writeText(json.encodeToString(StoredToolPresets.serializer(), presets))
        java.nio.file.Files.move(
            tmp.toPath(),
            presetsFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
    }
}

actual fun createToolPresetsRepository(): ToolPresetsRepository = ToolPresetsRepositoryDesktop()
