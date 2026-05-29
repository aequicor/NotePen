package ru.kyamshanov.notepen.library.api

import ru.kyamshanov.notepen.document.domain.model.CanonicalBookId

/**
 * A book that has been materialized locally and is ready to open in the editor.
 *
 * Returned by [Library.open]; the backend is responsible for any download/caching required to
 * produce [localPath].
 *
 * @property localPath absolute path to the locally available document file.
 * @property identity canonical content-addressed identity ([CanonicalBookId] from `:shared`),
 *   or `null` when not yet computed.
 * @property readOnly whether the document is read-only (e.g. opened from a Reader-role library).
 */
public data class OpenableDocument(
    public val localPath: String,
    public val identity: CanonicalBookId?,
    public val readOnly: Boolean,
)
