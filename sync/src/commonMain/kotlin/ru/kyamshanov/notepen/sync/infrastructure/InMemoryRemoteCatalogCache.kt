package ru.kyamshanov.notepen.sync.infrastructure

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import ru.kyamshanov.notepen.sync.domain.port.RemoteCatalogCache

/**
 * In-memory [RemoteCatalogCache] suitable for Phase 2.
 *
 * Phase 5 will replace this with a persistent SQLDelight-backed implementation
 * that survives app restarts so the Remote section can be shown in offline mode.
 */
class InMemoryRemoteCatalogCache : RemoteCatalogCache {

    private val _catalog = MutableStateFlow<RemoteCatalog?>(null)
    override val catalog: StateFlow<RemoteCatalog?> = _catalog.asStateFlow()

    override suspend fun update(value: RemoteCatalog?) {
        _catalog.value = value
    }
}
