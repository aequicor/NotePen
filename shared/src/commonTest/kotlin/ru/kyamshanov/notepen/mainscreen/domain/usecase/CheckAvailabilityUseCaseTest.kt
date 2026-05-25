package ru.kyamshanov.notepen.mainscreen.domain.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.domain.model.RecentFile
import ru.kyamshanov.notepen.mainscreen.domain.port.FileAvailabilityChecker
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [CheckAvailabilityUseCase].
 *
 * impl: shared/src/commonMain/.../usecase/CheckAvailabilityUseCase.kt
 */
class CheckAvailabilityUseCaseTest {
    private class FakeChecker(
        private val statusMap: Map<String, AvailabilityStatus> = emptyMap(),
        private val default: AvailabilityStatus = AvailabilityStatus.AVAILABLE,
    ) : FileAvailabilityChecker {
        override suspend fun check(uri: String): AvailabilityStatus = statusMap[uri] ?: default

        override fun checkSync(uri: String): AvailabilityStatus = statusMap[uri] ?: default
    }

    private class FakeRepo : FileHistoryRepository {
        val statusUpdates = mutableListOf<Pair<String, AvailabilityStatus>>()

        override suspend fun getAll(): List<RecentFile> = emptyList()

        override suspend fun upsert(
            file: RecentFile,
            lastPageIndex: Int,
        ) {}

        override suspend fun updateStatus(
            id: String,
            status: AvailabilityStatus,
        ) {
            statusUpdates.add(id to status)
        }

        override suspend fun updateLastPage(
            uri: String,
            pageIndex: Int,
        ) {}

        override suspend fun rollbackUpsert(uri: String) {}
    }

    private fun makeFile(
        id: String,
        uri: String,
    ) = RecentFile(
        id = id,
        uri = uri,
        displayName = "file.pdf",
        openedAt = 100L,
    )

    /**
     * [AC-9a] Emits AvailabilityUpdate for each file; statuses match checker result.
     * impl: CheckAvailabilityUseCase.execute
     */
    @Test
    fun emitsUpdateForEachFile() =
        runTestBlocking {
            val files =
                listOf(
                    makeFile("id1", "uri1"),
                    makeFile("id2", "uri2"),
                    makeFile("id3", "uri3"),
                )
            val checker =
                FakeChecker(
                    statusMap =
                        mapOf(
                            "uri1" to AvailabilityStatus.AVAILABLE,
                            "uri2" to AvailabilityStatus.NOT_FOUND,
                            "uri3" to AvailabilityStatus.FILE_ERROR,
                        ),
                )
            val repo = FakeRepo()
            val useCase = CheckAvailabilityUseCase(checker, repo)

            val updates = useCase.execute(files).toList()

            assertEquals(3, updates.size)
            val byId = updates.associateBy { it.id }
            assertEquals(AvailabilityStatus.AVAILABLE, byId["id1"]?.status)
            assertEquals(AvailabilityStatus.NOT_FOUND, byId["id2"]?.status)
            assertEquals(AvailabilityStatus.FILE_ERROR, byId["id3"]?.status)
        }

    /**
     * [AC-9a] Repository.updateStatus is called for each file with correct status.
     * impl: CheckAvailabilityUseCase.execute
     */
    @Test
    fun updatesRepositoryStatusForEachFile() =
        runTestBlocking {
            val files = listOf(makeFile("id1", "uri1"), makeFile("id2", "uri2"))
            val checker = FakeChecker(default = AvailabilityStatus.NOT_FOUND)
            val repo = FakeRepo()
            val useCase = CheckAvailabilityUseCase(checker, repo)

            useCase.execute(files).toList()

            assertEquals(2, repo.statusUpdates.size)
            val ids = repo.statusUpdates.map { it.first }.toSet()
            assertTrue(ids.contains("id1"))
            assertTrue(ids.contains("id2"))
        }

    /**
     * Empty file list → flow completes with no emissions.
     * impl: CheckAvailabilityUseCase.execute
     */
    @Test
    fun emptyList_emitsNothing() =
        runTestBlocking {
            val useCase = CheckAvailabilityUseCase(FakeChecker(), FakeRepo())
            val updates = useCase.execute(emptyList()).toList()
            assertTrue(updates.isEmpty())
        }

    /**
     * [AC-9a] Checker that hangs forever → timeout (2 s) yields FILE_ERROR.
     * impl: CheckAvailabilityUseCase.execute
     */
    @Test
    fun check_timeoutExceeded_emitsFILE_ERROR() =
        runTest {
            val hangingChecker =
                object : FileAvailabilityChecker {
                    override suspend fun check(uri: String): AvailabilityStatus {
                        delay(Long.MAX_VALUE)
                        return AvailabilityStatus.AVAILABLE
                    }

                    override fun checkSync(uri: String): AvailabilityStatus = AvailabilityStatus.AVAILABLE
                }
            val repo = FakeRepo()
            val useCase = CheckAvailabilityUseCase(hangingChecker, repo)
            val file = makeFile("t1", "file://test.pdf")

            val results = useCase.execute(listOf(file)).toList()

            assertEquals(1, results.size)
            assertEquals(AvailabilityStatus.FILE_ERROR, results[0].status)
        }

    /**
     * Semaphore(5) limits concurrency to at most 5 simultaneous checks.
     * impl: CheckAvailabilityUseCase.execute
     */
    @Test
    fun check_maxConcurrency_limitedTo5() =
        runTest {
            var maxConcurrent = 0
            var currentConcurrent = 0
            val mutex = Mutex()

            val countingChecker =
                object : FileAvailabilityChecker {
                    override suspend fun check(uri: String): AvailabilityStatus {
                        mutex.withLock {
                            currentConcurrent++
                            if (currentConcurrent > maxConcurrent) maxConcurrent = currentConcurrent
                        }
                        yield()
                        mutex.withLock { currentConcurrent-- }
                        return AvailabilityStatus.AVAILABLE
                    }

                    override fun checkSync(uri: String): AvailabilityStatus = AvailabilityStatus.AVAILABLE
                }
            val repo = FakeRepo()
            val useCase = CheckAvailabilityUseCase(countingChecker, repo)
            val files = (1..10).map { makeFile("$it", "file://test$it.pdf") }

            useCase.execute(files).toList()

            assertTrue(maxConcurrent <= 5, "Max concurrent was $maxConcurrent, expected ≤ 5")
        }
}
