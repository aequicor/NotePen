package ru.kyamshanov.notepen.library.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.document.domain.model.CanonicalBookId
import ru.kyamshanov.notepen.library.api.Library
import ru.kyamshanov.notepen.library.api.LibraryBackend
import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryConnectionStore
import ru.kyamshanov.notepen.library.api.LibraryEntry
import ru.kyamshanov.notepen.library.api.LibraryId
import ru.kyamshanov.notepen.library.api.LibraryRegistry
import ru.kyamshanov.notepen.library.api.MergedLibraryEntry

/**
 * Default [LibraryRegistry]: aggregates one or more connected [Library] instances behind a single
 * reactive view, routing connection specs to the matching [LibraryBackend].
 *
 * [mergedBooks] is the cross-library shelf: books carrying the same canonical
 * [ru.kyamshanov.notepen.document.domain.model.CanonicalBookId] (e.g. the same file held by the
 * local library AND served by a LAN peer) collapse into a single [MergedLibraryEntry] tagged with
 * every [LibraryId] that holds it (M6 dedup). Entries without an identity stay distinct — the
 * absence of an id is never treated as "same book", so two un-hashed books can never falsely merge.
 *
 * ## Persistence
 * Successfully connected specs accepted by [shouldPersist] are written to the [connectionStore] so
 * they can be auto-reconnected at startup. By default **every** kind is persisted — including
 * [LibraryConnection.Local], now that local libraries are user-created (each its own folder + name)
 * rather than a single always-on default. [disconnect] removes the matching spec from the store.
 *
 * @param backends the available backends, one per [LibraryBackendKind].
 * @param scope long-lived scope keeping [libraries] / [mergedBooks] hot and owning each connected
 *   library's work.
 * @param ioDispatcher dispatcher for the (potentially blocking) connect/probe calls.
 * @param connectionStore durable store of saved connections; `null` disables persistence
 *   (e.g. in tests), so [savedConnections] returns empty and connects are not recorded.
 * @param shouldPersist predicate deciding which successfully-connected specs are saved; defaults to
 *   persisting every connection.
 */
public class DefaultLibraryRegistry(
    backends: List<LibraryBackend>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val connectionStore: LibraryConnectionStore? = null,
    private val shouldPersist: (LibraryConnection) -> Boolean = { true },
) : LibraryRegistry {
    private val backendsByKind = backends.associateBy { it.kind }
    private val connectMutex = Mutex()

    private val librariesState = MutableStateFlow<List<Library>>(emptyList())
    override val libraries: StateFlow<List<Library>> = librariesState.asStateFlow()

    private val mergedBooksState = MutableStateFlow<List<MergedLibraryEntry>>(emptyList())
    override val mergedBooks: StateFlow<List<MergedLibraryEntry>> = mergedBooksState.asStateFlow()

    /**
     * The spec each connected library was created from, keyed by [LibraryId]. Lets [disconnect]
     * (which only carries an id) remove the right entry from the [connectionStore].
     */
    private val specsById = mutableMapOf<LibraryId, LibraryConnection>()

    /**
     * The coroutine currently mirroring the combined book listing into [mergedBooksState]. Replaced
     * whenever the library set changes (the previous one is cancelled first), so [mergedBooks]
     * always reflects exactly the connected libraries.
     */
    private var mergeJob: Job? = null

    override suspend fun connect(spec: LibraryConnection): Result<Library> =
        connectMutex.withLock {
            val backend =
                backendForKind(spec)
                    ?: return Result.failure(
                        IllegalStateException("No backend registered for connection ${spec::class.simpleName}"),
                    )
            val result = withContext(ioDispatcher) { backend.connect(spec, scope) }
            result.onSuccess { library ->
                val id = library.descriptor.id
                specsById[id] = spec
                // Replace any existing library with the same id (idempotent reconnect).
                updateLibraries(librariesState.value.filterNot { it.descriptor.id == id } + library)
                if (shouldPersist(spec)) {
                    connectionStore?.add(spec)
                }
            }
        }

    override suspend fun disconnect(id: LibraryId) {
        connectMutex.withLock {
            updateLibraries(librariesState.value.filterNot { it.descriptor.id == id })
            specsById.remove(id)?.let { spec ->
                if (shouldPersist(spec)) {
                    connectionStore?.remove(spec)
                }
            }
        }
    }

    override suspend fun savedConnections(): List<LibraryConnection> = connectionStore?.load().orEmpty()

    /** Publishes a new library set and re-points [mergeJob] at the new combined book listing. */
    private fun updateLibraries(libs: List<Library>) {
        librariesState.value = libs
        mergeJob?.cancel()
        mergeJob =
            scope.launch {
                combineBooks(libs).collect { merged -> mergedBooksState.value = merged }
            }
    }

    private fun backendForKind(spec: LibraryConnection): LibraryBackend? =
        when (spec) {
            is LibraryConnection.Local -> backendsByKind[LibraryBackendKind.Local]
            is LibraryConnection.PeerLan -> backendsByKind[LibraryBackendKind.PeerLan]
            is LibraryConnection.GitHub -> backendsByKind[LibraryBackendKind.GitHub]
            is LibraryConnection.Cloud -> backendsByKind[LibraryBackendKind.Cloud]
        }
}

/**
 * Combines the [Library.books] flows of [libraries] into a single deduplicated merged listing.
 *
 * Dedup is by canonical identity ([LibraryEntry.identity]): entries that share an identity collapse
 * into one [MergedLibraryEntry] carrying the union of the [LibraryId]s that hold them. Entries with
 * a `null` identity are NOT merged — each stays its own [MergedLibraryEntry] (an unknown id is never
 * treated as equal to another unknown id), so two un-hashed books never falsely collapse.
 *
 * Order is deterministic and stable: books are emitted in first-appearance order while scanning
 * libraries in registry order then each library's book order. A duplicate seen later only widens the
 * already-placed entry's [MergedLibraryEntry.libraryIds] (preserving its original slot); it does not
 * reorder the listing. An empty library set yields an empty list immediately.
 */
private fun combineBooks(libraries: List<Library>): Flow<List<MergedLibraryEntry>> =
    if (libraries.isEmpty()) {
        flowOf(emptyList())
    } else {
        combine(libraries.map { lib -> lib.books }) { perLibraryBooks ->
            dedupByIdentity(libraries, perLibraryBooks.toList())
        }
    }

/**
 * Folds the per-library book lists into the deduplicated shelf. See [combineBooks] for the ordering
 * and merge contract. [perLibraryBooks] is index-aligned with [libraries].
 */
private fun dedupByIdentity(
    libraries: List<Library>,
    perLibraryBooks: List<List<LibraryEntry>>,
): List<MergedLibraryEntry> {
    val ordered = ArrayList<MergedLibraryEntry>()
    // Identity -> position in `ordered`, so a later duplicate widens the existing entry in place.
    val positionByIdentity = HashMap<CanonicalBookId, Int>()
    perLibraryBooks.forEachIndexed { index, books ->
        val libraryId = libraries[index].descriptor.id
        books.forEach { entry ->
            val identity = entry.identity
            val existingPos = identity?.let { positionByIdentity[it] }
            if (existingPos == null) {
                if (identity != null) positionByIdentity[identity] = ordered.size
                ordered.add(MergedLibraryEntry(entry = entry, libraryIds = listOf(libraryId)))
            } else {
                val existing = ordered[existingPos]
                if (libraryId !in existing.libraryIds) {
                    ordered[existingPos] = existing.copy(libraryIds = existing.libraryIds + libraryId)
                }
            }
        }
    }
    return ordered
}
