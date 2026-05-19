package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
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
 * - Cache entries are intentionally **not** cleared on departure — the main
 *   screen needs to keep the peer tile visible (marked offline) so the user
 *   can still browse documents that were already cached locally.
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
                lastKnown = current
                for (host in arrivals) {
                    logger.info { "Host ${host.name} connected — requesting RemoteCatalog" }
                    runCatching { client.send(host.id, NetworkMessage.RemoteCatalogRequest) }
                        .onFailure { logger.warn { "RemoteCatalogRequest to ${host.id} failed: ${it::class.simpleName}" } }
                }
            }
        }
        scope.launch {
            client.incomingMessages.collect { hostMessage ->
                when (val msg = hostMessage.message) {
                    is NetworkMessage.RemoteCatalogResponse -> {
                        logger.info {
                            "Received RemoteCatalog from '${hostMessage.host.name}': " +
                                "${msg.catalog.recent.size} recents, ${msg.catalog.folders.size} folders"
                        }
                        cache.update(hostMessage.host, msg.catalog)
                    }
                    is NetworkMessage.RemoteCatalogChanged -> {
                        logger.info {
                            "Host '${hostMessage.host.name}' signalled catalog change — re-requesting"
                        }
                        runCatching {
                            client.send(hostMessage.host.id, NetworkMessage.RemoteCatalogRequest)
                        }.onFailure {
                            logger.warn {
                                "Re-request after RemoteCatalogChanged failed: ${it::class.simpleName}"
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

/**
 * Mirror of [RemoteCatalogClientCoordinator] for the host side. Keeps a
 * per-client cache of [ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog]
 * up to date — the host's main screen renders one tile per connected client.
 *
 * Behaviour is identical: request on arrival, update on response, drop on
 * departure. The only difference is the transport (server vs client).
 */
class RemoteCatalogHostCoordinator(
    private val server: PeerServer,
    private val cache: RemoteCatalogCache,
) {

    /** Starts the request/response loop. Cancelling [scope] stops it. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            var lastKnown = emptySet<String>()
            server.connectedPeers.distinctUntilChanged().collect { peers ->
                val current = peers.map { it.id }.toSet()
                val arrivals = peers.filter { it.id !in lastKnown }
                lastKnown = current
                for (peer in arrivals) {
                    logger.info { "Peer ${peer.name} connected — requesting RemoteCatalog" }
                    runCatching { server.send(peer.id, NetworkMessage.RemoteCatalogRequest) }
                        .onFailure { logger.warn { "RemoteCatalogRequest to ${peer.id} failed: ${it::class.simpleName}" } }
                }
            }
        }
        scope.launch {
            server.incomingMessages.collect { peerMessage ->
                when (val msg = peerMessage.message) {
                    is NetworkMessage.RemoteCatalogResponse -> {
                        logger.info {
                            "Received RemoteCatalog from peer '${peerMessage.peer.name}': " +
                                "${msg.catalog.recent.size} recents, ${msg.catalog.folders.size} folders"
                        }
                        cache.update(peerMessage.peer, msg.catalog)
                    }
                    is NetworkMessage.RemoteCatalogChanged -> {
                        logger.info {
                            "Peer '${peerMessage.peer.name}' signalled catalog change — re-requesting"
                        }
                        runCatching {
                            server.send(peerMessage.peer.id, NetworkMessage.RemoteCatalogRequest)
                        }.onFailure {
                            logger.warn {
                                "Re-request after RemoteCatalogChanged failed: ${it::class.simpleName}"
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}
