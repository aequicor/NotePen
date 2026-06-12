package ru.kyamshanov.notepen.sync.infrastructure

import ru.kyamshanov.notepen.sync.domain.model.ConnectFailureKind

/**
 * Best-effort classification of a transport-level connect failure into a
 * [ConnectFailureKind].
 *
 * Matches on exception class names and message text rather than concrete
 * types: the relevant exceptions differ per Ktor engine and platform (CIO
 * wraps java.net types on JVM/Android), and the Android 17 local-network
 * block carries no dedicated exception at all — a denied UDP socket reports
 * EPERM and a denied TCP connect just times out, indistinguishable from an
 * offline host except by message sniffing.
 */
internal fun classifyConnectFailure(error: Throwable?): ConnectFailureKind {
    // Classify across the whole cause chain, most specific kind first: an
    // Android 17 denial can surface as a timeout wrapping an EPERM cause, and
    // the EPERM is the actionable signal.
    val chain = causeChain(error)
    return when {
        chain.any(::isBlocked) -> ConnectFailureKind.LOCAL_NETWORK_BLOCKED
        chain.any(::isRefused) -> ConnectFailureKind.CONNECTION_REFUSED
        chain.any(::isTimeout) -> ConnectFailureKind.TIMED_OUT
        else -> ConnectFailureKind.UNKNOWN
    }
}

private const val MAX_CAUSE_DEPTH = 10

private fun causeChain(error: Throwable?): List<Throwable> {
    val chain = mutableListOf<Throwable>()
    var current: Throwable? = error
    while (current != null && chain.size < MAX_CAUSE_DEPTH && current !in chain) {
        chain += current
        current = current.cause
    }
    return chain
}

private fun isBlocked(error: Throwable): Boolean {
    val message = error.message.orEmpty()
    return message.contains("EPERM", ignoreCase = true) ||
        message.contains("permission denied", ignoreCase = true) ||
        message.contains("operation not permitted", ignoreCase = true)
}

private fun isRefused(error: Throwable): Boolean {
    val message = error.message.orEmpty()
    return message.contains("connection refused", ignoreCase = true) ||
        message.contains("ECONNREFUSED", ignoreCase = true)
}

private fun isTimeout(error: Throwable): Boolean {
    val name = error::class.simpleName.orEmpty()
    return name.contains("ConnectTimeout", ignoreCase = true) ||
        name.contains("SocketTimeout", ignoreCase = true) ||
        error.message.orEmpty().contains("timed out", ignoreCase = true) ||
        error.message.orEmpty().contains("timeout", ignoreCase = true)
}
