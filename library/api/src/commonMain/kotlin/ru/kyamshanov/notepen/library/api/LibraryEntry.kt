package ru.kyamshanov.notepen.library.api

import ru.kyamshanov.notepen.document.domain.model.CanonicalBookId

/**
 * A single book as listed by one [Library].
 *
 * @property libraryBookId per-library locator of the book.
 * @property displayName human-readable title shown in the UI.
 * @property sizeBytes file size in bytes, or `null` when the backend does not report it.
 * @property modifiedAt last-modified time in epoch milliseconds, or `null` when unknown.
 * @property identity canonical content-addressed identity ([CanonicalBookId] from `:shared`),
 *   or `null` when not yet computed.
 */
public data class LibraryEntry(
    public val libraryBookId: LibraryBookId,
    public val displayName: String,
    public val sizeBytes: Long? = null,
    public val modifiedAt: Long? = null,
    public val identity: CanonicalBookId? = null,
)

/**
 * A book viewed across all connected libraries, with the set of libraries that hold it.
 *
 * Produced by [LibraryRegistry.mergedBooks] after deduplicating by canonical identity.
 *
 * @property entry a representative [LibraryEntry] for the book.
 * @property libraryIds the libraries that hold this book.
 */
public data class MergedLibraryEntry(
    public val entry: LibraryEntry,
    public val libraryIds: List<LibraryId>,
)
