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

    /** Notifies the remote side that the connection will be closed gracefully. */
    @Serializable
    @SerialName("disconnect")
    data object Disconnect : NetworkMessage()
}
