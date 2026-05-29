package ru.kyamshanov.notepen.sync.domain.port

/**
 * Outcome of a host-side library mutation requested by a remote librarian.
 *
 * @property newLibraryBookId the per-library id of the book that was added or
 *   replaced, when the backend reports one; `null` for removals or when the
 *   backend has no stable id to return.
 */
data class LibraryMutationOutcome(
    val newLibraryBookId: String?,
)

/**
 * Host-side seam letting the sync layer apply library mutations **without**
 * depending on the `:library` modules.
 *
 * `:library:impl` depends on `:sync` (never the reverse), so the concrete
 * adapter that resolves `targetLibraryId` to a real `Library` and calls its
 * `addBook` / `removeBook` / `replaceBook` lives in `:library:impl` (or the app
 * DI layer) and implements this port. The sync layer only knows how to validate
 * the request, reassemble + verify the uploaded file, and hand a local path to
 * this target.
 *
 * Implementations resolve [targetLibraryId] to a concrete host library. An empty
 * [targetLibraryId] selects the host's single default local library. A failure
 * (unknown library, unsupported operation, I/O error) is reported as a
 * [Result.failure]; the handler maps it to a rejected
 * [ru.kyamshanov.notepen.sync.domain.model.NetworkMessage.LibraryMutationResult].
 */
interface LibraryMutationTarget {
    /**
     * Adds the file at [localPath] to the host library identified by
     * [targetLibraryId].
     *
     * @param targetLibraryId opaque host-library id; empty selects the default.
     * @param localPath absolute path to the already-verified uploaded file.
     * @param displayName human-readable name requested for the book.
     */
    suspend fun addBook(
        targetLibraryId: String,
        localPath: String,
        displayName: String,
    ): Result<LibraryMutationOutcome>

    /**
     * Removes the book [libraryBookId] from the host library [targetLibraryId].
     *
     * Backends without a delete operation return a [Result.failure] (the handler
     * surfaces it as an `"unsupported"` mutation result).
     */
    suspend fun removeBook(
        targetLibraryId: String,
        libraryBookId: String,
    ): Result<LibraryMutationOutcome>

    /**
     * Replaces the content of [libraryBookId] in [targetLibraryId] with the file
     * at [localPath].
     *
     * @param localPath absolute path to the already-verified uploaded file.
     * @param displayName human-readable name requested for the replacement.
     */
    suspend fun replaceBook(
        targetLibraryId: String,
        libraryBookId: String,
        localPath: String,
        displayName: String,
    ): Result<LibraryMutationOutcome>
}
