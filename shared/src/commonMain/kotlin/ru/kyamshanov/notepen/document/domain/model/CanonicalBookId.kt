package ru.kyamshanov.notepen.document.domain.model

import kotlin.jvm.JvmInline

/**
 * Content-addressed identity of a document, derived from the full original
 * file bytes (a `sha256` hex digest).
 *
 * Two files with byte-identical content share the same [CanonicalBookId]
 * regardless of where they live on disk or which device holds them — this is
 * what lets sync address "the same book" across peers without agreeing on a
 * path. Contrast with a per-library locator (a relative path / blob key),
 * which is only meaningful inside one library.
 *
 * @property hex lowercase `sha256` hex digest of the file's full content
 *   (64 chars). Callers must pass a real digest; this type does not validate.
 */
@JvmInline
value class CanonicalBookId(
    val hex: String,
)
