package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.merge
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
 * For every incoming request the handler:
 * 1. Resolves `documentId` → host file URI via [provider]. Unknown ids are
 *    answered with `success = false` / no snapshot.
 * 2. Asks [projection] to start following the document (lazy-loads from disk
 *    on first touch, then keeps state in sync with `mergedDeltas`).
 * 3. For save: serialises the projection state back to
 *    [AnnotationRepository.save] and replies with [NetworkMessage.SaveResult].
 *    For snapshot: emits [NetworkMessage.AnnotationSnapshot] built from
 *    [HostAnnotationProjection.snapshotDtos].
 *
 * Coexists peacefully with the host's `DetailsContent` write-path: the
 * projection itself sees both local edits (mirrored by [SyncEngine.applyLocal]
 * onto `mergedDeltas`) and peer edits, so persistence stays consistent
 * regardless of which side actually drew the strokes.
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
            merge(
                server.incomingMessages.filterIsInstance<NetworkMessage.SaveRequest>(),
                server.incomingMessages.filterIsInstance<NetworkMessage.AnnotationSnapshotRequest>(),
            ).collect { msg ->
                scope.launch {
                    when (msg) {
                        is NetworkMessage.SaveRequest -> handleSave(msg, scope)
                        is NetworkMessage.AnnotationSnapshotRequest -> handleSnapshot(msg, scope)
                        else -> Unit
                    }
                }
            }
        }
    }

    private suspend fun handleSave(req: NetworkMessage.SaveRequest, scope: CoroutineScope) {
        val documentId = req.documentId
        if (documentId.isEmpty()) {
            logger.warn { "Headless: SaveRequest id=${req.requestId} missing documentId, ignoring" }
            return
        }
        val hostUri = provider.resolveUri(documentId)
        if (hostUri == null) {
            logger.warn { "Headless save denied: $documentId not in catalog" }
            // Both signals so the tablet's DocumentStatusCoordinator can mark
            // orphan immediately regardless of which it listens for.
            server.send(
                NetworkMessage.DocumentNotFound(
                    documentId = documentId,
                    reason = "Unknown documentId — not in last published catalog",
                ),
            )
            server.send(
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
            NetworkMessage.SaveResult(
                requestId = req.requestId,
                success = result.isSuccess,
                errorMessage = result.exceptionOrNull()?.message,
                documentId = documentId,
            ),
        )
    }

    private suspend fun handleSnapshot(
        req: NetworkMessage.AnnotationSnapshotRequest,
        scope: CoroutineScope,
    ) {
        val documentId = req.documentId
        if (documentId.isEmpty()) {
            logger.warn { "Headless: AnnotationSnapshotRequest missing documentId, ignoring" }
            return
        }
        if (provider.resolveUri(documentId) == null) {
            logger.warn { "Headless snapshot denied: $documentId not in catalog" }
            server.send(
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
            NetworkMessage.AnnotationSnapshot(
                strokes = snapshot,
                documentId = documentId,
            ),
        )
    }
}
