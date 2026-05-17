package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.port.RemoteCatalogCache
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

private val logger = KotlinLogging.logger {}

/**
 * Tablet-side glue that keeps a [RemoteCatalogCache] in sync with the host.
 *
 * Behaviour:
 * - On every transition into [PairingState.Connected], send a
 *   [NetworkMessage.RemoteCatalogRequest].
 * - Whenever a [NetworkMessage.RemoteCatalogResponse] arrives, update the cache.
 *
 * The cache itself outlives connection drops — Phase 5 will rely on that so
 * the Remote section can render from the last-known catalog while offline.
 */
class RemoteCatalogClientCoordinator(
    private val client: SyncClient,
    private val cache: RemoteCatalogCache,
) {

    /** Starts the request/response loop. Cancelling [scope] stops it. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            client.state
                .distinctUntilChanged()
                .collect { st ->
                    if (st is PairingState.Connected) {
                        logger.info { "Pairing connected — requesting RemoteCatalog" }
                        runCatching { client.send(NetworkMessage.RemoteCatalogRequest) }
                            .onFailure { logger.warn { "RemoteCatalogRequest failed: ${it::class.simpleName}" } }
                    }
                }
        }
        scope.launch {
            client.incomingMessages
                .filterIsInstance<NetworkMessage.RemoteCatalogResponse>()
                .collect { msg ->
                    logger.info {
                        "Received RemoteCatalog from '${msg.catalog.hostName}': " +
                            "${msg.catalog.recent.size} recents, ${msg.catalog.folders.size} folders"
                    }
                    cache.update(msg.catalog)
                }
        }
    }
}
