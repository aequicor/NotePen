package ru.kyamshanov.notepen.mainscreen.ui.library

import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.kyamshanov.notepen.library.api.Library
import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryBookId
import ru.kyamshanov.notepen.library.api.LibraryCapabilities
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryConnectionState
import ru.kyamshanov.notepen.library.api.LibraryDescriptor
import ru.kyamshanov.notepen.library.api.LibraryEntry
import ru.kyamshanov.notepen.library.api.LibraryId
import ru.kyamshanov.notepen.library.api.LibraryRegistry
import ru.kyamshanov.notepen.library.api.LibraryRole
import ru.kyamshanov.notepen.library.api.MergedLibraryEntry
import ru.kyamshanov.notepen.library.api.OpenableDocument
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryFolderContentsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var lifecycle: LifecycleRegistry

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(
        libraryId: String,
        registry: LibraryRegistry,
        onOpenEditor: (uri: String, lastPageIndex: Int) -> Unit = { _, _ -> },
    ) = LibraryFolderContentsViewModel(
        lifecycle = lifecycle,
        libraryId = libraryId,
        libraryRegistry = registry,
        onOpenEditor = onOpenEditor,
    )

    @Test
    fun resolvesLibraryById_mapsBooksAndTitle() {
        val lib =
            FakeLibrary(
                LibraryDescriptor(LibraryId("local:/a"), "Моя библиотека", LibraryBackendKind.Local, LibraryRole.Librarian),
                listOf(LibraryEntry(LibraryBookId("b1"), "book1.pdf"), LibraryEntry(LibraryBookId("b2"), "book2.pdf")),
            )
        val model = vm("local:/a", FakeLibraryRegistry(listOf(lib)))

        val state = model.state.value
        assertFalse(state.isLoading, "a resolved library is not loading")
        assertEquals("Моя библиотека", state.title, "title is the library's display name")
        assertEquals(listOf("b1", "b2"), state.items.map { it.id })
        assertEquals(listOf("book1.pdf", "book2.pdf"), state.items.map { it.displayName })
    }

    @Test
    fun blankLibraryId_isTerminalNotLoading() {
        // A stale serialized back-stack restored after process death carries libraryId = "".
        val model = vm("", FakeLibraryRegistry(emptyList()))

        val state = model.state.value
        assertFalse(state.isLoading, "a blank (stale-restore) libraryId must not spin forever")
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun unresolvedNonBlankId_staysLoading() {
        // id not present yet (e.g. a saved library still connecting at startup, after navigation).
        val model = vm("local:/not-yet", FakeLibraryRegistry(emptyList()))

        assertTrue(model.state.value.isLoading, "an unresolved non-blank id waits (loading) for the library to connect")
    }

    @Test
    fun openItem_opensViaLibraryAndRoutesToEditor() {
        val lib =
            FakeLibrary(
                LibraryDescriptor(LibraryId("local:/a"), "Моя", LibraryBackendKind.Local, LibraryRole.Librarian),
                listOf(LibraryEntry(LibraryBookId("b1"), "book1.pdf")),
            )
        var openedPath: String? = null
        val model = vm("local:/a", FakeLibraryRegistry(listOf(lib))) { uri, _ -> openedPath = uri }

        model.openItem("b1")

        assertEquals(
            "/opened/b1",
            openedPath,
            "openItem must materialize via Library.open and route localPath to the editor",
        )
    }
}

private class FakeLibrary(
    override val descriptor: LibraryDescriptor,
    booksValue: List<LibraryEntry> = emptyList(),
) : Library {
    override val capabilities: LibraryCapabilities = LibraryCapabilities.fromRole(descriptor.role)
    override val books: MutableStateFlow<List<LibraryEntry>> = MutableStateFlow(booksValue)
    override val connectionState: StateFlow<LibraryConnectionState> =
        MutableStateFlow(LibraryConnectionState.Connected)

    override suspend fun refresh() = Unit

    override suspend fun open(id: LibraryBookId): Result<OpenableDocument> =
        Result.success(OpenableDocument(localPath = "/opened/${id.value}", identity = null, readOnly = false))
}

private class FakeLibraryRegistry(
    initialLibraries: List<Library>,
) : LibraryRegistry {
    override val libraries: MutableStateFlow<List<Library>> = MutableStateFlow(initialLibraries)
    override val mergedBooks: StateFlow<List<MergedLibraryEntry>> = MutableStateFlow(emptyList())

    override suspend fun connect(spec: LibraryConnection): Result<Library> =
        Result.failure(UnsupportedOperationException("not used in tests"))

    override suspend fun disconnect(id: LibraryId) = Unit

    override suspend fun savedConnections(): List<LibraryConnection> = emptyList()
}
