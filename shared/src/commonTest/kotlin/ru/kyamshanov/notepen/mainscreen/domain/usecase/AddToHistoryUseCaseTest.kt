package ru.kyamshanov.notepen.mainscreen.domain.usecase

import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.domain.model.RecentFile
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [AddToHistoryUseCase].
 *
 * impl: shared/src/commonMain/.../usecase/AddToHistoryUseCase.kt
 */
class AddToHistoryUseCaseTest {

    // region Fake

    private class FakeFileHistoryRepository(
        private val initialFiles: List<RecentFile> = emptyList(),
    ) : FileHistoryRepository {
        val upsertCalls = mutableListOf<Pair<RecentFile, Int>>()

        override suspend fun getAll(): List<RecentFile> = initialFiles

        override suspend fun upsert(file: RecentFile, lastPageIndex: Int) {
            upsertCalls.add(file to lastPageIndex)
        }

        override suspend fun updateStatus(id: String, status: AvailabilityStatus) {}
        override suspend fun updateLastPage(uri: String, pageIndex: Int) {}
        override suspend fun rollbackUpsert(uri: String) {}
    }

    // endregion

    private fun makeFile(
        id: String = "id",
        uri: String,
        displayName: String = "file.pdf",
        fileSize: Long? = 1024L,
        openedAt: Long = 100L,
        status: AvailabilityStatus = AvailabilityStatus.AVAILABLE,
    ) = RecentFile(
        id = id,
        uri = uri,
        displayName = displayName,
        fileSize = fileSize,
        openedAt = openedAt,
        availabilityStatus = status,
    )

    // -----------------------------------------------------------------------
    // CC-1 Critical, CC-2 Critical: SAF fuzzy match detected
    // -----------------------------------------------------------------------

    /**
     * [CC-1 Critical] SAF fuzzy match: content:// URI, same displayName AND fileSize,
     * different URI → SafFuzzyMatchDetected returned, upsert NOT called.
     * impl: AddToHistoryUseCase.execute
     */
    @Test
    fun safFuzzyMatch_sameName_sameSize_differentUri_returnsDetected() = runTestBlocking {
        val existing = makeFile(
            id = "existing-id",
            uri = "content://provider/document/100",
            displayName = "report.pdf",
            fileSize = 2048L,
        )
        val repo = FakeFileHistoryRepository(listOf(existing))
        val useCase = AddToHistoryUseCase(repo)

        val result = useCase.execute(
            uri = "content://provider/document/999",
            displayName = "report.pdf",
            fileSize = 2048L,
            openedAt = 200L,
        ).getOrThrow()

        assertIs<AddHistoryResult.SafFuzzyMatchDetected>(result)
        assertEquals(existing, (result as AddHistoryResult.SafFuzzyMatchDetected).existing)
        assertEquals("content://provider/document/999", result.newUri)
        assertTrue(repo.upsertCalls.isEmpty(), "upsert must NOT be called on fuzzy match")
    }

    // -----------------------------------------------------------------------
    // AC-5b: fileSize = null → no fuzzy match (size required for match)
    // -----------------------------------------------------------------------

    /**
     * [AC-5b] content:// URI with same displayName but fileSize=null → NOT a fuzzy match.
     * impl: AddToHistoryUseCase.execute
     */
    @Test
    fun safFuzzyMatch_nullFileSize_noFuzzyMatch() = runTestBlocking {
        val existing = makeFile(
            id = "existing-id",
            uri = "content://provider/document/100",
            displayName = "report.pdf",
            fileSize = null,
        )
        val repo = FakeFileHistoryRepository(listOf(existing))
        val useCase = AddToHistoryUseCase(repo)

        val result = useCase.execute(
            uri = "content://provider/document/999",
            displayName = "report.pdf",
            fileSize = null,
            openedAt = 200L,
        ).getOrThrow()

        // Must NOT be fuzzy match — should be Added
        assertIs<AddHistoryResult.Added>(result)
    }

    /**
     * [AC-5b] Incoming fileSize = null → no fuzzy match even if existing has a size.
     * impl: AddToHistoryUseCase.execute
     */
    @Test
    fun safFuzzyMatch_incomingNullSize_existingHasSize_noFuzzyMatch() = runTestBlocking {
        val existing = makeFile(
            uri = "content://provider/document/100",
            displayName = "report.pdf",
            fileSize = 2048L,
        )
        val repo = FakeFileHistoryRepository(listOf(existing))
        val useCase = AddToHistoryUseCase(repo)

        val result = useCase.execute(
            uri = "content://provider/document/999",
            displayName = "report.pdf",
            fileSize = null,
            openedAt = 200L,
        ).getOrThrow()

        assertIs<AddHistoryResult.Added>(result)
    }

    // -----------------------------------------------------------------------
    // SAF: different displayName → no fuzzy match
    // -----------------------------------------------------------------------

    /**
     * SAF: different displayName → no fuzzy match.
     * impl: AddToHistoryUseCase.execute
     */
    @Test
    fun safFuzzyMatch_differentDisplayName_noFuzzyMatch() = runTestBlocking {
        val existing = makeFile(
            uri = "content://provider/document/100",
            displayName = "report.pdf",
            fileSize = 2048L,
        )
        val repo = FakeFileHistoryRepository(listOf(existing))
        val useCase = AddToHistoryUseCase(repo)

        val result = useCase.execute(
            uri = "content://provider/document/999",
            displayName = "other.pdf",
            fileSize = 2048L,
            openedAt = 200L,
        ).getOrThrow()

        assertIs<AddHistoryResult.Added>(result)
    }

    // -----------------------------------------------------------------------
    // AC-4: existing URI → Moved result
    // -----------------------------------------------------------------------

    /**
     * [AC-4] Existing same normalized URI → Moved result, upsert called with lastPageIndex.
     * impl: AddToHistoryUseCase.execute
     */
    @Test
    fun existingUri_returnsMoved_upsertCalledWithLastPageIndex() = runTestBlocking {
        val uri = "content://provider/document/100"
        val existing = makeFile(id = "existing-id", uri = uri, displayName = "file.pdf", fileSize = 512L)
        val repo = FakeFileHistoryRepository(listOf(existing))
        val useCase = AddToHistoryUseCase(repo)

        val result = useCase.execute(
            uri = uri,
            displayName = "file.pdf",
            fileSize = 512L,
            openedAt = 999L,
            lastPageIndex = 5,
        ).getOrThrow()

        val moved = assertIs<AddHistoryResult.Moved>(result)
        assertEquals("existing-id", moved.record.id)
        assertEquals(1, repo.upsertCalls.size)
        assertEquals(5, repo.upsertCalls[0].second)
    }

    // -----------------------------------------------------------------------
    // AC-2: new URI, empty history → Added
    // -----------------------------------------------------------------------

    /**
     * [AC-2] New URI in empty history → Added result, upsert called.
     * impl: AddToHistoryUseCase.execute
     */
    @Test
    fun newUri_emptyHistory_returnsAdded() = runTestBlocking {
        val repo = FakeFileHistoryRepository(emptyList())
        val useCase = AddToHistoryUseCase(repo)

        val result = useCase.execute(
            uri = "/home/user/file.pdf",
            displayName = "file.pdf",
            fileSize = 1024L,
            openedAt = 100L,
        ).getOrThrow()

        assertIs<AddHistoryResult.Added>(result)
        assertEquals(1, repo.upsertCalls.size)
    }

    // -----------------------------------------------------------------------
    // Non-SAF path: content:// matching same displayName + null size → no fuzzy match
    // -----------------------------------------------------------------------

    /**
     * content:// with same displayName + null size on BOTH sides → no fuzzy match.
     * impl: AddToHistoryUseCase.execute
     */
    @Test
    fun safFuzzyMatch_bothNullSize_noFuzzyMatch() = runTestBlocking {
        val existing = makeFile(
            uri = "content://provider/document/100",
            displayName = "report.pdf",
            fileSize = null,
        )
        val repo = FakeFileHistoryRepository(listOf(existing))
        val useCase = AddToHistoryUseCase(repo)

        val result = useCase.execute(
            uri = "content://provider/document/999",
            displayName = "report.pdf",
            fileSize = null,
            openedAt = 200L,
        ).getOrThrow()

        assertIs<AddHistoryResult.Added>(result)
    }
}
