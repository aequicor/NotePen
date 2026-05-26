package ru.kyamshanov.notepen.session

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.mainscreen.infrastructure.getAppDataDir
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Desktop (JVM) implementation of [SessionRepository].
 *
 * Stores data under a `sessions/` subfolder of [appDataDir] in three files:
 * - `sessions/_autosave.json` — the single autosaved [SessionData] (absent when none).
 * - `sessions/named.json` — the `List<NamedSession>`.
 * - `sessions/pending_restore.json` — the one-shot pending restore (absent when none).
 *
 * Concurrency/atomicity mirror
 * [ru.kyamshanov.notepen.ToolPresetsRepositoryDesktop] and
 * [ru.kyamshanov.notepen.mainscreen.infrastructure.FileHistoryRepositoryDesktop]:
 * an in-process [Mutex] plus a cross-process [java.nio.channels.FileLock], with
 * every write done to a temp file then promoted via an atomic
 * [java.nio.file.Files.move]. All file work runs on [ioDispatcher].
 *
 * @param appDataDir application data root; the `sessions/` subfolder lives inside it.
 * @param json serializer; tolerant of unknown keys for forward compatibility.
 * @param ioDispatcher dispatcher for blocking file IO (injected per project rules;
 *   never reference [Dispatchers] directly in the body).
 */
class SessionRepositoryDesktop(
    private val appDataDir: File = getAppDataDir(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SessionRepository {
    private val sessionsDir get() = File(appDataDir, "sessions")
    private val autosaveFile get() = File(sessionsDir, "_autosave.json")
    private val namedFile get() = File(sessionsDir, "named.json")
    private val pendingRestoreFile get() = File(sessionsDir, "pending_restore.json")
    private val lockFile get() = File(sessionsDir, ".sessions.lock")
    private val inProcessMutex = Mutex()

    override suspend fun loadAutosave(): SessionData? =
        withContext(ioDispatcher) {
            try {
                val text = autosaveFile.takeIf { it.exists() }?.readText() ?: return@withContext null
                json.decodeFromString(SessionData.serializer(), text)
            } catch (_: Exception) {
                null
            }
        }

    override suspend fun saveAutosave(data: SessionData) {
        withLockedIo {
            writeAtomic(autosaveFile, json.encodeToString(SessionData.serializer(), data))
        }
    }

    override suspend fun clearAutosave() {
        withLockedIo {
            autosaveFile.delete()
        }
    }

    override suspend fun listNamed(): List<NamedSession> = withContext(ioDispatcher) { readNamedUnsafe() }

    override suspend fun saveNamed(session: NamedSession) {
        withLockedIo {
            val merged = readNamedUnsafe().filterNot { it.id == session.id } + session
            writeNamedUnsafe(merged)
        }
    }

    override suspend fun deleteNamed(id: String) {
        withLockedIo {
            writeNamedUnsafe(readNamedUnsafe().filterNot { it.id == id })
        }
    }

    override suspend fun savePendingRestore(data: SessionData) {
        withLockedIo {
            writeAtomic(pendingRestoreFile, json.encodeToString(SessionData.serializer(), data))
        }
    }

    override suspend fun consumePendingRestore(): SessionData? =
        withLockedIo {
            // Read then clear under the same lock so the slot is consumed atomically.
            val text = pendingRestoreFile.takeIf { it.exists() }?.readText()
            pendingRestoreFile.delete()
            text?.let {
                try {
                    json.decodeFromString(SessionData.serializer(), it)
                } catch (_: Exception) {
                    null
                }
            }
        }

    /**
     * Two-level lock: [inProcessMutex] then [ioDispatcher] then a cross-process
     * [java.nio.channels.FileLock] held for the duration of [block].
     */
    private suspend fun <T> withLockedIo(block: () -> T): T =
        inProcessMutex.withLock {
            withContext(ioDispatcher) {
                sessionsDir.mkdirs()
                RandomAccessFile(lockFile, "rw").use { raf ->
                    raf.channel.use { channel ->
                        val lock = channel.lock()
                        try {
                            block()
                        } finally {
                            lock.release()
                        }
                    }
                }
            }
        }

    private fun readNamedUnsafe(): List<NamedSession> =
        try {
            namedFile.takeIf { it.exists() }?.let {
                json.decodeFromString(NAMED_SERIALIZER, it.readText())
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    private fun writeNamedUnsafe(sessions: List<NamedSession>) {
        writeAtomic(namedFile, json.encodeToString(NAMED_SERIALIZER, sessions))
    }

    /** Writes [content] to a sibling temp file, then atomically replaces [target]. */
    private fun writeAtomic(
        target: File,
        content: String,
    ) {
        sessionsDir.mkdirs()
        val tmp = File(sessionsDir, target.name + ".tmp")
        tmp.writeText(content)
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private companion object {
        private val NAMED_SERIALIZER =
            kotlinx.serialization.builtins.ListSerializer(NamedSession.serializer())
    }
}

/** Creates the desktop-backed [SessionRepository]. */
actual fun createSessionRepository(): SessionRepository = SessionRepositoryDesktop()
