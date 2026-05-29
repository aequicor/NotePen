package ru.kyamshanov.notepen.sync.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Typed WebSocket protocol for peer-to-peer communication.
 *
 * All variants are serialised as JSON with a `type` discriminator.
 * New variants must be added with a `@SerialName` to maintain wire compatibility.
 */
@Serializable
sealed class NetworkMessage {
    /** Sent by a client immediately after WebSocket upgrade to initiate pairing. */
    @Serializable
    @SerialName("pair_request")
    data class PairRequest(
        val code: String,
        val device: DeviceInfo,
    ) : NetworkMessage()

    /** Sent by the server when the pairing code is accepted. */
    @Serializable
    @SerialName("pair_accepted")
    data class PairAccepted(
        val serverDevice: DeviceInfo,
    ) : NetworkMessage()

    /** Sent by the server when the pairing code is rejected or expired. */
    @Serializable
    @SerialName("pair_rejected")
    data class PairRejected(
        val reason: String,
    ) : NetworkMessage()

    /** Sent by either side to keep the connection alive. */
    @Serializable
    @SerialName("ping")
    data object Ping : NetworkMessage()

    /** Reply to [Ping]. */
    @Serializable
    @SerialName("pong")
    data object Pong : NetworkMessage()

    /**
     * A single chunk of a file being transferred from sender to receiver.
     *
     * [documentId] groups chunks of the same logical document — required when
     * multiple parallel transfers share one WebSocket session. Defaults to
     * empty for legacy callers (Phase 1 compatibility); Phase 3 requires it.
     */
    @Serializable
    @SerialName("file_chunk")
    data class FileChunk(
        val transferId: String,
        val fileName: String,
        val chunkIndex: Int,
        val totalChunks: Int,
        /** Base64-encoded chunk bytes. */
        val dataBase64: String,
        val documentId: String = "",
    ) : NetworkMessage()

    /** Acknowledgement for a received [FileChunk]. */
    @Serializable
    @SerialName("file_chunk_ack")
    data class FileChunkAck(
        val transferId: String,
        val chunkIndex: Int,
        val documentId: String = "",
    ) : NetworkMessage()

    /**
     * Broadcasts a stroke addition or removal to the peer.
     *
     * [documentId] identifies which logical document the delta belongs to —
     * required for routing once multiple documents can be open in parallel.
     * Defaults to empty for legacy callers; populated by [SyncEngine].
     */
    @Serializable
    @SerialName("stroke_delta")
    data class StrokeDeltaMessage(
        val delta: StrokeDelta,
        val documentId: String = "",
    ) : NetworkMessage()

    /**
     * Broadcast from host to viewer: current viewport and optional pointer position.
     *
     * Sent at ≤30 fps via `conflate()` to avoid flooding the WebSocket.
     * [pointerX] / [pointerY] are normalised [0..1] within the page, or null if
     * the pointer is not active.
     */
    @Serializable
    @SerialName("projection_frame")
    data class ProjectionFrame(
        val page: Int,
        val viewportOffsetY: Float,
        val viewportScale: Float,
        val pointerX: Float? = null,
        val pointerY: Float? = null,
    ) : NetworkMessage()

    /** Viewer sends this to request detaching from the host's viewport ("free scroll"). */
    @Serializable
    @SerialName("projection_detach")
    data object ProjectionDetach : NetworkMessage()

    /** Viewer sends this to re-attach to the host's viewport. */
    @Serializable
    @SerialName("projection_attach")
    data object ProjectionAttach : NetworkMessage()

    /** Notifies the remote side that the connection will be closed gracefully. */
    @Serializable
    @SerialName("disconnect")
    data object Disconnect : NetworkMessage()

    /**
     * Header sent before a sequence of [FileChunk] messages. Allows the receiver
     * to allocate buffers and verify integrity once the transfer completes.
     *
     * [documentId] identifies which logical document this file represents — set
     * by the host when responding to a [DocumentOpenRequest]. Receiver uses it
     * to key the local copy and any pending-delta queue.
     */
    @Serializable
    @SerialName("file_transfer_start")
    data class FileTransferStart(
        val transferId: String,
        val fileName: String,
        val totalChunks: Int,
        val totalSize: Long,
        /** Hex-encoded SHA-256 of the full file. */
        val sha256: String,
        val documentId: String = "",
    ) : NetworkMessage()

    /**
     * Tablet (client) asks the host (server) to persist the current document.
     *
     * [documentId] identifies which document to save — required once multiple
     * documents can be open in parallel. Empty for legacy callers.
     */
    @Serializable
    @SerialName("save_request")
    data class SaveRequest(
        val requestId: String,
        val documentId: String = "",
    ) : NetworkMessage()

    /** Reply to [SaveRequest] from the host. */
    @Serializable
    @SerialName("save_result")
    data class SaveResult(
        val requestId: String,
        val success: Boolean,
        val errorMessage: String? = null,
        val documentId: String = "",
    ) : NetworkMessage()

    /**
     * Tablet → host: request the full set of annotations the host currently
     * holds for the document identified by [documentId]. Used at bootstrap,
     * after the PDF has been received and the local annotation bundle (if any)
     * has been loaded.
     */
    @Serializable
    @SerialName("annotation_snapshot_request")
    data class AnnotationSnapshotRequest(
        val documentId: String = "",
    ) : NetworkMessage()

    /**
     * Host → tablet: full annotation snapshot for [documentId]. Each entry is
     * a stroke that already exists on the host (loaded from disk and/or drawn
     * earlier). Receiver must de-duplicate by [StrokeDelta.Added.strokeId]
     * against any strokes already known locally.
     */
    @Serializable
    @SerialName("annotation_snapshot")
    data class AnnotationSnapshot(
        val strokes: List<StrokeDelta.Added>,
        val documentId: String = "",
    ) : NetworkMessage()

    /**
     * Tablet → host: request the host to stream the document identified by
     * [documentId]. Host validates against the catalog snapshot last sent to
     * this client and responds with a [FileTransferStart] (followed by chunks)
     * or [DocumentNotFound]. New in Phase 1; consumers wired in Phase 3.
     */
    @Serializable
    @SerialName("document_open_request")
    data class DocumentOpenRequest(
        val documentId: String,
    ) : NetworkMessage()

    /**
     * Host → tablet: the requested [documentId] could not be served. Reasons
     * include unknown id, file removed from the host's library, or read error.
     */
    @Serializable
    @SerialName("document_not_found")
    data class DocumentNotFound(
        val documentId: String,
        val reason: String,
    ) : NetworkMessage()

    /**
     * Tablet → host: request the host's current library catalog (recent files
     * + folders). Issued on every reconnect — the tablet does not rely on a
     * push from the host.
     */
    @Serializable
    @SerialName("remote_catalog_request")
    data object RemoteCatalogRequest : NetworkMessage()

    /**
     * Host → tablet: full [RemoteCatalog] snapshot. The host also stamps a
     * per-client `allowedDocumentIds` set internally; subsequent
     * [DocumentOpenRequest]s are validated against that set.
     */
    @Serializable
    @SerialName("remote_catalog_response")
    data class RemoteCatalogResponse(
        val catalog: RemoteCatalog,
    ) : NetworkMessage()

    /**
     * Push notification (любая сторона → пир): локальный каталог сменился
     * (добавлен/удалён/перемещён файл, изменена папка). Получатель в ответ
     * должен запросить свежий [RemoteCatalogRequest] у того же отправителя.
     *
     * Сам пейлоад намеренно пустой — фактический снапшот всегда тянется
     * через `RemoteCatalogRequest`/`RemoteCatalogResponse`, чтобы не плодить
     * параллельные пути синхронизации и сохранить per-peer allow-list.
     */
    @Serializable
    @SerialName("remote_catalog_changed")
    data object RemoteCatalogChanged : NetworkMessage()

    /**
     * Librarian → host: add a new book to the host's local library identified by
     * [targetLibraryId]. The book bytes follow this message as a [FileChunk]
     * stream (optionally preceded by a [FileTransferStart]) whose `transferId`
     * equals [requestId] — that is how the inbound upload is correlated back to
     * this request on the host. The host verifies the reassembled file against
     * [fileSize] and [contentSha256] before applying the mutation.
     *
     * Only peers in the host's per-peer **write allow-set** (librarian peers) may
     * mutate; any other peer is rejected with [LibraryMutationResult] `ok=false`.
     * Additive: pre-M5 hosts simply never emit/handle this variant.
     *
     * @property targetLibraryId opaque id of the host library to mutate (resolved
     *   by the host's library registry); empty selects the host's default local library.
     * @property displayName human-readable name to give the added book.
     * @property fileSize expected total byte length of the uploaded file.
     * @property contentSha256 lowercase hex SHA-256 of the full uploaded file.
     * @property requestId unique id correlating the reply ([LibraryMutationResult])
     *   and the inbound [FileChunk] stream (`transferId == requestId`).
     */
    @Serializable
    @SerialName("library_add_request")
    data class LibraryAddRequest(
        val targetLibraryId: String,
        val displayName: String,
        val fileSize: Long,
        val contentSha256: String,
        val requestId: String,
    ) : NetworkMessage()

    /**
     * Librarian → host: remove the book identified by [libraryBookId] from the
     * host library [targetLibraryId]. No file transfer accompanies this request.
     *
     * Subject to the same per-peer write allow-set as [LibraryAddRequest].
     * Removal may be unsupported by the host's backend; in that case the host
     * replies [LibraryMutationResult] `ok=false` with an explanatory error.
     *
     * @property targetLibraryId opaque id of the host library to mutate.
     * @property libraryBookId per-library locator of the book to remove.
     * @property requestId unique id correlating the [LibraryMutationResult] reply.
     */
    @Serializable
    @SerialName("library_remove_request")
    data class LibraryRemoveRequest(
        val targetLibraryId: String,
        val libraryBookId: String,
        val requestId: String,
    ) : NetworkMessage()

    /**
     * Librarian → host: replace the content of the book identified by
     * [libraryBookId] in the host library [targetLibraryId] with newly uploaded
     * bytes. Like [LibraryAddRequest], the replacement bytes follow as a
     * [FileChunk] stream whose `transferId` equals [requestId], verified against
     * [fileSize] and [contentSha256].
     *
     * Subject to the same per-peer write allow-set as [LibraryAddRequest].
     *
     * @property targetLibraryId opaque id of the host library to mutate.
     * @property libraryBookId per-library locator of the book to replace.
     * @property displayName human-readable name to give the replacement book.
     * @property fileSize expected total byte length of the uploaded file.
     * @property contentSha256 lowercase hex SHA-256 of the full uploaded file.
     * @property requestId unique id correlating the reply and inbound chunk stream.
     */
    @Serializable
    @SerialName("library_replace_request")
    data class LibraryReplaceRequest(
        val targetLibraryId: String,
        val libraryBookId: String,
        val displayName: String,
        val fileSize: Long,
        val contentSha256: String,
        val requestId: String,
    ) : NetworkMessage()

    /**
     * Host → librarian: outcome of a
     * [LibraryAddRequest] / [LibraryRemoveRequest] / [LibraryReplaceRequest],
     * correlated by [requestId].
     *
     * @property requestId echoes the originating request's id.
     * @property ok `true` if the mutation was applied; `false` if rejected (not a
     *   librarian, verification mismatch, unsupported operation, or I/O error).
     * @property error human-readable failure reason when [ok] is `false`.
     * @property newLibraryBookId the per-library id of the added/replaced book on
     *   success, when the backend reports one.
     */
    @Serializable
    @SerialName("library_mutation_result")
    data class LibraryMutationResult(
        val requestId: String,
        val ok: Boolean,
        val error: String? = null,
        val newLibraryBookId: String? = null,
    ) : NetworkMessage()
}
