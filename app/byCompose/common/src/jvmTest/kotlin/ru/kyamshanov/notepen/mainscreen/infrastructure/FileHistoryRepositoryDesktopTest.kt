package ru.kyamshanov.notepen.mainscreen.infrastructure

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.domain.model.RecentFile
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты [FileHistoryRepositoryDesktop].
 * TC-02: upsert + getAll
 * TC-19: 21 запись → вытеснение старых
 */
class FileHistoryRepositoryDesktopTest {

    private lateinit var tmpDir: File
    private lateinit var repository: FileHistoryRepositoryDesktop

    @BeforeTest
    fun setUp() {
        tmpDir = createTempDir("notepen-test-history")
        repository = FileHistoryRepositoryDesktop(dataDir = tmpDir)
    }

    @AfterTest
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    private fun makeFile(id: String, uri: String = "/tmp/$id.pdf", openedAt: Long = System.currentTimeMillis()) =
        RecentFile(
            id = id,
            uri = uri,
            displayName = id,
            fileSize = null,
            openedAt = openedAt,
            availabilityStatus = AvailabilityStatus.AVAILABLE,
            thumbnailKey = null,
            fileMtime = null,
            lastPageIndex = 0,
        )

    // TC-02: upsert persists entry and getAll returns it sorted
    @Test
    fun `upsert then getAll returns entry sorted by openedAt desc`() = runTest {
        val older = makeFile("a", openedAt = 1000L)
        val newer = makeFile("b", openedAt = 2000L)
        repository.upsert(older, 0)
        repository.upsert(newer, 0)

        val result = repository.getAll()
        assertEquals(2, result.size)
        assertEquals("b", result[0].id)
        assertEquals("a", result[1].id)
    }

    // TC-02: upsert with same uri does not create a duplicate
    @Test
    fun `upsert with same uri does not create duplicate`() = runTest {
        val file = makeFile("x", uri = "/tmp/x.pdf")
        repository.upsert(file, 0)
        repository.upsert(file.copy(displayName = "updated"), 3)

        val result = repository.getAll()
        // FileHistoryManager.applyUpsert keeps list size stable when same uri is upserted
        assertEquals(1, result.size)
    }

    // TC-19: 21 records → eviction keeps max 20
    @Test
    fun `upsert 21 entries causes eviction to max 20`() = runTest {
        for (i in 1..21) {
            repository.upsert(makeFile("file$i", uri = "/tmp/file$i.pdf", openedAt = i.toLong()), 0)
        }
        val result = repository.getAll()
        assertTrue(result.size <= 20, "Expected max 20 entries, got ${result.size}")
    }

    @Test
    fun `updateStatus changes status of matching entry`() = runTest {
        val file = makeFile("z")
        repository.upsert(file, 0)
        repository.updateStatus(file.id, AvailabilityStatus.NOT_FOUND)

        val result = repository.getAll()
        assertEquals(AvailabilityStatus.NOT_FOUND, result.first { it.id == "z" }.availabilityStatus)
    }

    @Test
    fun `rollbackUpsert removes entry by uri`() = runTest {
        val file = makeFile("r", uri = "/tmp/r.pdf")
        repository.upsert(file, 0)
        repository.rollbackUpsert(file.uri)

        assertTrue(repository.getAll().none { it.uri == file.uri })
    }

    // CC-5: crash recovery — data persists across repository reinstantiation (simulates process restart)
    @Test
    fun `upsert persistsAcrossReinstantiation`() = runTest {
        val file = makeFile("crash-test", uri = "/tmp/crash-test.pdf")
        repository.upsert(file, 0)

        // Simulate process restart by creating a new repository instance pointing to same dir
        val repo2 = FileHistoryRepositoryDesktop(dataDir = tmpDir)
        val files = repo2.getAll()

        assertTrue(files.any { it.uri == "/tmp/crash-test.pdf" }, "File must survive reinstantiation")
    }

    // TC-45 / CC-10: Concurrent upserts from multiple coroutines — no duplicates, no data corruption
    @Test
    fun `upsert_concurrent_noDuplicates`() = runTest {
        val jobs = (1..5).map { i ->
            launch {
                val file = makeFile("id-$i", uri = "/tmp/file$i.pdf", openedAt = i.toLong())
                repository.upsert(file, 0)
            }
        }
        jobs.forEach { it.join() }
        val all = repository.getAll()
        assertEquals(5, all.size, "All 5 concurrent upserts must be stored")
        assertEquals(5, all.map { it.uri }.toSet().size, "No duplicate URIs after concurrent upserts")
    }

    // CC-24: applyUpsert returns evictedUri when history is full (21st entry)
    @Test
    fun `upsert 21st entry returns evicted uri from applyUpsert`() {
        val entries = (1..20).map { i ->
            ru.kyamshanov.notepen.mainscreen.domain.model.RecentFile(
                id = "file$i",
                uri = "/tmp/file$i.pdf",
                displayName = "file$i",
                fileSize = null,
                openedAt = i.toLong(),
                availabilityStatus = AvailabilityStatus.AVAILABLE,
                thumbnailKey = null,
                fileMtime = null,
                lastPageIndex = 0,
            )
        }
        val newcomer = ru.kyamshanov.notepen.mainscreen.domain.model.RecentFile(
            id = "newcomer",
            uri = "/tmp/newcomer.pdf",
            displayName = "newcomer",
            fileSize = null,
            openedAt = 9999L,
            availabilityStatus = AvailabilityStatus.AVAILABLE,
            thumbnailKey = null,
            fileMtime = null,
            lastPageIndex = 0,
        )
        val (resultList, evictedUri) = ru.kyamshanov.notepen.mainscreen.domain.model.FileHistoryManager.applyUpsert(entries, newcomer)
        assertEquals(20, resultList.size, "List size must remain 20 after eviction")
        assertTrue(evictedUri != null, "evictedUri must not be null when list was full")
        assertEquals("/tmp/file1.pdf", evictedUri, "Oldest AVAILABLE file should be evicted")
    }
}
