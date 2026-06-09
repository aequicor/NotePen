package ru.kyamshanov.notepen.sync.infrastructure

import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.sync.domain.Direction
import ru.kyamshanov.notepen.sync.domain.SessionCipher
import ru.kyamshanov.notepen.sync.domain.SessionCipherException
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Protocol-level tests for [SecureChannel]: the authenticated-encryption layer that
 * secures the peer link on top of the cleartext pairing handshake. These exercise key
 * derivation, the key-confirmation hello, and message sealing without a live socket
 * (the socket-bound [SecureChannel.sendEncrypted]/[SecureChannel.receiveEncrypted]
 * are covered end-to-end by [KtorPeerServerTest]).
 */
class SecureChannelTest {
    private val code = "PAIRINGCODE0123456789ABCDE"
    private val json = Json { classDiscriminator = "type" }

    /** Mirrors what the client and host do after the handshake: both derive from the same inputs. */
    private fun pairOfCiphers(
        pairingCode: String = code,
        clientNonce: String = SecureChannel.newNonce(),
        serverNonce: String = SecureChannel.newNonce(),
    ): Pair<SessionCipher, SessionCipher> {
        val client = SecureChannel.deriveCipher(pairingCode, clientNonce, serverNonce)
        val server = SecureChannel.deriveCipher(pairingCode, clientNonce, serverNonce)
        return client to server
    }

    @Test
    fun ciphersFromSameCodeAndNoncesRoundTripANetworkMessage() {
        // Same pairing code + same handshake nonces on both ends => interoperable keys.
        val clientNonce = SecureChannel.newNonce()
        val serverNonce = SecureChannel.newNonce()
        val (client, server) = pairOfCiphers(clientNonce = clientNonce, serverNonce = serverNonce)

        val message: NetworkMessage =
            NetworkMessage.StrokeDeltaMessage(
                delta =
                    StrokeDelta.Removed(
                        strokeId = "s-1",
                        pageIndex = 3,
                        authorDeviceId = "dev-7",
                        clock = 42L,
                    ),
                documentId = "doc-42",
            )

        // Client seals C2S; host opens C2S — the JSON survives the seal/open round trip.
        val plaintext = json.encodeToString(NetworkMessage.serializer(), message).encodeToByteArray()
        val frame = client.seal(Direction.CLIENT_TO_SERVER, plaintext)
        val recovered =
            json.decodeFromString(NetworkMessage.serializer(), server.open(Direction.CLIENT_TO_SERVER, frame).decodeToString())

        assertEquals(message, recovered)
    }

    @Test
    fun roundTripsServerToClientToo() {
        val (client, server) = pairOfCiphers()
        val message: NetworkMessage = NetworkMessage.PairAccepted(DeviceInfo("h", "Host", "127.0.0.1", 9), serverNonce = "x")

        val plaintext = json.encodeToString(NetworkMessage.serializer(), message).encodeToByteArray()
        val frame = server.seal(Direction.SERVER_TO_CLIENT, plaintext)
        val recovered =
            json.decodeFromString(NetworkMessage.serializer(), client.open(Direction.SERVER_TO_CLIENT, frame).decodeToString())

        assertEquals(message, recovered)
    }

    @Test
    fun helloConfirmationSucceedsWithMatchingCodeAndNonces() {
        val clientNonce = SecureChannel.newNonce()
        val serverNonce = SecureChannel.newNonce()
        val (client, server) = pairOfCiphers(clientNonce = clientNonce, serverNonce = serverNonce)

        // Host -> client hello, verified by client against the announced serverNonce.
        val serverHello = SecureChannel.buildHello(server, Direction.SERVER_TO_CLIENT, ownNonce = serverNonce)
        SecureChannel.verifyHello(client, Direction.SERVER_TO_CLIENT, serverHello, expectedPeerNonce = serverNonce)

        // Client -> host hello-ack, verified by host against the announced clientNonce.
        val clientHello = SecureChannel.buildHello(client, Direction.CLIENT_TO_SERVER, ownNonce = clientNonce)
        SecureChannel.verifyHello(server, Direction.CLIENT_TO_SERVER, clientHello, expectedPeerNonce = clientNonce)
    }

    @Test
    fun wrongPairingCodeFailsTheHello() {
        // Both sides agree on the nonces (they travel in cleartext), but the attacker
        // does not know the real pairing code, so its derived key differs and the
        // host's hello cannot be opened — the link fails closed before any data flows.
        val clientNonce = SecureChannel.newNonce()
        val serverNonce = SecureChannel.newNonce()
        val honestServer = SecureChannel.deriveCipher(code, clientNonce, serverNonce)
        val attacker = SecureChannel.deriveCipher("WRONGCODE0123456789ABCDEFG", clientNonce, serverNonce)

        val serverHello = SecureChannel.buildHello(honestServer, Direction.SERVER_TO_CLIENT, ownNonce = serverNonce)

        assertFailsWith<SessionCipherException> {
            SecureChannel.verifyHello(attacker, Direction.SERVER_TO_CLIENT, serverHello, expectedPeerNonce = serverNonce)
        }
    }

    @Test
    fun helloEchoingTheWrongNonceIsRejectedEvenWithTheRightKey() {
        // Decryption succeeds (right key) but the echoed nonce is not the one the peer
        // announced — e.g. a replayed hello from a different session — so verification
        // must still reject it.
        val clientNonce = SecureChannel.newNonce()
        val serverNonce = SecureChannel.newNonce()
        val (client, server) = pairOfCiphers(clientNonce = clientNonce, serverNonce = serverNonce)

        val helloWithOtherNonce = SecureChannel.buildHello(server, Direction.SERVER_TO_CLIENT, ownNonce = SecureChannel.newNonce())

        assertFailsWith<SessionCipherException> {
            SecureChannel.verifyHello(client, Direction.SERVER_TO_CLIENT, helloWithOtherNonce, expectedPeerNonce = serverNonce)
        }
    }

    @Test
    fun differentSessionsDeriveDifferentKeys() {
        // Same code, different nonces (a later session) => the recorded ciphertext of an
        // earlier session cannot be opened by the new session's cipher.
        val sessionA = SecureChannel.deriveCipher(code, SecureChannel.newNonce(), SecureChannel.newNonce())
        val frameFromSessionA = sessionA.seal(Direction.CLIENT_TO_SERVER, "hi".encodeToByteArray())
        val sessionB = SecureChannel.deriveCipher(code, SecureChannel.newNonce(), SecureChannel.newNonce())

        assertFailsWith<SessionCipherException> {
            sessionB.open(Direction.CLIENT_TO_SERVER, frameFromSessionA)
        }
    }

    @Test
    fun missingNonceFailsClosed() {
        // A peer predating the encrypted channel sends an empty nonce; derivation must
        // refuse rather than silently key off a degenerate salt.
        assertFailsWith<SessionCipherException> {
            SecureChannel.deriveCipher(code, clientNonce = "", serverNonce = SecureChannel.newNonce())
        }
    }

    @Test
    fun malformedNonceFailsClosed() {
        assertFailsWith<SessionCipherException> {
            SecureChannel.deriveCipher(code, clientNonce = "!!!not-base64!!!", serverNonce = SecureChannel.newNonce())
        }
    }

    @Test
    fun newNonceIsFreshAndCorrectlySized() {
        val a = SecureChannel.newNonce()
        val b = SecureChannel.newNonce()
        assertTrue(a != b, "nonces must be random")
        assertEquals(SecureChannel.NONCE_BYTES, decodeBase64(a).size)
    }
}
