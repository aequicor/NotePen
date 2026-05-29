package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ru.kyamshanov.notepen.document.domain.sha256Hex
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.LibraryMutationOutcome
import ru.kyamshanov.notepen.sync.domain.port.LibraryMutationTarget
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.infrastructure.FileTransferReceiver
import ru.kyamshanov.notepen.sync.infrastructure.okio_delete
import ru.kyamshanov.notepen.sync.infrastructure.okio_readBytes
import ru.kyamshanov.notepen.sync.infrastructure.okio_tempFilePath

private val logger = KotlinLogging.logger {}

/** Upper bound on how long the host waits for an upload's chunks after the request arrives. */
private const val UPLOAD_TIMEOUT_MS = 120_000L

/** Bound on the per-request chunk channel so a stalled receiver can't grow it unbounded. */
private const val CHUNK_CHANNEL_CAPACITY = 256

/**
 * Host-side handler for Librarian-over-LAN library mutations.
 *
 * Listens for [NetworkMessage.LibraryAddRequest] / [NetworkMessage.LibraryReplaceRequest] /
 * [NetworkMessage.LibraryRemoveRequest] and, for each:
 *
 * 1. **Validate authorisation** — the requesting peer MUST be in the host's
 *    per-peer write allow-set ([RemoteCatalogProvider.isLibrarian]); otherwise it
 *    is rejected with [NetworkMessage.LibraryMutationResult] `ok=false` and **no
 *    mutation** is performed. Default-deny: a peer that was never granted the
 *    Librarian role is always refused.
 * 2. **Reassemble** — for add/replace, consume the inbound
 *    [NetworkMessage.FileChunk] stream tagged with `transferId == requestId`
 *    (mirroring [RemoteDocumentOpener] in the reverse direction) into a temp file.
 * 3. **Verify** — reject on byte-size mismatch or SHA-256 mismatch against the
 *    request's declared `fileSize` / `contentSha256`, deleting the temp file.
 * 4. **Apply** — hand the verified path to [LibraryMutationTarget], which adds /
 *    replaces the book in the host's matching local [Library]. The local library's
 *    catalog StateFlow then updates and [RemoteCatalogProvider] re-serves it, so
 *    connected peers see the change reactively.
 * 5. **Reply** — send a [NetworkMessage.LibraryMutationResult] back to the
 *    requester with the outcome (and the new per-library book id on success).
 *
 * **No lost chunks (single-collector demux).** A *single*, long-lived collector
 * subscribes to [PeerServer.incomingMessages] in [start] and stays subscribed for
 * the handler's whole lifetime. It is the *only* consumer of that flow here, so
 * there is no late-subscription window. When a `Library*Request` for an upload
 * arrives it registers the `requestId` and opens a per-request [Channel]; every
 * subsequent matching [NetworkMessage.FileTransferStart] / [NetworkMessage.FileChunk]
 * is demuxed by the same collector into that channel — which a child coroutine's
 * [FileTransferReceiver] drains. Because the collector is already subscribed
 * before the request is acknowledged, no chunk can arrive "before" a subscriber
 * exists, regardless of how fast the client streams after the request.
 *
 * Each upload runs on its own child coroutine so several uploads can run in
 * parallel across peers without head-of-line blocking.
 *
 * @param server peer server: source of inbound messages + reply channel.
 * @param provider holds the per-peer write allow-set (the authorisation gate).
 * @param target seam to the host's local library; resolves `targetLibraryId`.
 */
class HostLibraryMutationHandler(
    private val server: PeerServer,
    private val provider: RemoteCatalogProvider,
    private val target: LibraryMutationTarget,
) {
    // In-flight uploads, keyed by requestId. The single collector routes matching
    // chunk messages here; the per-upload child coroutine drains+removes its entry.
    private val transfersMutex = Mutex()
    private val activeTransfers = mutableMapOf<String, Channel<NetworkMessage>>()

    /** Starts the single request/chunk demux loop; runs until [scope] is cancelled. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            server.incomingMessages.collect { peerMessage ->
                val peerId = peerMessage.peer.id
                when (val msg = peerMessage.message) {
                    is NetworkMessage.LibraryAddRequest ->
                        beginUpload(scope, msg.requestId) { ch ->
                            handleAdd(peerId, msg, ch)
                        }
                    is NetworkMessage.LibraryReplaceRequest ->
                        beginUpload(scope, msg.requestId) { ch ->
                            handleReplace(peerId, msg, ch)
                        }
                    is NetworkMessage.LibraryRemoveRequest ->
                        scope.launch { handleRemove(peerId, msg) }
                    // Demux chunk traffic to the in-flight upload registered under its
                    // transferId, if any. trySend keeps the demux loop non-suspending
                    // under back-pressure; the bounded channel is sized well above any
                    // single upload's chunk count.
                    is NetworkMessage.FileTransferStart ->
                        transfersMutex.withLock { activeTransfers[msg.transferId] }?.trySend(msg)
                    is NetworkMessage.FileChunk ->
                        transfersMutex.withLock { activeTransfers[msg.transferId] }?.trySend(msg)
                    else -> Unit
                }
            }
        }
    }

    /**
     * Registers the per-[requestId] chunk channel **synchronously, inside the
     * single collector** (before the loop returns to receive the next message),
     * then launches [block] on its own coroutine to drain it. Because the same
     * collector that registers the channel is the one that later routes the
     * matching chunks, registration is *guaranteed* to happen-before any chunk of
     * this upload is processed — there is no late-subscription window.
     */
    private suspend fun beginUpload(
        scope: CoroutineScope,
        requestId: String,
        block: suspend (channel: Channel<NetworkMessage>) -> Unit,
    ) {
        val channel = Channel<NetworkMessage>(capacity = CHUNK_CHANNEL_CAPACITY)
        transfersMutex.withLock { activeTransfers[requestId] = channel }
        scope.launch {
            try {
                block(channel)
            } finally {
                transfersMutex.withLock { activeTransfers.remove(requestId) }
                channel.close()
            }
        }
    }

    private suspend fun handleAdd(
        peerId: String,
        req: NetworkMessage.LibraryAddRequest,
        channel: Channel<NetworkMessage>,
    ) {
        if (rejectIfNotLibrarian(peerId, req.requestId, "add")) return
        withUploadedFile(
            peerId = peerId,
            requestId = req.requestId,
            channel = channel,
            expectedSize = req.fileSize,
            expectedSha256 = req.contentSha256,
        ) { localPath ->
            target.addBook(req.targetLibraryId, localPath, req.displayName)
        }
    }

    private suspend fun handleReplace(
        peerId: String,
        req: NetworkMessage.LibraryReplaceRequest,
        channel: Channel<NetworkMessage>,
    ) {
        if (rejectIfNotLibrarian(peerId, req.requestId, "replace")) return
        withUploadedFile(
            peerId = peerId,
            requestId = req.requestId,
            channel = channel,
            expectedSize = req.fileSize,
            expectedSha256 = req.contentSha256,
        ) { localPath ->
            target.replaceBook(req.targetLibraryId, req.libraryBookId, localPath, req.displayName)
        }
    }

    private suspend fun handleRemove(
        peerId: String,
        req: NetworkMessage.LibraryRemoveRequest,
    ) {
        if (rejectIfNotLibrarian(peerId, req.requestId, "remove")) return
        val outcome = target.removeBook(req.targetLibraryId, req.libraryBookId)
        reply(peerId, req.requestId, outcome)
    }

    /** Returns `true` (and replies with a rejection) when [peerId] is not a granted librarian. */
    private suspend fun rejectIfNotLibrarian(
        peerId: String,
        requestId: String,
        op: String,
    ): Boolean {
        if (provider.isLibrarian(peerId)) return false
        logger.warn { "Library $op denied: peer $peerId is not a librarian (req=$requestId)" }
        server.send(
            peerId,
            NetworkMessage.LibraryMutationResult(
                requestId = requestId,
                ok = false,
                error = "Not authorised — peer is not a librarian of this host",
            ),
        )
        return true
    }

    /**
     * Reassembles the inbound chunk stream for [requestId] (delivered via [channel]
     * by the demux loop) into a temp file, verifies size + SHA-256, runs [apply]
     * on the verified path, replies, and always deletes the temp file. Any failure
     * path replies `ok=false`. The channel registration/teardown is owned by
     * [beginUpload]; this method only drains.
     */
    @Suppress("LongParameterList")
    private suspend fun withUploadedFile(
        peerId: String,
        requestId: String,
        channel: Channel<NetworkMessage>,
        expectedSize: Long,
        expectedSha256: String,
        apply: suspend (localPath: String) -> Result<LibraryMutationOutcome>,
    ) {
        val tempPath = okio_tempFilePath(".upload")
        // Network boundary: a failure on any step must become a protocol reply,
        // not propagate. runCatching keeps detekt's TooGenericExceptionCaught happy
        // while matching the broad-failure handling used elsewhere in this module.
        runCatching { receiveVerifyApply(peerId, requestId, tempPath, channel, expectedSize, expectedSha256, apply) }
            .onFailure { e ->
                logger.warn { "Library upload failed (req=$requestId): ${e::class.simpleName}: ${e.message}" }
                rejectUpload(peerId, requestId, "Upload error: ${e::class.simpleName}")
            }
        okio_delete(tempPath)
    }

    @Suppress("LongParameterList")
    private suspend fun receiveVerifyApply(
        peerId: String,
        requestId: String,
        tempPath: String,
        channel: Channel<NetworkMessage>,
        expectedSize: Long,
        expectedSha256: String,
        apply: suspend (localPath: String) -> Result<LibraryMutationOutcome>,
    ) {
        val received =
            withTimeoutOrNull(UPLOAD_TIMEOUT_MS) {
                // The channel only ever carries this request's FileTransferStart /
                // FileChunk (the demux routes by transferId == requestId), so the
                // receiver needs no further filtering.
                FileTransferReceiver(incoming = channel.consumeAsFlow(), destDir = tempPath).awaitFileTo(tempPath)
            }
        val verification = received?.let { verify(tempPath, expectedSize, expectedSha256) }
        when {
            received == null -> rejectUpload(peerId, requestId, "Timed out waiting for upload chunks")
            verification != null -> rejectUpload(peerId, requestId, verification)
            else -> reply(peerId, requestId, apply(tempPath))
        }
    }

    /** Returns an error description when the file at [path] fails verification, or `null` if OK. */
    private fun verify(
        path: String,
        expectedSize: Long,
        expectedSha256: String,
    ): String? {
        val bytes = okio_readBytes(path)
        val actualSha by lazy { sha256Hex(bytes) }
        return when {
            bytes.size.toLong() != expectedSize -> "Size mismatch: expected $expectedSize, got ${bytes.size}"
            !actualSha.equals(expectedSha256, ignoreCase = true) ->
                "Checksum mismatch: expected $expectedSha256, got $actualSha"
            else -> null
        }
    }

    private suspend fun rejectUpload(
        peerId: String,
        requestId: String,
        reason: String,
    ) {
        logger.warn { "Library mutation rejected (req=$requestId): $reason" }
        server.send(
            peerId,
            NetworkMessage.LibraryMutationResult(requestId = requestId, ok = false, error = reason),
        )
    }

    private suspend fun reply(
        peerId: String,
        requestId: String,
        outcome: Result<LibraryMutationOutcome>,
    ) {
        outcome
            .onSuccess { result ->
                logger.info { "Library mutation applied (req=$requestId, newId=${result.newLibraryBookId})" }
                server.send(
                    peerId,
                    NetworkMessage.LibraryMutationResult(
                        requestId = requestId,
                        ok = true,
                        newLibraryBookId = result.newLibraryBookId,
                    ),
                )
            }.onFailure { e ->
                logger.warn { "Library mutation failed (req=$requestId): ${e.message}" }
                server.send(
                    peerId,
                    NetworkMessage.LibraryMutationResult(
                        requestId = requestId,
                        ok = false,
                        error = e.message ?: e::class.simpleName ?: "Mutation failed",
                    ),
                )
            }
    }
}
