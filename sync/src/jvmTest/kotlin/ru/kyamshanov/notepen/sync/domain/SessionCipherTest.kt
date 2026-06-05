package ru.kyamshanov.notepen.sync.domain

import ru.kyamshanov.notepen.sync.domain.port.hkdfSha256
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SessionCipherTest {
    private val sharedSecret = "pairing-code-123456".encodeToByteArray()
    private val salt = "session-salt".encodeToByteArray()
    private val prefix = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

    private fun cipher(prefixBytes: ByteArray = prefix) =
        SessionCipher(sharedSecret = sharedSecret, salt = salt, sessionPrefix = prefixBytes)

    @Test
    fun sealThenOpenRoundTrips() {
        // Two independent cipher instances over the same secret/salt/prefix model
        // the client and the host: one seals C2S, the other opens C2S.
        val client = cipher()
        val server = cipher()
        val message = "hello, peer".encodeToByteArray()

        val frame = client.seal(Direction.CLIENT_TO_SERVER, message)
        val recovered = server.open(Direction.CLIENT_TO_SERVER, frame)

        assertContentEquals(message, recovered)
    }

    @Test
    fun roundTripsInBothDirectionsIndependently() {
        val client = cipher()
        val server = cipher()

        val toServer = "c2s".encodeToByteArray()
        val toClient = "s2c".encodeToByteArray()

        val c2sFrame = client.seal(Direction.CLIENT_TO_SERVER, toServer)
        val s2cFrame = server.seal(Direction.SERVER_TO_CLIENT, toClient)

        assertContentEquals(toServer, server.open(Direction.CLIENT_TO_SERVER, c2sFrame))
        assertContentEquals(toClient, client.open(Direction.SERVER_TO_CLIENT, s2cFrame))
    }

    @Test
    fun frameLayoutIsNoncePlusCiphertext() {
        val frame = cipher().seal(Direction.CLIENT_TO_SERVER, "x".encodeToByteArray())

        // First 8 bytes are the injected session prefix; next 4 are the counter
        // (big-endian 0 for the first frame).
        assertContentEquals(prefix, frame.copyOfRange(0, SessionCipher.SESSION_PREFIX_BYTES))
        assertContentEquals(
            byteArrayOf(0, 0, 0, 0),
            frame.copyOfRange(SessionCipher.SESSION_PREFIX_BYTES, SessionCipher.NONCE_BYTES),
        )
        // GCM adds a 16-byte tag, so a 1-byte plaintext yields 12 + 1 + 16 bytes.
        assertEquals(SessionCipher.NONCE_BYTES + 1 + 16, frame.size)
    }

    @Test
    fun counterIncrementsPerSealWithinDirection() {
        val client = cipher()

        val first = client.seal(Direction.CLIENT_TO_SERVER, "a".encodeToByteArray())
        val second = client.seal(Direction.CLIENT_TO_SERVER, "b".encodeToByteArray())

        val firstCounter = first.copyOfRange(SessionCipher.SESSION_PREFIX_BYTES, SessionCipher.NONCE_BYTES)
        val secondCounter = second.copyOfRange(SessionCipher.SESSION_PREFIX_BYTES, SessionCipher.NONCE_BYTES)
        assertContentEquals(byteArrayOf(0, 0, 0, 0), firstCounter)
        assertContentEquals(byteArrayOf(0, 0, 0, 1), secondCounter)
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val client = cipher()
        val server = cipher()
        val frame = client.seal(Direction.CLIENT_TO_SERVER, "secret".encodeToByteArray())

        // Flip a bit in the ciphertext body (past the 12-byte nonce).
        val tampered = frame.copyOf()
        tampered[SessionCipher.NONCE_BYTES] = (tampered[SessionCipher.NONCE_BYTES].toInt() xor 0x01).toByte()

        assertFailsWith<SessionCipherException> {
            server.open(Direction.CLIENT_TO_SERVER, tampered)
        }
    }

    @Test
    fun tamperedNonceIsRejected() {
        val client = cipher()
        val server = cipher()
        val frame = client.seal(Direction.CLIENT_TO_SERVER, "secret".encodeToByteArray())

        // Flipping a prefix byte changes the GCM IV; authentication must fail.
        val tampered = frame.copyOf()
        tampered[0] = (tampered[0].toInt() xor 0x01).toByte()

        assertFailsWith<SessionCipherException> {
            server.open(Direction.CLIENT_TO_SERVER, tampered)
        }
    }

    @Test
    fun replayedFrameIsRejected() {
        val client = cipher()
        val server = cipher()
        val frame = client.seal(Direction.CLIENT_TO_SERVER, "once".encodeToByteArray())

        // First delivery succeeds.
        server.open(Direction.CLIENT_TO_SERVER, frame)
        // Replaying the identical frame (same counter) must be rejected.
        assertFailsWith<SessionCipherException> {
            server.open(Direction.CLIENT_TO_SERVER, frame)
        }
    }

    @Test
    fun outOfOrderOlderCounterIsRejected() {
        val client = cipher()
        val server = cipher()

        val frame0 = client.seal(Direction.CLIENT_TO_SERVER, "0".encodeToByteArray())
        val frame1 = client.seal(Direction.CLIENT_TO_SERVER, "1".encodeToByteArray())

        // Accept the newer frame first, then the older one must be rejected.
        server.open(Direction.CLIENT_TO_SERVER, frame1)
        assertFailsWith<SessionCipherException> {
            server.open(Direction.CLIENT_TO_SERVER, frame0)
        }
    }

    @Test
    fun replayWindowAdvancesOnlyOnSuccessfulOpen() {
        val client = cipher()
        val server = cipher()

        val frame0 = client.seal(Direction.CLIENT_TO_SERVER, "0".encodeToByteArray())
        val frame1 = client.seal(Direction.CLIENT_TO_SERVER, "1".encodeToByteArray())

        // A forged frame1 must fail AND must not advance the replay marker, so the
        // genuine frame0 is still accepted afterwards.
        val forged = frame1.copyOf()
        forged[forged.size - 1] = (forged[forged.size - 1].toInt() xor 0x01).toByte()
        assertFailsWith<SessionCipherException> {
            server.open(Direction.CLIENT_TO_SERVER, forged)
        }

        assertContentEquals("0".encodeToByteArray(), server.open(Direction.CLIENT_TO_SERVER, frame0))
    }

    @Test
    fun wrongSharedSecretFailsToOpen() {
        val client = cipher()
        val attacker =
            SessionCipher(
                sharedSecret = "different-pairing-code".encodeToByteArray(),
                salt = salt,
                sessionPrefix = prefix,
            )
        val frame = client.seal(Direction.CLIENT_TO_SERVER, "secret".encodeToByteArray())

        assertFailsWith<SessionCipherException> {
            attacker.open(Direction.CLIENT_TO_SERVER, frame)
        }
    }

    @Test
    fun wrongSaltFailsToOpen() {
        val client = cipher()
        val server =
            SessionCipher(
                sharedSecret = sharedSecret,
                salt = "other-salt".encodeToByteArray(),
                sessionPrefix = prefix,
            )
        val frame = client.seal(Direction.CLIENT_TO_SERVER, "secret".encodeToByteArray())

        assertFailsWith<SessionCipherException> {
            server.open(Direction.CLIENT_TO_SERVER, frame)
        }
    }

    @Test
    fun directionsUseSeparateKeysSoCrossDirectionOpenFails() {
        val client = cipher()
        val server = cipher()
        // Sealed as C2S, but opened as S2C — different derived key, must fail even
        // though the counter (0) has never been seen on the S2C inbound channel.
        val frame = client.seal(Direction.CLIENT_TO_SERVER, "secret".encodeToByteArray())

        assertFailsWith<SessionCipherException> {
            server.open(Direction.SERVER_TO_CLIENT, frame)
        }
    }

    @Test
    fun rejectsFrameShorterThanNonce() {
        assertFailsWith<SessionCipherException> {
            cipher().open(Direction.CLIENT_TO_SERVER, ByteArray(SessionCipher.NONCE_BYTES - 1))
        }
    }

    @Test
    fun rejectsWrongSizedSessionPrefix() {
        assertFailsWith<IllegalArgumentException> {
            SessionCipher(sharedSecret = sharedSecret, salt = salt, sessionPrefix = ByteArray(7))
        }
    }

    @Test
    fun longPlaintextRoundTrips() {
        val client = cipher()
        val server = cipher()
        val big = ByteArray(100_000) { (it % 251).toByte() }

        val frame = client.seal(Direction.CLIENT_TO_SERVER, big)
        assertContentEquals(big, server.open(Direction.CLIENT_TO_SERVER, frame))
    }

    /**
     * RFC 5869 Appendix A.1 — Test Case 1 (basic, SHA-256). Validates the HKDF
     * extract+expand implementation against the published known-answer vector.
     */
    @Test
    fun hkdfMatchesRfc5869TestCase1() {
        val ikm = hexToBytes("0b".repeat(22))
        val salt = hexToBytes("000102030405060708090a0b0c")
        val info = hexToBytes("f0f1f2f3f4f5f6f7f8f9")
        val length = 42
        val expected =
            hexToBytes(
                "3cb25f25faacd57a90434f64d0362f2a" +
                    "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                    "34007208d5b887185865",
            )

        val okm = hkdfSha256(secret = ikm, salt = salt, info = info, lengthBytes = length)

        assertContentEquals(expected, okm)
    }

    /**
     * RFC 5869 Appendix A.3 — Test Case 3 (zero-length salt and info). Exercises
     * the empty-salt branch (substituted with a HashLen zero block).
     */
    @Test
    fun hkdfMatchesRfc5869TestCase3EmptySaltAndInfo() {
        val ikm = hexToBytes("0b".repeat(22))
        val expected =
            hexToBytes(
                "8da4e775a563c18f715f802a063c5a31" +
                    "b8a11f5c5ee1879ec3454e5f3c738d2d" +
                    "9d201395faa4b61a96c8",
            )

        val okm = hkdfSha256(secret = ikm, salt = ByteArray(0), info = ByteArray(0), lengthBytes = 42)

        assertContentEquals(expected, okm)
    }

    @Test
    fun derivedDirectionalKeysDiffer() {
        // INFO_C2S vs INFO_S2C must produce distinct keystreams from the same IKM.
        val c2s = hkdfSha256(sharedSecret, salt, SessionCipher.INFO_C2S.encodeToByteArray(), SessionCipher.KEY_BYTES)
        val s2c = hkdfSha256(sharedSecret, salt, SessionCipher.INFO_S2C.encodeToByteArray(), SessionCipher.KEY_BYTES)
        assertEquals(SessionCipher.KEY_BYTES, c2s.size)
        assertTrue(!c2s.contentEquals(s2c), "Directional keys must differ")
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.filterNot { it.isWhitespace() }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
