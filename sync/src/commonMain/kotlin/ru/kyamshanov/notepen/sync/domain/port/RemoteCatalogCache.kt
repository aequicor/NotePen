package ru.kyamshanov.notepen.sync.domain.port

import kotlinx.coroutines.flow.StateFlow
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog

/**
 * Tablet-side cache of the last [RemoteCatalog] the host sent us.
 *
 * Phase 2 uses an in-memory implementation. Phase 5 (offline mode) will
 * upgrade this to a persistent store so the Remote section stays visible
 * across app restarts and disconnects.
 */
interface RemoteCatalogCache {

    /**
     * Current cached catalog, or `null` if we never received one. Hot stream —
     * the UI subscribes and re-renders whenever a fresh
     * [ru.kyamshanov.notepen.sync.domain.model.NetworkMessage.RemoteCatalogResponse]
     * arrives.
     */
    val catalog: StateFlow<RemoteCatalog?>

    /** Replaces the cached catalog with [value] (or `null` to clear). */
    suspend fun update(value: RemoteCatalog?)
}
