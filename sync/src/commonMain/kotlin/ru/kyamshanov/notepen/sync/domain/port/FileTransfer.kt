package ru.kyamshanov.notepen.sync.domain.port

import kotlinx.coroutines.flow.Flow

/** Byte count transferred so far and total size in bytes. */
data class TransferProgress(
    val transferred: Long,
    val total: Long,
) {
    val fraction: Float get() = if (total > 0) transferred.toFloat() / total else 0f
}

/**
 * Port for chunked file transfer over an established [PeerServer] / [SyncClient] connection.
 *
 * Implementations split the source file into Base64-encoded [NetworkMessage.FileChunk]s
 * and send them over the WebSocket. The receiving side reassembles them and writes the
 * output file.
 */
interface FileTransfer {
    /**
     * Sends the file at [sourcePath] to the connected peer.
     *
     * @param sourcePath absolute path of the file to send
     * @param transferId unique identifier for this transfer (UUID)
     * @return [Flow] of [TransferProgress] until the transfer completes
     */
    fun send(
        sourcePath: String,
        transferId: String,
    ): Flow<TransferProgress>

    /**
     * Receives a file being sent by the peer and writes it to [destPath].
     *
     * Suspends until all chunks arrive or an error occurs.
     *
     * @param transferId must match the sender's [transferId]
     * @param destPath absolute path to write the received file
     */
    suspend fun receive(
        transferId: String,
        destPath: String,
    )
}
