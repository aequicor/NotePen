package ru.kyamshanov.notepen.library.infrastructure

import android.content.Context
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryConnectionStore
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Android [LibraryConnectionStore]. Persists the saved [LibraryConnection]s as a JSON array in
 * `library_connections.json` under `context.filesDir`.
 *
 * Writes stage to a temp file and then `renameTo` the target. Note this is **not** a guaranteed
 * atomic move on Android (`File.renameTo` is best-effort and not atomic across all filesystems), but
 * it still avoids a partial overwrite of the live file in the common case. A [Mutex] serialises
 * in-process access; a read of an absent/unreadable file yields an empty list. Backward
 * compatibility relies on the polymorphic sealed serializer + property defaults of
 * [LibraryConnection] (`ignoreUnknownKeys = true`).
 *
 * @param context application context providing `filesDir`.
 * @param ioDispatcher dispatcher for the blocking file I/O.
 */
public class AndroidLibraryConnectionStore(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LibraryConnectionStore {
    private val mutex = Mutex()
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val file: File get() = File(context.filesDir, FILE_NAME)
    private val tmpFile: File get() = File(context.filesDir, "$FILE_NAME.tmp")

    override suspend fun load(): List<LibraryConnection> =
        withContext(ioDispatcher) {
            mutex.withLock { readUnsafe() }
        }

    override suspend fun save(connections: List<LibraryConnection>) {
        withContext(ioDispatcher) {
            mutex.withLock { writeUnsafe(connections) }
        }
    }

    override suspend fun add(connection: LibraryConnection): List<LibraryConnection> =
        withContext(ioDispatcher) {
            mutex.withLock {
                val updated = readUnsafe().filterNot { it == connection } + connection
                writeUnsafe(updated)
                updated
            }
        }

    override suspend fun remove(connection: LibraryConnection): List<LibraryConnection> =
        withContext(ioDispatcher) {
            mutex.withLock {
                val updated = readUnsafe().filterNot { it == connection }
                writeUnsafe(updated)
                updated
            }
        }

    private fun readUnsafe(): List<LibraryConnection> {
        val f = file
        if (!f.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(serializer, f.readText())
        }.getOrElse { t ->
            logger.warn(t) { "LibraryConnectionStore: cannot read $f, falling back to empty" }
            emptyList()
        }
    }

    private fun writeUnsafe(connections: List<LibraryConnection>) {
        runCatching {
            val tmp = tmpFile
            tmp.writeText(json.encodeToString(serializer, connections))
            if (!tmp.renameTo(file)) {
                // renameTo can fail if the target already exists on some filesystems; fall back to
                // an overwrite-in-place (loses atomicity but keeps the data consistent on success).
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }.onFailure { t ->
            logger.warn(t) { "LibraryConnectionStore: cannot save connections to $file" }
            runCatching { tmpFile.delete() }
        }
    }

    private companion object {
        const val FILE_NAME = "library_connections.json"
        val serializer = ListSerializer(LibraryConnection.serializer())
    }
}
