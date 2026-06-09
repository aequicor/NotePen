package ru.kyamshanov.notepen.sync.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import ru.kyamshanov.notepen.mainscreen.domain.model.Folder
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.HostMessage
import ru.kyamshanov.notepen.sync.domain.model.LibraryManifest
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.OpenDocumentInfo
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.port.LibraryManifestProvider
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryOpenDocumentsRegistry
import ru.kyamshanov.notepen.sync.infrastructure.decodeBase64
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DocumentTransferRequestHandlerTest {
    @Test
    fun clientSideHandlerRepliesNotFoundToRequestingHost() =
        runTest {
            val client = FakeSyncClient()
            DocumentTransferRequestHandler(client = client, provider = emptyProvider())
                .start(scope = backgroundScope)
            client.awaitSubscribed()

            val host = DeviceInfo(id = "host-1", name = "PC", host = "127.0.0.1", port = 1)
            client.emit(host, NetworkMessage.DocumentOpenRequest(documentId = "doc-missing"))

            val sent = withTimeout(1_000) { client.sent.first() }
            assertEquals("host-1", sent.hostId)
            val message = assertIs<NetworkMessage.DocumentNotFound>(sent.message)
            assertEquals("doc-missing", message.documentId)
        }

    @Test
    fun clientSideHandlerStreamsOpenDocumentToRequestingHost() =
        runTest {
            val file = File.createTempFile("notepen-transfer-", ".pdf")
            file.writeBytes("pdf-bytes".encodeToByteArray())
            val openDocuments = InMemoryOpenDocumentsRegistry()
            openDocuments.publish(
                listOf(
                    OpenDocumentInfo(
                        documentId = "tablet-doc",
                        displayName = "tablet.pdf",
                        absolutePath = file.absolutePath,
                        fileSize = file.length(),
                    ),
                ),
            )
            val provider =
                RemoteCatalogProvider(
                    hostName = "tablet",
                    manifestProvider = TransferEmptyManifestProvider,
                    folderRepository = TransferEmptyFolderRepository,
                    openDocumentsProvider = openDocuments,
                )
            provider.buildSnapshotFor("host-1")
            val client = FakeSyncClient()
            DocumentTransferRequestHandler(client = client, provider = provider)
                .start(scope = backgroundScope)
            client.awaitSubscribed()

            val host = DeviceInfo(id = "host-1", name = "PC", host = "127.0.0.1", port = 1)
            client.emit(host, NetworkMessage.DocumentOpenRequest(documentId = "tablet-doc"))

            val start = assertIs<NetworkMessage.FileTransferStart>(withTimeout(1_000) { client.sent.first() }.message)
            val chunk =
                assertIs<NetworkMessage.FileChunk>(
                    withTimeout(1_000) {
                        client.sent.first { it.message is NetworkMessage.FileChunk }
                    }.message,
                )
            assertEquals("tablet-doc", start.documentId)
            assertEquals("tablet-doc", chunk.documentId)
            assertEquals("pdf-bytes", decodeBase64(chunk.dataBase64).decodeToString())
        }

    private fun emptyProvider(): RemoteCatalogProvider =
        RemoteCatalogProvider(
            hostName = "tablet",
            manifestProvider = TransferEmptyManifestProvider,
            folderRepository = TransferEmptyFolderRepository,
        )
}

private class FakeSyncClient : SyncClient {
    private val incoming = MutableSharedFlow<HostMessage>(extraBufferCapacity = 8)
    private val outgoing = MutableSharedFlow<SentMessage>(replay = 8, extraBufferCapacity = 8)

    override val incomingMessages: Flow<HostMessage> = incoming.asSharedFlow()
    override val pairingStates: Flow<Map<String, PairingState>> = MutableStateFlow(emptyMap<String, PairingState>()).asStateFlow()
    override val connectedHosts: Flow<Set<DeviceInfo>> = MutableStateFlow(emptySet<DeviceInfo>()).asStateFlow()
    val sent: Flow<SentMessage> = outgoing.asSharedFlow()

    suspend fun awaitSubscribed() {
        incoming.subscriptionCount.first { it > 0 }
    }

    suspend fun emit(
        host: DeviceInfo,
        message: NetworkMessage,
    ) {
        incoming.emit(HostMessage(host = host, message = message))
    }

    override suspend fun connect(
        server: DeviceInfo,
        pairingCode: String,
        selfInfo: DeviceInfo,
    ): Result<DeviceInfo> = Result.success(server)

    override suspend fun send(
        hostId: String,
        message: NetworkMessage,
    ) {
        outgoing.emit(SentMessage(hostId, message))
    }

    override suspend fun broadcast(message: NetworkMessage) = Unit

    override suspend fun disconnect(hostId: String) = Unit

    override suspend fun disconnectAll() = Unit
}

private data class SentMessage(
    val hostId: String,
    val message: NetworkMessage,
)

private object TransferEmptyManifestProvider : LibraryManifestProvider {
    override suspend fun current(): LibraryManifest = LibraryManifest(emptyList())

    override suspend fun resolveAbsolutePath(id: ru.kyamshanov.notepen.sync.domain.model.BookId): String? = null
}

private object TransferEmptyFolderRepository : FolderRepository {
    override suspend fun create(
        name: String,
        parentId: String?,
    ): Folder = error("Not needed")

    override suspend fun delete(id: String) = Unit

    override suspend fun addFile(
        folderId: String,
        uri: String,
    ) = Unit

    override suspend fun removeFile(
        folderId: String,
        uri: String,
    ) = Unit

    override suspend fun rename(
        id: String,
        newName: String,
    ) = Unit

    override suspend fun getAll(): List<Folder> = emptyList()

    override suspend fun getFilesInFolder(folderId: String): List<String> = emptyList()
}
