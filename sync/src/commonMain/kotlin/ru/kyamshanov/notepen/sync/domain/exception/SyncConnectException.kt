package ru.kyamshanov.notepen.sync.domain.exception

import ru.kyamshanov.notepen.sync.domain.model.ConnectFailureKind

/**
 * Failure of a client→host connect attempt, carrying a [kind] so UI layers can
 * show an actionable message instead of the raw transport text.
 */
class SyncConnectException(
    val kind: ConnectFailureKind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
