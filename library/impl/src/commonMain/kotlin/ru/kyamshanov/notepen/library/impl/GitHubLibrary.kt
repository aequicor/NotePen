package ru.kyamshanov.notepen.library.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.library.api.Library
import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryBookId
import ru.kyamshanov.notepen.library.api.LibraryCapabilities
import ru.kyamshanov.notepen.library.api.LibraryConnectionState
import ru.kyamshanov.notepen.library.api.LibraryDescriptor
import ru.kyamshanov.notepen.library.api.LibraryEntry
import ru.kyamshanov.notepen.library.api.LibraryId
import ru.kyamshanov.notepen.library.api.LibraryRole
import ru.kyamshanov.notepen.library.api.NotLibrarianException
import ru.kyamshanov.notepen.library.api.OpenableDocument
import ru.kyamshanov.notepen.sync.cloud.domain.CloudFile
import ru.kyamshanov.notepen.sync.cloud.domain.CloudStorageProvider
import ru.kyamshanov.notepen.sync.infrastructure.okio_delete
import ru.kyamshanov.notepen.sync.infrastructure.okio_readBytes
import ru.kyamshanov.notepen.sync.infrastructure.okio_writeBytes

/** The repo-relative directory a GitHub library reads its books from. */
internal const val GITHUB_LIBRARY_BOOKS_PATH = "books"

/** GitHub's Contents-API path-download ceiling; larger files must use the blob-by-sha endpoint. */
private const val CONTENTS_API_RAW_LIMIT_BYTES = 1L * 1024 * 1024

/**
 * A [Library] reading a GitHub repository as a book shelf, backed by the **existing**
 * [CloudStorageProvider] (no new networking — the provider wraps the GitHub Contents + Git Data
 * APIs). Books live under the repo-relative [GITHUB_LIBRARY_BOOKS_PATH] (`books/`); each file there
 * becomes a [LibraryEntry] whose [LibraryEntry.libraryBookId] is its repo path (`books/<name>`).
 *
 * ## Role
 * The role is derived from the connection token: a write token grants [LibraryRole.Librarian]
 * (add / replace via upload), an empty/absent token grants [LibraryRole.Reader] (download only).
 * [LibraryCapabilities] follow from the role. Reader-role mutations keep the [NotLibrarianException]
 * defaults; even for the Librarian role, [removeBook] stays unsupported because the
 * [CloudStorageProvider] SPI exposes no delete (documented on [removeBook]).
 *
 * ## Identity (change-detection only)
 * The GitHub blob `sha` is used **only** to detect that a book changed between [refresh]es; it is
 * NOT the canonical id. [LibraryEntry.identity] is left `null` until the file is materialized locally
 * (M0 computes the content-addressed `CanonicalBookId` then). The blob sha doubles as the optimistic
 * concurrency token for [replaceBook].
 *
 * ## Caching
 * [open] downloads the book bytes (raw Contents API for files up to ~1 MB, the blob-by-sha endpoint
 * for larger ones) and writes them to [cacheDir]; the cached path becomes the [OpenableDocument].
 * On [refresh], any cached file whose blob sha changed upstream is invalidated (deleted) so the next
 * [open] re-downloads the new content (M6 re-sync). Cap-bounded eviction of the cache dir is handled
 * out-of-band by `CacheEvictor` in the DI layer.
 *
 * @param descriptor immutable description of this library (id derived from the repo slug + role).
 * @param provider the cloud provider configured for this repo + token (read/list/download/upload).
 * @param cacheDir absolute directory under which downloaded books are cached.
 * @param ioDispatcher dispatcher for the (blocking) network + filesystem work.
 * @param scope long-lived scope; an initial [refresh] runs on it so [books] populates after connect.
 */
internal class GitHubLibrary(
    override val descriptor: LibraryDescriptor,
    private val provider: CloudStorageProvider,
    private val cacheDir: String,
    private val ioDispatcher: CoroutineDispatcher,
    scope: CoroutineScope,
) : Library {
    override val capabilities: LibraryCapabilities = LibraryCapabilities.fromRole(descriptor.role)

    private val booksState = MutableStateFlow<List<LibraryEntry>>(emptyList())
    override val books: StateFlow<List<LibraryEntry>> = booksState.asStateFlow()

    private val connectionStateFlow = MutableStateFlow<LibraryConnectionState>(LibraryConnectionState.Connecting)
    override val connectionState: StateFlow<LibraryConnectionState> = connectionStateFlow.asStateFlow()

    /** Latest blob sha per book path, used to detect content changes across refreshes. */
    private val shaByPath = mutableMapOf<String, String>()

    init {
        scope.launch { refresh() }
    }

    override suspend fun refresh() {
        connectionStateFlow.value = LibraryConnectionState.Connecting
        runCatching { withContext(ioDispatcher) { provider.list(GITHUB_LIBRARY_BOOKS_PATH) } }
            .onSuccess { files ->
                invalidateChangedCaches(files)
                shaByPath.clear()
                files.forEach { shaByPath[it.path] = it.sha }
                booksState.value = files.map(CloudFile::toLibraryEntry)
                connectionStateFlow.value = LibraryConnectionState.Connected
            }.onFailure { e ->
                connectionStateFlow.value = LibraryConnectionState.Error(e.message ?: e::class.simpleName ?: "error")
            }
    }

    /**
     * M6 re-sync: for every listed book whose blob sha differs from the one we cached on the last
     * refresh, delete the stale local cache file so the next [open] re-downloads the new content.
     *
     * The blob sha is GitHub's change-detection token, NOT the canonical id — a changed file is new
     * content, so on re-materialization it correctly hashes to a new
     * [ru.kyamshanov.notepen.document.domain.model.CanonicalBookId]. This does not fight canonical
     * identity; it simply ensures the bytes on disk are the current ones before they are re-hashed.
     * Runs before [shaByPath] is rebuilt so it can compare new-vs-previous.
     */
    private suspend fun invalidateChangedCaches(files: List<CloudFile>) {
        files.forEach { file ->
            val previousSha = shaByPath[file.path]
            if (previousSha != null && previousSha != file.sha) {
                val cachedPath = cachePathFor(file.path)
                withContext(ioDispatcher) { okio_delete(cachedPath) }
            }
        }
    }

    override suspend fun open(id: LibraryBookId): Result<OpenableDocument> =
        runCatching {
            val path = id.value
            val sha = shaByPath[path]
            val size = booksState.value.firstOrNull { it.libraryBookId.value == path }?.sizeBytes ?: 0L
            val bytes =
                withContext(ioDispatcher) {
                    if (size > CONTENTS_API_RAW_LIMIT_BYTES && sha != null) {
                        provider.downloadBySha(sha = sha, fallbackPath = path)
                    } else {
                        provider.download(path)
                    }
                }
            val cachedPath = cachePathFor(path)
            withContext(ioDispatcher) { okio_writeBytes(cachedPath, bytes) }
            OpenableDocument(
                localPath = cachedPath,
                // The blob sha is change-detection only; the canonical id is computed at local
                // materialization (M0), not here.
                identity = null,
                readOnly = descriptor.role == LibraryRole.Reader,
            )
        }

    override suspend fun addBook(src: String): Result<LibraryEntry> =
        runCatching {
            requireLibrarian()
            val bytes = withContext(ioDispatcher) { okio_readBytes(src) }
            val name = src.substringAfterLast('/').substringAfterLast('\\')
            val path = "$GITHUB_LIBRARY_BOOKS_PATH/$name"
            // A fresh add has no previous sha; an existing path here would need its sha for
            // optimistic concurrency, so reuse any known sha to update in place idempotently.
            val uploaded = withContext(ioDispatcher) { provider.upload(path, bytes, shaByPath[path]) }
            shaByPath[uploaded.path] = uploaded.sha
            val entry = uploaded.toLibraryEntry()
            booksState.value = (booksState.value.filterNot { it.libraryBookId.value == uploaded.path } + entry)
            entry
        }

    override suspend fun replaceBook(
        id: LibraryBookId,
        src: String,
    ): Result<LibraryEntry> =
        runCatching {
            requireLibrarian()
            val path = id.value
            val previousSha =
                shaByPath[path]
                    ?: error("Cannot replace '$path': unknown current revision (refresh the library first)")
            val bytes = withContext(ioDispatcher) { okio_readBytes(src) }
            val uploaded = withContext(ioDispatcher) { provider.upload(path, bytes, previousSha) }
            shaByPath[uploaded.path] = uploaded.sha
            val entry = uploaded.toLibraryEntry()
            booksState.value = booksState.value.map { if (it.libraryBookId.value == path) entry else it }
            entry
        }

    // removeBook keeps the Library default (NotLibrarianException): the CloudStorageProvider SPI
    // exposes no delete operation, so there is nothing to delegate to. It is wired if/when the SPI
    // grows a delete (GitHub's Contents API supports DELETE with the blob sha).

    private fun requireLibrarian() {
        if (descriptor.role != LibraryRole.Librarian) throw NotLibrarianException()
    }

    /** Local cache path for the repo-relative [path]: `<cacheDir>/<repoSlug>/<flattened path>`. */
    private fun cachePathFor(path: String): String {
        val safeRepo = descriptor.displayName.replace('/', '_')
        val safeName = path.replace('/', '_')
        return "$cacheDir/$safeRepo/$safeName"
    }

    companion object {
        /** Builds a [LibraryId] for the GitHub library backed by the repo slug [repo]. */
        fun idForRepo(repo: String): LibraryId = LibraryId("github:$repo")
    }
}

/** Maps a repo file to a [LibraryEntry] (identity deferred until local materialization). */
private fun CloudFile.toLibraryEntry(): LibraryEntry =
    LibraryEntry(
        libraryBookId = LibraryBookId(path),
        displayName = path.substringAfterLast('/'),
        sizeBytes = sizeBytes,
        modifiedAt = null,
        // The GitHub blob sha is change-detection only, not the canonical content id.
        identity = null,
    )

/**
 * Builds the [LibraryDescriptor] for a GitHub library.
 *
 * @param repo the `owner/name` repository slug (also the display name and id seed).
 * @param hasWriteToken whether a write-capable token is present → [LibraryRole.Librarian], else
 *   [LibraryRole.Reader].
 */
internal fun gitHubDescriptor(
    repo: String,
    hasWriteToken: Boolean,
): LibraryDescriptor =
    LibraryDescriptor(
        id = GitHubLibrary.idForRepo(repo),
        displayName = repo,
        kind = LibraryBackendKind.GitHub,
        role = if (hasWriteToken) LibraryRole.Librarian else LibraryRole.Reader,
    )
