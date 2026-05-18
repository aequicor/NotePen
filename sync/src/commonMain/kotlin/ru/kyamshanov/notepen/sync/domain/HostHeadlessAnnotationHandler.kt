package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.annotation.domain.port.AnnotationRepository
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.PeerServer

private val logger = KotlinLogging.logger {}

/**
 * Host-side handler that fulfils tablet-originated
 * [NetworkMessage.SaveRequest] and [NetworkMessage.AnnotationSnapshotRequest]s
 * without requiring the host to open the document in its own `DetailsContent`.
 *
 * Replies (`SaveResult`, `AnnotationSnapshot`, `DocumentNotFound`) are sent
 * back to the **requesting peer** only.
 */
class HostHeadlessAnnotationHandler(
    private val server: PeerServer,
    private val provider: RemoteCatalogProvider,
    private val projection: HostAnnotationProjection,
    private val repository: AnnotationRepository,
) {

    /** Starts the request loop; runs until [scope] is cancelled. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            server.incomingMessages.collect { peerMessage ->
                when (val msg = peerMessage.message) {
                    is NetworkMessage.SaveRequest ->
                        scope.launch { handleSave(peerMessage.peer.id, msg, scope) }
                    is NetworkMessage.AnnotationSnapshotRequest ->
                        scope.launch { handleSnapshot(peerMessage.peer.id, msg, scope) }
                    else -> Unit
                }
            }
        }
    }

    private suspend fun handleSave(peerId: String, req: NetworkMessage.SaveRequest, scope: CoroutineScope) {
        val documentId = req.documentId
        if (documentId.isEmpty()) {
            logger.warn { "Headless: SaveRequest id=${req.requestId} missing documentId, ignoring" }
            return
        }
        val hostUri = provider.resolveUri(peerId, documentId)
        if (hostUri == null) {
            logger.warn { "Headless save denied: $documentId not in catalog for $peerId" }
            server.send(
                peerId,
                NetworkMessage.DocumentNotFound(
                    documentId = documentId,
                    reason = "Unknown documentId — not in last published catalog",
                ),
            )
            server.send(
                peerId,
                NetworkMessage.SaveResult(
                    requestId = req.requestId,
                    success = false,
                    errorMessage = "Unknown documentId",
                    documentId = documentId,
                ),
            )
            return
        }
        projection.follow(documentId, scope)
        val state = projection.stateOf(documentId)
        if (state == null) {
            server.send(
                peerId,
                NetworkMessage.SaveResult(
                    requestId = req.requestId,
                    success = false,
                    errorMessage = "No state available",
                    documentId = documentId,
                ),
            )
            return
        }
        val result = repository.save(
            pdfPath = hostUri,
            annotations = state.pages,
            scale = state.scale,
            pen = state.pen,
            marker = state.marker,
            eraser = state.eraser,
            currentPage = state.currentPage,
            currentPageOffset = state.currentPageOffset,
        )
        logger.info {
            "Headless save id=${req.requestId} doc=$documentId success=${result.isSuccess}"
        }
        server.send(
            peerId,
            NetworkMessage.SaveResult(
                requestId = req.requestId,
                success = result.isSuccess,
                errorMessage = result.exceptionOrNull()?.message,
                documentId = documentId,
            ),
        )
    }

    private suspend fun handleSnapshot(
        peerId: String,
        req: NetworkMessage.AnnotationSnapshotRequest,
        scope: CoroutineScope,
    ) {
        val documentId = req.documentId
        if (documentId.isEmpty()) {
            logger.warn { "Headless: AnnotationSnapshotRequest missing documentId, ignoring" }
            return
        }
        if (provider.resolveUri(peerId, documentId) == null) {
            logger.warn { "Headless snapshot denied: $documentId not in catalog for $peerId" }
            server.send(
                peerId,
                NetworkMessage.DocumentNotFound(
                    documentId = documentId,
                    reason = "Unknown documentId — not in last published catalog",
                ),
            )
            return
        }
        projection.follow(documentId, scope)
        val snapshot = projection.snapshotDtos(documentId).orEmpty()
        logger.info { "Headless snapshot doc=$documentId strokes=${snapshot.size}" }
        server.send(
            peerId,
            NetworkMessage.AnnotationSnapshot(
                strokes = snapshot,
                documentId = documentId,
            ),
        )
    }
}
