package ru.kyamshanov.notepen.library.api

import kotlin.jvm.JvmInline

/**
 * Stable identifier of a connected [Library] within a [LibraryRegistry].
 *
 * @property value an opaque, registry-unique string.
 */
@JvmInline
public value class LibraryId(
    public val value: String,
)

/**
 * A locator that identifies a single book **within one particular library**.
 *
 * This is backend-relative (e.g. a relative path for a local folder, a blob path for GitHub)
 * and is therefore only meaningful together with the owning [Library]. For cross-library
 * identity use the canonical content-addressed identity carried by [LibraryEntry].
 *
 * @property value an opaque, per-library locator string.
 */
@JvmInline
public value class LibraryBookId(
    public val value: String,
)
