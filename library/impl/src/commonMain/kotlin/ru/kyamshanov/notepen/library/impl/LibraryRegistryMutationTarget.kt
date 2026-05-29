package ru.kyamshanov.notepen.library.impl

import ru.kyamshanov.notepen.library.api.Library
import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryBookId
import ru.kyamshanov.notepen.library.api.LibraryEntry
import ru.kyamshanov.notepen.library.api.LibraryId
import ru.kyamshanov.notepen.library.api.LibraryRegistry
import ru.kyamshanov.notepen.sync.domain.port.LibraryMutationOutcome
import ru.kyamshanov.notepen.sync.domain.port.LibraryMutationTarget

/**
 * Adapter bridging the sync layer's [LibraryMutationTarget] port to the real
 * [LibraryRegistry] — the seam that lets `:sync`'s `HostLibraryMutationHandler`
 * apply a remote librarian's mutation to one of this host's connected libraries
 * without `:sync` depending on `:library` (the dependency only runs
 * `:library:impl` → `:sync`).
 *
 * Resolution of `targetLibraryId`:
 *  - a non-empty id selects the connected [Library] whose [LibraryId] matches;
 *  - an empty id selects the host's single local-folder library (the common case
 *    — a librarian uploads to "the host's library" without naming it).
 *
 * The resolved library's mutating operations are called directly. The local
 * library's `books` StateFlow then updates, the registry's `mergedBooks` follows,
 * and the host's `RemoteCatalogProvider` re-serves the grown catalog — so paired
 * peers observe the new book reactively with no extra plumbing here.
 *
 * @param registryProvider returns the live registry. A provider (not the
 *   registry directly) lets the DI layer wire this before the registry is built
 *   in the same `main()` body.
 */
public class LibraryRegistryMutationTarget(
    private val registryProvider: () -> LibraryRegistry?,
) : LibraryMutationTarget {
    override suspend fun addBook(
        targetLibraryId: String,
        localPath: String,
        displayName: String,
    ): Result<LibraryMutationOutcome> =
        withLibrary(targetLibraryId) { library ->
            library.addBook(localPath).map { it.toOutcome() }
        }

    override suspend fun removeBook(
        targetLibraryId: String,
        libraryBookId: String,
    ): Result<LibraryMutationOutcome> =
        withLibrary(targetLibraryId) { library ->
            library.removeBook(LibraryBookId(libraryBookId)).map { LibraryMutationOutcome(newLibraryBookId = null) }
        }

    override suspend fun replaceBook(
        targetLibraryId: String,
        libraryBookId: String,
        localPath: String,
        displayName: String,
    ): Result<LibraryMutationOutcome> =
        withLibrary(targetLibraryId) { library ->
            library.replaceBook(LibraryBookId(libraryBookId), localPath).map { it.toOutcome() }
        }

    private suspend inline fun withLibrary(
        targetLibraryId: String,
        block: (Library) -> Result<LibraryMutationOutcome>,
    ): Result<LibraryMutationOutcome> =
        resolve(targetLibraryId)
            .mapCatching { library -> block(library).getOrThrow() }

    /** Resolves [targetLibraryId] to a mutable host library, or a [Result.failure] describing why not. */
    private fun resolve(targetLibraryId: String): Result<Library> {
        val registry =
            registryProvider() ?: return failure("Library registry unavailable on this host")
        val libs = registry.libraries.value
        val library =
            if (targetLibraryId.isEmpty()) {
                libs.firstOrNull { it.descriptor.kind == LibraryBackendKind.Local }
            } else {
                libs.firstOrNull { it.descriptor.id == LibraryId(targetLibraryId) }
            }
        return when {
            library == null && targetLibraryId.isEmpty() -> failure("No local library on this host to mutate")
            library == null -> failure("Unknown target library '$targetLibraryId'")
            // Defensive: never trust the wire — only librarian-capable libraries may be mutated.
            !library.capabilities.run { canAdd || canRemove || canReplace } ->
                failure("Library '$targetLibraryId' is read-only on this host")
            else -> Result.success(library)
        }
    }

    private fun <T> failure(message: String): Result<T> = Result.failure(IllegalStateException(message))
}

private fun LibraryEntry.toOutcome(): LibraryMutationOutcome = LibraryMutationOutcome(newLibraryBookId = libraryBookId.value)
