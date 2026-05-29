package ru.kyamshanov.notepen.library.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.library.api.Library
import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryBookId
import ru.kyamshanov.notepen.library.api.LibraryCapabilities
import ru.kyamshanov.notepen.library.api.LibraryConnectionState
import ru.kyamshanov.notepen.library.api.LibraryDescriptor
import ru.kyamshanov.notepen.library.api.LibraryEntry
import ru.kyamshanov.notepen.library.api.LibraryId
import ru.kyamshanov.notepen.library.api.LibraryRole
import ru.kyamshanov.notepen.library.api.OpenableDocument
import ru.kyamshanov.notepen.mainscreen.domain.port.LibraryFolder
import ru.kyamshanov.notepen.mainscreen.domain.port.LibraryFolderItem

/**
 * A [Library] backed by a local filesystem folder, wrapping the existing
 * [LibraryFolder] port (whose desktop implementation is `FileSystemLibraryFolder`).
 *
 * The user is always the [LibraryRole.Librarian] of their own local folder, so all mutating
 * operations are available. Book identity ([LibraryEntry.identity]) is left `null` in M1 — the
 * content-addressed [ru.kyamshanov.notepen.document.domain.model.CanonicalBookId] is wired in a
 * later milestone.
 *
 * @param descriptor the immutable description of this library (id derived from the root path).
 * @param libraryFolder the underlying local folder port providing the reactive book listing.
 * @param scope long-lived scope used to keep [books] hot for the lifetime of the connection.
 */
internal class LocalFolderLibrary(
    override val descriptor: LibraryDescriptor,
    private val libraryFolder: LibraryFolder,
    scope: CoroutineScope,
) : Library {
    override val capabilities: LibraryCapabilities = LibraryCapabilities.fromRole(descriptor.role)

    // Seed with the folder's current snapshot so the first read matches existing behaviour (no
    // transient empty frame on top of an already-scanned folder), then mirror future updates.
    private val booksState =
        MutableStateFlow(libraryFolder.items.value.map(LibraryFolderItem::toLibraryEntry))
    override val books: StateFlow<List<LibraryEntry>> = booksState.asStateFlow()

    init {
        scope.launch {
            libraryFolder.items.collect { items ->
                booksState.value = items.map(LibraryFolderItem::toLibraryEntry)
            }
        }
    }

    // A local folder is always reachable; there is no connection to lose.
    override val connectionState: StateFlow<LibraryConnectionState> =
        ConstantConnectionState.connected

    override suspend fun refresh() {
        libraryFolder.refresh()
    }

    override suspend fun open(id: LibraryBookId): Result<OpenableDocument> =
        runCatching {
            val item =
                libraryFolder.items.value.firstOrNull { it.id == id.value }
                    ?: error("No book with id '${id.value}' in library '${descriptor.id.value}'")
            OpenableDocument(
                localPath = item.uri,
                // Content-addressed identity is not computed in M1.
                identity = null,
                // The owner can edit their own local copy.
                readOnly = false,
            )
        }

    override suspend fun addBook(src: String): Result<LibraryEntry> = libraryFolder.addCopy(src).map(LibraryFolderItem::toLibraryEntry)

    // removeBook / replaceBook keep the Library defaults (NotLibrarianException): the existing
    // LibraryFolder port exposes neither removal nor replacement, so there is nothing to delegate
    // to. They are wired when the LibraryFolder port grows those operations (see M5 brief).

    companion object {
        /** Builds a [LibraryId] for the local folder rooted at [rootPath]. */
        fun idForRoot(rootPath: String): LibraryId = LibraryId("local:$rootPath")
    }
}

/** Maps the legacy folder item to the abstract [LibraryEntry] (identity deferred to a later milestone). */
private fun LibraryFolderItem.toLibraryEntry(): LibraryEntry =
    LibraryEntry(
        libraryBookId = LibraryBookId(id),
        displayName = displayName,
        sizeBytes = sizeBytes,
        modifiedAt = modifiedAt,
        identity = null,
    )

/** Builds the [LibraryDescriptor] for a local folder library rooted at [rootPath]. */
internal fun localFolderDescriptor(rootPath: String): LibraryDescriptor =
    LibraryDescriptor(
        id = LocalFolderLibrary.idForRoot(rootPath),
        displayName = rootPath.substringAfterLast('/').substringAfterLast('\\').ifBlank { rootPath },
        kind = LibraryBackendKind.Local,
        // The user owns and fully controls their local folder.
        role = LibraryRole.Librarian,
    )
