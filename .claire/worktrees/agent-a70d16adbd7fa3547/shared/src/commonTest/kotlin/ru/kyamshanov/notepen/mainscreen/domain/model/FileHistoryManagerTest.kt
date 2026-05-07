package ru.kyamshanov.notepen.mainscreen.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileHistoryManagerTest {

    private fun makeFile(
        uri: String,
        status: AvailabilityStatus = AvailabilityStatus.AVAILABLE,
        openedAt: Long = System.currentTimeMillis(),
    ) = RecentFile(
        id = uri,
        uri = uri,
        displayName = uri,
        openedAt = openedAt,
        availabilityStatus = status,
    )

    // --- findEvictIndex ---

    /**
     * AC-9e: NOT_FOUND is evicted before AVAILABLE.
     */
    @Test
    fun findEvictIndex_notFoundEvictedBeforeAvailable() {
        val entries = listOf(
            makeFile("a", AvailabilityStatus.AVAILABLE, openedAt = 100),
            makeFile("b", AvailabilityStatus.NOT_FOUND, openedAt = 200),
            makeFile("c", AvailabilityStatus.AVAILABLE, openedAt = 50),
        )
        val idx = FileHistoryManager.findEvictIndex(entries)
        assertEquals(AvailabilityStatus.NOT_FOUND, entries[idx].availabilityStatus)
    }

    /**
     * CC-8 Critical: all UNKNOWN → pessimistic, evict oldest.
     */
    @Test
    fun findEvictIndex_allUnknown_evictsOldest() {
        val entries = listOf(
            makeFile("a", AvailabilityStatus.UNKNOWN, openedAt = 300),
            makeFile("b", AvailabilityStatus.UNKNOWN, openedAt = 100),
            makeFile("c", AvailabilityStatus.UNKNOWN, openedAt = 200),
        )
        val idx = FileHistoryManager.findEvictIndex(entries)
        assertEquals("b", entries[idx].uri)
    }

    /**
     * AC-5: all AVAILABLE → evict oldest by openedAt (standard LRU).
     */
    @Test
    fun findEvictIndex_allAvailable_evictsOldest() {
        val entries = listOf(
            makeFile("a", AvailabilityStatus.AVAILABLE, openedAt = 300),
            makeFile("b", AvailabilityStatus.AVAILABLE, openedAt = 100),
            makeFile("c", AvailabilityStatus.AVAILABLE, openedAt = 200),
        )
        val idx = FileHistoryManager.findEvictIndex(entries)
        assertEquals("b", entries[idx].uri)
    }

    // --- applyUpsert ---

    /**
     * AC-4: existing URI → moved to front, size unchanged.
     */
    @Test
    fun applyUpsert_existingUri_movedToFront_sizeUnchanged() {
        val now = 1000L
        val existing = listOf(
            makeFile("first", openedAt = 200),
            makeFile("second", openedAt = 100),
        )
        val newFile = makeFile("second", openedAt = now)
        val (result, evicted) = FileHistoryManager.applyUpsert(existing, newFile)
        assertEquals("second", result.first().uri)
        assertEquals(2, result.size)
        assertNull(evicted)
    }

    /**
     * AC-2: new URI, size < 20 → added without eviction.
     */
    @Test
    fun applyUpsert_newUri_sizeLessThan20_addedWithoutEviction() {
        val existing = (1..5).map { makeFile("file$it", openedAt = it.toLong()) }
        val newFile = makeFile("newfile", openedAt = 999)
        val (result, evicted) = FileHistoryManager.applyUpsert(existing, newFile)
        assertEquals(6, result.size)
        assertEquals("newfile", result.first().uri)
        assertNull(evicted)
    }

    /**
     * AC-9e: size == 20, has NOT_FOUND → NOT_FOUND evicted, not AVAILABLE.
     */
    @Test
    fun applyUpsert_full_notFoundPresent_evictsNotFound() {
        val entries = (1..19).map { makeFile("available$it", AvailabilityStatus.AVAILABLE, openedAt = it.toLong()) } +
            listOf(makeFile("bad", AvailabilityStatus.NOT_FOUND, openedAt = 5))
        assertEquals(20, entries.size)
        val newFile = makeFile("newcomer", openedAt = 9999)
        val (result, evicted) = FileHistoryManager.applyUpsert(entries, newFile)
        assertEquals(20, result.size)
        assertEquals("bad", evicted)
        assertTrue(result.none { it.uri == "bad" })
        assertEquals("newcomer", result.first().uri)
    }

    // --- UriNormalizer ---

    /**
     * TC-01: content:// URI returned as-is.
     */
    @Test
    fun uriNormalizer_contentScheme_returnedAsIs() {
        val uri = "content://com.example.provider/document/123"
        assertEquals(uri, UriNormalizer.normalize(uri))
    }

    /**
     * TC-01: desktop path with trailing slash → trimmed.
     */
    @Test
    fun uriNormalizer_trailingSlash_trimmed() {
        val uri = "/home/user/file.pdf/"
        val result = UriNormalizer.normalize(uri)
        assertEquals("/home/user/file.pdf", result)
    }
}
