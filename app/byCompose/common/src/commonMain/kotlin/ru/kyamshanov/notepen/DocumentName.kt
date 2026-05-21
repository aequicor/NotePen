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
