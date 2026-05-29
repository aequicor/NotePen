package ru.kyamshanov.notepen.sync.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform
import ru.kyamshanov.notepen.sync.domain.documentIdToCacheFileName
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.TransferProgress

private val logger = KotlinLogging.logger {}

/**
 * Result returned by [FileTransferReceiver.awaitFile] when a transfer completes.
 */
data class ReceivedFile(
    val transferId: String,
    val fileName: String,
    val destPath: String,
    val totalBytes: Long,
)

/**
 * Consumes [NetworkMessage.FileTransferStart] + [NetworkMessage.FileChunk] from
 * an incoming-messages flow and writes the assembled bytes to disk.
 *
 * One instance handles one transfer at a time. If a new [NetworkMessage.FileTransferStart]
 * arrives mid-transfer it is silently ignored — callers must drive separate
 * receivers for parallel transfers (NotePen sync needs only one).
 *
 * @param incoming flow of messages from the active session
 * @param destDir directory where received files are written (must exist or be creatable)
 * @param onProgress optional progress callback called after each chunk
 */
class FileTransferReceiver(
    private val incoming: Flow<NetworkMessage>,
    private val destDir: String,
    private val onProgress: (TransferProgress) -> Unit = {},
) {
    /**
     * Suspends until a full transfer is received. Returns the absolute path of the
     * written file. Throws if the underlying flow terminates before completion.
     *
     * The destination file name is derived from the transfer's `documentId`
     * (or `fileName` for legacy empty ids) under [destDir] — the
     * cache-by-documentId scheme used by the host→client document download path.
     */
    suspend fun awaitFile(): ReceivedFile = receive { header -> joinPath(destDir, cacheNameFor(header)) }

    /**
     * Like [awaitFile] but writes the assembled bytes to the explicit [destPath]
     * instead of deriving a cache name. Used by the **client→host** upload path
     * (Librarian-over-LAN), where the host reassembles into a temp file before
     * verifying and handing it to the local library.
     */
    suspend fun awaitFileTo(destPath: String): ReceivedFile = receive { destPath }

    private suspend fun receive(destPathFor: (NetworkMessage.FileTransferStart) -> String): ReceivedFile {
        var header: NetworkMessage.FileTransferStart? = null
        val buffers = HashMap<Int, ByteArray>()

        return incoming
            .filter { it is NetworkMessage.FileTransferStart || it is NetworkMessage.FileChunk }
            .transform { msg ->
                when (msg) {
                    is NetworkMessage.FileTransferStart -> {
                        if (header == null) {
                            header = msg
                            buffers.clear()
                            logger.info {
                                "FileTransferReceiver: start ${msg.transferId} " +
                                    "(${msg.totalChunks} chunks, ${msg.totalSize} bytes)"
                            }
                        }
                    }
                    is NetworkMessage.FileChunk -> {
                        val h = header ?: return@transform
                        if (msg.transferId != h.transferId) return@transform
                        buffers[msg.chunkIndex] = decodeBase64(msg.dataBase64)
                        val received = buffers.values.sumOf { it.size }.toLong()
                        onProgress(TransferProgress(transferred = received, total = h.totalSize))
                        if (buffers.size == h.totalChunks) {
                            val assembled = assemble(h, buffers)
                            val dest = destPathFor(h)
                            okio_writeBytes(dest, assembled)
                            logger.info { "FileTransferReceiver: completed → $dest" }
                            emit(
                                ReceivedFile(
                                    transferId = h.transferId,
                                    fileName = h.fileName,
                                    destPath = dest,
                                    totalBytes = h.totalSize,
                                ),
                            )
                        }
                    }
                    else -> Unit
                }
            }.first()
    }

    private fun assemble(
        header: NetworkMessage.FileTransferStart,
        buffers: Map<Int, ByteArray>,
    ): ByteArray =
        ByteArray(header.totalSize.toInt()).also { out ->
            var offset = 0
            for (i in 0 until header.totalChunks) {
                val chunk = buffers.getValue(i)
                chunk.copyInto(out, offset)
                offset += chunk.size
            }
        }

    // Имя кеш-файла включает hash(documentId) — два файла с одинаковым `fileName`,
    // но разными documentId (`book.pdf#a` и `book.pdf#b`) больше не затирают друг
    // друга. Для legacy-вызовов (пустой documentId) поведение не меняется.
    private fun cacheNameFor(header: NetworkMessage.FileTransferStart): String =
        if (header.documentId.isNotEmpty()) {
            documentIdToCacheFileName(header.documentId, header.fileName)
        } else {
            header.fileName
        }
}

private fun joinPath(
    dir: String,
    name: String,
): String {
    val sep = if (dir.contains('\\')) "\\" else "/"
    return if (dir.endsWith('/') || dir.endsWith('\\')) "$dir$name" else "$dir$sep$name"
}
