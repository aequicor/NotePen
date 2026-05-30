package ru.kyamshanov.notepen.library.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.document.domain.model.CanonicalBookId
import ru.kyamshanov.notepen.document.domain.port.DocumentIdentityProvider
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
 * operations are available.
 *
 * ## Canonical identity (M6 — enables cross-library dedup)
 * The local library is the one place where computing the content-addressed
 * [CanonicalBookId] is cheap enough to do eagerly: the injected [identityProvider] caches the
 * `sha256` in a `.notepen.id` sidecar, so it is hashed at most once per file across runs.
 *
 * Identities are filled **asynchronously** so the shelf is never blocked on hashing the whole
 * library: each `items` snapshot is published immediately with `identity = null`, then a background
 * job resolves [DocumentIdentityProvider.identityForPath] per file and re-publishes each entry with
 * its identity as it lands. On a warm sidecar cache this resolves almost instantly. Failures (e.g. a
 * file deleted mid-scan) leave that entry's identity `null` — it simply won't participate in dedup.
 *
 * @param descriptor the immutable description of this library (id derived from the root path).
 * @param libraryFolder the underlying local folder port providing the reactive book listing.
 * @param identityProvider computes/caches the canonical id; `null` disables identity population
 *   (entries keep `identity = null`, matching pre-M6 behaviour).
 * @param scope long-lived scope used to keep [books] hot and run identity resolution.
 */
internal class LocalFolderLibrary(
    override val descriptor: LibraryDescriptor,
    private val libraryFolder: LibraryFolder,
    private val identityProvider: DocumentIdentityProvider?,
    private val scope: CoroutineScope,
) : Library {
    override val capabilities: LibraryCapabilities = LibraryCapabilities.fromRole(descriptor.role)

    // Seed with the folder's current snapshot so the first read matches existing behaviour (no
    // transient empty frame on top of an already-scanned folder), then mirror future updates.
    private val booksState =
        MutableStateFlow(libraryFolder.items.value.map(LibraryFolderItem::toLibraryEntry))
    override val books: StateFlow<List<LibraryEntry>> = booksState.asStateFlow()

    /** Resolved canonical id per book uri, so a re-scan re-uses already-computed identities. */
    private val identityByUri = mutableMapOf<String, CanonicalBookId>()

    init {
        scope.launch {
            libraryFolder.items.collect { items ->
                booksState.value =
                    items.map { item -> item.toLibraryEntry().withCachedIdentity() }
                resolveIdentities(items)
            }
        }
    }

    /** Returns a copy of this entry stamped with a previously-resolved identity, if any. */
    private fun LibraryEntry.withCachedIdentity(): LibraryEntry =
        identityByUri[libraryBookIdToUri(libraryBookId.value)]?.let { copy(identity = it) } ?: this

    private fun libraryBookIdToUri(id: String): String = libraryFolder.items.value.firstOrNull { it.id == id }?.uri ?: id

    /**
     * Resolves canonical ids for [items] off the shelf path. Each resolved id is cached and patched
     * into [booksState] individually, so a slow file never holds up the rest of the shelf.
     */
    private fun resolveIdentities(items: List<LibraryFolderItem>) {
        val provider = identityProvider ?: return
        items.forEach { item ->
            if (identityByUri.containsKey(item.uri)) {
                patchIdentity(item.id, identityByUri.getValue(item.uri))
                return@forEach
            }
            scope.launch {
                val canonical =
                    runCatching { provider.identityForPath(item.uri) }.getOrNull()?.canonicalId
                        ?: return@launch
                identityByUri[item.uri] = canonical
                patchIdentity(item.id, canonical)
            }
        }
    }

    /** Patches the [identity] of the entry with the given book id in [booksState], if still present. */
    private fun patchIdentity(
        bookId: String,
        identity: CanonicalBookId,
    ) {
        booksState.value =
            booksState.value.map { entry ->
                if (entry.libraryBookId.value == bookId && entry.identity != identity) {
                    entry.copy(identity = identity)
                } else {
                    entry
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
