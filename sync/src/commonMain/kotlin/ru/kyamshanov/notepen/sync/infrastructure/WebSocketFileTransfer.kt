package ru.kyamshanov.notepen.sync.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.FileTransfer
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.sync.domain.port.TransferProgress

private const val CHUNK_SIZE_BYTES = 64 * 1024 // 64 KB
private val logger = KotlinLogging.logger {}

/**
 * [FileTransfer] implementation that serialises file bytes as Base64-encoded
 * [NetworkMessage.FileChunk]s sent over the active WebSocket connection.
 *
 * Both [PeerServer] and [SyncClient] are accepted so the same class works on
 * either side of the channel; exactly one of them must be non-null.
 *
 * When configured with [server], a non-null [peerId] addresses the transfer
 * to that single peer; `null` falls back to [PeerServer.broadcast] for the
 * rare cases where every peer should receive the bytes.
 */
class WebSocketFileTransfer(
    private val server: PeerServer? = null,
    private val client: SyncClient? = null,
    private val peerId: String? = null,
    private val hostId: String? = null,
) : FileTransfer {

    init {
        require((server != null) xor (client != null)) {
            "Exactly one of server or client must be provided"
        }
    }

    override fun send(sourcePath: String, transferId: String): Flow<TransferProgress> =
        send(sourcePath = sourcePath, transferId = transferId, documentId = "")

    /**
     * Sends the file at [sourcePath] tagged with [documentId]. The id propagates
     * to [NetworkMessage.FileTransferStart] and every [NetworkMessage.FileChunk]
     * so the receiver can route parallel transfers.
     */
    fun send(
        sourcePath: String,
        transferId: String,
        documentId: String,
    ): Flow<TransferProgress> = flow {
        val bytes = okio_readBytes(sourcePath)
        val total = bytes.size.toLong()
        val chunkCount = (bytes.size + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES
        val fileName = sourcePath.substringAfterLast('/').substringAfterLast('\\')

        val header = NetworkMessage.FileTransferStart(
            transferId = transferId,
            fileName = fileName,
            totalChunks = chunkCount,
            totalSize = total,
            sha256 = "",
            documentId = documentId,
        )
        dispatch(header)

        var sent = 0L
        for (i in 0 until chunkCount) {
            val start = i * CHUNK_SIZE_BYTES
            val end = minOf(start + CHUNK_SIZE_BYTES, bytes.size)
            val chunk = bytes.copyOfRange(start, end)
            val encoded = encodeBase64(chunk)
            val msg = NetworkMessage.FileChunk(
                transferId = transferId,
                fileName = fileName,
                chunkIndex = i,
                totalChunks = chunkCount,
                dataBase64 = encoded,
                documentId = documentId,
            )
            dispatch(msg)
            sent += (end - start)
            emit(TransferProgress(transferred = sent, total = total))
            logger.debug { "Sent chunk ${i + 1}/$chunkCount for $transferId (doc=$documentId)" }
        }
    }

    private suspend fun dispatch(message: NetworkMessage) {
        when {
            server != null && peerId != null -> server.send(peerId, message)
            server != null -> server.broadcast(message)
            client != null && hostId != null -> client.send(hostId, message)
            client != null -> client.broadcast(message)
        }
    }

    override suspend fun receive(transferId: String, destPath: String) {
        // Full chunked-receive flow is handled by [FileTransferReceiver] which
        // subscribes to the active session's incomingMessages flow.
        logger.info { "WebSocketFileTransfer.receive() is a no-op; use FileTransferReceiver" }
    }
}

/** Reads the file at [path] and returns its bytes. Platform-provided via expect/actual. */
expect fun okio_readBytes(path: String): ByteArray

/** Writes [bytes] to the file at [path], creating parent directories. */
expect fun okio_writeBytes(path: String, bytes: ByteArray)

/** True when a regular file exists at [path]. Used for offline cache lookups. */
expect fun okio_exists(path: String): Boolean

/** Best-effort: deletes the file at [path]. Returns true on success. No-op if it doesn't exist. */
expect fun okio_delete(path: String): Boolean

/** Base64-encodes [bytes]. Platform-provided via expect/actual. */
expect fun encodeBase64(bytes: ByteArray): String

/** Base64-decodes [encoded]. Platform-provided via expect/actual. */
expect fun decodeBase64(encoded: String): ByteArray
