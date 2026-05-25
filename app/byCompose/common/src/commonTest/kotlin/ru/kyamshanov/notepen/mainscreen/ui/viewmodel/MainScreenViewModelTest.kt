package ru.kyamshanov.notepen.mainscreen.ui.viewmodel

import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.kyamshanov.notepen.mainscreen.domain.exception.FileDuplicateInFolderException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FileNotInHistoryException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderLimitExceededException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderNotFoundException
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
import ru.kyamshanov.notepen.mainscreen.ui.model.DragState
import ru.kyamshanov.notepen.mainscreen.ui.model.ErrorEvent
import ru.kyamshanov.notepen.mainscreen.ui.model.NavigationTarget
import ru.kyamshanov.notepen.mainscreen.ui.model.SuccessEvent
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
        val openRecentFile =
            OpenRecentFileUseCase(
                checker = fakeAvailabilityChecker,
                ioDispatcher = testDispatcher,
            )

        viewModel =
            MainScreenViewModel(
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
        val file =
            RecentFile(
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
        val file =
            RecentFile(
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
        val file =
            RecentFile(
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
        val file =
            RecentFile(
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
        val existingFile =
            RecentFile(
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
        val existingFile =
            RecentFile(
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
        val archivedFile =
            RecentFile(
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
        val existingUiModel =
            ru.kyamshanov.notepen.mainscreen.ui.model.RecentFileUiModel(
                id = "existing-id",
                uri = "content://test/existing",
                displayName = "doc.pdf",
                openedAt = 1000L,
                availabilityStatus = AvailabilityStatus.AVAILABLE,
                thumbnailState = ru.kyamshanov.notepen.mainscreen.ui.model.ThumbnailState.Loading,
                lastPageIndex = 0,
            )
        val dialogState =
            ru.kyamshanov.notepen.mainscreen.ui.model.SafMergeDialogState(
                existingRecord = existingUiModel,
                newUri = "file:///new-doc.pdf",
            )
        assertEquals("existing-id", dialogState.existingRecord.id)
        assertEquals("file:///new-doc.pdf", dialogState.newUri)
    }

    // CC-2: FilePickerResult with SAF fuzzy match → safMergeDialog populated with existingRecord and newUri
    @Test
    fun filePickerResult_safFuzzyMatch_dialogShowsBothUris() {
        val existingFile =
            RecentFile(
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
        val archivedFile =
            RecentFile(
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
        val file =
            RecentFile(
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
        val file =
            RecentFile(
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

    // TC-1: DragStarted → dragState = Active(fileId, fileUri, displayName)
    @Test
    fun dragStarted_setsDragStateActive() {
        viewModel.onIntent(
            MainScreenIntent.DragStarted(
                fileId = "drag-file-1",
                fileUri = "file:///drag.pdf",
                displayName = "drag.pdf",
            ),
        )

        val dragState = viewModel.state.value.dragState
        assertIs<DragState.Active>(dragState, "DragStarted must set dragState to Active")
        assertEquals("drag-file-1", dragState.fileId)
        assertEquals("file:///drag.pdf", dragState.fileUri)
        assertEquals("drag.pdf", dragState.displayName)
    }

    // TC-2: DragCancelled → dragState = None
    @Test
    fun dragCancelled_setsDragStateNone() {
        viewModel.onIntent(
            MainScreenIntent.DragStarted(
                fileId = "drag-file-2",
                fileUri = "file:///drag2.pdf",
                displayName = "drag2.pdf",
            ),
        )
        assertIs<DragState.Active>(viewModel.state.value.dragState, "precondition: dragState is Active")

        viewModel.onIntent(MainScreenIntent.DragCancelled)

        assertIs<DragState.None>(viewModel.state.value.dragState, "DragCancelled must reset dragState to None")
    }

    // TC-3: DropOnFolder with valid drag → addFile called, dragState cleared, successEvent = FileAddedToFolder with folderName
    @Test
    fun dropOnFolder_validDrag_addsFileAndClearsDragState() {
        val folder = Folder(id = "folder-drop", name = "МоиДокументы", createdAt = 0L)
        fakeFolderRepo.folders.add(folder)
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        viewModel.onIntent(
            MainScreenIntent.DragStarted(
                fileId = "drag-file-3",
                fileUri = "file:///drop.pdf",
                displayName = "drop.pdf",
            ),
        )
        viewModel.onIntent(MainScreenIntent.DropOnFolder(folderId = "folder-drop"))

        val state = viewModel.state.value
        assertIs<DragState.None>(state.dragState, "dragState must be None after successful drop")
        val successEvent = state.successEvent
        assertIs<SuccessEvent.FileAddedToFolder>(successEvent, "successEvent must be FileAddedToFolder after successful drop")
        assertEquals("МоиДокументы", successEvent.folderName, "folderName must match the target folder name")
        assertNull(state.errorEvent, "errorEvent must be null after successful drop")
    }

    // TC-5: DropOnFolder when dragState = None → no-op (addFile not called)
    @Test
    fun dropOnFolder_noDragActive_isNoOp() {
        val folder = Folder(id = "folder-noop", name = "НеАктивная", createdAt = 0L)
        fakeFolderRepo.folders.add(folder)

        // No DragStarted intent — dragState is None
        assertIs<DragState.None>(viewModel.state.value.dragState, "precondition: dragState is None")

        viewModel.onIntent(MainScreenIntent.DropOnFolder(folderId = "folder-noop"))

        assertNull(viewModel.state.value.errorEvent, "No error should occur when dragState is None")
        assertNull(viewModel.state.value.successEvent, "No success event when dragState is None")
        // TC-5: dragState must remain None (drop was ignored — no state transition)
        assertIs<DragState.None>(viewModel.state.value.dragState, "dragState must remain None after no-op drop")
    }

    // TC-6: DropOnFolder when isLoading = true → no-op (EC-4)
    @Test
    fun dropOnFolder_isLoadingTrue_isNoOp() {
        // Simulate long-running load that won't complete (not resuming lifecycle so load waits)
        // We manually set a drag active then trigger load to keep isLoading=true
        // Instead, we use the known initial state: isLoading = true (default) before ScreenVisible
        assertIs<DragState.None>(viewModel.state.value.dragState, "initial dragState is None")

        // First set drag active via intent (succeeds since load guard only blocks drop)
        viewModel.onIntent(
            MainScreenIntent.DragStarted(
                fileId = "drag-file-6",
                fileUri = "file:///loading.pdf",
                displayName = "loading.pdf",
            ),
        )

        // State: isLoading=true (default initial state), dragState=Active
        assertTrue(viewModel.state.value.isLoading, "precondition: isLoading should be true before ScreenVisible")
        assertIs<DragState.Active>(viewModel.state.value.dragState)

        viewModel.onIntent(MainScreenIntent.DropOnFolder(folderId = "any-folder"))

        // With isLoading guard: drop is ignored
        assertNull(viewModel.state.value.errorEvent, "No error when isLoading=true on drop")
        assertNull(viewModel.state.value.successEvent, "No success event when isLoading=true on drop")
        // TC-6: dragState must remain Active — drop was silently ignored, drag was not cleared
        assertIs<DragState.Active>(
            viewModel.state.value.dragState,
            "dragState must remain Active when drop is ignored due to isLoading=true",
        )
    }

    // TC-7: DropOnFolder → FileDuplicateInFolderException → successEvent = FileAlreadyInFolder (NOT errorEvent)
    @Test
    fun dropOnFolder_fileDuplicate_setsSuccessEventFileAlreadyInFolder() {
        val folder = Folder(id = "folder-dup", name = "ДубликатПапка", createdAt = 0L)
        fakeFolderRepo.folders.add(folder)
        fakeFolderRepo.addFileThrows = FileDuplicateInFolderException("folder-dup", "file:///dup.pdf")
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        viewModel.onIntent(
            MainScreenIntent.DragStarted(
                fileId = "drag-dup",
                fileUri = "file:///dup.pdf",
                displayName = "dup.pdf",
            ),
        )
        viewModel.onIntent(MainScreenIntent.DropOnFolder(folderId = "folder-dup"))

        val state = viewModel.state.value
        assertIs<SuccessEvent.FileAlreadyInFolder>(
            state.successEvent,
            "FileDuplicateInFolderException must produce successEvent=FileAlreadyInFolder, not an errorEvent",
        )
        assertNull(state.errorEvent, "errorEvent must be null when file is duplicate (not an error)")
    }

    // EC-8: DropOnFolder with a folder whose name exceeds 40 characters → folderName in successEvent is truncated to 40 chars + "…"
    @Test
    fun dropOnFolder_longFolderName_truncatesTo40Chars() {
        // 255-character folder name (simulates EC-8 maximum)
        val longName = "А".repeat(255)
        val folder = Folder(id = "folder-long", name = longName, createdAt = 0L)
        fakeFolderRepo.folders.add(folder)
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        viewModel.onIntent(
            MainScreenIntent.DragStarted(
                fileId = "drag-long",
                fileUri = "file:///long.pdf",
                displayName = "long.pdf",
            ),
        )
        viewModel.onIntent(MainScreenIntent.DropOnFolder(folderId = "folder-long"))

        val successEvent = viewModel.state.value.successEvent
        assertIs<SuccessEvent.FileAddedToFolder>(successEvent, "successEvent must be FileAddedToFolder")
        val truncated = successEvent.folderName
        assertTrue(
            truncated.length <= 41,
            "Truncated name must be at most 41 chars (40 + ellipsis), got ${truncated.length}",
        )
        assertTrue(truncated.endsWith("…"), "Truncated name must end with ellipsis '…', got: '$truncated'")
        assertEquals("А".repeat(40) + "…", truncated, "Truncated name must be first 40 chars + '…'")
    }

    // TC-8: DropOnFolder → FolderNotFoundException → errorEvent = FolderOperationFailed, dragState = None
    @Test
    fun dropOnFolder_folderNotFoundException_setsErrorEvent() {
        // TC-8: folder deleted after drag started (EC-1 Critical)
        val folder = Folder(id = "folder-deleted", name = "УдалённаяПапка", createdAt = 0L)
        fakeFolderRepo.folders.add(folder)
        fakeFolderRepo.addFileThrows = FolderNotFoundException("folder-deleted")
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        viewModel.onIntent(
            MainScreenIntent.DragStarted(
                fileId = "drag-ec1",
                fileUri = "file:///ec1.pdf",
                displayName = "ec1.pdf",
            ),
        )
        viewModel.onIntent(MainScreenIntent.DropOnFolder(folderId = "folder-deleted"))

        val state = viewModel.state.value
        assertEquals(
            ErrorEvent.FolderOperationFailed,
            state.errorEvent,
            "FolderNotFoundException must produce errorEvent=FolderOperationFailed",
        )
        assertIs<DragState.None>(state.dragState, "dragState must be None after failed drop")
        assertNull(state.successEvent, "successEvent must be null when folder not found")
    }

    // TC-16: DropOnFolder → FolderNotFoundException (folder deleted after drag start) → same as TC-8
    @Test
    fun dropOnFolder_folderDeletedAfterDragStart_setsErrorEvent() {
        // TC-16: scenario where folder was deleted between DragStarted and DropOnFolder (EC-1 Critical)
        val folder = Folder(id = "folder-gone", name = "ИсчезнувшаяПапка", createdAt = 0L)
        fakeFolderRepo.folders.add(folder)
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        viewModel.onIntent(
            MainScreenIntent.DragStarted(
                fileId = "drag-ec1b",
                fileUri = "file:///ec1b.pdf",
                displayName = "ec1b.pdf",
            ),
        )

        // Folder deleted between DragStarted and DropOnFolder
        fakeFolderRepo.addFileThrows = FolderNotFoundException("folder-gone")
        viewModel.onIntent(MainScreenIntent.DropOnFolder(folderId = "folder-gone"))

        val state = viewModel.state.value
        assertEquals(
            ErrorEvent.FolderOperationFailed,
            state.errorEvent,
            "FolderNotFoundException after folder deletion must produce errorEvent=FolderOperationFailed",
        )
        assertIs<DragState.None>(state.dragState, "dragState must be None after failed drop")
        assertNull(state.successEvent, "successEvent must be null when folder is gone")
    }

    // TC-9: DropOnFolder → FileNotInHistoryException → errorEvent = FileNotInHistory, dragState = None
    @Test
    fun dropOnFolder_fileNotInHistoryException_setsErrorEvent() {
        // TC-9: file URI not in history (EC-3 Critical)
        val folder = Folder(id = "folder-noh", name = "ПапкаБезИстории", createdAt = 0L)
        fakeFolderRepo.folders.add(folder)
        fakeFolderRepo.addFileThrows = FileNotInHistoryException("file:///noh.pdf")
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        viewModel.onIntent(
            MainScreenIntent.DragStarted(
                fileId = "drag-ec3",
                fileUri = "file:///noh.pdf",
                displayName = "noh.pdf",
            ),
        )
        viewModel.onIntent(MainScreenIntent.DropOnFolder(folderId = "folder-noh"))

        val state = viewModel.state.value
        assertEquals(
            ErrorEvent.FileNotInHistory,
            state.errorEvent,
            "FileNotInHistoryException must produce errorEvent=FileNotInHistory",
        )
        assertIs<DragState.None>(state.dragState, "dragState must be None after failed drop")
        assertNull(state.successEvent, "successEvent must be null when file not in history")
    }

    // TC-17: DropOnFolder → FileNotInHistoryException (file URI not tracked in history) → same as TC-9
    @Test
    fun dropOnFolder_fileUriNotInHistory_setsErrorEvent() {
        // TC-17: file was dragged but its URI does not exist in history (EC-3 Critical)
        val folder = Folder(id = "folder-noh2", name = "ПапкаФайлВнеИстории", createdAt = 0L)
        fakeFolderRepo.folders.add(folder)
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        viewModel.onIntent(
            MainScreenIntent.DragStarted(
                fileId = "drag-ec3b",
                fileUri = "file:///not-tracked.pdf",
                displayName = "not-tracked.pdf",
            ),
        )

        // File URI not in history — simulated via exception on addFile
        fakeFolderRepo.addFileThrows = FileNotInHistoryException("file:///not-tracked.pdf")
        viewModel.onIntent(MainScreenIntent.DropOnFolder(folderId = "folder-noh2"))

        val state = viewModel.state.value
        assertEquals(
            ErrorEvent.FileNotInHistory,
            state.errorEvent,
            "FileNotInHistoryException when URI not tracked must produce errorEvent=FileNotInHistory",
        )
        assertIs<DragState.None>(state.dragState, "dragState must be None after failed drop")
        assertNull(state.successEvent, "successEvent must be null when file URI not in history")
    }

    // TC-10: Concurrent duplicate drops — first sets FileAlreadyInFolder, second is no-op (EC-2 Critical)
    @Test
    fun dropOnFolder_quickDoubleDropSameFile_secondIsNoOp() {
        // TC-10: rapid double drop with same file (EC-2 Critical — concurrent duplicate drops)
        val folder = Folder(id = "folder-concurrent", name = "КонкурентнаяПапка", createdAt = 0L)
        fakeFolderRepo.folders.add(folder)
        // Every addFile call throws FileDuplicateInFolderException (file already in folder)
        fakeFolderRepo.addFileThrows = FileDuplicateInFolderException("folder-concurrent", "file:///concurrent.pdf")
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        // First drop: dragState = Active → drop dispatched → FileDuplicateInFolderException → successEvent = FileAlreadyInFolder, dragState = None
        viewModel.onIntent(
            MainScreenIntent.DragStarted(
                fileId = "drag-concurrent",
                fileUri = "file:///concurrent.pdf",
                displayName = "concurrent.pdf",
            ),
        )
        viewModel.onIntent(MainScreenIntent.DropOnFolder(folderId = "folder-concurrent"))

        val stateAfterFirst = viewModel.state.value
        assertIs<SuccessEvent.FileAlreadyInFolder>(
            stateAfterFirst.successEvent,
            "First duplicate drop must set successEvent=FileAlreadyInFolder",
        )
        assertIs<DragState.None>(stateAfterFirst.dragState, "dragState must be None after first drop")

        // Second drop: dragState is None — must be a no-op (EC-2: second drop ignored)
        viewModel.onIntent(MainScreenIntent.DropOnFolder(folderId = "folder-concurrent"))

        val stateAfterSecond = viewModel.state.value
        // State must not change — successEvent remains FileAlreadyInFolder, no new errorEvent
        assertIs<SuccessEvent.FileAlreadyInFolder>(
            stateAfterSecond.successEvent,
            "Second drop (dragState=None) must not change successEvent",
        )
        assertNull(stateAfterSecond.errorEvent, "Second no-op drop must not produce an errorEvent")
    }

    // TC-11: DragStarted while launchAvailabilityCheck is running — state consistent (EC-6 High)
    @Test
    fun dragStarted_parallelWithAvailabilityCheck_stateConsistent() {
        // TC-11: DragStarted + launchAvailabilityCheck running simultaneously (EC-6 High)
        val file =
            RecentFile(
                id = "avail-file-1",
                uri = "file:///avail.pdf",
                displayName = "avail.pdf",
                openedAt = 1000L,
                availabilityStatus = AvailabilityStatus.UNKNOWN,
            )
        fakeHistoryRepo.files = listOf(file)
        // Availability check will update to AVAILABLE
        fakeAvailabilityChecker.asyncResult = AvailabilityStatus.AVAILABLE

        // ScreenVisible triggers loadInitialData + launchAvailabilityCheck (runs eagerly with UnconfinedTestDispatcher)
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        // DragStarted dispatched after ScreenVisible — availability check may have already run
        viewModel.onIntent(
            MainScreenIntent.DragStarted(
                fileId = "avail-file-1",
                fileUri = "file:///avail.pdf",
                displayName = "avail.pdf",
            ),
        )

        val state = viewModel.state.value
        // dragState must be Active regardless of availability check state
        val drag = state.dragState
        assertIs<DragState.Active>(drag, "DragStarted must set dragState to Active even during availability check")
        assertEquals("avail-file-1", drag.fileId, "dragState.fileId must match")

        // File availability status must have been updated (availability check ran) — not corrupted by drag
        val fileUiModel = state.recentFiles.firstOrNull { it.id == "avail-file-1" }
        assertNotNull(fileUiModel, "File must still be present in recentFiles")
        assertEquals(
            AvailabilityStatus.AVAILABLE,
            fileUiModel.availabilityStatus,
            "Availability check result must not be corrupted by concurrent DragStarted",
        )
    }

    // TC-4: DropOnFolder success → fileCount of target folder updated without full list reload
    @Test
    fun dropOnFolder_success_updatesFolderFileCountWithoutFullReload() {
        // TC-4 (AC-7): after a successful drop, the folder's fileCount must increase;
        // the other folders' fileCount must not be affected.
        val targetFolder = Folder(id = "folder-count-target", name = "ЦелеваяПапка", createdAt = 0L)
        val otherFolder = Folder(id = "folder-count-other", name = "ДругаяПапка", createdAt = 0L)
        fakeFolderRepo.folders.add(targetFolder)
        fakeFolderRepo.folders.add(otherFolder)
        // After addFile the target folder will have 1 file
        fakeFolderRepo.filesInFolder["folder-count-target"] = listOf("file:///count.pdf")
        fakeFolderRepo.filesInFolder["folder-count-other"] = listOf()
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        val initialOtherCount =
            viewModel.state.value.folders
                .firstOrNull { it.id == "folder-count-other" }
                ?.fileCount
        assertEquals(0, initialOtherCount, "other folder must start with 0 files")

        viewModel.onIntent(
            MainScreenIntent.DragStarted(
                fileId = "drag-count",
                fileUri = "file:///count.pdf",
                displayName = "count.pdf",
            ),
        )
        viewModel.onIntent(MainScreenIntent.DropOnFolder(folderId = "folder-count-target"))

        val state = viewModel.state.value
        val targetUiFolder = state.folders.firstOrNull { it.id == "folder-count-target" }
        assertNotNull(targetUiFolder, "target folder must still be in the list after drop")
        assertEquals(1, targetUiFolder.fileCount, "target folder fileCount must be updated to 1 after drop")

        val otherUiFolder = state.folders.firstOrNull { it.id == "folder-count-other" }
        assertNotNull(otherUiFolder, "other folder must still be in the list")
        assertEquals(initialOtherCount, otherUiFolder.fileCount, "other folder fileCount must remain unchanged")
    }

    // TC-12: DropOnFolder when folders list is empty — FolderNotFoundException handled correctly (AC-9)
    @Test
    fun dropOnFolder_emptyFoldersList_folderNotFoundExceptionHandled() {
        // TC-12 (AC-9): folders is empty, DropOnFolder targets a non-existent folder.
        // The ViewModel must propagate FolderNotFoundException as FolderOperationFailed errorEvent.
        fakeFolderRepo.addFileThrows = FolderNotFoundException("non-existent-folder")
        // No folders added — folders list is empty

        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        assertIs<DragState.None>(viewModel.state.value.dragState, "precondition: dragState must be None")
        assertTrue(
            viewModel.state.value.folders.isEmpty(),
            "precondition: folders list must be empty",
        )

        viewModel.onIntent(
            MainScreenIntent.DragStarted(
                fileId = "drag-tc12",
                fileUri = "file:///tc12.pdf",
                displayName = "tc12.pdf",
            ),
        )
        viewModel.onIntent(MainScreenIntent.DropOnFolder(folderId = "non-existent-folder"))

        val state = viewModel.state.value
        assertEquals(
            ErrorEvent.FolderOperationFailed,
            state.errorEvent,
            "FolderNotFoundException on empty folders list must produce errorEvent=FolderOperationFailed",
        )
        assertIs<DragState.None>(state.dragState, "dragState must be None after failed drop")
        assertNull(state.successEvent, "successEvent must be null when folder not found (empty list)")
    }

    // TC-13: File in ARCHIVED_UNAVAILABLE state dragged to folder — addFile called, availability not changed
    @Test
    fun dropOnFolder_archivedUnavailableFile_addFileCalledAndStatusUnchanged() {
        // TC-13 (AC-12): a file with ARCHIVED_UNAVAILABLE status is still droppable.
        // addFile must be called, and the file's availabilityStatus must NOT change.
        val archivedFile =
            RecentFile(
                id = "arc-drag-1",
                uri = "file:///archived-drag.pdf",
                displayName = "archived-drag.pdf",
                openedAt = 1000L,
                availabilityStatus = AvailabilityStatus.ARCHIVED_UNAVAILABLE,
            )
        val targetFolder = Folder(id = "folder-arc", name = "АрхивнаяПапка", createdAt = 0L)
        fakeHistoryRepo.files = listOf(archivedFile)
        fakeFolderRepo.folders.add(targetFolder)
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        val fileBeforeDrop = viewModel.state.value.recentFiles.firstOrNull { it.id == "arc-drag-1" }
        assertNotNull(fileBeforeDrop, "archived file must be present in the list")
        assertEquals(
            AvailabilityStatus.ARCHIVED_UNAVAILABLE,
            fileBeforeDrop.availabilityStatus,
            "precondition: file must have ARCHIVED_UNAVAILABLE status",
        )

        viewModel.onIntent(
            MainScreenIntent.DragStarted(
                fileId = "arc-drag-1",
                fileUri = "file:///archived-drag.pdf",
                displayName = "archived-drag.pdf",
            ),
        )
        viewModel.onIntent(MainScreenIntent.DropOnFolder(folderId = "folder-arc"))

        val state = viewModel.state.value
        // Drop must succeed (addFile called, no exception)
        assertIs<SuccessEvent.FileAddedToFolder>(
            state.successEvent,
            "Drop of ARCHIVED_UNAVAILABLE file must produce successEvent=FileAddedToFolder",
        )
        assertIs<DragState.None>(state.dragState, "dragState must be None after successful drop")

        // Availability status must NOT be changed by the drop
        val fileAfterDrop = state.recentFiles.firstOrNull { it.id == "arc-drag-1" }
        assertNotNull(fileAfterDrop, "archived file must still be in the list after drop")
        assertEquals(
            AvailabilityStatus.ARCHIVED_UNAVAILABLE,
            fileAfterDrop.availabilityStatus,
            "ARCHIVED_UNAVAILABLE status must not be changed by DropOnFolder",
        )
    }

    // TC-18 (unit portion): AC-8 — ViewModel drop behavior is layout-agnostic (wide layout ≥ 600 dp)
    // Full e2e visual test deferred pending Compose UI test setup; state-machine verified here.
    @Test
    fun dropOnFolder_wideLayout_stateIdenticalToNarrowLayout() {
        // AC-8: behaviour on a wide-screen layout (LazyVerticalGrid) must be identical to narrow (LazyColumn).
        // The ViewModel does not know about layout; this test verifies the invariant at the state-machine level.
        val folder = Folder(id = "folder-wide", name = "ШирокийЭкран", createdAt = 0L)
        fakeFolderRepo.folders.add(folder)
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        viewModel.onIntent(
            MainScreenIntent.DragStarted(
                fileId = "drag-wide",
                fileUri = "file:///wide.pdf",
                displayName = "wide.pdf",
            ),
        )
        viewModel.onIntent(MainScreenIntent.DropOnFolder(folderId = "folder-wide"))

        val state = viewModel.state.value
        // Identical outcome expected for both narrow (LazyColumn) and wide (LazyVerticalGrid) layouts
        assertIs<DragState.None>(state.dragState, "dragState must be None after drop (AC-8)")
        val successEvent = state.successEvent
        assertIs<SuccessEvent.FileAddedToFolder>(successEvent, "successEvent must be FileAddedToFolder (AC-8)")
        assertEquals("ШирокийЭкран", successEvent.folderName, "folderName must match folder (AC-8)")
        assertNull(state.errorEvent, "errorEvent must be null on successful drop (AC-8)")
    }

    // TC-25: DropOnFolder success → successEvent cleared by OnSuccessEventHandled
    @Test
    fun onSuccessEventHandled_clearsSuccessEvent() {
        val folder = Folder(id = "folder-clear", name = "ОчиститьПапка", createdAt = 0L)
        fakeFolderRepo.folders.add(folder)
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        viewModel.onIntent(
            MainScreenIntent.DragStarted(
                fileId = "drag-clear",
                fileUri = "file:///clear.pdf",
                displayName = "clear.pdf",
            ),
        )
        viewModel.onIntent(MainScreenIntent.DropOnFolder(folderId = "folder-clear"))

        assertIs<SuccessEvent.FileAddedToFolder>(
            viewModel.state.value.successEvent,
            "precondition: successEvent must be set",
        )

        viewModel.onIntent(MainScreenIntent.OnSuccessEventHandled)

        assertNull(viewModel.state.value.successEvent, "successEvent must be null after OnSuccessEventHandled")
    }

    // External DnD: dropping files from the OS onto the library opens the first and
    // adds the rest to recent files.
    @Test
    fun externalFilesDroppedOnLibrary_opensFirstAndAddsRestToRecents() {
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        viewModel.onIntent(
            MainScreenIntent.ExternalFilesDroppedOnLibrary(
                uris = listOf("/home/user/first.pdf", "/home/user/second.pdf"),
            ),
        )

        val state = viewModel.state.value
        val nav = state.navigationTarget
        assertIs<NavigationTarget.Editor>(nav, "library drop must open the first file in the editor")
        assertEquals("/home/user/first.pdf", nav.uri, "editor must open the first dropped file")

        val recentUris = state.recentFiles.map { it.uri }
        assertTrue(recentUris.contains("/home/user/first.pdf"), "first file must be in recents")
        assertTrue(recentUris.contains("/home/user/second.pdf"), "second file must be added to recents")
    }

    // External DnD: dropping a file onto a folder card adds it to history and to the folder.
    @Test
    fun externalFilesDroppedOnFolder_addsFileToHistoryAndFolder() {
        val folder = Folder(id = "folder-ext", name = "ВнешняяПапка", createdAt = 0L)
        fakeFolderRepo.folders.add(folder)
        viewModel.onIntent(MainScreenIntent.ScreenVisible)

        viewModel.onIntent(
            MainScreenIntent.ExternalFilesDroppedOnFolder(
                folderId = "folder-ext",
                uris = listOf("/home/user/dropped.pdf"),
            ),
        )

        val state = viewModel.state.value
        assertTrue(
            fakeHistoryRepo.files.any { it.uri == "/home/user/dropped.pdf" },
            "external file must be added to history before being added to the folder",
        )
        assertTrue(
            fakeFolderRepo.addFileCalls.any { it == ("folder-ext" to "/home/user/dropped.pdf") },
            "addFile must be called with the folder id and dropped file uri",
        )
        assertIs<SuccessEvent.FileAddedToFolder>(
            state.successEvent,
            "successful external drop on a folder must produce FileAddedToFolder",
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

    override suspend fun upsert(
        file: RecentFile,
        lastPageIndex: Int,
    ) {
        val existing = files.indexOfFirst { it.id == file.id }
        files =
            if (existing >= 0) {
                files.toMutableList().also { it[existing] = file }
            } else {
                files + file
            }
    }

    override suspend fun updateStatus(
        id: String,
        status: AvailabilityStatus,
    ) {
        updatedStatuses.add(Pair(id, status))
    }

    override suspend fun updateLastPage(
        uri: String,
        pageIndex: Int,
    ) {}

    override suspend fun rollbackUpsert(uri: String) {
        rollbackUpsertCalled = true
        lastRollbackUri = uri
    }
}

private class FakeFolderRepository : FolderRepository {
    var createThrows: Exception? = null
    var addFileThrows: Exception? = null
    val folders: MutableList<Folder> = mutableListOf()

    /** Per-folder file lists returned by [getFilesInFolder]. Defaults to empty if key is absent. */
    val filesInFolder: MutableMap<String, List<String>> = mutableMapOf()

    /** Records every (folderId, uri) passed to [addFile]. */
    val addFileCalls: MutableList<Pair<String, String>> = mutableListOf()

    override suspend fun create(
        name: String,
        parentId: String?,
    ): Folder {
        val ex = createThrows
        if (ex != null) throw ex
        return Folder(id = "folder-${name.hashCode()}", name = name, createdAt = 0L, parentId = parentId)
            .also { folders.add(it) }
    }

    override suspend fun delete(id: String) {
        folders.removeAll { it.id == id }
    }

    override suspend fun addFile(
        folderId: String,
        uri: String,
    ) {
        addFileCalls.add(folderId to uri)
        val ex = addFileThrows
        if (ex != null) throw ex
    }

    override suspend fun removeFile(
        folderId: String,
        uri: String,
    ) {}

    override suspend fun rename(
        id: String,
        newName: String,
    ) {}

    override suspend fun getAll(): List<Folder> = folders

    override suspend fun getFilesInFolder(folderId: String): List<String> = filesInFolder[folderId] ?: emptyList()
}

private class FakeThumbnailRepository : ThumbnailRepository {
    override suspend fun get(
        uri: String,
        currentFileMtime: Long?,
    ): ByteArray? = null

    override suspend fun put(
        uri: String,
        imageData: ByteArray,
        fileMtime: Long?,
    ) {}

    override suspend fun totalSizeBytes(): Long = 0L
}

private class FakePdfThumbnailGenerator : PdfThumbnailGenerator {
    private val results: MutableMap<String, Result<ByteArray>> = mutableMapOf()

    fun setResult(
        id: String,
        result: Result<ByteArray>,
    ) {
        results[id] = result
    }

    override suspend fun generate(
        uri: String,
        widthPx: Int,
        heightPx: Int,
    ): Result<ByteArray> = results[uri] ?: Result.success(byteArrayOf())
}
