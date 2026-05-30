package ru.kyamshanov.notepen.sync.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.sync.domain.port.CacheFileStore
import ru.kyamshanov.notepen.sync.domain.port.CachedFile
import ru.kyamshanov.notepen.sync.domain.port.LocalDocumentIdRegistry
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryOpenDocumentRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CacheEvictorTest {
    /** In-memory [CacheFileStore] over a path→CachedFile map; records deletions. */
    private class FakeFileStore(
        files: List<CachedFile>,
    ) : CacheFileStore {
        val files = files.associateBy { it.path }.toMutableMap()
        val deleted = mutableListOf<String>()

        override suspend fun listFiles(directory: String): List<CachedFile> = files.values.filter { it.path.startsWith("$directory/") }

        override suspend fun delete(path: String): Boolean {
            files.remove(path)
            deleted += path
            return true
        }
    }

    /** Static path→documentId map standing in for the persistent registry. */
    private class FakeIdRegistry(
        private val byPath: Map<String, String>,
    ) : LocalDocumentIdRegistry {
        override fun lookup(localPath: String): String? = byPath[localPath]

        override suspend fun register(
            localPath: String,
            documentId: String,
        ) = Unit

        override suspend fun forget(localPath: String) = Unit
    }

    private val dir = "/cache"

    private fun file(
        name: String,
        size: Long,
        access: Long,
    ) = CachedFile(path = "$dir/$name", sizeBytes = size, lastAccessMillis = access)

    @Test
    fun underCap_noEviction() =
        runTest {
            val store = FakeFileStore(listOf(file("a", 10, 1), file("b", 10, 2)))
            val evictor =
                CacheEvictor(
                    fileStore = store,
                    openDocumentRegistry = InMemoryOpenDocumentRegistry(),
                    documentIdRegistry = null,
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                    capBytes = 100,
                )

            val deleted = evictor.evict(listOf(dir))

            assertEquals(0, deleted, "already under cap → nothing evicted")
            assertTrue(store.deleted.isEmpty())
        }

    @Test
    fun overCap_evictsLeastRecentlyUsedFirst_untilUnderCap() =
        runTest {
            // 4 files × 30 B = 120 B, cap 70 B → must drop 50+ B, i.e. the two oldest (a, b).
            val store =
                FakeFileStore(
                    listOf(
                        file("a", 30, access = 1),
                        file("b", 30, access = 2),
                        file("c", 30, access = 3),
                        file("d", 30, access = 4),
                    ),
                )
            val evictor =
                CacheEvictor(
                    fileStore = store,
                    openDocumentRegistry = InMemoryOpenDocumentRegistry(),
                    documentIdRegistry = null,
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                    capBytes = 70,
                )

            val deleted = evictor.evict(listOf(dir))

            assertEquals(2, deleted)
            assertEquals(listOf("$dir/a", "$dir/b"), store.deleted, "oldest-by-access evicted first")
            assertTrue(store.files.keys.containsAll(setOf("$dir/c", "$dir/d")), "newer files survive")
        }

    @Test
    fun overCap_skipsOpenPinnedDocument_evictsNextEvictableInstead() =
        runTest {
            // Oldest file 'a' maps to an OPEN document; it must be skipped even though it is the LRU
            // candidate. Its bytes still count toward the cap, so the next-oldest evictable ('b','c')
            // are removed to get under the 70 B cap.
            val store =
                FakeFileStore(
                    listOf(
                        file("a", 30, access = 1),
                        file("b", 30, access = 2),
                        file("c", 30, access = 3),
                        file("d", 30, access = 4),
                    ),
                )
            val openDocs = InMemoryOpenDocumentRegistry().apply { acquire("doc-a") }
            val idRegistry = FakeIdRegistry(mapOf("$dir/a" to "doc-a"))
            val evictor =
                CacheEvictor(
                    fileStore = store,
                    openDocumentRegistry = openDocs,
                    documentIdRegistry = idRegistry,
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                    capBytes = 70,
                )

            evictor.evict(listOf(dir))

            assertTrue(store.files.containsKey("$dir/a"), "the open/pinned document is NEVER evicted")
            assertFalse(store.files.containsKey("$dir/b"), "next-oldest evictable is removed")
            assertFalse(store.files.containsKey("$dir/c"), "and the one after, to reach the cap")
            assertTrue(store.files.containsKey("$dir/d"))
        }

    @Test
    fun evict_spansMultipleDirectories() =
        runTest {
            val lan = "/lan"
            val github = "/github"
            val store =
                FakeFileStore(
                    listOf(
                        CachedFile("$lan/x", 40, 1),
                        CachedFile("$github/y", 40, 2),
                        CachedFile("$github/z", 40, 3),
                    ),
                )
            val evictor =
                CacheEvictor(
                    fileStore = store,
                    openDocumentRegistry = InMemoryOpenDocumentRegistry(),
                    documentIdRegistry = null,
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                    capBytes = 50,
                )

            val deleted = evictor.evict(listOf(lan, github))

            assertEquals(2, deleted, "the combined cap spans both cache dirs")
            assertEquals(listOf("$lan/x", "$github/y"), store.deleted, "global LRU across directories")
        }
}
