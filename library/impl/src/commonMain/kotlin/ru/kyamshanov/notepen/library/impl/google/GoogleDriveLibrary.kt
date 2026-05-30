package ru.kyamshanov.notepen.library.impl.google

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.library.api.DriveLikeStore
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
import ru.kyamshanov.notepen.library.api.RemoteFile
import ru.kyamshanov.notepen.sync.infrastructure.okio_delete
import ru.kyamshanov.notepen.sync.infrastructure.okio_readBytes
import ru.kyamshanov.notepen.sync.infrastructure.okio_writeBytes

/**
 * A [Library] reading a **Google Drive folder** as a book shelf, backed by the id-addressed
 * [DriveLikeStore]. Each non-folder child of the shelf folder becomes a [LibraryEntry] whose
 * [LibraryEntry.libraryBookId] is the opaque Drive `fileId` and whose [LibraryEntry.displayName] is
 * the Drive title — identity and name are kept separate (unlike the path-addressed GitHub library).
 *
 * ## Role
 * The role comes from the OAuth scope the connection was granted (see
 * [GoogleOAuthConfig.roleForScope]): a read-only scope → [LibraryRole.Reader] (download only), a
 * write scope → [LibraryRole.Librarian] (add / replace via Drive upload). Reader-role mutations keep
 * the [NotLibrarianException] defaults; `removeBook` stays unsupported for now (delete is a later
 * milestone) even for the Librarian role.
 *
 * ## Identity (change-detection only)
 * The Drive [RemoteFile.version] is used **only** to detect that a book changed between [refresh]es;
 * it is NOT the canonical id. [LibraryEntry.identity] stays `null` until the file is materialized
 * locally (the content-addressed `CanonicalBookId` is computed there).
 *
 * ## Caching
 * [open] downloads the book bytes and writes them under [cacheDir]; the cached path becomes the
 * [OpenableDocument]. On [refresh], any cached file whose Drive version changed upstream is deleted
 * so the next [open] re-downloads the new content.
 *
 * @param descriptor immutable description of this library (id derived from the folder id + role).
 * @param store the Drive-like store configured for this folder + credentials.
 * @param folderId the Drive folder id used as the shelf.
 * @param cacheDir absolute directory under which downloaded books are cached.
 * @param ioDispatcher dispatcher for the (blocking) network + filesystem work.
 * @param scope long-lived scope; an initial [refresh] runs on it so [books] populates after connect.
 */
internal class GoogleDriveLibrary(
    override val descriptor: LibraryDescriptor,
    private val store: DriveLikeStore,
    private val folderId: String,
    private val cacheDir: String,
    private val ioDispatcher: CoroutineDispatcher,
    scope: CoroutineScope,
) : Library {
    override val capabilities: LibraryCapabilities = LibraryCapabilities.fromRole(descriptor.role)

    private val booksState = MutableStateFlow<List<LibraryEntry>>(emptyList())
    override val books: StateFlow<List<LibraryEntry>> = booksState.asStateFlow()

    private val connectionStateFlow = MutableStateFlow<LibraryConnectionState>(LibraryConnectionState.Connecting)
    override val connectionState: StateFlow<LibraryConnectionState> = connectionStateFlow.asStateFlow()

    /** Latest Drive version per file id, used to detect content changes across refreshes. */
    private val versionById = mutableMapOf<String, String>()

    init {
        scope.launch { refresh() }
    }

    override suspend fun refresh() {
        connectionStateFlow.value = LibraryConnectionState.Connecting
        runCatching { withContext(ioDispatcher) { store.listChildren(folderId) } }
            .onSuccess { files ->
                invalidateChangedCaches(files)
                versionById.clear()
                files.forEach { file -> file.version?.let { versionById[file.id] = it } }
                booksState.value = files.map(RemoteFile::toLibraryEntry)
                connectionStateFlow.value = LibraryConnectionState.Connected
            }.onFailure { e ->
                connectionStateFlow.value = LibraryConnectionState.Error(e.message ?: e::class.simpleName ?: "error")
            }
    }

    /**
     * For every listed book whose Drive version differs from the one cached on the last refresh,
     * delete the stale local cache file so the next [open] re-downloads the new content. Runs before
     * [versionById] is rebuilt so it compares new-vs-previous.
     */
    private suspend fun invalidateChangedCaches(files: List<RemoteFile>) {
        files.forEach { file ->
            val previous = versionById[file.id]
            if (previous != null && file.version != null && previous != file.version) {
                withContext(ioDispatcher) { okio_delete(cachePathFor(file.id)) }
            }
        }
    }

    override suspend fun open(id: LibraryBookId): Result<OpenableDocument> =
        runCatching {
            val bytes = withContext(ioDispatcher) { store.download(id.value) }
            val cachedPath = cachePathFor(id.value)
            withContext(ioDispatcher) { okio_writeBytes(cachedPath, bytes) }
            OpenableDocument(
                localPath = cachedPath,
                // Canonical id is computed at local materialization, not here.
                identity = null,
                readOnly = descriptor.role == LibraryRole.Reader,
            )
        }

    override suspend fun addBook(src: String): Result<LibraryEntry> =
        runCatching {
            requireLibrarian()
            val bytes = withContext(ioDispatcher) { okio_readBytes(src) }
            val name = src.substringAfterLast('/').substringAfterLast('\\')
            val created = withContext(ioDispatcher) { store.create(folderId, name, bytes) }
            created.version?.let { versionById[created.id] = it }
            val entry = created.toLibraryEntry()
            booksState.value = booksState.value.filterNot { it.libraryBookId.value == created.id } + entry
            entry
        }

    override suspend fun replaceBook(
        id: LibraryBookId,
        src: String,
    ): Result<LibraryEntry> =
        runCatching {
            requireLibrarian()
            val bytes = withContext(ioDispatcher) { okio_readBytes(src) }
            val updated = withContext(ioDispatcher) { store.update(id.value, bytes, versionById[id.value]) }
            updated.version?.let { versionById[updated.id] = it }
            val entry = updated.toLibraryEntry()
            booksState.value = booksState.value.map { if (it.libraryBookId.value == id.value) entry else it }
            entry
        }

    // removeBook keeps the Library default (NotLibrarianException) for now; Drive supports delete by
    // fileId, so it is wired in a later hardening milestone once the DriveLikeStore.delete path is
    // exercised end-to-end.

    private fun requireLibrarian() {
        if (descriptor.role != LibraryRole.Librarian) throw NotLibrarianException()
    }

    /** Local cache path for the opaque [fileId]: `<cacheDir>/<folderId>/<sanitized fileId>`. */
    private fun cachePathFor(fileId: String): String = "$cacheDir/${folderId.sanitized()}/${fileId.sanitized()}"

    private fun String.sanitized(): String = map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")

    companion object {
        /** Builds a [LibraryId] for the Google Drive library rooted at the shelf folder [folderId]. */
        fun idForFolder(folderId: String): LibraryId = LibraryId("gdrive:$folderId")
    }
}

/** Maps a Drive file to a [LibraryEntry] (identity deferred until local materialization). */
private fun RemoteFile.toLibraryEntry(): LibraryEntry =
    LibraryEntry(
        libraryBookId = LibraryBookId(id),
        displayName = name,
        sizeBytes = sizeBytes,
        modifiedAt = modifiedAt,
        identity = null,
    )

/**
 * Builds the [LibraryDescriptor] for a Google Drive library.
 *
 * @param folderId the shelf folder id (also the id seed).
 * @param displayName human-readable shelf name shown in the UI.
 * @param role the access level derived from the granted OAuth scope.
 */
internal fun googleDriveDescriptor(
    folderId: String,
    displayName: String,
    role: LibraryRole,
): LibraryDescriptor =
    LibraryDescriptor(
        id = GoogleDriveLibrary.idForFolder(folderId),
        displayName = displayName,
        kind = LibraryBackendKind.Cloud,
        role = role,
    )
