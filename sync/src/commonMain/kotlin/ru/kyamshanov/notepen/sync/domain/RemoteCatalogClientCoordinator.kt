package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.RemoteCatalogCache
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

private val logger = KotlinLogging.logger {}

/**
 * Tablet-side glue that keeps a multi-host [RemoteCatalogCache] in sync with
 * each connected host.
 *
 * Behaviour:
 * - On every new host appearing in [SyncClient.connectedHosts], send a
 *   [NetworkMessage.RemoteCatalogRequest] to that host only.
 * - Whenever a [NetworkMessage.RemoteCatalogResponse] arrives, update the
 *   cache entry for the originating host.
 * - When a host disappears from [SyncClient.connectedHosts], drop its entry
 *   from the cache so the UI doesn't keep showing stale items from a peer
 *   that the user explicitly disconnected.
 */
class RemoteCatalogClientCoordinator(
    private val client: SyncClient,
    private val cache: RemoteCatalogCache,
) {

    /** Starts the request/response loop. Cancelling [scope] stops it. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            var lastKnown = emptySet<String>()
            client.connectedHosts.distinctUntilChanged().collect { hosts ->
                val current = hosts.map { it.id }.toSet()
                val arrivals = hosts.filter { it.id !in lastKnown }
                val departures = lastKnown - current
                lastKnown = current
                for (host in arrivals) {
                    logger.info { "Host ${host.name} connected — requesting RemoteCatalog" }
                    runCatching { client.send(host.id, NetworkMessage.RemoteCatalogRequest) }
                        .onFailure { logger.warn { "RemoteCatalogRequest to ${host.id} failed: ${it::class.simpleName}" } }
                }
                for (hostId in departures) {
                    cache.clear(hostId)
                }
            }
        }
        scope.launch {
            client.incomingMessages.collect { hostMessage ->
                val msg = hostMessage.message
                if (msg is NetworkMessage.RemoteCatalogResponse) {
                    logger.info {
                        "Received RemoteCatalog from '${hostMessage.host.name}': " +
                            "${msg.catalog.recent.size} recents, ${msg.catalog.folders.size} folders"
                    }
                    cache.update(hostMessage.host, msg.catalog)
                }
            }
        }
    }
}
