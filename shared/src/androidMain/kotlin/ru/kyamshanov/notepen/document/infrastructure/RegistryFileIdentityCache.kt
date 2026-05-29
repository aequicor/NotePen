package ru.kyamshanov.notepen.document.infrastructure

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.document.domain.port.IdentityCache
import java.io.File

/**
 * Android [IdentityCache] backed by a single registry file under app-private
 * storage. Android documents are content URIs with no writable "next to"
 * location, so a sidecar (the desktop strategy) is impossible — instead every
 * digest is recorded as one `path\tfingerprint\tsha256` line in [registryPath].
 *
 * The registry is loaded lazily on first access and kept in memory; writes
 * rewrite the whole (small) file under [mutex]. A `null` fingerprint disables
 * the persistent layer for that entry — content URIs whose size/mtime are not
 * cheaply available fall back to the in-memory session cache in the provider.
 *
 * Read/write failures degrade to a miss / no-op.
 */
class RegistryFileIdentityCache(
    private val registryPath: String,
    private val ioDispatcher: CoroutineDispatcher,
) : IdentityCache {
    private val mutex = Mutex()
    private var loaded = false
    private var entries: MutableMap<String, Entry> = mutableMapOf()

    override suspend fun get(
        path: String,
        fingerprint: String?,
    ): String? {
        if (fingerprint == null) return null
        ensureLoaded()
        return mutex.withLock { entries[path]?.takeIf { it.fingerprint == fingerprint }?.sha256 }
    }

    override suspend fun put(
        path: String,
        fingerprint: String?,
        sha256Hex: String,
    ) {
        if (fingerprint == null) return
        ensureLoaded()
        val snapshot: Map<String, Entry>
        mutex.withLock {
            entries[path] = Entry(fingerprint, sha256Hex)
            snapshot = entries.toMap()
        }
        persist(snapshot)
    }

    private suspend fun ensureLoaded() {
        mutex.withLock {
            if (loaded) return
            entries =
                withContext(ioDispatcher) {
                    runCatching { readEntries() }.getOrElse { mutableMapOf() }
                }
            loaded = true
        }
    }

    private fun readEntries(): MutableMap<String, Entry> {
        val file = File(registryPath)
        if (!file.isFile) return mutableMapOf()
        val map = mutableMapOf<String, Entry>()
        file.readText().lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split('\t')
            if (parts.size == ENTRY_FIELD_COUNT) {
                map[parts[0]] = Entry(parts[1], parts[2])
            }
        }
        return map
    }

    private suspend fun persist(snapshot: Map<String, Entry>) {
        withContext(ioDispatcher) {
            runCatching {
                val text =
                    snapshot.entries.joinToString("\n") { (path, entry) ->
                        "$path\t${entry.fingerprint}\t${entry.sha256}"
                    }
                val file = File(registryPath)
                file.parentFile?.mkdirs()
                file.writeText(text)
            }
        }
    }

    private data class Entry(
        val fingerprint: String,
        val sha256: String,
    )

    private companion object {
        const val ENTRY_FIELD_COUNT = 3
    }
}
