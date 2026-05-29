package ru.kyamshanov.notepen

/**
 * Resolves a human-readable document name from a file path or URI.
 *
 * On Android a picked document is a `content://` URI whose last path segment is
 * an opaque, URL-encoded id (e.g. `document%3A12001`) — useless as a label. The
 * Android implementation queries the `ContentResolver` for the real display
 * name; desktop (and any non-`content://` path) falls back to the basename.
 *
 * Returns `null` when no usable name can be derived (caller substitutes a
 * fallback label).
 */
expect fun resolveDocumentDisplayName(filePathOrUri: String): String?

/**
 * Resolves the size, in bytes, of a document from a file path or URI.
 *
 * Used to strengthen recents de-duplication: Android SAF can hand back a
 * different `content://` URI for the same physical file across picks, so the
 * fuzzy-match safety net keys on display name + size. Without a size the net
 * cannot fire and a duplicate recents entry is created.
 *
 * On Android a picked document is a `content://` URI; the implementation queries
 * `OpenableColumns.SIZE` via the `ContentResolver`. Desktop (and any
 * non-`content://` path) reads the filesystem length.
 *
 * Returns `null` when the size cannot be determined.
 */
expect fun resolveDocumentSize(filePathOrUri: String): Long?
