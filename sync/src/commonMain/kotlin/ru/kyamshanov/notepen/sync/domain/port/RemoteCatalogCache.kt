package ru.kyamshanov.notepen.sync.domain.port

import kotlinx.coroutines.flow.StateFlow
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog

/**
 * Tablet-side cache of the most recent [RemoteCatalog] each connected host
 * sent us, keyed by `DeviceInfo.id`.
 *
 * Phase 2 uses an in-memory implementation. Phase 5 (offline mode) will
 * upgrade this to a persistent store so the Remote section stays visible
 * across app restarts and disconnects.
 */
interface RemoteCatalogCache {
    /** Snapshot keyed by host. New host catalogs are added on each `update`. */
    val catalogs: StateFlow<Map<DeviceInfo, RemoteCatalog>>

    /** Inserts or replaces the cached catalog for [host]. */
    suspend fun update(
        host: DeviceInfo,
        value: RemoteCatalog,
    )

    /** Removes the cached catalog for [hostId] (e.g. when the host disconnects). */
    suspend fun clear(hostId: String)
}
