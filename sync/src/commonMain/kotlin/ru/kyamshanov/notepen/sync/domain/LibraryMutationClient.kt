package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import ru.kyamshanov.notepen.document.domain.sha256Hex
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.sync.infrastructure.WebSocketFileTransfer
import ru.kyamshanov.notepen.sync.infrastructure.okio_readBytes

private val logger = KotlinLogging.logger {}

/** Upper bound on how long the client waits for the host's mutation result. */
private const val MUTATION_TIMEOUT_MS = 120_000L

/** Per-library locator of a book added or replaced on the host (echoed by the host on success). */
public data class RemoteMutationOutcome(
    public val newLibraryBookId: String?,
)

/**
 * Client-side driver for **Librarian-over-LAN** library mutations — the mirror of
 * [HostLibraryMutationHandler]. Sends a `library_*_request` to the host this
 * client is paired with, streams the book bytes (for add/replace) as a
 * [NetworkMessage.FileChunk] stream tagged `transferId == requestId`, and awaits
 * the correlated [NetworkMessage.LibraryMutationResult].
 *
 * Lives in `:sync` (not `:library:impl`) so the protocol logic — request framing,
 * the client→host chunk upload via [WebSocketFileTransfer], and result
 * correlation — is unit-testable against a fake [SyncClient] without any
 * `:library` types. `PeerLanLibrary` (the Librarian-capable [Library]) delegates
 * its `addBook` / `replaceBook` / `removeBook` here.
 *
 * The success path returns the host-reported [RemoteMutationOutcome]; every
 * failure (rejected by host, timeout, send/read error) is a [Result.failure] with
 * an explanatory message that the caller surfaces verbatim.
 *
 * @param client the paired sync client used to send the request + chunks.
 * @param hostId id of the host (peer) whose library is mutated; equals the
 *   `DeviceInfo.id` this client paired with.
 * @param readBytes reads the bytes of a local source file (overridable for tests);
 *   defaults to the platform file read used by the rest of the transfer code.
 * @param sha256 computes the lowercase hex SHA-256 of the bytes (defaults to the
 *   shared crypto helper); the host re-verifies against this.
 * @param newRequestId mints a fresh, unique request id per call.
 */
public class LibraryMutationClient(
    private val client: SyncClient,
    private val hostId: String,
    private val readBytes: (path: String) -> ByteArray = ::okio_readBytes,
    private val sha256: (ByteArray) -> String = ::sha256Hex,
    private val newRequestId: () -> String,
    private val timeoutMs: Long = MUTATION_TIMEOUT_MS,
) {
    /**
     * Uploads the file at [localPath] to the host's library [targetLibraryId] as a
     * new book named [displayName], awaiting the host's outcome.
     */
    public suspend fun addBook(
        targetLibraryId: String,
        localPath: String,
        displayName: String,
    ): Result<RemoteMutationOutcome> =
        uploadAndAwait(localPath) { requestId, bytes ->
            NetworkMessage.LibraryAddRequest(
                targetLibraryId = targetLibraryId,
                displayName = displayName,
                fileSize = bytes.size.toLong(),
                contentSha256 = sha256(bytes),
                requestId = requestId,
            )
        }

    /**
     * Replaces the content of [libraryBookId] in the host's library [targetLibraryId]
     * with the file at [localPath], awaiting the host's outcome.
     */
    public suspend fun replaceBook(
        targetLibraryId: String,
        libraryBookId: String,
        localPath: String,
        displayName: String,
    ): Result<RemoteMutationOutcome> =
        uploadAndAwait(localPath) { requestId, bytes ->
            NetworkMessage.LibraryReplaceRequest(
                targetLibraryId = targetLibraryId,
                libraryBookId = libraryBookId,
                displayName = displayName,
                fileSize = bytes.size.toLong(),
                contentSha256 = sha256(bytes),
                requestId = requestId,
            )
        }

    /**
     * Asks the host to remove [libraryBookId] from [targetLibraryId]. No file is
     * uploaded. The host's local-folder backend reports removal as unsupported
     * today — that surfaces here as a [Result.failure].
     */
    public suspend fun removeBook(
        targetLibraryId: String,
        libraryBookId: String,
    ): Result<RemoteMutationOutcome> {
        val requestId = newRequestId()
        val send =
            runCatching {
                client.send(
                    hostId,
                    NetworkMessage.LibraryRemoveRequest(
                        targetLibraryId = targetLibraryId,
                        libraryBookId = libraryBookId,
                        requestId = requestId,
                    ),
                )
            }
        if (send.isFailure) return Result.failure(send.exceptionOrNull()!!)
        return awaitResult(requestId)
    }

    private suspend fun uploadAndAwait(
        localPath: String,
        buildRequest: (requestId: String, bytes: ByteArray) -> NetworkMessage,
    ): Result<RemoteMutationOutcome> {
        val requestId = newRequestId()
        val sent =
            runCatching {
                val request = buildRequest(requestId, readBytes(localPath))
                // Header + chunk stream are correlated to the request by transferId == requestId.
                // The request must reach the host before the chunks: the host registers the
                // per-requestId chunk channel when it processes this request (see
                // HostLibraryMutationHandler.beginUpload), so any chunk arriving afterwards is
                // routed into it. Chunks sent before the request would have no channel to land in.
                client.send(hostId, request)
                WebSocketFileTransfer(client = client, hostId = hostId)
                    .send(sourcePath = localPath, transferId = requestId)
                    .collect { /* progress: not surfaced yet */ }
            }
        return sent.fold(
            onSuccess = { awaitResult(requestId) },
            onFailure = { e ->
                logger.warn { "Library upload send failed (req=$requestId): ${e.message}" }
                Result.failure(e)
            },
        )
    }

    private suspend fun awaitResult(requestId: String): Result<RemoteMutationOutcome> {
        val result =
            withTimeoutOrNull(timeoutMs) {
                client.incomingMessages
                    .filter { it.host.id == hostId }
                    .map { it.message }
                    .filter { it is NetworkMessage.LibraryMutationResult && it.requestId == requestId }
                    .first() as NetworkMessage.LibraryMutationResult
            } ?: return Result.failure(IllegalStateException("Timed out waiting for host mutation result"))
        return if (result.ok) {
            Result.success(RemoteMutationOutcome(newLibraryBookId = result.newLibraryBookId))
        } else {
            Result.failure(IllegalStateException(result.error ?: "Host rejected the mutation"))
        }
    }
}
