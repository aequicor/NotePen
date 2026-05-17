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
    data class PairRejected(val reason: String) : NetworkMessage()

    /** Sent by either side to keep the connection alive. */
    @Serializable
    @SerialName("ping")
    data object Ping : NetworkMessage()

    /** Reply to [Ping]. */
    @Serializable
    @SerialName("pong")
    data object Pong : NetworkMessage()

    /** A single chunk of a file being transferred from sender to receiver. */
    @Serializable
    @SerialName("file_chunk")
    data class FileChunk(
        val transferId: String,
        val fileName: String,
        val chunkIndex: Int,
        val totalChunks: Int,
        /** Base64-encoded chunk bytes. */
        val dataBase64: String,
    ) : NetworkMessage()

    /** Acknowledgement for a received [FileChunk]. */
    @Serializable
    @SerialName("file_chunk_ack")
    data class FileChunkAck(
        val transferId: String,
        val chunkIndex: Int,
    ) : NetworkMessage()

    /** Broadcasts a stroke addition or removal to the peer. */
    @Serializable
    @SerialName("stroke_delta")
    data class StrokeDeltaMessage(val delta: StrokeDelta) : NetworkMessage()

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
    ) : NetworkMessage()

    /** Tablet (client) asks the host (server) to persist the current document. */
    @Serializable
    @SerialName("save_request")
    data class SaveRequest(val requestId: String) : NetworkMessage()

    /** Reply to [SaveRequest] from the host. */
    @Serializable
    @SerialName("save_result")
    data class SaveResult(
        val requestId: String,
        val success: Boolean,
        val errorMessage: String? = null,
    ) : NetworkMessage()

    /**
     * Tablet → host: request the full set of annotations the host currently
     * holds for the open document. Used at bootstrap, after the PDF has been
     * received and the local annotation bundle (if any) has been loaded.
     */
    @Serializable
    @SerialName("annotation_snapshot_request")
    data object AnnotationSnapshotRequest : NetworkMessage()

    /**
     * Host → tablet: full annotation snapshot. Each entry is a stroke that
     * already exists on the host (loaded from disk and/or drawn earlier).
     * Receiver must de-duplicate by [StrokeDelta.Added.strokeId] against any
     * strokes already known locally.
     */
    @Serializable
    @SerialName("annotation_snapshot")
    data class AnnotationSnapshot(
        val strokes: List<StrokeDelta.Added>,
    ) : NetworkMessage()
}
