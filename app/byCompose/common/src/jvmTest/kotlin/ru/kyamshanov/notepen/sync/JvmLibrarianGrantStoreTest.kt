package ru.kyamshanov.notepen.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.sync.infrastructure.JvmLibrarianGrantStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class JvmLibrarianGrantStoreTest {
    private val dataDir: Path = Files.createTempDirectory("notepen-librarian-grants-test")

    @AfterTest
    fun cleanup() {
        dataDir.toFile().deleteRecursively()
    }

    private fun store() =
        JvmLibrarianGrantStore(
            dataDir = dataDir,
            ioDispatcher = UnconfinedTestDispatcher(),
        )

    @Test
    fun load_onMissingFile_returnsEmpty() =
        runTest {
            assertTrue(store().load().isEmpty())
        }

    @Test
    fun grantThenLoad_survivesRestart() =
        runTest {
            store().grant("peer-1")
            store().grant("peer-2")
            // A fresh store instance simulates a host restart re-reading from disk.
            assertEquals(setOf("peer-1", "peer-2"), store().load(), "grants persist across store instances")
        }

    @Test
    fun grant_isIdempotent() =
        runTest {
            val store = store()
            store.grant("peer-1")
            store.grant("peer-1")
            assertEquals(setOf("peer-1"), store.load(), "granting the same peer twice keeps a single entry")
        }

    @Test
    fun revoke_dropsMatchingPeer_andIsNoOpWhenAbsent() =
        runTest {
            val store = store()
            store.grant("a")
            store.grant("b")
            store.revoke("a")
            assertEquals(setOf("b"), store.load())
            store.revoke("missing")
            assertEquals(setOf("b"), store().load(), "revoking an absent peer is a no-op")
        }

    @Test
    fun grant_isAtomic_noTempFileLeftBehind() =
        runTest {
            store().grant("p")
            val files = dataDir.toFile().listFiles()?.map { it.name }.orEmpty()
            assertTrue(files.contains("librarian_peers.json"), "target file written")
            assertFalse(files.any { it.endsWith(".tmp") }, "atomic rename leaves no temp file: $files")
        }

    @Test
    fun load_onCorruptFile_returnsEmpty() =
        runTest {
            Files.writeString(dataDir.resolve("librarian_peers.json"), "{ not json")
            assertTrue(store().load().isEmpty(), "corrupt file degrades to empty, not a crash")
        }
}
