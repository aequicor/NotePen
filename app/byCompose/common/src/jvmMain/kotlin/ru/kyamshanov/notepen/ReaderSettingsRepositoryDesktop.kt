package ru.kyamshanov.notepen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.mainscreen.infrastructure.getAppDataDir
import ru.kyamshanov.notepen.reflow.api.ReaderSettingsRepository
import ru.kyamshanov.notepen.reflow.api.StoredReaderSettings

/**
 * Desktop (JVM) implementation of [ReaderSettingsRepository].
 *
 * Stores settings in `$HOME/.notepen/reader_settings.json`. Two-level locking
 * mirrors [ToolPresetsRepositoryDesktop]: an in-process [Mutex] plus a
 * cross-process [java.nio.channels.FileLock], all file work confined to
 * [Dispatchers.IO].
 */
class ReaderSettingsRepositoryDesktop(
    private val dataDir: java.io.File = getAppDataDir(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ReaderSettingsRepository {
    private val settingsFile get() = java.io.File(dataDir, "reader_settings.json")
    private val lockFile get() = java.io.File(dataDir, "reader_settings.lock")
    private val inProcessMutex = Mutex()

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

    override suspend fun save(settings: StoredReaderSettings) {
        inProcessMutex.withLock {
            withContext(Dispatchers.IO) {
                java.io.RandomAccessFile(lockFile, "rw").use { raf ->
                    raf.channel.use { channel ->
                        val lock = channel.lock()
                        try {
                            writeAtomic(settings)
                        } finally {
                            lock.release()
                        }
                    }
                }
            }
        }
    }

    private fun writeAtomic(settings: StoredReaderSettings) {
        val tmp = java.io.File(dataDir, "reader_settings.tmp.json")
        tmp.writeText(json.encodeToString(StoredReaderSettings.serializer(), settings))
        java.nio.file.Files.move(
            tmp.toPath(),
            settingsFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
    }
}

actual fun createReaderSettingsRepository(): ReaderSettingsRepository = ReaderSettingsRepositoryDesktop()
