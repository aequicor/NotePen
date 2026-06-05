package ru.kyamshanov.notepen.sync.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.mainscreen.domain.model.Folder
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.sync.domain.model.BookId
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.LibraryBook
import ru.kyamshanov.notepen.sync.domain.model.LibraryManifest
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.OpenDocumentInfo
import ru.kyamshanov.notepen.sync.domain.model.PeerMessage
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState
import ru.kyamshanov.notepen.sync.domain.port.CatalogChangeNotifier
import ru.kyamshanov.notepen.sync.domain.port.LibraryManifestProvider
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryOpenDocumentsRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The host serves a single per-peer catalog that is the **tagged union** of all shared libraries
 * (so a client connected to one library projects only its subset), and tolerates a document-id
 * collision between libraries by keeping the first deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteCatalogProviderTest {
    @Test
    fun servesTaggedUnionOfNamedLibraries() =
        runBlocking {
            val math = source("local:/Math", "Math", book("a#1", "alg.pdf", "/Math/alg.pdf"))
            val phys = source("local:/Physics", "Physics", book("b#2", "mech.pdf", "/Physics/mech.pdf"))
            val provider =
                RemoteCatalogProvider(
                    hostName = "Desk",
                    sharedLibrariesProvider = { listOf(math, phys) },
                    folderRepository = EmptyFolders,
                )

            val catalog = provider.buildSnapshotFor("peer-1")

            assertEquals(
                mapOf("a#1" to "local:/Math", "b#2" to "local:/Physics"),
                catalog.recent.associate { it.documentId to it.libraryId },
                "each book is tagged with the library it came from",
            )
            assertEquals(
                listOf("local:/Math" to "Math", "local:/Physics" to "Physics"),
                catalog.libraries.map { it.libraryId to it.displayName },
                "both named libraries are advertised in the catalog",
            )
        }

    @Test
    fun collidingDocumentIdAcrossLibraries_keepsFirst() =
        runBlocking {
            // Two libraries holding a file at the same relative path collide on document id.
            val a = source("local:/A", "A", book("notes.pdf#x", "notes.pdf", "/A/notes.pdf"))
            val b = source("local:/B", "B", book("notes.pdf#x", "notes.pdf", "/B/notes.pdf"))
            val provider =
                RemoteCatalogProvider(
                    hostName = "Desk",
                    sharedLibrariesProvider = { listOf(a, b) },
                    folderRepository = EmptyFolders,
                )

            val catalog = provider.buildSnapshotFor("peer-1")

            assertEquals(1, catalog.recent.size, "the duplicate document id is dropped (first kept)")
            assertEquals("local:/A", catalog.recent.single().libraryId, "the first library's entry survives")
        }

    @Test
    fun openDocumentsAreAdvertisedAndAuthorizedForTransfer() =
        runBlocking {
            val openDocuments = InMemoryOpenDocumentsRegistry()
            openDocuments.publish(
                listOf(
                    OpenDocumentInfo(
                        documentId = "tablet-doc",
                        displayName = "tablet.pdf",
                        absolutePath = "/tablet/tablet.pdf",
                        fileSize = 42L,
                    ),
                ),
            )
            val provider =
                RemoteCatalogProvider(
                    hostName = "Tablet",
                    sharedLibrariesProvider = { emptyList() },
                    folderRepository = EmptyFolders,
                    openDocumentsProvider = openDocuments,
                )

            val catalog = provider.buildSnapshotFor("pc")

            assertEquals(listOf("tablet-doc"), catalog.openDocuments.map { it.documentId })
            assertEquals("/tablet/tablet.pdf", provider.resolveUri("pc", "tablet-doc"))
            assertTrue(provider.isAllowed("pc", "tablet-doc"))
        }

    @Test
    fun openDocumentsChangeBroadcastsRemoteCatalogChanged() =
        runTest {
            val openDocuments = InMemoryOpenDocumentsRegistry()
            val server = CatalogChangedFakePeerServer()
            val provider =
                RemoteCatalogProvider(
                    hostName = "Tablet",
                    sharedLibrariesProvider = { emptyList() },
                    folderRepository = EmptyFolders,
                    openDocumentsProvider = openDocuments,
                )
            provider.broadcastChanges(
                notifier = EmptyCatalogChangeNotifier,
                server = server,
                scope = backgroundScope,
            )
            runCurrent()

            openDocuments.publish(
                listOf(
                    OpenDocumentInfo(
                        documentId = "tablet-doc",
                        displayName = "tablet.pdf",
                        absolutePath = "/tablet/tablet.pdf",
                    ),
                ),
            )
            runCurrent()

            assertEquals(listOf<NetworkMessage>(NetworkMessage.RemoteCatalogChanged), server.broadcasts)
        }

    private fun book(
        id: String,
        name: String,
        uri: String,
    ) = FakeBook(BookId(id), name, uri)

    private fun source(
        libraryId: String,
        name: String,
        vararg books: FakeBook,
    ) = SharedLibrarySource(libraryId, name, FakeManifestProvider(books.toList()))
}

private class FakeBook(
    val id: BookId,
    val name: String,
    val uri: String,
)

private class FakeManifestProvider(
    private val books: List<FakeBook>,
) : LibraryManifestProvider {
    override suspend fun current(): LibraryManifest =
        LibraryManifest(books.map { LibraryBook(it.id, it.name, it.name, fileSize = 1L, modifiedAt = 0L) })

    override suspend fun resolveAbsolutePath(id: BookId): String? = books.firstOrNull { it.id == id }?.uri
}

private object EmptyFolders : FolderRepository {
    override suspend fun getAll(): List<Folder> = emptyList()

    override suspend fun getFilesInFolder(folderId: String): List<String> = emptyList()

    override suspend fun create(
        name: String,
        parentId: String?,
    ): Folder = error("unused")

    override suspend fun rename(
        id: String,
        newName: String,
    ) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun addFile(
        folderId: String,
        uri: String,
    ) = Unit

    override suspend fun removeFile(
        folderId: String,
        uri: String,
    ) = Unit
}

private object EmptyCatalogChangeNotifier : CatalogChangeNotifier {
    override val changes: Flow<Unit> = MutableSharedFlow<Unit>()

    override fun notifyChanged() = Unit
}

private class CatalogChangedFakePeerServer : PeerServer {
    override val lifecycle: Flow<ServerLifecycleState> = MutableStateFlow(ServerLifecycleState.Idle).asStateFlow()
    override val connectedPeers: Flow<Set<DeviceInfo>> = MutableStateFlow(emptySet<DeviceInfo>()).asStateFlow()
    override val pendingApprovals: Flow<DeviceInfo> = MutableSharedFlow()
    override val incomingMessages: Flow<PeerMessage> = MutableSharedFlow()
    val broadcasts = mutableListOf<NetworkMessage>()

    override suspend fun start(): Result<ServerLifecycleState.Running> = error("unused")

    override suspend fun send(
        peerId: String,
        message: NetworkMessage,
    ) = Unit

    override suspend fun broadcast(message: NetworkMessage) {
        broadcasts += message
    }

    override suspend fun approve(peerId: String) = Unit

    override suspend fun reject(peerId: String) = Unit

    override suspend fun disconnect(peerId: String) = Unit

    override suspend fun disconnectAll() = Unit

    override suspend fun stop() = Unit
}
