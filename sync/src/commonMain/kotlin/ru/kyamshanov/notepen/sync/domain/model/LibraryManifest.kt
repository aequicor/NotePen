package ru.kyamshanov.notepen.sync.domain.model

/**
 * Stable, path-independent identifier of a book in the local library.
 *
 * Occupies the same opaque slot as the wire `documentId` string, so paired
 * clients keep treating it as an opaque token. Derived from the book's path
 * *relative to the library root*, so it survives host restarts and relocation
 * of the root itself. (Surviving a move of the file *within* the root requires
 * a persisted per-book id and is intentionally deferred.)
 */
@JvmInline
value class BookId(val value: String)

/**
 * A single book discovered under the library root.
 *
 * @property id stable identifier, see [BookId].
 * @property relativePath path relative to the library root — the identity
 *   source. Never absolute, so it cannot leak host filesystem layout to peers.
 * @property displayName human-readable name shown to peers (the file name).
 * @property fileSize size in bytes, or `null` if unknown.
 * @property modifiedAt last-modified epoch millis, used for ordering.
 */
data class LibraryBook(
    val id: BookId,
    val relativePath: String,
    val displayName: String,
    val fileSize: Long?,
    val modifiedAt: Long,
)

/** Immutable snapshot of the books currently present under the library root. */
data class LibraryManifest(
    val books: List<LibraryBook>,
)
