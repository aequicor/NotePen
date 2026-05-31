package ru.kyamshanov.notepen.library.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryConnectionStore
import ru.kyamshanov.notepen.mainscreen.infrastructure.getAppDataDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists

private val logger = KotlinLogging.logger {}

/**
 * Desktop (JVM) [LibraryConnectionStore]. Persists the saved [LibraryConnection]s as a JSON array in
 * `library_connections.json` under the app data dir ([getAppDataDir]).
 *
 * Writes are atomic: the JSON is staged to a temp file and then `ATOMIC_MOVE`d over the target, so an
 * interrupted save never leaves a truncated/corrupt file. A [Mutex] serialises in-process access; a
 * read of an absent or unreadable file yields an empty list rather than throwing. Backward
 * compatibility relies on the polymorphic sealed serializer + property defaults of
 * [LibraryConnection] (`ignoreUnknownKeys = true` tolerates fields written by newer versions).
 *
 * @param dataDir directory the connections file lives in; defaults to the app data dir.
 * @param ioDispatcher dispatcher for the blocking file I/O.
 */
public class JvmLibraryConnectionStore(
    private val dataDir: Path = getAppDataDir().toPath(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LibraryConnectionStore {
    private val mutex = Mutex()
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val file: Path get() = dataDir.resolve(FILE_NAME)

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
                val updated = readUnsafe().filterNot { sameConnection(it, connection) } + connection
                writeUnsafe(updated)
                updated
            }
        }

    override suspend fun remove(connection: LibraryConnection): List<LibraryConnection> =
        withContext(ioDispatcher) {
            mutex.withLock {
                val updated = readUnsafe().filterNot { sameConnection(it, connection) }
                writeUnsafe(updated)
                updated
            }
        }

    /**
     * Whether [a] and [b] denote the SAME saved library for de-dup. Two [LibraryConnection.Local] specs
     * are the same iff they share a [LibraryConnection.Local.rootPath] — the folder is the identity, the
     * display name is not — so re-adding a folder under a new name replaces rather than duplicates it
     * (and a disconnect removes it regardless of the name it was saved under). All other kinds compare by
     * full value equality.
     */
    private fun sameConnection(
        a: LibraryConnection,
        b: LibraryConnection,
    ): Boolean =
        if (a is LibraryConnection.Local && b is LibraryConnection.Local) {
            a.rootPath == b.rootPath
        } else {
            a == b
        }

    private fun readUnsafe(): List<LibraryConnection> {
        val path = file
        if (!Files.exists(path)) return emptyList()
        return runCatching {
            json.decodeFromString(serializer, Files.readString(path))
        }.getOrElse { t ->
            logger.warn(t) { "LibraryConnectionStore: cannot read $path, falling back to empty" }
            emptyList()
        }
    }

    private fun writeUnsafe(connections: List<LibraryConnection>) {
        runCatching {
            Files.createDirectories(dataDir)
            val tmp = dataDir.resolve("$FILE_NAME.tmp")
            Files.writeString(tmp, json.encodeToString(serializer, connections))
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { t ->
            logger.warn(t) { "LibraryConnectionStore: cannot save connections to $file" }
            runCatching { dataDir.resolve("$FILE_NAME.tmp").deleteIfExists() }
        }
    }

    private companion object {
        const val FILE_NAME = "library_connections.json"
        val serializer = kotlinx.serialization.builtins.ListSerializer(LibraryConnection.serializer())
    }
}
