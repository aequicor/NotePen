package ru.kyamshanov.notepen.mainscreen.ui.folder

import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.domain.model.Folder
import ru.kyamshanov.notepen.mainscreen.domain.model.RecentFile
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.PdfThumbnailGenerator
import ru.kyamshanov.notepen.mainscreen.domain.port.ThumbnailRepository
import ru.kyamshanov.notepen.mainscreen.domain.usecase.AddToHistoryUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FolderContentsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var lifecycle: LifecycleRegistry
    private lateinit var historyRepo: FakeHistoryRepository
    private lateinit var folderRepo: FakeFolderRepository
    private lateinit var viewModel: FolderContentsViewModel
    private var openEditorCalls = 0
    private var openFolderCalls = 0

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        lifecycle = LifecycleRegistry()
        historyRepo = FakeHistoryRepository()
        folderRepo = FakeFolderRepository()
        openEditorCalls = 0
        openFolderCalls = 0

        viewModel =
            FolderContentsViewModel(
                lifecycle = lifecycle,
                folderId = "folder-1",
                folderName = "Папка",
                historyRepository = historyRepo,
                folderRepository = folderRepo,
                addToHistory = AddToHistoryUseCase(historyRepo),
                thumbnailRepository = FakeThumbnailRepository(),
                thumbnailGenerator = FakeThumbnailGenerator(),
                onOpenEditor = { _, _ -> openEditorCalls++ },
                onOpenFolder = { _, _ -> openFolderCalls++ },
                nowMillis = { 1_000L },
            )
        lifecycle.resume()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // External DnD into an already-open folder: file is added to history + folder, editor NOT opened.
    @Test
    fun addExternalFiles_addsToHistoryAndFolderWithoutOpeningEditor() {
        folderRepo.folders.add(Folder(id = "folder-1", name = "Папка", createdAt = 0L))

        viewModel.addExternalFiles(listOf("/home/user/dropped.pdf"))

        assertTrue(
            historyRepo.files.any { it.uri == "/home/user/dropped.pdf" },
            "external file must be added to history",
        )
        assertTrue(
            folderRepo.addFileCalls.any { it == ("folder-1" to "/home/user/dropped.pdf") },
            "file must be linked to the open folder",
        )
        assertFalse(openEditorCalls > 0, "editor must NOT be opened on external drop into an open folder")
    }

    @Test
    fun addExternalFiles_blankUris_areIgnored() {
        viewModel.addExternalFiles(listOf("", "   "))

        assertTrue(folderRepo.addFileCalls.isEmpty(), "blank uris must not trigger addFile")
        assertFalse(openEditorCalls > 0, "editor must not open for blank input")
    }
}

private class FakeHistoryRepository : FileHistoryRepository {
    var files: List<RecentFile> = emptyList()

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
    ) {}

    override suspend fun updateLastPage(
        uri: String,
        pageIndex: Int,
    ) {}

    override suspend fun rollbackUpsert(uri: String) {}
}

private class FakeFolderRepository : FolderRepository {
    val folders: MutableList<Folder> = mutableListOf()
    val addFileCalls: MutableList<Pair<String, String>> = mutableListOf()
    val filesInFolder: MutableMap<String, List<String>> = mutableMapOf()

    override suspend fun create(
        name: String,
        parentId: String?,
    ): Folder =
        Folder(id = "folder-${name.hashCode()}", name = name, createdAt = 0L, parentId = parentId)
            .also { folders.add(it) }

    override suspend fun delete(id: String) {
        folders.removeAll { it.id == id }
    }

    override suspend fun addFile(
        folderId: String,
        uri: String,
    ) {
        addFileCalls.add(folderId to uri)
        filesInFolder[folderId] = (filesInFolder[folderId] ?: emptyList()) + uri
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

private class FakeThumbnailGenerator : PdfThumbnailGenerator {
    override suspend fun generate(
        uri: String,
        widthPx: Int,
        heightPx: Int,
    ): Result<ByteArray> = Result.success(byteArrayOf())
}
