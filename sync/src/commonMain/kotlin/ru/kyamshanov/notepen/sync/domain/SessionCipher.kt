package ru.kyamshanov.notepen.sync.domain

import ru.kyamshanov.notepen.sync.domain.port.aesGcmOpen
import ru.kyamshanov.notepen.sync.domain.port.aesGcmSeal
import ru.kyamshanov.notepen.sync.domain.port.hkdfSha256
import ru.kyamshanov.notepen.sync.domain.port.secureRandomBytes

/**
 * Logical direction of an encrypted frame on the peer link.
 *
 * Each direction is keyed independently (see [SessionCipher]) so the two
 * keystreams never overlap. The connecting peer (client) seals
 * [CLIENT_TO_SERVER] and opens [SERVER_TO_CLIENT]; the host (server) does the
 * reverse.
 */
enum class Direction {
    CLIENT_TO_SERVER,
    SERVER_TO_CLIENT,
}

/** Raised when a frame cannot be authenticated, is malformed, or is a replay. */
class SessionCipherException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Symmetric, per-session AEAD channel for the peer-to-peer sync link.
 *
 * Built purely on the platform crypto primitives ([hkdfSha256], [aesGcmSeal],
 * [aesGcmOpen], [secureRandomBytes]) so it stays platform-agnostic and unit
 * testable. The pairing code (shared secret) plus a per-session [salt] are run
 * through HKDF-SHA-256 to derive two independent 32-byte AES-256-GCM keys, one
 * per [Direction] (`info` labels [INFO_C2S] / [INFO_S2C]). Client→server and
 * server→client therefore never share a keystream even though both sides start
 * from the same secret.
 *
 * ### Frame format
 * ```
 * frame = nonce(12) || ciphertext-with-gcm-tag
 * nonce = sessionPrefix(8) || counter(4, big-endian)
 * ```
 * The 8-byte [sessionPrefix] is chosen once (random by default, injectable for
 * tests) and shared by every frame of this cipher; the 4-byte counter is
 * per-direction and increments on each [seal]. With a fixed prefix and a
 * monotonic counter, no `(key, nonce)` pair is ever reused for the lifetime of
 * the session — the requirement AES-GCM depends on for security.
 *
 * ### Replay protection
 * [open] tracks the highest counter accepted per inbound [Direction] and rejects
 * any frame whose counter is less than or equal to it. Combined with GCM
 * authentication, this rejects tampering, forgery, replay and reordering.
 *
 * Instances are **not** thread-safe; confine each to a single connection.
 *
 * @param sharedSecret the pairing code bytes shared out-of-band by both peers.
 * @param salt per-session salt mixed into HKDF; need not be secret but should be
 *   fresh per session (e.g. random or a handshake transcript hash).
 * @param sessionPrefix the fixed 8-byte nonce prefix; defaults to a fresh random
 *   value and is exposed only so tests can pin it. Must be exactly
 *   [SESSION_PREFIX_BYTES] long.
 */
class SessionCipher(
    sharedSecret: ByteArray,
    salt: ByteArray,
    private val sessionPrefix: ByteArray = secureRandomBytes(SESSION_PREFIX_BYTES),
) {
    private val clientToServerKey: ByteArray = hkdfSha256(sharedSecret, salt, INFO_C2S.encodeToByteArray(), KEY_BYTES)
    private val serverToClientKey: ByteArray = hkdfSha256(sharedSecret, salt, INFO_S2C.encodeToByteArray(), KEY_BYTES)

    /** Next counter to stamp into an outbound nonce, per direction. */
    private val outboundCounters =
        mutableMapOf(
            Direction.CLIENT_TO_SERVER to 0L,
            Direction.SERVER_TO_CLIENT to 0L,
        )

    /** Highest counter accepted on an inbound frame, per direction (-1 = nothing yet). */
    private val inboundHighestSeen =
        mutableMapOf(
            Direction.CLIENT_TO_SERVER to -1L,
            Direction.SERVER_TO_CLIENT to -1L,
        )

    init {
        require(sessionPrefix.size == SESSION_PREFIX_BYTES) {
            "sessionPrefix must be $SESSION_PREFIX_BYTES bytes, was ${sessionPrefix.size}"
        }
    }

    /**
     * Encrypts [plaintext] for transmission in [directionOutbound] and returns a
     * complete frame (`nonce || ciphertext+tag`). Advances the per-direction
     * counter by one.
     *
     * @throws SessionCipherException if this direction's counter space is exhausted
     *   (after 2^32 frames).
     */
    fun seal(
        directionOutbound: Direction,
        plaintext: ByteArray,
    ): ByteArray {
        val counter = outboundCounters.getValue(directionOutbound)
        if (counter > MAX_COUNTER) {
            throw SessionCipherException("Nonce counter exhausted for $directionOutbound; rekey required")
        }
        val nonce = nonceFor(counter)
        val ciphertext = aesGcmSeal(keyFor(directionOutbound), nonce, EMPTY_AAD, plaintext)
        outboundCounters[directionOutbound] = counter + 1
        return nonce + ciphertext
    }

    /**
     * Verifies and decrypts a [frame] received on [directionInbound].
     *
     * Enforces strict forward progress: the frame's counter must be greater than
     * the highest previously accepted on this direction, so replays and
     * out-of-order frames are rejected. On success the highest-seen marker
     * advances to this frame's counter.
     *
     * @throws SessionCipherException if the frame is too short, replayed/out of
     *   order, or fails GCM authentication (wrong key, tampered bytes, or an
     *   altered nonce).
     */
    fun open(
        directionInbound: Direction,
        frame: ByteArray,
    ): ByteArray {
        if (frame.size < NONCE_BYTES) {
            throw SessionCipherException("Frame too short: ${frame.size} bytes, need at least $NONCE_BYTES")
        }
        val nonce = frame.copyOfRange(0, NONCE_BYTES)
        val counter = counterFromNonce(nonce)
        val highestSeen = inboundHighestSeen.getValue(directionInbound)
        if (counter <= highestSeen) {
            throw SessionCipherException(
                "Replayed or out-of-order frame on $directionInbound: counter=$counter, highestSeen=$highestSeen",
            )
        }
        val ciphertext = frame.copyOfRange(NONCE_BYTES, frame.size)
        val plaintext = aesGcmOpen(keyFor(directionInbound), nonce, EMPTY_AAD, ciphertext)
        // Only advance after authentication succeeds, so a forged/tampered frame
        // can never poison the replay window.
        inboundHighestSeen[directionInbound] = counter
        return plaintext
    }

    private fun keyFor(direction: Direction): ByteArray =
        when (direction) {
            Direction.CLIENT_TO_SERVER -> clientToServerKey
            Direction.SERVER_TO_CLIENT -> serverToClientKey
        }

    private fun nonceFor(counter: Long): ByteArray {
        val nonce = ByteArray(NONCE_BYTES)
        sessionPrefix.copyInto(nonce, destinationOffset = 0)
        // 4-byte big-endian counter in the trailing bytes.
        nonce[SESSION_PREFIX_BYTES] = (counter ushr 24).toByte()
        nonce[SESSION_PREFIX_BYTES + 1] = (counter ushr 16).toByte()
        nonce[SESSION_PREFIX_BYTES + 2] = (counter ushr 8).toByte()
        nonce[SESSION_PREFIX_BYTES + 3] = counter.toByte()
        return nonce
    }

    private fun counterFromNonce(nonce: ByteArray): Long {
        var value = 0L
        for (i in 0 until COUNTER_BYTES) {
            value = (value shl 8) or (nonce[SESSION_PREFIX_BYTES + i].toLong() and 0xFF)
        }
        return value
    }

    companion object {
        /** HKDF `info` label for the client→server key. */
        const val INFO_C2S: String = "notepen-c2s"

        /** HKDF `info` label for the server→client key. */
        const val INFO_S2C: String = "notepen-s2c"

        /** AES-256 key length in bytes. */
        const val KEY_BYTES: Int = 32

        /** Fixed random nonce prefix length in bytes. */
        const val SESSION_PREFIX_BYTES: Int = 8

        /** Per-direction frame-counter length in bytes (big-endian). */
        const val COUNTER_BYTES: Int = 4

        /** Total GCM nonce length: prefix + counter. */
        const val NONCE_BYTES: Int = SESSION_PREFIX_BYTES + COUNTER_BYTES

        /** Largest value the 4-byte counter can hold (2^32 - 1). */
        private const val MAX_COUNTER: Long = 0xFFFF_FFFFL

        private val EMPTY_AAD: ByteArray = ByteArray(0)
    }
}
