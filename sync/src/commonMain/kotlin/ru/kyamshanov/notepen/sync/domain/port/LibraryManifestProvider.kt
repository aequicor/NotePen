package ru.kyamshanov.notepen.sync.domain.port

import ru.kyamshanov.notepen.sync.domain.model.BookId
import ru.kyamshanov.notepen.sync.domain.model.LibraryManifest

/**
 * Source of the host's shareable library: the set of books a paired peer is
 * allowed to see, plus resolution of an opaque [BookId] back to a concrete
 * local path for streaming.
 *
 * This is the single seam that decides *what a device publishes*. The desktop
 * host backs it with a sandboxed directory (the "local library"); a client
 * device backs it with its own recents. Neither ever exposes anything outside
 * its published set.
 */
interface LibraryManifestProvider {
    /** Current snapshot of published books. Cheap enough to call per request. */
    suspend fun current(): LibraryManifest

    /**
     * Resolves [id] back to a local absolute path, or `null` if it is not part
     * of the current library. Implementations MUST only ever return paths
     * inside their published scope — this is the path-traversal boundary.
     */
    suspend fun resolveAbsolutePath(id: BookId): String?
}
