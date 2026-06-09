package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import ru.kyamshanov.notepen.sync.domain.model.RemoteEntry
import ru.kyamshanov.notepen.sync.domain.port.LocalDocumentIdRegistry
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.sync.infrastructure.FileTransferReceiver
import ru.kyamshanov.notepen.sync.infrastructure.okio_exists

private val logger = KotlinLogging.logger {}

/** Result of [RemoteDocumentOpener.open]. */
sealed class RemoteDocumentResult {
    data class Success(
        val documentId: String,
        val localPath: String,
    ) : RemoteDocumentResult()

    data class NotFound(
        val documentId: String,
        val reason: String,
    ) : RemoteDocumentResult()

    data class Timeout(
        val documentId: String,
    ) : RemoteDocumentResult()

    data class Failure(
        val documentId: String,
        val cause: Throwable,
    ) : RemoteDocumentResult()
}

/**
 * Peer-side coordinator that converts a remote catalog entry into a local PDF
 * file ready to open in the editor.
 *
 * Multi-host aware: when more than one host is connected, the opener picks
 * the host whose latest catalog snapshot includes the requested `documentId`.
 * Ties (multiple hosts with the same document) are resolved by iteration
 * order of the [catalogs] map; this is deterministic but not user-driven.
 *
 * @param client peer client used to send requests to connected hosts.
 * @param server peer server used to send requests to connected clients.
 * @param catalogs per-host catalog snapshots — drives host selection.
 * @param destDir directory where received files are written.
 * @param requestTimeoutMs upper bound on the whole open flow.
 * @param onAfterDownload invoked after a file is freshly streamed in from a host (NOT on an offline
 *   cache hit). Lets the caller bound the cache after it just grew — wired to [CacheEvictor] in the
 *   DI layer. Defaults to a no-op so the domain stays decoupled from eviction.
 */
class RemoteDocumentOpener(
    private val client: SyncClient? = null,
    private val server: PeerServer? = null,
    private val catalogs: Flow<Map<DeviceInfo, RemoteCatalog>>,
    private val destDir: String,
    /** Реестр, в котором запоминаем `localPath → documentId`. Null отключает запись. */
    private val documentIdRegistry: LocalDocumentIdRegistry? = null,
    private val requestTimeoutMs: Long = 60_000L,
    private val onAfterDownload: suspend () -> Unit = {},
) {
    init {
        require(client != null || server != null) {
            "At least one of client or server must be provided"
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun open(
        documentId: String,
        displayName: String? = null,
    ): RemoteDocumentResult {
        // Phase 5 offline-first: if the file is already in the local cache use it
        // directly. Keeps the Remote section functional without a live peer.
        if (displayName != null) {
            val cachedPath = joinPath(destDir, documentIdToCacheFileName(documentId, displayName))
            if (okio_exists(cachedPath)) {
                logger.info { "Opening cached copy of $documentId at $cachedPath" }
                documentIdRegistry?.register(cachedPath, documentId)
                return RemoteDocumentResult.Success(documentId, cachedPath)
            }
        }
        val location =
            withTimeoutOrNull(requestTimeoutMs) {
                pickHost(documentId)
            } ?: return RemoteDocumentResult.NotFound(
                documentId = documentId,
                reason = "No connected host has this document",
            )
        val catalogDisplayName = location.entry.displayName
        val catalogCachedPath = joinPath(destDir, documentIdToCacheFileName(documentId, catalogDisplayName))
        if (okio_exists(catalogCachedPath)) {
            logger.info { "Opening cached copy of $documentId at $catalogCachedPath" }
            documentIdRegistry?.register(catalogCachedPath, documentId)
            return RemoteDocumentResult.Success(documentId, catalogCachedPath)
        }
        val host = location.host
        logger.info { "Requesting $documentId from host=${host.name}" }

        val outcome: RemoteDocumentResult? =
            withTimeoutOrNull(requestTimeoutMs) {
                coroutineScope {
                    val fromThisHost =
                        incomingFrom(host.id)
                    val notFound =
                        async(start = CoroutineStart.UNDISPATCHED) {
                            fromThisHost
                                .filter { it is NetworkMessage.DocumentNotFound && it.documentId == documentId }
                                .first()
                        }
                    val received =
                        async(start = CoroutineStart.UNDISPATCHED) {
                            val incoming =
                                merge(
                                    fromThisHost
                                        .filter { it is NetworkMessage.FileTransferStart && it.documentId == documentId },
                                    fromThisHost
                                        .filter { it is NetworkMessage.FileChunk && it.documentId == documentId },
                                )
                            FileTransferReceiver(incoming = incoming, destDir = destDir).awaitFile()
                        }
                    val sendResult =
                        runCatching {
                            send(host.id, NetworkMessage.DocumentOpenRequest(documentId = documentId))
                        }
                    if (sendResult.isFailure) {
                        notFound.cancel()
                        received.cancel()
                        return@coroutineScope RemoteDocumentResult.Failure(documentId, sendResult.exceptionOrNull()!!)
                    }
                    val winner: RemoteDocumentResult =
                        select {
                            notFound.onAwait { msg ->
                                val nf = msg as NetworkMessage.DocumentNotFound
                                RemoteDocumentResult.NotFound(documentId, nf.reason)
                            }
                            received.onAwait { file ->
                                documentIdRegistry?.register(file.destPath, documentId)
                                // The cache just grew by a fresh download; let the caller trim it.
                                runCatching { onAfterDownload() }
                                RemoteDocumentResult.Success(documentId, file.destPath)
                            }
                        }
                    notFound.cancel()
                    received.cancel()
                    winner
                }
            }
        return outcome ?: RemoteDocumentResult.Timeout(documentId).also {
            logger.warn { "Timed out waiting for $documentId from ${host.id}" }
        }
    }

    private suspend fun send(
        hostId: String,
        message: NetworkMessage,
    ) {
        val syncClient = client
        val peerServer = server
        val clientHasHost = syncClient?.connectedHosts?.first()?.any { it.id == hostId } == true
        val serverHasPeer = peerServer?.connectedPeers?.first()?.any { it.id == hostId } == true
        when {
            clientHasHost -> syncClient.send(hostId, message)
            serverHasPeer -> peerServer.send(hostId, message)
            syncClient != null && peerServer == null -> syncClient.send(hostId, message)
            peerServer != null && syncClient == null -> peerServer.send(hostId, message)
            else -> {
                syncClient?.send(hostId, message)
                peerServer?.send(hostId, message)
            }
        }
    }

    private fun incomingFrom(hostId: String): Flow<NetworkMessage> {
        val clientIncoming =
            client
                ?.incomingMessages
                ?.filter { it.host.id == hostId }
                ?.map { it.message }
        val serverIncoming =
            server
                ?.incomingMessages
                ?.filter { it.peer.id == hostId }
                ?.map { it.message }
        return when {
            clientIncoming != null && serverIncoming != null -> merge(clientIncoming, serverIncoming)
            clientIncoming != null -> clientIncoming
            serverIncoming != null -> serverIncoming
            else -> error("RemoteDocumentOpener has no incoming transport")
        }
    }

    private suspend fun pickHost(documentId: String): RemoteDocumentLocation =
        catalogs
            .map { snapshot ->
                snapshot.entries
                    .firstNotNullOfOrNull { entry ->
                        val catalog = entry.value
                        val remoteEntry =
                            catalog.openDocuments.firstOrNull { it.documentId == documentId }
                                ?: catalog.recent.firstOrNull { it.documentId == documentId }
                                ?: return@firstNotNullOfOrNull null
                        RemoteDocumentLocation(host = entry.key, entry = remoteEntry)
                    }
            }
            .filterNotNull()
            .first()
}

private data class RemoteDocumentLocation(
    val host: DeviceInfo,
    val entry: RemoteEntry,
)

private fun joinPath(
    dir: String,
    name: String,
): String {
    val sep = if (dir.contains('\\')) "\\" else "/"
    return if (dir.endsWith('/') || dir.endsWith('\\')) "$dir$name" else "$dir$sep$name"
}
