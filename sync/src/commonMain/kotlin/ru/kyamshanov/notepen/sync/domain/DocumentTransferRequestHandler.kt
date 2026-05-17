package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlin.random.Random
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.infrastructure.WebSocketFileTransfer

private val logger = KotlinLogging.logger {}

/**
 * Host-side handler for [NetworkMessage.DocumentOpenRequest]s.
 *
 * Workflow per request:
 * 1. Look up [documentId][NetworkMessage.DocumentOpenRequest.documentId] in
 *    [provider]'s last-built catalog. Unknown → reply with
 *    [NetworkMessage.DocumentNotFound] (no disk access).
 * 2. If known, stream the file with [WebSocketFileTransfer], tagging every
 *    chunk with the [documentId] so the tablet can route it.
 *
 * **Authorization**: [provider.allowedDocumentIds][RemoteCatalogProvider.allowedDocumentIds]
 * IS the allow-list — anything not in the snapshot is denied, even if a path
 * to it exists on disk. This is the path-traversal defense the plan calls for.
 *
 * Multi-doc safe: each request runs as an independent coroutine, so the host
 * can fan out several transfers in parallel over the same WebSocket session.
 */
class DocumentTransferRequestHandler(
    private val server: PeerServer,
    private val provider: RemoteCatalogProvider,
) {

    /** Start listening for requests; runs until [scope] is cancelled. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            server.incomingMessages
                .filterIsInstance<NetworkMessage.DocumentOpenRequest>()
                .collect { request ->
                    scope.launch { handle(request) }
                }
        }
    }

    private suspend fun handle(request: NetworkMessage.DocumentOpenRequest) {
        val uri = provider.resolveUri(request.documentId)
        if (uri == null || request.documentId !in provider.allowedDocumentIds) {
            logger.warn { "DocumentOpenRequest denied: ${request.documentId} not in catalog" }
            server.send(
                NetworkMessage.DocumentNotFound(
                    documentId = request.documentId,
                    reason = "Unknown documentId — not in last published catalog",
                ),
            )
            return
        }
        val transferId = "tx-${Random.nextLong().toString(16)}"
        logger.info { "Streaming '$uri' for doc=${request.documentId} (transferId=$transferId)" }
        runCatching {
            WebSocketFileTransfer(server = server)
                .send(sourcePath = uri, transferId = transferId, documentId = request.documentId)
                .collect { /* progress ignored on host */ }
        }.onFailure { e ->
            logger.warn { "Stream failed for ${request.documentId}: ${e::class.simpleName}: ${e.message}" }
            runCatching {
                server.send(
                    NetworkMessage.DocumentNotFound(
                        documentId = request.documentId,
                        reason = "Read error: ${e::class.simpleName}",
                    ),
                )
            }
        }
    }
}
