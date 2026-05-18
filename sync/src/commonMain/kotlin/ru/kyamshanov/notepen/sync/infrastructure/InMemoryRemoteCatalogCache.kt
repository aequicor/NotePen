package ru.kyamshanov.notepen.sync.infrastructure

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import ru.kyamshanov.notepen.sync.domain.port.RemoteCatalogCache

/**
 * In-memory multi-host [RemoteCatalogCache].
 *
 * Phase 5 will replace this with a persistent SQLDelight-backed implementation
 * that survives app restarts so the Remote section can be shown in offline mode.
 */
class InMemoryRemoteCatalogCache : RemoteCatalogCache {

    private val _catalogs = MutableStateFlow<Map<DeviceInfo, RemoteCatalog>>(emptyMap())
    override val catalogs: StateFlow<Map<DeviceInfo, RemoteCatalog>> = _catalogs.asStateFlow()

    override suspend fun update(host: DeviceInfo, value: RemoteCatalog) {
        _catalogs.update { current ->
            // Re-key by host id: drop any earlier entry whose `DeviceInfo` had stale fields.
            val withoutOld = current.filterKeys { it.id != host.id }
            withoutOld + (host to value)
        }
    }

    override suspend fun clear(hostId: String) {
        _catalogs.update { current -> current.filterKeys { it.id != hostId } }
    }
}
