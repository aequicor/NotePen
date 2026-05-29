package ru.kyamshanov.notepen.library.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryConnectionStore
import ru.kyamshanov.notepen.mainscreen.domain.port.LibraryFolder
import ru.kyamshanov.notepen.mainscreen.domain.port.LibraryFolderItem
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies the [DefaultLibraryRegistry] ↔ [LibraryConnectionStore] wiring: which connections persist,
 * connect/disconnect bookkeeping, and that [DefaultLibraryRegistry.savedConnections] is sourced from
 * the store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLibraryRegistryPersistenceTest {
    /** In-memory [LibraryConnectionStore], mutex-guarded like the real impls. */
    private class FakeConnectionStore(
        initial: List<LibraryConnection> = emptyList(),
    ) : LibraryConnectionStore {
        private val mutex = Mutex()
        private var connections = initial.toList()

        override suspend fun load(): List<LibraryConnection> = mutex.withLock { connections }

        override suspend fun save(connections: List<LibraryConnection>) {
            mutex.withLock { this.connections = connections.toList() }
        }

        override suspend fun add(connection: LibraryConnection): List<LibraryConnection> =
            mutex.withLock {
                connections = connections.filterNot { it == connection } + connection
                connections
            }

        override suspend fun remove(connection: LibraryConnection): List<LibraryConnection> =
            mutex.withLock {
                connections = connections.filterNot { it == connection }
                connections
            }
    }

    private class FakeLibraryFolder : LibraryFolder {
        override val items: StateFlow<List<LibraryFolderItem>> = MutableStateFlow(emptyList<LibraryFolderItem>()).asStateFlow()

        override suspend fun addCopy(sourceUri: String): Result<LibraryFolderItem> =
            Result.failure(UnsupportedOperationException("not used"))

        override suspend fun refresh() = Unit
    }

    private fun localRegistry(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
        store: LibraryConnectionStore,
    ): DefaultLibraryRegistry =
        DefaultLibraryRegistry(
            backends = listOf(LocalFolderLibraryBackend { _, _ -> FakeLibraryFolder() }),
            scope = scope,
            ioDispatcher = dispatcher,
            connectionStore = store,
        )

    @Test
    fun savedConnections_areSourcedFromStore() =
        runTest {
            val seeded = listOf(LibraryConnection.PeerLan(peerId = "seeded"))
            val store = FakeConnectionStore(initial = seeded)
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val registry = localRegistry(scope, UnconfinedTestDispatcher(testScheduler), store)

            assertEquals(seeded, registry.savedConnections(), "savedConnections() reads through the store")
            scope.cancel()
        }

    @Test
    fun connectingLocal_isNotPersisted() =
        runTest {
            val store = FakeConnectionStore()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val registry = localRegistry(scope, UnconfinedTestDispatcher(testScheduler), store)

            registry.connect(LibraryConnection.Local("/tmp/lib")).getOrThrow()

            assertEquals(emptyList<LibraryConnection>(), store.load(), "the always-on local library is not persisted")
            scope.cancel()
        }

    @Test
    fun connectingPeerLan_persists_andDisconnectRemoves() =
        runTest {
            val store = FakeConnectionStore()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val registry =
                DefaultLibraryRegistry(
                    backends =
                        listOf(
                            LocalFolderLibraryBackend { _, _ -> FakeLibraryFolder() },
                            // Real PeerLan backend with an empty catalog snapshot: connect() only
                            // projects the (empty) catalog as a read-only library — no transport.
                            PeerLanLibraryBackend(
                                catalogs = MutableStateFlow(emptyMap<DeviceInfo, RemoteCatalog>()),
                                documentOpenerProvider = { null },
                            ),
                        ),
                    scope = scope,
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                    connectionStore = store,
                )
            val peer = LibraryConnection.PeerLan(peerId = "peer-1", host = "10.0.0.5")

            val library = registry.connect(peer).getOrThrow()
            assertEquals(listOf(peer), store.load(), "a connected PeerLan library is persisted")

            registry.disconnect(library.descriptor.id)
            assertEquals(emptyList<LibraryConnection>(), store.load(), "disconnect removes the persisted spec")
            scope.cancel()
        }
}
