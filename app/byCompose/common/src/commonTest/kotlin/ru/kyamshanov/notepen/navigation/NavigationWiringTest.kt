package ru.kyamshanov.notepen.navigation

import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.kyamshanov.notepen.mainscreen.ui.MainScreenIntent
import ru.kyamshanov.notepen.mainscreen.ui.model.NavigationTarget
import ru.kyamshanov.notepen.mainscreen.ui.viewmodel.MainScreenViewModel
import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.domain.model.Folder
import ru.kyamshanov.notepen.mainscreen.domain.model.RecentFile
import ru.kyamshanov.notepen.mainscreen.domain.port.FileAvailabilityChecker
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.PdfThumbnailGenerator
import ru.kyamshanov.notepen.mainscreen.domain.port.ThumbnailRepository
import ru.kyamshanov.notepen.mainscreen.domain.usecase.AddToHistoryUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.CheckAvailabilityUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.OpenRecentFileUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Stage 07: Smoke-тест навигации.
 *
 * ACT-NOW R2: OpenRecentFile → navigationTarget = Editor → onNavigationHandled() → null
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationWiringTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var lifecycle: LifecycleRegistry
    private lateinit var viewModel: MainScreenViewModel

    private val testUri = "file:///test/document.pdf"
    private val testFileId = "test-id-001"
    private val testFile = RecentFile(
        id = testFileId,
        uri = testUri,
        displayName = "document.pdf",
        openedAt = 1_000_000L,
        fileSize = 1024L,
        fileMtime = 2_000_000L,
        availabilityStatus = AvailabilityStatus.AVAILABLE,
        lastPageIndex = 3,
    )

    private val fakeHistory = object : FileHistoryRepository {
        override suspend fun getAll(): List<RecentFile> = listOf(testFile)
        override suspend fun upsert(file: RecentFile, lastPageIndex: Int) {}
        override suspend fun rollbackUpsert(uri: String) {}
        override suspend fun updateLastPage(uri: String, pageIndex: Int) {}
        override suspend fun updateStatus(id: String, status: AvailabilityStatus) {}
    }

    private val fakeFolder = object : FolderRepository {
        override suspend fun getAll() = emptyList<Folder>()
        override suspend fun create(name: String): Folder = throw UnsupportedOperationException()
        override suspend fun delete(id: String) {}
        override suspend fun rename(id: String, newName: String) {}
        override suspend fun addFile(folderId: String, uri: String) {}
        override suspend fun removeFile(folderId: String, uri: String) {}
        override suspend fun getFilesInFolder(folderId: String) = emptyList<String>()
    }

    private val fakeAvailability = object : FileAvailabilityChecker {
        override suspend fun check(uri: String): AvailabilityStatus = AvailabilityStatus.AVAILABLE
        override fun checkSync(uri: String): AvailabilityStatus = AvailabilityStatus.AVAILABLE
    }

    private val fakeThumbnailRepo = object : ThumbnailRepository {
        override suspend fun get(uri: String, currentFileMtime: Long?): ByteArray? = null
        override suspend fun put(uri: String, imageData: ByteArray, fileMtime: Long?) {}
        override suspend fun totalSizeBytes(): Long = 0L
    }

    private val fakeThumbnailGen = object : PdfThumbnailGenerator {
        override suspend fun generate(uri: String, widthPx: Int, heightPx: Int): Result<ByteArray> =
            Result.failure(UnsupportedOperationException())
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        lifecycle = LifecycleRegistry()
        viewModel = MainScreenViewModel(
            lifecycle = lifecycle,
            historyRepository = fakeHistory,
            folderRepository = fakeFolder,
            addToHistory = AddToHistoryUseCase(fakeHistory),
            checkAvailability = CheckAvailabilityUseCase(fakeAvailability, fakeHistory),
            openRecentFile = OpenRecentFileUseCase(
                checker = fakeAvailability,
                ioDispatcher = testDispatcher,
            ),
            thumbnailRepository = fakeThumbnailRepo,
            thumbnailGenerator = fakeThumbnailGen,
            nowMillis = { 3_000_000L },
        )
        lifecycle.resume()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * ACT-NOW R2: OpenRecentFile intent → navigationTarget = Editor(uri, lastPage)
     * (impl: MainScreenViewModel.openRecentFileById)
     */
    @Test
    fun openRecentFile_setsNavigationTargetEditor() {
        viewModel.onIntent(MainScreenIntent.ScreenVisible)
        viewModel.onIntent(MainScreenIntent.OpenRecentFile(testFileId))

        val state = viewModel.state.value
        val target = state.navigationTarget
        assertIs<NavigationTarget.Editor>(target)
        assertEquals(testUri, target.uri)
        assertEquals(3, target.lastPageIndex)
    }

    /**
     * ACT-NOW R2: after onNavigationHandled(), navigationTarget becomes null
     * (impl: MainScreenViewModel.onNavigationHandled)
     */
    @Test
    fun onNavigationHandled_clearsNavigationTarget() {
        viewModel.onIntent(MainScreenIntent.ScreenVisible)
        viewModel.onIntent(MainScreenIntent.OpenRecentFile(testFileId))

        viewModel.onNavigationHandled()

        assertNull(viewModel.state.value.navigationTarget)
    }
}
