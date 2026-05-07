package ru.kyamshanov.notepen.mainscreen.domain.usecase

import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.domain.port.FileAvailabilityChecker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Unit tests for [OpenRecentFileUseCase].
 *
 * impl: shared/src/commonMain/.../usecase/OpenRecentFileUseCase.kt
 */
class OpenRecentFileUseCaseTest {

    private class FakeFileAvailabilityChecker(
        private val status: AvailabilityStatus,
    ) : FileAvailabilityChecker {
        override suspend fun check(uri: String): AvailabilityStatus = status
        override fun checkSync(uri: String): AvailabilityStatus = status
    }

    /**
     * [CC-19 High] AVAILABLE status → OpenFileResult.Success with correct URI.
     * impl: OpenRecentFileUseCase.execute
     */
    @Test
    fun available_returnsSuccess() = runTestBlocking {
        val useCase = OpenRecentFileUseCase(FakeFileAvailabilityChecker(AvailabilityStatus.AVAILABLE))
        val result = useCase.execute("/home/user/file.pdf")
        val success = assertIs<OpenFileResult.Success>(result)
        assertEquals("/home/user/file.pdf", success.uri)
    }

    /**
     * [CC-19] NOT_FOUND status → OpenFileResult.NotAvailable(NOT_FOUND).
     * impl: OpenRecentFileUseCase.execute
     */
    @Test
    fun notFound_returnsNotAvailable() = runTestBlocking {
        val useCase = OpenRecentFileUseCase(FakeFileAvailabilityChecker(AvailabilityStatus.NOT_FOUND))
        val result = useCase.execute("/home/user/file.pdf")
        val notAvailable = assertIs<OpenFileResult.NotAvailable>(result)
        assertEquals(AvailabilityStatus.NOT_FOUND, notAvailable.status)
    }

    /**
     * FILE_ERROR status → OpenFileResult.NotAvailable(FILE_ERROR).
     * impl: OpenRecentFileUseCase.execute
     */
    @Test
    fun fileError_returnsNotAvailable() = runTestBlocking {
        val useCase = OpenRecentFileUseCase(FakeFileAvailabilityChecker(AvailabilityStatus.FILE_ERROR))
        val result = useCase.execute("content://provider/doc/1")
        val notAvailable = assertIs<OpenFileResult.NotAvailable>(result)
        assertEquals(AvailabilityStatus.FILE_ERROR, notAvailable.status)
    }

    /**
     * [CC-12 High] checkSync throws SecurityException → OpenFileResult.NotAvailable(FILE_ERROR).
     * SAF permission revoked at the moment of opening.
     * impl: OpenRecentFileUseCase.execute
     */
    @Test
    fun checkSync_securityException_returnsFileError() = runTestBlocking {
        val throwingChecker = object : FileAvailabilityChecker {
            override suspend fun check(uri: String): AvailabilityStatus = AvailabilityStatus.AVAILABLE
            override fun checkSync(uri: String): AvailabilityStatus =
                throw SecurityException("SAF permission revoked")
        }
        val useCase = OpenRecentFileUseCase(throwingChecker)
        val result = useCase.execute("content://some.provider/doc.pdf")
        val notAvailable = assertIs<OpenFileResult.NotAvailable>(result)
        assertEquals(
            AvailabilityStatus.FILE_ERROR,
            notAvailable.status,
            "SecurityException must map to FILE_ERROR",
        )
    }
}
