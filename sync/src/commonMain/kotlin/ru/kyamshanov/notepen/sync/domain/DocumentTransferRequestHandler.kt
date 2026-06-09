package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.sync.infrastructure.WebSocketFileTransfer
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Handler for [NetworkMessage.DocumentOpenRequest]s sent by the opposite side of
 * a paired channel.
 *
 * Workflow per request:
 * 1. Look up the requested `documentId` in [provider]'s per-peer allow-list.
 *    Unknown → reply [NetworkMessage.DocumentNotFound] to the requester only.
 * 2. If allowed, stream the file with [WebSocketFileTransfer], tagging every
 *    chunk with the `documentId` and addressing chunks back to the requester.
 *
 * **Authorization**: [RemoteCatalogProvider.isAllowed] IS the allow-list —
 * anything not in the snapshot the requesting peer was served is denied,
 * even if a path to it exists on disk.
 *
 * Multi-doc safe: each request runs as an independent coroutine, so the device
 * can fan out several transfers in parallel across peers.
 */
class DocumentTransferRequestHandler private constructor(
    private val server: PeerServer?,
    private val client: SyncClient?,
    private val provider: RemoteCatalogProvider,
) {
    constructor(
        server: PeerServer,
        provider: RemoteCatalogProvider,
    ) : this(server = server, client = null, provider = provider)

    constructor(
        client: SyncClient,
        provider: RemoteCatalogProvider,
    ) : this(server = null, client = client, provider = provider)

    /** Start listening for requests; runs until [scope] is cancelled. */
    fun start(scope: CoroutineScope) {
        server?.let { peerServer ->
            scope.launch {
                peerServer.incomingMessages.collect { peerMessage ->
                    val msg = peerMessage.message
                    if (msg !is NetworkMessage.DocumentOpenRequest) return@collect
                    scope.launch { handle(peerMessage.peer.id, msg) }
                }
            }
        }
        client?.let { syncClient ->
            scope.launch {
                syncClient.incomingMessages.collect { hostMessage ->
                    val msg = hostMessage.message
                    if (msg !is NetworkMessage.DocumentOpenRequest) return@collect
                    scope.launch { handle(hostMessage.host.id, msg) }
                }
            }
        }
    }

    private suspend fun handle(
        peerId: String,
        request: NetworkMessage.DocumentOpenRequest,
    ) {
        val uri = provider.resolveUri(peerId, request.documentId)
        if (uri == null || !provider.isAllowed(peerId, request.documentId)) {
            logger.warn { "DocumentOpenRequest denied for $peerId: ${request.documentId}" }
            send(
                peerId,
                NetworkMessage.DocumentNotFound(
                    documentId = request.documentId,
                    reason = "Unknown documentId — not in last published catalog",
                ),
            )
            return
        }
        val transferId = "tx-${Random.nextLong().toString(16)}"
        logger.info { "Streaming '$uri' for doc=${request.documentId} to peer=$peerId (transferId=$transferId)" }
        runCatching {
            WebSocketFileTransfer(
                server = server,
                client = client,
                peerId = peerId.takeIf { server != null },
                hostId = peerId.takeIf { client != null },
            )
                .send(sourcePath = uri, transferId = transferId, documentId = request.documentId)
                .collect { /* progress ignored on host */ }
        }.onFailure { e ->
            logger.warn { "Stream failed for ${request.documentId}: ${e::class.simpleName}: ${e.message}" }
            runCatching {
                send(
                    peerId,
                    NetworkMessage.DocumentNotFound(
                        documentId = request.documentId,
                        reason = "Read error: ${e::class.simpleName}",
                    ),
                )
            }
        }
    }

    private suspend fun send(
        peerId: String,
        message: NetworkMessage,
    ) {
        when {
            server != null -> server.send(peerId, message)
            client != null -> client.send(peerId, message)
        }
    }
}
