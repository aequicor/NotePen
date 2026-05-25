package ru.kyamshanov.notepen.mainscreen.domain.model

import ru.kyamshanov.notepen.mainscreen.domain.exception.FileDuplicateInFolderException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FileNotInHistoryException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderLimitExceededException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderNameCharsInvalidException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderNameInvalidException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderNameTooLongException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderNotFoundException
import ru.kyamshanov.notepen.mainscreen.domain.exception.HistoryFlushException
import ru.kyamshanov.notepen.mainscreen.domain.exception.ThumbnailGenerationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileHistoryManagerTest {
    private fun makeFile(
        uri: String,
        status: AvailabilityStatus = AvailabilityStatus.AVAILABLE,
        openedAt: Long = 0L,
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
        val entries =
            listOf(
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
        val entries =
            listOf(
                makeFile("a", AvailabilityStatus.UNKNOWN, openedAt = 300),
                makeFile("b", AvailabilityStatus.UNKNOWN, openedAt = 100),
                makeFile("c", AvailabilityStatus.UNKNOWN, openedAt = 200),
            )
        val idx = FileHistoryManager.findEvictIndex(entries)
        assertEquals("b", entries[idx].uri)
    }

    /**
     * TC-40 / CC-8 Critical: 20 UNKNOWN entries → pessimistic eviction picks the oldest by openedAt.
     */
    @Test
    fun findEvictIndex_allUnknown_twentyEntries_evictsOldestByOpenedAt() {
        val list =
            (1..20).map { i ->
                RecentFile(
                    id = "id-$i",
                    uri = "/file$i.pdf",
                    displayName = "file$i.pdf",
                    openedAt = i.toLong(),
                    availabilityStatus = AvailabilityStatus.UNKNOWN,
                )
            }
        val idx = FileHistoryManager.findEvictIndex(list)
        // Oldest entry has openedAt=1, which is index 0
        assertEquals(0, idx, "Oldest UNKNOWN entry (openedAt=1) must be evicted")
    }

    /**
     * AC-5: all AVAILABLE → evict oldest by openedAt (standard LRU).
     */
    @Test
    fun findEvictIndex_allAvailable_evictsOldest() {
        val entries =
            listOf(
                makeFile("a", AvailabilityStatus.AVAILABLE, openedAt = 300),
                makeFile("b", AvailabilityStatus.AVAILABLE, openedAt = 100),
                makeFile("c", AvailabilityStatus.AVAILABLE, openedAt = 200),
            )
        val idx = FileHistoryManager.findEvictIndex(entries)
        assertEquals("b", entries[idx].uri)
    }

    /**
     * NOT_FOUND with the highest openedAt should still be evicted over older AVAILABLE entries.
     * Priority is by status (NOT_FOUND first), then by openedAt within the same status group.
     */
    @Test
    fun findEvictIndex_notFoundNewest_stillEvictedOverOlderAvailable() {
        val entries =
            listOf(
                makeFile("old_available", AvailabilityStatus.AVAILABLE, openedAt = 1),
                makeFile("not_found_newest", AvailabilityStatus.NOT_FOUND, openedAt = 9999),
                makeFile("recent_available", AvailabilityStatus.AVAILABLE, openedAt = 500),
            )
        val idx = FileHistoryManager.findEvictIndex(entries)
        assertEquals("not_found_newest", entries[idx].uri)
    }

    // --- applyUpsert ---

    /**
     * AC-4: existing URI → moved to front, size unchanged.
     */
    @Test
    fun applyUpsert_existingUri_movedToFront_sizeUnchanged() {
        val existing =
            listOf(
                makeFile("first", openedAt = 200),
                makeFile("second", openedAt = 100),
            )
        val newFile = makeFile("second", openedAt = 999)
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
        val available = (1..19).map { makeFile("available$it", AvailabilityStatus.AVAILABLE, openedAt = it.toLong()) }
        val bad = makeFile("bad", AvailabilityStatus.NOT_FOUND, openedAt = 5)
        val entries = available + listOf(bad)
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

    // --- Exception construction ---

    /**
     * Verify all exception classes construct without throwing.
     */
    @Test
    fun exceptions_constructWithoutException() {
        HistoryFlushException("flush error")
        HistoryFlushException("flush error", RuntimeException("cause"))
        FolderLimitExceededException()
        FolderNameInvalidException("bad name")
        FolderNameTooLongException(300)
        FolderNameCharsInvalidException("bad@chars")
        FolderNotFoundException("folder-id-123")
        FileNotInHistoryException("file:///path/file.pdf")
        FileDuplicateInFolderException("folder-id", "file:///path/file.pdf")
        ThumbnailGenerationException("gen error")
        ThumbnailGenerationException("gen error", RuntimeException("cause"))
    }

    // --- RecentFile domain model sanity ---

    /**
     * Basic sanity test for RecentFile domain model: equality and copy.
     */
    @Test
    fun recentFile_dataClass_equalityAndCopy() {
        val file =
            RecentFile(
                id = "id-1",
                uri = "/docs/file.pdf",
                displayName = "file.pdf",
                fileSize = 1024L,
                openedAt = 1000L,
                availabilityStatus = AvailabilityStatus.AVAILABLE,
                thumbnailKey = "thumb-key",
                fileMtime = 900L,
                lastPageIndex = 3,
            )
        val copy = file.copy(lastPageIndex = 5)

        assertEquals(file, file.copy())
        assertEquals(5, copy.lastPageIndex)
        assertEquals(file.id, copy.id)
        assertEquals(file.uri, copy.uri)
        assertTrue(file != copy)
    }
}
