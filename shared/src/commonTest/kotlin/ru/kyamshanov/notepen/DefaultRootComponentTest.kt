package ru.kyamshanov.notepen

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.domain.model.RecentFile
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRootComponentTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `openDetailsExternally focuses existing details instead of pushing duplicate`() {
        val root = newRootComponent()
        val uri = "file:///docs/broadcast.pdf"

        root.openDetailsExternally(uri)
        root.openDetailsExternally(uri)

        val stack = root.stack.value
        assertEquals(2, stack.items.size)
        val active = assertIs<RootComponent.Child.DetailsChild>(stack.active.instance)
        assertEquals(uri, active.component.model.value.title)
    }

    private fun newRootComponent(): DefaultRootComponent {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        return DefaultRootComponent(
            componentContext = DefaultComponentContext(lifecycle),
            historyRepository = EmptyHistoryRepository,
            mainComponentFactory = { _, _, _, _, _, _, _ -> object : MainComponent {} },
            peerCatalogComponentFactory = { _, _, _, _, _ -> object : PeerCatalogComponent {} },
            folderComponentFactory = { _, _, _, _, _, _ -> object : FolderComponent {} },
            libraryFolderComponentFactory = { _, _, _, _ -> object : LibraryFolderComponent {} },
            settingsComponentFactory = { _, _ ->
                object : SettingsComponent {
                    override fun onBack() = Unit
                }
            },
            librarySourcesComponentFactory = { _, _ ->
                object : LibrarySourcesComponent {
                    override fun onBack() = Unit
                }
            },
        )
    }
}

private object EmptyHistoryRepository : FileHistoryRepository {
    override suspend fun getAll(): List<RecentFile> = emptyList()

    override suspend fun upsert(
        file: RecentFile,
        lastPageIndex: Int,
    ) = Unit

    override suspend fun updateStatus(
        id: String,
        status: AvailabilityStatus,
    ) = Unit

    override suspend fun updateLastPage(
        uri: String,
        pageIndex: Int,
    ) = Unit

    override suspend fun rollbackUpsert(uri: String) = Unit
}
