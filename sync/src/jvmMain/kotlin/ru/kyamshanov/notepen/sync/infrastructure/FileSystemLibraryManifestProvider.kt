package ru.kyamshanov.notepen.sync.infrastructure

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.kyamshanov.notepen.sync.domain.bookIdFromRelativePath
import ru.kyamshanov.notepen.sync.domain.model.BookId
import ru.kyamshanov.notepen.sync.domain.model.LibraryBook
import ru.kyamshanov.notepen.sync.domain.model.LibraryManifest
import ru.kyamshanov.notepen.sync.domain.port.LibraryManifestProvider
import java.io.File

/**
 * [LibraryManifestProvider] backed by a sandboxed directory — the desktop
 * host's "local library".
 *
 * Only files *inside* [root] (after symlink / `..` canonicalisation) and
 * accepted by [isBook] are published; this replaces the old "share whatever is
 * in recents" behaviour and is the path-traversal boundary. Each [current] walk
 * refreshes an id→canonical-path cache, so [resolveAbsolutePath] is an O(1)
 * lookup that can never return a path outside [root].
 *
 * @param root library directory; need not exist yet (an absent/again-deleted
 *   directory simply yields an empty manifest).
 * @param isBook predicate deciding which files count as books (e.g. by
 *   extension); annotation sidecars and unrelated files are filtered out here.
 */
class FileSystemLibraryManifestProvider(
    root: File,
    private val isBook: (File) -> Boolean,
) : LibraryManifestProvider {
    private val rootCanonical: File = root.canonicalFile
    private val mutex = Mutex()
    private var pathById: Map<String, String> = emptyMap()

    override suspend fun current(): LibraryManifest {
        if (!rootCanonical.isDirectory) {
            mutex.withLock { pathById = emptyMap() }
            return LibraryManifest(books = emptyList())
        }
        val books = mutableListOf<LibraryBook>()
        val resolved = mutableMapOf<String, String>()
        rootCanonical
            .walkTopDown()
            .filter { it.isFile && isBook(it) }
            .forEach { file ->
                val canonical = file.canonicalFile
                // Drop anything that escaped the root via a symlink — the
                // canonical path is the trustworthy one. The boundary is
                // enforced here (walk time); the cached path is not re-checked
                // when later read, so callers must not weaken this guarantee.
                if (!canonical.isUnder(rootCanonical)) return@forEach
                val relativePath =
                    canonical.relativeTo(rootCanonical).path.replace(File.separatorChar, '/')
                val id = bookIdFromRelativePath(relativePath)
                books +=
                    LibraryBook(
                        id = id,
                        relativePath = relativePath,
                        displayName = canonical.name,
                        fileSize = canonical.length(),
                        modifiedAt = canonical.lastModified(),
                    )
                resolved[id.value] = canonical.path
            }
        mutex.withLock { pathById = resolved }
        return LibraryManifest(books = books)
    }

    override suspend fun resolveAbsolutePath(id: BookId): String? = mutex.withLock { pathById[id.value] }

    private fun File.isUnder(dir: File): Boolean = toPath().startsWith(dir.toPath())
}
