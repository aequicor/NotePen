package ru.kyamshanov.notepen.sync.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.mainscreen.infrastructure.getAppDataDir
import ru.kyamshanov.notepen.sync.domain.port.LibrarianGrantStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists

private val logger = KotlinLogging.logger {}

/**
 * Desktop (JVM) [LibrarianGrantStore]. Persists the host's per-peer Librarian
 * write allow-set as a JSON array of peer ids in `librarian_peers.json` under the
 * app data dir ([getAppDataDir]).
 *
 * This is the durable mirror of the read allow-set's auto-approve grants: a peer
 * the operator approves as a librarian is re-granted after a host restart so the
 * Librarian-over-LAN capability survives across sessions.
 *
 * Writes are atomic (temp file + `ATOMIC_MOVE`) and serialised by a [Mutex]; a
 * read of an absent or unreadable file yields an empty set rather than throwing.
 *
 * @param dataDir directory the grants file lives in; defaults to the app data dir.
 * @param ioDispatcher dispatcher for the blocking file I/O.
 */
public class JvmLibrarianGrantStore(
    private val dataDir: Path = getAppDataDir().toPath(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LibrarianGrantStore {
    private val mutex = Mutex()
    private val json = Json { prettyPrint = true }

    private val file: Path get() = dataDir.resolve(FILE_NAME)

    override suspend fun load(): Set<String> =
        withContext(ioDispatcher) {
            mutex.withLock { readUnsafe() }
        }

    override suspend fun grant(peerId: String) {
        withContext(ioDispatcher) {
            mutex.withLock { writeUnsafe(readUnsafe() + peerId) }
        }
    }

    override suspend fun revoke(peerId: String) {
        withContext(ioDispatcher) {
            mutex.withLock { writeUnsafe(readUnsafe() - peerId) }
        }
    }

    private fun readUnsafe(): Set<String> {
        val path = file
        if (!Files.exists(path)) return emptySet()
        return runCatching {
            json.decodeFromString(serializer, Files.readString(path)).toSet()
        }.getOrElse { t ->
            logger.warn(t) { "LibrarianGrantStore: cannot read $path, falling back to empty" }
            emptySet()
        }
    }

    private fun writeUnsafe(peerIds: Set<String>) {
        runCatching {
            Files.createDirectories(dataDir)
            val tmp = dataDir.resolve("$FILE_NAME.tmp")
            Files.writeString(tmp, json.encodeToString(serializer, peerIds.toList()))
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { t ->
            logger.warn(t) { "LibrarianGrantStore: cannot save grants to $file" }
            runCatching { dataDir.resolve("$FILE_NAME.tmp").deleteIfExists() }
        }
    }

    private companion object {
        const val FILE_NAME = "librarian_peers.json"
        val serializer = ListSerializer(String.serializer())
    }
}
