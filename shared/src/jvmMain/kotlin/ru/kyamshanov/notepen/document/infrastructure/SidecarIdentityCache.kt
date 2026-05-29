package ru.kyamshanov.notepen.document.infrastructure

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.document.domain.port.IdentityCache
import java.io.File

/**
 * Desktop [IdentityCache] that stores each document's digest in a tiny sidecar
 * `<file>.notepen.id` next to the original — same locality convention the
 * annotation sidecars already use. The sidecar holds `<fingerprint>\n<sha256>`;
 * a fingerprint mismatch (the document was edited in place) is treated as a
 * miss so the digest is recomputed.
 *
 * All file I/O hops to [ioDispatcher]. Read/write failures degrade gracefully
 * to a cache miss / no-op write — identity computation still works, it just
 * re-hashes next time.
 */
class SidecarIdentityCache(
    private val ioDispatcher: CoroutineDispatcher,
) : IdentityCache {
    override suspend fun get(
        path: String,
        fingerprint: String?,
    ): String? {
        if (fingerprint == null) return null
        return withContext(ioDispatcher) {
            val sidecar = File(sidecarPath(path))
            if (!sidecar.isFile) return@withContext null
            runCatching {
                val lines = sidecar.readText().lines()
                if (lines.size >= 2 && lines[0] == fingerprint) lines[1].trim() else null
            }.getOrNull()
        }
    }

    override suspend fun put(
        path: String,
        fingerprint: String?,
        sha256Hex: String,
    ) {
        if (fingerprint == null) return
        withContext(ioDispatcher) {
            runCatching {
                File(sidecarPath(path)).writeText("$fingerprint\n$sha256Hex")
            }
        }
    }

    private fun sidecarPath(path: String): String = "$path.notepen.id"
}
