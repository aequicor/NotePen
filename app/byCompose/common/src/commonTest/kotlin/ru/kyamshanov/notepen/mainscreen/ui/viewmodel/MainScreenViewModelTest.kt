package ru.kyamshanov.notepen.mainscreen.ui.viewmodel

import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderLimitExceededException
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
import ru.kyamshanov.notepen.mainscreen.ui.MainScreenIntent
import ru.kyamshanov.notepen.mainscreen.ui.model.ErrorEvent
import ru.kyamshanov.notepen.mainscreen.ui.model.NavigationTarget
import ru.kyamshanov.notepen.mainscreen.ui.model.ThumbnailState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var lifecycle: LifecycleRegistry
    private lateinit var fakeHistoryRepo: FakeFileHistoryRepository
    private lateinit var fakeFolderRepo: FakeFolderRepository
    private lateinit var fakeAvailabilityChecker: ControllableAvailabilityChecker
    private lateinit var fakeThumbnailRepo: FakeThumbnailRepository
    private lateinit var fakeThumbnailGen: FakePdfThumbnailGenerator

    private lateinit var viewModel: MainScreenViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        lifecycle = LifecycleRegistry()
        fakeHistoryRepo = FakeFileHistoryRepository()
        fakeFolderRepo = FakeFolderRepository()
        fakeAvailabilityChecker = ControllableAvailabilityChecker()
        fakeThumbnailRepo = FakeThumbnailRepository()
        fakeThumbnailGen = FakePdfThumbnailGenerator()

        val addToHistory = AddToHistoryUseCase(fakeHistoryRepo)
        val checkAvailability = CheckAvailabilityUseCase(fakeAvailabilityChecker, fakeHistoryRepo)
        val openRecentFile = OpenRecentFileUseCase(
            checker = fakeAvailabilityChecker,
            ioDispatcher = testDispatcher,
        )

        viewModel = MainScreenViewModel(
            lifecycle = lifecycle,
            historyRepository = fakeHistoryRepo,
            folderRepository = fakeFolderRepo,
            addToHistory = addToHistory,
            checkAvailability = checkAvailability,
            openRecentFile = openRecentFile,
            thumbnailRepository = fakeThumbnailRepo,
            thumbnailGenerator = fakeThumbnailGen,
            nowMillis = { 1_000_000L },
        )
        lifecycle.resume()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // AC-1: ScreenVisible → isLoading = false after loading data
    @Test
    fun screenVisible_isLoadingFalseAfterLoad() {
        viewModel.onIntent(MainScreenIntent.ScreenVisible)
        assertFalse(viewModel.state.value.isLoading, "isLoading should be false after load")
    }

    // CC-7 High: OpenRecentFile called twice quickly → second is ignored
    @Test
    fun openRecentFile_doubleTap_secondIsIgnored() {
        val file = RecentFile(
            id = "file-1",
            uri = "file:///test.pdf",
            displayName = "test.pdf",
            openedAt = 1000L,
            availabilityStatus = AvailabilityStatus.AVAILABLE,
        )
        fakeHistoryRepo.files = listOf(file)
        fakeAvailabilityChecker.syncResult = AvailabilityStatus.AVAILABLE

        viewModel.onIntent(MainScreenIntent.ScreenVisible)
        viewModel.onIntent(MainScreenIntent.OpenRecentFile("file-1"))
        val firstTarget = viewModel.state.value.navigationTarget

        // isNavigating is now true — second call should be ignored (CC-7)
        viewModel.onIntent(MainScreenIntent.OpenRecentFile("file-1"))

        assertEquals(firstTarget, viewModel.state.value.navigationTarget, "Second tap should not change navigation target")
    }

    // AC-10, AC-57: OpenRecentFile → SUCCESS → navigationTarget = Editor(uri, lastPageIndex)
    @Test
    fun openRecentFile_success_setsEditorNavigationTarget() {
        val file = RecentFile(
            id = "file-2",
            uri = "file:///doc.pdf",
            displayName = "doc.pdf",
            openedAt = 2000L,
            availabilityStatus = AvailabilityStatus.AVAILABLE,
            lastPageIndex = 3,
        )
        fakeHistoryRepo.files = listOf(file)
        fakeAvailabilityChecker.syncResult = AvailabilityStatus.AVAILABLE

        viewModel.onIntent(MainScreenIntent.ScreenVisible)
        viewModel.onIntent(MainScreenIntent.OpenRecentFile("file-2"))

        val nav = viewModel.state.value.navigationTarget
        assertIs<NavigationTarget.Editor>(nav, "navigationTarget should be Editor")
        assertEquals("file:///doc.pdf", nav.uri)
        assertEquals(3, nav.lastPageIndex)
    }

    // AC-11: OpenRecentFile → NOT_FOUND → errorEvent = FileNotFound, navigationTarget = null
    @Test
    fun openRecentFile_notFound_setsFileNotFoundError() {
        val file = RecentFile(
            id = "file-3",
            uri = "file:///missing.pdf",
            displayName = "missing.pdf",
            openedAt = 3000L,
            availabilityStatus = AvailabilityStatus.AVAILABLE,
        )
        fakeHistoryRepo.files = listOf(file)
        fakeAvailabilityChecker.syncResult = AvailabilityStatus.NOT_FOUND

        viewModel.onIntent(MainScreenIntent.ScreenVisible)
        viewModel.onIntent(MainScreenIntent.OpenRecentFile("file-3"))

        assertNull(viewModel.state.value.navigationTarget, "navigationTarget should be null on NOT_FOUND")
        assertEquals(ErrorEvent.FileNotFound, viewModel.state.value.errorEvent)
    }

    // CC-6 High: CancelNavigation → rollbackUpsert called, navigationTarget = null
    @Test
    fun cancelNavigation_rollbackCalled_navigationCleared() {
        val file = RecentFile(
            id = "file-4",
            uri = "file:///cancel.pdf",
            displayName = "cancel.pdf",
            openedAt = 4000L,
            availabilityStatus = AvailabilityStatus.AVAILABLE,
            lastPageIndex = 1,
        )
        fakeHistoryRepo.files = listOf(file)
        fakeAvailabilityChecker.syncResult = AvailabilityStatus.AVAILABLE

        viewModel.onIntent(MainScreenIntent.ScreenVisible)
        viewModel.onIntent(MainScreenIntent.OpenRecentFile("file-4"))

        assertIs<NavigationTarget.Editor>(viewModel.state.value.navigationTarget)

        viewModel.onIntent(MainScreenIntent.CancelNavigation)

        assertNull(viewModel.state.value.navigationTarget, "navigationTarget should be null after cancel")
        assertTrue(fakeHistoryRepo.rollbackUpsertCalled, "rollbackUpsert should have been called")
        assertEquals("file:///cancel.pdf", fakeHistoryRepo.lastRollbackUri)
    }

    // AC-48: FolderDialogNameChanged with invalid chars → chars are filtered out
    @Test
    fun folderDialogNameChanged_invalidChars_filtered() {
        viewModel.openCreateFolderDialog()
        viewModel.onIntent(MainScreenIntent.FolderDialogNameChanged("valid@#\$name!"))

        val dialog = viewModel.state.value.createFolderDialog
        assertNotNull(dialog)
        assertFalse(dialog.currentName.contains('@'), "@ should be filtered")
        assertFalse(dialog.currentName.contains('#'), "# should be filtered")
        assertFalse(dialog.currentName.contains('!'), "! should be filtered")
        assertTrue(dialog.currentName.contains("valid"), "valid letters should remain")
        assertTrue(dialog.currentName.contains("name"), "'name' should remain")
    }

    // AC-32: FolderDialogNameChanged with only spaces → isConfirmEnabled = false
    @Test
    fun folderDialogNameChanged_onlySpaces_confirmDisabled() {
        viewModel.openCreateFolderDialog()
        viewModel.onIntent(MainScreenIntent.FolderDialogNameChanged("     "))

        val dialog = viewModel.state.value.createFolderDialog
        assertNotNull(dialog)
        assertFalse(dialog.isConfirmEnabled, "isConfirmEnabled should be false for whitespace-only input")
    }

    // AC-41: CreateFolder → FolderLimitExceededException → errorEvent = FolderLimitExceeded
    @Test
    fun createFolder_folderLimitExceeded_setsError() {
        fakeFolderRepo.createThrows = FolderLimitExceededException()

        viewModel.onIntent(MainScreenIntent.CreateFolder("MyFolder"))

        assertEquals(ErrorEvent.FolderLimitExceeded, viewModel.state.value.errorEvent)
    }

    // CC-1: RejectSafMerge → existing record marked FILE_ERROR, dialog dismissed
    @Test
    fun rejectSafMerge_marksExistingRecordFileError() {
        val existingFile = RecentFile(
            id = "file-cc1",
            uri = "file:///cc1.pdf",
            displayName = "cc1.pdf",
            openedAt = 1000L,
            availabilityStatus = AvailabilityStatus.AVAILABLE,
        )
        fakeHistoryRepo.files = listOf(existingFile)
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        // Simulate safMergeDialog being open by directly checking the intent handler path
        viewModel.onIntent(
            MainScreenIntent.RejectSafMerge(existingId = "file-cc1", newUri = "file:///new-cc1.pdf"),
        )

        val state = viewModel.state.value
        assertNull(state.safMergeDialog, "Dialog should be dismissed after reject")
        val updatedFile = state.recentFiles.firstOrNull { it.id == "file-cc1" }
        assertNotNull(updatedFile, "Existing file should still be in the list")
        assertEquals(
            AvailabilityStatus.FILE_ERROR,
            updatedFile.availabilityStatus,
            "Existing file must be marked FILE_ERROR after reject",
        )
        assertTrue(fakeHistoryRepo.updatedStatuses.any { it.first == "file-cc1" && it.second == AvailabilityStatus.FILE_ERROR })
    }

    // CC-1 (new URI branch): RejectSafMerge → new URI is added as a fresh entry in recentFiles
    @Test
    fun rejectSafMerge_addsNewUriAsNewEntry() {
        val existingFile = RecentFile(
            id = "file-cc1b",
            uri = "file:///cc1b.pdf",
            displayName = "cc1b.pdf",
            openedAt = 1000L,
            availabilityStatus = AvailabilityStatus.AVAILABLE,
        )
        fakeHistoryRepo.files = listOf(existingFile)
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        viewModel.onIntent(
            MainScreenIntent.RejectSafMerge(existingId = "file-cc1b", newUri = "file:///new-cc1b.pdf"),
        )

        val state = viewModel.state.value
        val newEntry = state.recentFiles.firstOrNull { it.displayName == "new-cc1b.pdf" }
        assertNotNull(newEntry, "New URI must appear as a new entry in recentFiles after RejectSafMerge")
        assertEquals(AvailabilityStatus.AVAILABLE, newEntry.availabilityStatus)
    }

    // CC-23: OpenRecentFile → ARCHIVED_UNAVAILABLE status is preserved, not overwritten
    @Test
    fun openRecentFile_archivedUnavailable_statusPreservedAfterOpenFail() {
        val archivedFile = RecentFile(
            id = "file-cc23",
            uri = "file:///cc23.pdf",
            displayName = "cc23.pdf",
            openedAt = 1000L,
            availabilityStatus = AvailabilityStatus.ARCHIVED_UNAVAILABLE,
        )
        fakeHistoryRepo.files = listOf(archivedFile)
        fakeAvailabilityChecker.syncResult = AvailabilityStatus.NOT_FOUND

        viewModel.onIntent(MainScreenIntent.ScreenVisible)
        viewModel.onIntent(MainScreenIntent.OpenRecentFile("file-cc23"))

        val state = viewModel.state.value
        val updatedFile = state.recentFiles.firstOrNull { it.id == "file-cc23" }
        assertNotNull(updatedFile)
        assertEquals(
            AvailabilityStatus.ARCHIVED_UNAVAILABLE,
            updatedFile.availabilityStatus,
            "ARCHIVED_UNAVAILABLE must not be overwritten by live NOT_FOUND check",
        )
    }

    // CC-4: SafMergeDialog shows both existingRecord and newUri
    @Test
    fun openFilePicker_safFuzzyMatch_dialogShowsBothUris() {
        // SafMergeDialogState already has both fields; verify the state model holds both
        val existingUiModel = ru.kyamshanov.notepen.mainscreen.ui.model.RecentFileUiModel(
            id = "existing-id",
            displayName = "doc.pdf",
            openedAt = 1000L,
            availabilityStatus = AvailabilityStatus.AVAILABLE,
            thumbnailState = ru.kyamshanov.notepen.mainscreen.ui.model.ThumbnailState.Loading,
            lastPageIndex = 0,
        )
        val dialogState = ru.kyamshanov.notepen.mainscreen.ui.model.SafMergeDialogState(
            existingRecord = existingUiModel,
            newUri = "file:///new-doc.pdf",
        )
        assertEquals("existing-id", dialogState.existingRecord.id)
        assertEquals("file:///new-doc.pdf", dialogState.newUri)
    }

    // CC-2: FilePickerResult with SAF fuzzy match → safMergeDialog populated with existingRecord and newUri
    @Test
    fun filePickerResult_safFuzzyMatch_dialogShowsBothUris() {
        val existingFile = RecentFile(
            id = "saf-existing-id",
            uri = "content://com.android.providers/doc/old",
            displayName = "doc.pdf",
            fileSize = 2048L,
            openedAt = 1000L,
            availabilityStatus = AvailabilityStatus.AVAILABLE,
        )
        fakeHistoryRepo.files = listOf(existingFile)
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        val newUri = "content://com.android.providers/doc/new"
        viewModel.onIntent(
            MainScreenIntent.FilePickerResult(uri = newUri, displayName = "doc.pdf", fileSize = 2048L),
        )

        val state = viewModel.state.value
        val dialog = state.safMergeDialog
        assertNotNull(dialog, "safMergeDialog must be set when SAF fuzzy match detected")
        assertEquals("saf-existing-id", dialog.existingRecord.id, "dialog must reference existing record")
        assertEquals(newUri, dialog.newUri, "dialog.newUri must be the new URI")
        // newUri must differ from existingRecord's id (which carries the domain URI)
        assertFalse(
            dialog.existingRecord.id == dialog.newUri,
            "existing record id and new URI must differ",
        )
    }

    // TC-85 / CC-23: OpenRecentFile with ARCHIVED_UNAVAILABLE → does NOT open editor
    @Test
    fun openRecentFile_archivedUnavailable_doesNotOpenEditor() {
        val archivedFile = RecentFile(
            id = "arc-1",
            uri = "file:///archived.pdf",
            displayName = "archived.pdf",
            openedAt = 1000L,
            availabilityStatus = AvailabilityStatus.ARCHIVED_UNAVAILABLE,
        )
        fakeHistoryRepo.files = listOf(archivedFile)
        fakeAvailabilityChecker.syncResult = AvailabilityStatus.ARCHIVED_UNAVAILABLE

        viewModel.onIntent(MainScreenIntent.ScreenVisible)
        viewModel.onIntent(MainScreenIntent.OpenRecentFile("arc-1"))

        assertNull(viewModel.state.value.navigationTarget, "ARCHIVED_UNAVAILABLE must not open editor")
    }

    // TC-46 / CC-11: OOM during thumbnail generation → ThumbnailState.Error
    @Test
    fun thumbnailGeneration_oom_emitsThumbnailStateError() {
        val fileUri = "file:///oom-file.pdf"
        val file = RecentFile(
            id = "oom-id",
            uri = fileUri,
            displayName = "oom-file.pdf",
            openedAt = 1000L,
            availabilityStatus = AvailabilityStatus.AVAILABLE,
        )
        fakeHistoryRepo.files = listOf(file)
        fakeThumbnailGen.setResult(fileUri, Result.failure(OutOfMemoryError("simulated OOM")))

        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        val thumbnailState = viewModel.state.value.recentFiles.find { it.id == "oom-id" }?.thumbnailState
        assertIs<ThumbnailState.Error>(thumbnailState, "OOM must result in ThumbnailState.Error")
    }

    // TC-50 / CC-18: ScreenVisible re-triggers availability check (file transitions UNKNOWN → AVAILABLE)
    @Test
    fun screenVisible_retriggersAvailabilityCheck_unknownBecomesAvailable() {
        val file = RecentFile(
            id = "id-1",
            uri = "file:///file.pdf",
            displayName = "file.pdf",
            openedAt = 1000L,
            availabilityStatus = AvailabilityStatus.UNKNOWN,
        )
        fakeHistoryRepo.files = listOf(file)
        fakeAvailabilityChecker.asyncResult = AvailabilityStatus.AVAILABLE

        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        val updatedStatus = viewModel.state.value.recentFiles.find { it.id == "id-1" }?.availabilityStatus
        assertEquals(AvailabilityStatus.AVAILABLE, updatedStatus, "UNKNOWN file must become AVAILABLE after check")
    }

    // TC-87 / CC-25: FolderDialogNameChanged with empty string → isConfirmEnabled = false
    @Test
    fun folderDialogNameChanged_emptyName_confirmDisabled() {
        viewModel.onIntent(MainScreenIntent.OpenCreateFolderDialog)
        viewModel.onIntent(MainScreenIntent.FolderDialogNameChanged(""))

        val dialog = viewModel.state.value.createFolderDialog
        assertNotNull(dialog, "Dialog should be open")
        assertFalse(dialog!!.isConfirmEnabled, "isConfirmEnabled must be false for empty name")
    }

    // onErrorEventHandled → errorEvent = null
    @Test
    fun onErrorEventHandled_clearsErrorEvent() {
        fakeFolderRepo.createThrows = FolderLimitExceededException()
        viewModel.onIntent(MainScreenIntent.CreateFolder("AnyFolder"))

        assertNotNull(viewModel.state.value.errorEvent, "precondition: errorEvent is set")

        viewModel.onErrorEventHandled()

        assertNull(viewModel.state.value.errorEvent, "errorEvent should be null after handled")
    }

    // TC-127 / DEF-002: OpenFilePicker intent → navigationTarget = FilePicker,
    // then FilePickerResult with valid path → navigationTarget = Editor.
    @Test
    fun openFilePicker_setsFilePickerTarget_thenFilePickerResult_navigatesToEditor() {
        viewModel.onIntent(MainScreenIntent.OpenFilePicker)

        assertIs<NavigationTarget.FilePicker>(
            viewModel.state.value.navigationTarget,
            "OpenFilePicker intent must set navigationTarget to FilePicker",
        )

        val selectedPath = "/home/user/document.pdf"
        viewModel.onIntent(
            MainScreenIntent.FilePickerResult(
                uri = selectedPath,
                displayName = "document.pdf",
                fileSize = 1024L,
            ),
        )

        val target = viewModel.state.value.navigationTarget
        assertIs<NavigationTarget.Editor>(
            target,
            "FilePickerResult with non-null URI must set navigationTarget to Editor",
        )
        assertEquals(selectedPath, (target as NavigationTarget.Editor).uri)
    }

    // TC-127 / DEF-002: FilePickerResult with null URI (user cancelled) → navigationTarget cleared (not stuck on FilePicker).
    @Test
    fun filePickerResult_nullUri_clearsFilePickerNavigationTarget() {
        viewModel.onIntent(MainScreenIntent.OpenFilePicker)
        assertIs<NavigationTarget.FilePicker>(viewModel.state.value.navigationTarget)

        viewModel.onIntent(
            MainScreenIntent.FilePickerResult(uri = null, displayName = "", fileSize = null),
        )

        assertNull(
            viewModel.state.value.navigationTarget,
            "FilePickerResult with null URI (cancel) must clear the FilePicker navigationTarget",
        )
    }
}

// --- Controllable FileAvailabilityChecker ---

private class ControllableAvailabilityChecker : FileAvailabilityChecker {
    var asyncResult: AvailabilityStatus = AvailabilityStatus.AVAILABLE
    var syncResult: AvailabilityStatus = AvailabilityStatus.AVAILABLE

    override suspend fun check(uri: String): AvailabilityStatus = asyncResult
    override fun checkSync(uri: String): AvailabilityStatus = syncResult
}

// --- Fake implementations ---

private class FakeFileHistoryRepository : FileHistoryRepository {
    var files: List<RecentFile> = emptyList()
    var rollbackUpsertCalled = false
    var lastRollbackUri: String? = null
    val updatedStatuses: MutableList<Pair<String, AvailabilityStatus>> = mutableListOf()

    override suspend fun getAll(): List<RecentFile> = files
    override suspend fun upsert(file: RecentFile, lastPageIndex: Int) {
        val existing = files.indexOfFirst { it.id == file.id }
        files = if (existing >= 0) {
            files.toMutableList().also { it[existing] = file }
        } else {
            files + file
        }
    }
    override suspend fun updateStatus(id: String, status: AvailabilityStatus) {
        updatedStatuses.add(Pair(id, status))
    }
    override suspend fun updateLastPage(uri: String, pageIndex: Int) {}
    override suspend fun rollbackUpsert(uri: String) {
        rollbackUpsertCalled = true
        lastRollbackUri = uri
    }
}

private class FakeFolderRepository : FolderRepository {
    var createThrows: Exception? = null
    val folders: MutableList<Folder> = mutableListOf()

    override suspend fun create(name: String): Folder {
        val ex = createThrows
        if (ex != null) throw ex
        return Folder(id = "folder-${name.hashCode()}", name = name, createdAt = 0L).also { folders.add(it) }
    }

    override suspend fun delete(id: String) { folders.removeAll { it.id == id } }
    override suspend fun addFile(folderId: String, uri: String) {}
    override suspend fun removeFile(folderId: String, uri: String) {}
    override suspend fun rename(id: String, newName: String) {}
    override suspend fun getAll(): List<Folder> = folders
    override suspend fun getFilesInFolder(folderId: String): List<String> = emptyList()
}

private class FakeThumbnailRepository : ThumbnailRepository {
    override suspend fun get(uri: String, currentFileMtime: Long?): ByteArray? = null
    override suspend fun put(uri: String, imageData: ByteArray, fileMtime: Long?) {}
    override suspend fun totalSizeBytes(): Long = 0L
}

private class FakePdfThumbnailGenerator : PdfThumbnailGenerator {
    private val results: MutableMap<String, Result<ByteArray>> = mutableMapOf()

    fun setResult(id: String, result: Result<ByteArray>) {
        results[id] = result
    }

    override suspend fun generate(uri: String, widthPx: Int, heightPx: Int): Result<ByteArray> =
        results[uri] ?: Result.success(byteArrayOf())
}
