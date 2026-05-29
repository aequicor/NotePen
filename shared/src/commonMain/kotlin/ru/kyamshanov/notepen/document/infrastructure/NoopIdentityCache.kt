package ru.kyamshanov.notepen.document.infrastructure

import ru.kyamshanov.notepen.document.domain.port.IdentityCache

/**
 * [IdentityCache] that persists nothing — every [get] misses. Use to disable
 * the cross-run cache (tests, or environments without writable storage); the
 * provider's in-memory cache still serves repeated lookups within a session.
 */
object NoopIdentityCache : IdentityCache {
    override suspend fun get(
        path: String,
        fingerprint: String?,
    ): String? = null

    override suspend fun put(
        path: String,
        fingerprint: String?,
        sha256Hex: String,
    ) = Unit
}
