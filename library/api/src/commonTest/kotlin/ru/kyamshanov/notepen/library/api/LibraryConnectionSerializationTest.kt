package ru.kyamshanov.notepen.library.api

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Polymorphic JSON round-trip of the sealed [LibraryConnection] hierarchy, plus the discriminator
 * contract relied on for backward compatibility.
 */
class LibraryConnectionSerializationTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

    private val listSerializer = ListSerializer(LibraryConnection.serializer())

    @Test
    fun roundTrip_allVariants() {
        val connections =
            listOf(
                LibraryConnection.Local(rootPath = "/tmp/lib", displayName = "My Lib"),
                LibraryConnection.PeerLan(peerId = "peer-1", host = "10.0.0.5"),
                LibraryConnection.PeerLan(peerId = "peer-2", host = null),
                LibraryConnection.PeerLan(
                    peerId = "peer-3",
                    host = "10.0.0.7",
                    port = 51234,
                    libraryId = "local:/r/Math",
                    libraryName = "Math",
                    pairingCode = "482193",
                ),
                LibraryConnection.GitHub(repo = "owner/name", token = "tok"),
                LibraryConnection.GitHub(repo = "anon/repo", token = null),
                LibraryConnection.Cloud(providerId = "drive", accountId = "acc"),
            )
        val text = json.encodeToString(listSerializer, connections)
        assertEquals(connections, json.decodeFromString(listSerializer, text))
    }

    @Test
    fun discriminator_isStableSerialName() {
        val text = json.encodeToString(LibraryConnection.serializer(), LibraryConnection.PeerLan("p"))
        // The persisted discriminator must stay "peer_lan" for forward/backward compat.
        assertTrue(text.contains("\"peer_lan\""), "PeerLan serialises with its stable type tag: $text")
    }

    @Test
    fun decode_fromStableDiscriminators() {
        val text =
            """
            [
              {"type":"local","rootPath":"/r"},
              {"type":"peer_lan","peerId":"p"},
              {"type":"github","repo":"o/r"},
              {"type":"cloud","providerId":"drive","accountId":"a"}
            ]
            """.trimIndent()
        val decoded = json.decodeFromString(listSerializer, text)
        assertEquals(
            listOf(
                LibraryConnection.Local("/r"),
                LibraryConnection.PeerLan(peerId = "p", host = null),
                LibraryConnection.GitHub(repo = "o/r", token = null),
                LibraryConnection.Cloud(providerId = "drive", accountId = "a"),
            ),
            decoded,
            "omitted optional fields fall back to defaults (backward compat)",
        )
        decoded.filterIsInstance<LibraryConnection.PeerLan>().single().let { peer ->
            assertEquals(null, peer.port, "legacy peer_lan has no port")
            assertEquals("", peer.libraryId, "legacy peer_lan targets the whole shelf")
            assertEquals(null, peer.pairingCode, "legacy peer_lan has no stored pairing code")
        }
        assertEquals(
            "",
            decoded.filterIsInstance<LibraryConnection.Local>().single().displayName,
            "a legacy local spec with no displayName key decodes to the empty default",
        )
    }
}
