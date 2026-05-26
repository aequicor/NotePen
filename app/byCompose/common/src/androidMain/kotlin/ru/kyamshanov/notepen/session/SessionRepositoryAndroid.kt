package ru.kyamshanov.notepen.session

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.AppContextHolder
import java.io.File

/**
 * Android implementation of [SessionRepository].
 *
 * Stores data under `context.filesDir/sessions` in two files:
 * - `_autosave.json` — the single autosaved [SessionData] (absent when none).
 * - `named.json` — the `List<NamedSession>`.
 *
 * Mirrors [ru.kyamshanov.notepen.mainscreen.infrastructure.FileHistoryRepositoryAndroid]:
 * an in-process [Mutex] guards writes, each of which is atomic via a temp file +
 * rename. All file work runs on [ioDispatcher].
 *
 * @param context application context, used only for its files directory.
 * @param json serializer; tolerant of unknown keys for forward compatibility.
 * @param ioDispatcher dispatcher for blocking file IO (injected per project rules;
 *   never reference [Dispatchers] directly in the body).
 */
class SessionRepositoryAndroid(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SessionRepository {
    private val sessionsDir get() = File(context.filesDir, "sessions")
    private val autosaveFile get() = File(sessionsDir, "_autosave.json")
    private val namedFile get() = File(sessionsDir, "named.json")
    private val mutex = Mutex()

    override suspend fun loadAutosave(): SessionData? =
        withContext(ioDispatcher) {
            try {
                val text = autosaveFile.takeIf { it.exists() }?.readText() ?: return@withContext null
                json.decodeFromString(SessionData.serializer(), text)
            } catch (_: Exception) {
                null
            }
        }

    override suspend fun saveAutosave(data: SessionData) =
        withContext(ioDispatcher) {
            mutex.withLock {
                writeAtomic(autosaveFile, json.encodeToString(SessionData.serializer(), data))
            }
        }

    override suspend fun clearAutosave() =
        withContext(ioDispatcher) {
            mutex.withLock {
                autosaveFile.delete()
                Unit
            }
        }

    override suspend fun listNamed(): List<NamedSession> = withContext(ioDispatcher) { readNamed() }

    override suspend fun saveNamed(session: NamedSession) =
        withContext(ioDispatcher) {
            mutex.withLock {
                val merged = readNamed().filterNot { it.id == session.id } + session
                writeAtomic(namedFile, json.encodeToString(NAMED_SERIALIZER, merged))
            }
        }

    override suspend fun deleteNamed(id: String) =
        withContext(ioDispatcher) {
            mutex.withLock {
                val remaining = readNamed().filterNot { it.id == id }
                writeAtomic(namedFile, json.encodeToString(NAMED_SERIALIZER, remaining))
            }
        }

    private fun readNamed(): List<NamedSession> =
        try {
            namedFile.takeIf { it.exists() }?.let {
                json.decodeFromString(NAMED_SERIALIZER, it.readText())
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    /** Writes [content] to a sibling temp file, then renames it over [target]. */
    private fun writeAtomic(
        target: File,
        content: String,
    ) {
        sessionsDir.mkdirs()
        val tmp = File(sessionsDir, target.name + ".tmp")
        tmp.writeText(content)
        tmp.renameTo(target)
    }

    private companion object {
        private val NAMED_SERIALIZER =
            kotlinx.serialization.builtins.ListSerializer(NamedSession.serializer())
    }
}

/** Creates the Android-backed [SessionRepository] using the app-wide context. */
actual fun createSessionRepository(): SessionRepository = SessionRepositoryAndroid(AppContextHolder.context)
