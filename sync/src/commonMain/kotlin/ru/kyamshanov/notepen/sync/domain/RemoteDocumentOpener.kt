package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.sync.infrastructure.FileTransferReceiver
import ru.kyamshanov.notepen.sync.infrastructure.okio_exists

private val logger = KotlinLogging.logger {}

/** Result of [RemoteDocumentOpener.open]. */
sealed class RemoteDocumentResult {
    data class Success(val documentId: String, val localPath: String) : RemoteDocumentResult()
    data class NotFound(val documentId: String, val reason: String) : RemoteDocumentResult()
    data class Timeout(val documentId: String) : RemoteDocumentResult()
    data class Failure(val documentId: String, val cause: Throwable) : RemoteDocumentResult()
}

/**
 * Tablet-side coordinator that converts a tap on a Remote-section tile into
 * a local PDF file ready to open in the editor.
 *
 * Sequence per call:
 * 1. Send [NetworkMessage.DocumentOpenRequest].
 * 2. Subscribe (in parallel) to:
 *    - [NetworkMessage.DocumentNotFound] for the same `documentId` — fail fast.
 *    - [NetworkMessage.FileTransferStart] + [NetworkMessage.FileChunk] tagged with
 *      the same `documentId` — feed to a [FileTransferReceiver] until the
 *      file lands on disk.
 * 3. Return the local path (`<destDir>/<fileName>`).
 *
 * Multiple parallel calls are safe: each subscribes its own filtered slice
 * of `client.incomingMessages` (a shared flow), so concurrent transfers
 * don't interfere.
 *
 * @param client peer client used to send the request and receive chunks.
 * @param destDir directory where received files are written.
 * @param requestTimeoutMs upper bound on the whole open flow.
 */
class RemoteDocumentOpener(
    private val client: SyncClient,
    private val destDir: String,
    private val requestTimeoutMs: Long = 60_000L,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun open(documentId: String, displayName: String? = null): RemoteDocumentResult {
        // Phase 5 offline-first: if the file is already in the local cache use it
        // directly. Keeps the Remote section functional without a live peer and
        // lets pending edits ride on top of the cached copy until reconnect.
        if (displayName != null) {
            val cachedPath = joinPath(destDir, displayName)
            if (okio_exists(cachedPath)) {
                logger.info { "Opening cached copy of $documentId at $cachedPath" }
                return RemoteDocumentResult.Success(documentId, cachedPath)
            }
        }
        logger.info { "Requesting remote document: $documentId" }
        val sendResult = runCatching {
            client.send(NetworkMessage.DocumentOpenRequest(documentId = documentId))
        }
        if (sendResult.isFailure) {
            return RemoteDocumentResult.Failure(documentId, sendResult.exceptionOrNull()!!)
        }

        val outcome: RemoteDocumentResult? = withTimeoutOrNull(requestTimeoutMs) {
            coroutineScope {
                // Race the host's rejection against the actual file delivery —
                // whichever completes first decides the outcome; select() cancels
                // the loser automatically.
                val notFound = async {
                    client.incomingMessages
                        .filterIsInstance<NetworkMessage.DocumentNotFound>()
                        .filter { it.documentId == documentId }
                        .first()
                }
                val received = async {
                    val incoming = merge(
                        client.incomingMessages
                            .filterIsInstance<NetworkMessage.FileTransferStart>()
                            .filter { it.documentId == documentId },
                        client.incomingMessages
                            .filterIsInstance<NetworkMessage.FileChunk>()
                            .filter { it.documentId == documentId },
                    )
                    FileTransferReceiver(incoming = incoming, destDir = destDir).awaitFile()
                }
                val winner: RemoteDocumentResult = select {
                    notFound.onAwait { msg ->
                        RemoteDocumentResult.NotFound(documentId, msg.reason)
                    }
                    received.onAwait { file ->
                        RemoteDocumentResult.Success(documentId, file.destPath)
                    }
                }
                notFound.cancel()
                received.cancel()
                winner
            }
        }
        return outcome ?: RemoteDocumentResult.Timeout(documentId).also {
            logger.warn { "Timed out waiting for $documentId" }
        }
    }
}

private fun joinPath(dir: String, name: String): String {
    val sep = if (dir.contains('\\')) "\\" else "/"
    return if (dir.endsWith('/') || dir.endsWith('\\')) "$dir$name" else "$dir$sep$name"
}

