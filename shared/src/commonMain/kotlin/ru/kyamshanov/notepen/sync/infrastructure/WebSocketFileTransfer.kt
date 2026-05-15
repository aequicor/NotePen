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
 */
class WebSocketFileTransfer(
    private val server: PeerServer? = null,
    private val client: SyncClient? = null,
) : FileTransfer {

    init {
        require((server != null) xor (client != null)) {
            "Exactly one of server or client must be provided"
        }
    }

    override fun send(sourcePath: String, transferId: String): Flow<TransferProgress> = flow {
        val bytes = okio_readBytes(sourcePath)
        val total = bytes.size.toLong()
        val chunkCount = (bytes.size + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES
        val fileName = sourcePath.substringAfterLast('/')

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
            )
            server?.send(msg) ?: client?.send(msg)
            sent += (end - start)
            emit(TransferProgress(transferred = sent, total = total))
            logger.debug { "Sent chunk $i/$chunkCount for $transferId" }
        }
    }

    override suspend fun receive(transferId: String, destPath: String) {
        // Receives chunks from incomingMessages of the server or client.
        // The caller must collect server.incomingMessages / client.incomingMessages
        // and route FileChunk messages here. Full implementation in M6.
        logger.info { "receive() stub — implement chunked reassembly in M6" }
    }
}

/** Reads the file at [path] and returns its bytes. Platform-provided via expect/actual. */
expect fun okio_readBytes(path: String): ByteArray

/** Base64-encodes [bytes]. Platform-provided via expect/actual. */
expect fun encodeBase64(bytes: ByteArray): String

/** Base64-decodes [encoded]. Platform-provided via expect/actual. */
expect fun decodeBase64(encoded: String): ByteArray
