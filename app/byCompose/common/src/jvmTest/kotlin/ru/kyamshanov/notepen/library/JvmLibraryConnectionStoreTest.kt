package ru.kyamshanov.notepen.library

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.infrastructure.JvmLibraryConnectionStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class JvmLibraryConnectionStoreTest {
    private val dataDir: Path = Files.createTempDirectory("notepen-libconn-test")

    @AfterTest
    fun cleanup() {
        dataDir.toFile().deleteRecursively()
    }

    private fun store() =
        JvmLibraryConnectionStore(
            dataDir = dataDir,
            ioDispatcher = UnconfinedTestDispatcher(),
        )

    @Test
    fun load_onMissingFile_returnsEmpty() =
        runTest {
            assertTrue(store().load().isEmpty())
        }

    @Test
    fun saveThenLoad_roundTripsAllVariants() =
        runTest {
            val store = store()
            val connections =
                listOf(
                    LibraryConnection.PeerLan(peerId = "peer-1", host = "10.0.0.5"),
                    LibraryConnection.PeerLan(peerId = "peer-2"),
                    LibraryConnection.GitHub(repo = "owner/name", token = "tok"),
                    LibraryConnection.Cloud(providerId = "drive", accountId = "acc"),
                    LibraryConnection.Local(rootPath = "/tmp/lib", displayName = "My Lib"),
                )
            store.save(connections)
            assertEquals(connections, store().load(), "a fresh store reads back the same list from disk")
        }

    @Test
    fun add_isIdempotent_andReplacesEqualEntry() =
        runTest {
            val store = store()
            val peer = LibraryConnection.PeerLan(peerId = "peer-1", host = "10.0.0.5")
            store.add(peer)
            val after = store.add(peer)
            assertEquals(listOf(peer), after, "adding an equal connection twice keeps a single entry")
            assertEquals(listOf(peer), store().load())
        }

    @Test
    fun remove_dropsMatchingEntry_andIsNoOpWhenAbsent() =
        runTest {
            val store = store()
            val a = LibraryConnection.PeerLan(peerId = "a")
            val b = LibraryConnection.GitHub(repo = "o/r")
            store.save(listOf(a, b))
            assertEquals(listOf(b), store.remove(a))
            assertEquals(listOf(b), store.remove(LibraryConnection.PeerLan(peerId = "missing")))
            assertEquals(listOf(b), store().load())
        }

    @Test
    fun save_isAtomic_noTempFileLeftBehind() =
        runTest {
            val store = store()
            store.save(listOf(LibraryConnection.PeerLan(peerId = "p")))
            val files = dataDir.toFile().listFiles()?.map { it.name }.orEmpty()
            assertTrue(files.contains("library_connections.json"), "target file written")
            assertFalse(
                files.any { it.endsWith(".tmp") },
                "atomic rename leaves no temp file behind: $files",
            )
        }

    @Test
    fun load_onCorruptFile_returnsEmpty() =
        runTest {
            Files.writeString(dataDir.resolve("library_connections.json"), "{ this is not json")
            assertTrue(store().load().isEmpty(), "corrupt file degrades to empty, not a crash")
        }

    @Test
    fun save_encryptsAtRest_secretNotInPlaintext_butRoundTrips() =
        runTest {
            val secret = "ghp_super_secret_pat_value"
            val connections = listOf(LibraryConnection.GitHub(repo = "owner/name", token = secret))
            store().save(connections)

            val bytes = Files.readAllBytes(dataDir.resolve("library_connections.json"))
            assertFalse(
                bytes.decodeToString().contains(secret),
                "the persisted token must not appear in plaintext on disk",
            )
            assertFalse(
                bytes.decodeToString().trimStart().startsWith("["),
                "the persisted file is an encrypted blob, not a bare JSON array",
            )
            assertEquals(connections, store().load(), "a fresh store decrypts the same list back")
        }

    @Test
    fun load_migratesLegacyPlaintextFile_thenReEncryptsIt() =
        runTest {
            // A pre-encryption plaintext file: a bare JSON array, exactly as older versions wrote it.
            val legacy =
                """
                [
                  { "type": "github", "repo": "owner/name", "token": "legacy_token" }
                ]
                """.trimIndent()
            val target = dataDir.resolve("library_connections.json")
            Files.writeString(target, legacy)

            val loaded = store().load()
            assertEquals(
                listOf(LibraryConnection.GitHub(repo = "owner/name", token = "legacy_token")),
                loaded,
                "a legacy plaintext file still loads (migration is transparent)",
            )

            val afterMigration = Files.readAllBytes(target).decodeToString()
            assertFalse(
                afterMigration.contains("legacy_token"),
                "load() re-encrypts the migrated file so the secret is no longer plaintext on disk",
            )
            assertEquals(loaded, store().load(), "the re-encrypted file decrypts back to the same list")
        }
}
