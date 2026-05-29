package ru.kyamshanov.notepen.library.api

import kotlinx.coroutines.flow.StateFlow

/**
 * A single connected library, abstracting over its storage backend (local folder, LAN peer,
 * GitHub, cloud).
 *
 * Reading operations are available to every role; the mutating operations ([addBook], [removeBook],
 * [replaceBook]) require the [LibraryRole.Librarian] role and throw [NotLibrarianException] by
 * default. Backends that grant the Librarian role override them.
 */
public interface Library {
    /** Immutable description of this library (id, name, kind, role). */
    public val descriptor: LibraryDescriptor

    /** The mutating operations permitted for this library's role; see [LibraryCapabilities.fromRole]. */
    public val capabilities: LibraryCapabilities

    /** The current list of books, updated reactively as the backend reports changes. */
    public val books: StateFlow<List<LibraryEntry>>

    /** The live connection status of this library. */
    public val connectionState: StateFlow<LibraryConnectionState>

    /** Re-reads the book listing from the backend, updating [books]. */
    public suspend fun refresh()

    /**
     * Materializes the book identified by [id] locally and returns it as an [OpenableDocument].
     *
     * @return a success with the openable document, or a failure if the book is missing or the
     *   backend could not produce a local copy.
     */
    public suspend fun open(id: LibraryBookId): Result<OpenableDocument>

    /**
     * Adds the document at [src] to this library. Requires the [LibraryRole.Librarian] role.
     *
     * @param src a backend-understood source locator for the document to add.
     * @return a success with the created [LibraryEntry], or a failure on error.
     */
    public suspend fun addBook(src: String): Result<LibraryEntry> = throw NotLibrarianException()

    /**
     * Removes the book identified by [id] from this library. Requires the [LibraryRole.Librarian] role.
     */
    public suspend fun removeBook(id: LibraryBookId): Result<Unit> = throw NotLibrarianException()

    /**
     * Replaces the content of the book identified by [id] with the document at [src].
     * Requires the [LibraryRole.Librarian] role.
     *
     * @param src a backend-understood source locator for the replacement document.
     * @return a success with the updated [LibraryEntry], or a failure on error.
     */
    public suspend fun replaceBook(
        id: LibraryBookId,
        src: String,
    ): Result<LibraryEntry> = throw NotLibrarianException()
}
