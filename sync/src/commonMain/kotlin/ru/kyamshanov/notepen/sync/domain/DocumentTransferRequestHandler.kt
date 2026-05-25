package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.infrastructure.WebSocketFileTransfer
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Host-side handler for [NetworkMessage.DocumentOpenRequest]s.
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
 * Multi-doc safe: each request runs as an independent coroutine, so the host
 * can fan out several transfers in parallel across peers.
 */
class DocumentTransferRequestHandler(
    private val server: PeerServer,
    private val provider: RemoteCatalogProvider,
) {
    /** Start listening for requests; runs until [scope] is cancelled. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            server.incomingMessages.collect { peerMessage ->
                val msg = peerMessage.message
                if (msg !is NetworkMessage.DocumentOpenRequest) return@collect
                scope.launch { handle(peerMessage.peer.id, msg) }
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
            server.send(
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
            WebSocketFileTransfer(server = server, peerId = peerId)
                .send(sourcePath = uri, transferId = transferId, documentId = request.documentId)
                .collect { /* progress ignored on host */ }
        }.onFailure { e ->
            logger.warn { "Stream failed for ${request.documentId}: ${e::class.simpleName}: ${e.message}" }
            runCatching {
                server.send(
                    peerId,
                    NetworkMessage.DocumentNotFound(
                        documentId = request.documentId,
                        reason = "Read error: ${e::class.simpleName}",
                    ),
                )
            }
        }
    }
}
