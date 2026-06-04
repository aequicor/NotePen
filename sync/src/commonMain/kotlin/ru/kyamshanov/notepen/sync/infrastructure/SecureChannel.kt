package ru.kyamshanov.notepen.sync.infrastructure

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.sync.domain.Direction
import ru.kyamshanov.notepen.sync.domain.SessionCipher
import ru.kyamshanov.notepen.sync.domain.SessionCipherException
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.hkdfSha256
import ru.kyamshanov.notepen.sync.domain.port.secureRandomBytes

/**
 * Application-layer authenticated-encryption channel for the peer sync link,
 * shared by [KtorPeerServer] (host) and [KtorSyncClient] (client).
 *
 * ### Why this exists
 * The sync transport is plain `ws://` over the LAN — no TLS, no PKI. Confidentiality
 * and integrity are provided instead by sealing every application message with an
 * AEAD ([SessionCipher]) whose key is derived from the **pairing code**: a
 * high-entropy secret both peers already hold out-of-band (the host generated it; the
 * client received it via QR or manual entry). The code is therefore never sent on the
 * wire as key material — only the existing rate-limited [PairingManager] validation
 * still echoes it during the cleartext bootstrap.
 *
 * ### Handshake → key → confirmation → steady state
 * The pairing handshake ([NetworkMessage.PairRequest] / [NetworkMessage.PairAccepted] /
 * [NetworkMessage.PairRejected]) stays in **cleartext** so the host can rate-limit and
 * the user can approve. The only addition is a fresh random nonce contributed by each
 * side (`clientNonce`, `serverNonce`) — non-secret, used purely for key freshness.
 *
 * Immediately after a session is accepted both sides independently:
 * 1. derive the session key material with [deriveCipher]: HKDF-SHA-256 over the
 *    pairing-code bytes (IKM) salted with `clientNonce || serverNonce`, yielding the
 *    two directional AEAD keys and a shared, deterministically-derived nonce prefix.
 *    Mixing both nonces means neither peer alone fixes the key, and a recorded session
 *    can never be replayed because every session salts differently.
 * 2. run a **key-confirmation** step ([buildHello] / [verifyHello]): the first
 *    post-handshake frame each side sends is an encrypted hello carrying a fixed marker
 *    plus that side's own handshake nonce. The peer decrypts it and checks the marker
 *    and the echoed nonce. A peer that knows a victim's device id but **not** the
 *    pairing code cannot produce a hello that opens (wrong key) — so it is rejected
 *    fail-closed and never reaches the message loop. This is what actually
 *    authenticates the peer (closing the bare-peer-id spoofing gap); the device id is
 *    used only for routing.
 *
 * From then on every [NetworkMessage] is serialized to JSON, sealed, and sent as a
 * single [Frame.Binary]; inbound binary frames are opened and deserialized
 * ([sendEncrypted] / [receiveEncrypted]). Control frames (ping/pong/close) are not
 * application data and are skipped on receive. File-transfer frames ride this channel
 * transparently because they are ordinary [NetworkMessage]s.
 *
 * All helpers here are pure plumbing over the platform crypto port and a
 * [WebSocketSession]; the actual nonce/replay/AEAD invariants live in [SessionCipher].
 */
internal object SecureChannel {
    /** Length in bytes of each side's per-session handshake nonce. */
    const val NONCE_BYTES: Int = 16

    /** HKDF `info` label deriving the shared nonce prefix both ciphers must agree on. */
    private const val INFO_PREFIX = "notepen-session-prefix"

    /**
     * Fixed marker prefixing the encrypted key-confirmation hello. Its presence after a
     * successful decrypt proves the peer holds the right key; it carries no secret.
     */
    private val HELLO_MARKER: ByteArray = "notepen-hello-v1".encodeToByteArray()

    /** Generates a fresh base64-encoded handshake nonce for [NetworkMessage.PairRequest]/[NetworkMessage.PairAccepted]. */
    fun newNonce(): String = encodeBase64(secureRandomBytes(NONCE_BYTES))

    /**
     * Builds the [SessionCipher] shared by both peers for this session.
     *
     * @param pairingCode the out-of-band shared secret (used as HKDF IKM).
     * @param clientNonce base64 nonce from [NetworkMessage.PairRequest].
     * @param serverNonce base64 nonce from [NetworkMessage.PairAccepted].
     * @throws SessionCipherException if either nonce is missing/blank or not the
     *   expected length — i.e. the peer predates the encrypted channel or sent junk.
     *   Failing here keeps the link fail-closed.
     */
    fun deriveCipher(
        pairingCode: String,
        clientNonce: String,
        serverNonce: String,
    ): SessionCipher {
        val clientNonceBytes = decodeNonceOrThrow(clientNonce, "clientNonce")
        val serverNonceBytes = decodeNonceOrThrow(serverNonce, "serverNonce")
        val secret = pairingCode.encodeToByteArray()
        // Salt = both nonces concatenated (client first), so it is identical on both
        // sides and neither peer alone determines the derived key.
        val salt = clientNonceBytes + serverNonceBytes
        // Both ciphers must use the SAME nonce prefix or their C2S/S2C GCM nonces could
        // collide; derive it deterministically from the same secret+salt rather than
        // sending it, so it stays consistent without trusting attacker-supplied bytes.
        val prefix = hkdfSha256(secret, salt, INFO_PREFIX.encodeToByteArray(), SessionCipher.SESSION_PREFIX_BYTES)
        return SessionCipher(sharedSecret = secret, salt = salt, sessionPrefix = prefix)
    }

    /**
     * Builds this side's encrypted key-confirmation hello: `seal(MARKER || ownNonce)`
     * in [direction]. The receiver checks both the marker and that the echoed nonce
     * matches the one this side announced in the cleartext handshake.
     *
     * @param ownNonce base64 nonce this side put in its handshake message
     *   ([NetworkMessage.PairRequest.clientNonce] for the client,
     *   [NetworkMessage.PairAccepted.serverNonce] for the server).
     */
    fun buildHello(
        cipher: SessionCipher,
        direction: Direction,
        ownNonce: String,
    ): ByteArray = cipher.seal(direction, HELLO_MARKER + decodeBase64(ownNonce))

    /**
     * Verifies a peer's encrypted hello [frame] received on [direction].
     *
     * Succeeds only if the frame decrypts (correct derived key ⇒ correct pairing
     * code), begins with [HELLO_MARKER], and echoes [expectedPeerNonce] — the nonce
     * the peer announced in its cleartext handshake. Any deviation throws so the
     * caller closes the session fail-closed.
     *
     * @param expectedPeerNonce base64 nonce the peer sent in its handshake message.
     * @throws SessionCipherException on decrypt/marker/nonce mismatch.
     */
    fun verifyHello(
        cipher: SessionCipher,
        direction: Direction,
        frame: ByteArray,
        expectedPeerNonce: String,
    ) {
        val plaintext = cipher.open(direction, frame)
        val expected = HELLO_MARKER + decodeBase64(expectedPeerNonce)
        if (!plaintext.contentEquals(expected)) {
            throw SessionCipherException("Key-confirmation hello mismatch")
        }
    }

    /**
     * Serializes [message] to JSON, seals it with [cipher] in [direction], and sends
     * it as a single binary frame.
     */
    suspend fun sendEncrypted(
        session: WebSocketSession,
        cipher: SessionCipher,
        direction: Direction,
        json: Json,
        message: NetworkMessage,
    ) {
        val plaintext = json.encodeToString(NetworkMessage.serializer(), message).encodeToByteArray()
        session.send(Frame.Binary(fin = true, data = cipher.seal(direction, plaintext)))
    }

    /**
     * Receives the next application message: pulls binary frames from [session]
     * (skipping control/non-binary frames), opens them with [cipher] on [direction],
     * and deserializes the [NetworkMessage].
     *
     * @return the decrypted message, or `null` when the channel closes (the incoming
     *   frame stream is exhausted).
     * @throws SessionCipherException if a binary frame fails authentication, replay
     *   check, or deserialization — the caller must treat this as fatal and close.
     */
    suspend fun receiveEncrypted(
        session: WebSocketSession,
        cipher: SessionCipher,
        direction: Direction,
        json: Json,
    ): NetworkMessage? {
        val frame = nextBinaryFrame(session) ?: return null
        val plaintext = cipher.open(direction, frame)
        return json.decodeFromString(NetworkMessage.serializer(), plaintext.decodeToString())
    }

    /**
     * Reads from [session] until a [Frame.Binary] arrives (skipping ping/pong/other
     * control frames) and returns its bytes, or `null` if the stream closes first.
     * Used by both the steady-state receive loop and the key-confirmation step so
     * neither trips over an interleaved control frame.
     */
    suspend fun nextBinaryFrame(session: WebSocketSession): ByteArray? {
        for (frame in session.incoming) {
            if (frame is Frame.Binary) return frame.data
        }
        return null
    }

    private fun decodeNonceOrThrow(
        nonce: String,
        label: String,
    ): ByteArray {
        // A blank nonce means the peer predates the encrypted channel; decoding it
        // yields the empty array, which the single size guard below rejects — so the
        // happy path and every malformed case fail closed with at most one throw here.
        val bytes =
            if (nonce.isBlank()) {
                ByteArray(0)
            } else {
                runCatching { decodeBase64(nonce) }.getOrElse {
                    throw SessionCipherException("Malformed $label", it)
                }
            }
        if (bytes.size != NONCE_BYTES) {
            throw SessionCipherException(
                "Invalid $label (got ${bytes.size} bytes, need $NONCE_BYTES) — peer may not support the encrypted channel",
            )
        }
        return bytes
    }
}
