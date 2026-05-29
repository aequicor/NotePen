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
import ru.kyamshanov.notepen.library.api.Library
import ru.kyamshanov.notepen.library.api.LibraryBackend
import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryEntry
import ru.kyamshanov.notepen.library.api.LibraryId
import ru.kyamshanov.notepen.library.api.LibraryRegistry
import ru.kyamshanov.notepen.library.api.MergedLibraryEntry

/**
 * Default [LibraryRegistry]: aggregates one or more connected [Library] instances behind a single
 * reactive view, routing connection specs to the matching [LibraryBackend].
 *
 * In M1 a single local-folder library is connected at startup, so [mergedBooks] flattens that one
 * library's books one-to-one (no cross-library dedup yet — that lands with canonical identity in a
 * later milestone). The combine logic is already written for N libraries.
 *
 * @param backends the available backends, one per [LibraryBackendKind].
 * @param scope long-lived scope keeping [libraries] / [mergedBooks] hot and owning each connected
 *   library's work.
 * @param ioDispatcher dispatcher for the (potentially blocking) connect/probe calls.
 */
public class DefaultLibraryRegistry(
    backends: List<LibraryBackend>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) : LibraryRegistry {
    private val backendsByKind = backends.associateBy { it.kind }
    private val connectMutex = Mutex()

    private val librariesState = MutableStateFlow<List<Library>>(emptyList())
    override val libraries: StateFlow<List<Library>> = librariesState.asStateFlow()

    private val mergedBooksState = MutableStateFlow<List<MergedLibraryEntry>>(emptyList())
    override val mergedBooks: StateFlow<List<MergedLibraryEntry>> = mergedBooksState.asStateFlow()

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
                // Replace any existing library with the same id (idempotent reconnect).
                updateLibraries(librariesState.value.filterNot { it.descriptor.id == id } + library)
            }
        }

    override suspend fun disconnect(id: LibraryId) {
        connectMutex.withLock {
            updateLibraries(librariesState.value.filterNot { it.descriptor.id == id })
        }
    }

    // M1: connections are wired explicitly at startup by the DI layer; nothing is persisted yet.
    // Connection persistence (JSON, atomic-write) lands in M2.
    override suspend fun savedConnections(): List<LibraryConnection> = emptyList()

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
 * Combines the [Library.books] flows of [libraries] into a single flat merged listing.
 *
 * Each emitted [MergedLibraryEntry] pairs a book entry with the libraries that hold it. In M1 each
 * book belongs to exactly one library (no canonical-identity dedup), so the result is the flat
 * concatenation of every library's books in library order. An empty library set yields an empty
 * list immediately.
 */
private fun combineBooks(libraries: List<Library>): Flow<List<MergedLibraryEntry>> =
    if (libraries.isEmpty()) {
        flowOf(emptyList())
    } else {
        combine(libraries.map { lib -> lib.books }) { perLibraryBooks ->
            buildList {
                perLibraryBooks.forEachIndexed { index, books ->
                    val libraryId = libraries[index].descriptor.id
                    books.forEach { entry: LibraryEntry ->
                        add(MergedLibraryEntry(entry = entry, libraryIds = listOf(libraryId)))
                    }
                }
            }
        }
    }
