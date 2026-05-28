import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.createAnnotationRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.sync.domain.CatalogDiffOrphanDetector
import ru.kyamshanov.notepen.sync.domain.DocumentStatusCoordinator
import ru.kyamshanov.notepen.sync.domain.DocumentTransferRequestHandler
import ru.kyamshanov.notepen.sync.domain.HostAnnotationProjection
import ru.kyamshanov.notepen.sync.domain.HostHeadlessAnnotationHandler
import ru.kyamshanov.notepen.sync.domain.LocalCachedDocumentCleaner
import ru.kyamshanov.notepen.sync.domain.PendingDeltaReplayCoordinator
import ru.kyamshanov.notepen.sync.domain.RemoteCatalogClientCoordinator
import ru.kyamshanov.notepen.sync.domain.RemoteCatalogHostCoordinator
import ru.kyamshanov.notepen.sync.domain.RemoteCatalogProvider
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentOpener
import ru.kyamshanov.notepen.sync.domain.SyncEngineRegistry
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState
import ru.kyamshanov.notepen.sync.domain.port.CatalogChangeNotifier
import ru.kyamshanov.notepen.sync.domain.port.LibraryManifestProvider
import ru.kyamshanov.notepen.sync.domain.port.LocalDocumentIdRegistry
import ru.kyamshanov.notepen.sync.domain.port.OpenDocumentRegistry
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryRemoteCatalogCache
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryRemoteDocumentStatusRegistry
import ru.kyamshanov.notepen.sync.infrastructure.JmDnsServiceRegistrar
import ru.kyamshanov.notepen.sync.infrastructure.KtorPeerServer
import ru.kyamshanov.notepen.sync.infrastructure.KtorSyncClient
import ru.kyamshanov.notepen.sync.infrastructure.SqlDelightPendingDeltaQueue
import ru.kyamshanov.notepen.sync.infrastructure.createSyncDatabaseJvm

/**
 * Snapshot of the running sync stack. Exposes only the pieces the rest of the
 * app touches (UI panels, the editor, the document opener); coordinators and
 * subscriptions live entirely inside [SyncRuntime] and disappear together
 * with their child scope on [SyncRuntime.disable].
 */
class SyncSession internal constructor(
    val peerServer: KtorPeerServer,
    val syncClient: KtorSyncClient,
    val syncEngineRegistry: SyncEngineRegistry,
    val pendingDeltaQueue: SqlDelightPendingDeltaQueue,
    val remoteDocumentOpener: RemoteDocumentOpener,
    val hostAnnotationProjection: HostAnnotationProjection,
    val serviceRegistrar: JmDnsServiceRegistrar,
    internal val httpClient: HttpClient,
    internal val sessionJob: Job,
)

/**
 * Lazy owner of NotePen's peer-to-peer sync stack.
 *
 * Until [enable] is called nothing in Ktor/CIO/jmDNS/SQLite is touched — keeps
 * cold-start cheap and silences `RemoteCatalogProvider` / `HostAnnotationProjection`
 * log noise for users that never use sync. [disable] cancels every coroutine
 * the session spawned (catalog broadcaster, message demux, lifecycle observer,
 * coordinators), stops the peer server, and closes the HTTP client.
 *
 * Both methods are idempotent: calling [enable] twice returns the same session;
 * [disable] when already disabled is a no-op.
 */
class SyncRuntime(
    private val selfInfo: DeviceInfo,
    private val selfId: String,
    private val selfName: String,
    private val parentScope: CoroutineScope,
    private val receivedDir: String,
    private val syncDatabasePath: String,
    private val libraryManifestProvider: LibraryManifestProvider,
    private val folderRepository: FolderRepository,
    private val catalogChangeNotifier: CatalogChangeNotifier,
    private val remoteCatalogCache: InMemoryRemoteCatalogCache,
    private val remoteDocumentStatusRegistry: InMemoryRemoteDocumentStatusRegistry,
    private val openDocumentRegistry: OpenDocumentRegistry,
    private val localDocumentIdRegistry: LocalDocumentIdRegistry,
) {
    private val _session = MutableStateFlow<SyncSession?>(null)
    val session: StateFlow<SyncSession?> = _session.asStateFlow()

    // Serialises enable/disable so concurrent UI clicks can't race the
    // expensive build (Ktor classloading, SQLite native init).
    private val mutex = Mutex()

    suspend fun enable(): SyncSession =
        mutex.withLock {
            _session.value?.let { return@withLock it }
            val built = build()
            _session.value = built
            built
        }

    suspend fun disable() {
        mutex.withLock {
            val current = _session.value ?: return
            _session.value = null
            tearDown(current)
        }
    }

    /** Snapshot of online peers across host server + outbound client. Empty when disabled. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun onlinePeerIds(): Flow<Set<String>> =
        session.flatMapLatest { current ->
            if (current == null) {
                flowOf(emptySet())
            } else {
                combine(
                    current.syncClient.connectedHosts,
                    current.peerServer.connectedPeers,
                ) { hosts, peers ->
                    (hosts.map { it.id } + peers.map { it.id }).toSet()
                }
            }
        }

    /** `documentId → pendingCount` from the offline buffer. Empty map when disabled. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun pendingDeltaCounts(): Flow<Map<String, Int>> =
        session.flatMapLatest { current ->
            current?.pendingDeltaQueue?.pendingCounts() ?: flowOf(emptyMap())
        }

    private suspend fun build(): SyncSession =
        withContext(Dispatchers.IO) {
            val sessionJob = SupervisorJob(parentScope.coroutineContext[Job])
            val sessionScope = CoroutineScope(parentScope.coroutineContext + sessionJob)

            val peerServer = KtorPeerServer(selfInfo = selfInfo, ioDispatcher = Dispatchers.IO)
            val httpClient = createSyncHttpClient()
            val syncClient = KtorSyncClient(httpClient)
            val serviceRegistrar = JmDnsServiceRegistrar()
            val pendingDeltaQueue = createPendingDeltaQueue(syncDatabasePath)
            val syncEngineRegistry =
                SyncEngineRegistry(
                    deviceId = selfId,
                    scope = sessionScope,
                    server = peerServer,
                    client = syncClient,
                    pendingQueue = pendingDeltaQueue,
                )

            startReplayCoordinators(sessionScope, syncEngineRegistry, peerServer, syncClient)

            val remoteCatalogProvider = buildCatalogProvider(sessionScope, peerServer, syncClient)
            DocumentTransferRequestHandler(server = peerServer, provider = remoteCatalogProvider)
                .start(scope = sessionScope)

            val hostAnnotationProjection =
                buildProjection(sessionScope, peerServer, remoteCatalogProvider, syncEngineRegistry)

            startCatalogCoordinators(sessionScope, peerServer, syncClient, pendingDeltaQueue)
            val remoteDocumentOpener =
                RemoteDocumentOpener(
                    client = syncClient,
                    catalogs = remoteCatalogCache.catalogs,
                    destDir = receivedDir,
                    documentIdRegistry = localDocumentIdRegistry,
                )

            wireSession(
                scope = sessionScope,
                peerServer = peerServer,
                syncClient = syncClient,
                registry = syncEngineRegistry,
                projection = hostAnnotationProjection,
                registrar = serviceRegistrar,
            )

            SyncSession(
                peerServer = peerServer,
                syncClient = syncClient,
                syncEngineRegistry = syncEngineRegistry,
                pendingDeltaQueue = pendingDeltaQueue,
                remoteDocumentOpener = remoteDocumentOpener,
                hostAnnotationProjection = hostAnnotationProjection,
                serviceRegistrar = serviceRegistrar,
                httpClient = httpClient,
                sessionJob = sessionJob,
            )
        }

    private fun startReplayCoordinators(
        scope: CoroutineScope,
        registry: SyncEngineRegistry,
        peerServer: KtorPeerServer,
        syncClient: KtorSyncClient,
    ) {
        val hostPeerConnected =
            peerServer.connectedPeers
                .distinctUntilChanged()
                .filter { it.isNotEmpty() }
                .map { }
        val clientConnected =
            syncClient.connectedHosts
                .distinctUntilChanged()
                .filter { it.isNotEmpty() }
                .map { }
        PendingDeltaReplayCoordinator(
            registry = registry,
            connectionEstablished = hostPeerConnected,
        ).start(scope = scope)
        PendingDeltaReplayCoordinator(
            registry = registry,
            connectionEstablished = clientConnected,
        ).start(scope = scope)
    }

    private fun buildCatalogProvider(
        scope: CoroutineScope,
        peerServer: KtorPeerServer,
        syncClient: KtorSyncClient,
    ): RemoteCatalogProvider {
        val provider =
            RemoteCatalogProvider(
                hostName = selfName,
                manifestProvider = libraryManifestProvider,
                folderRepository = folderRepository,
            )
        provider.serve(server = peerServer, scope = scope)
        provider.serve(client = syncClient, scope = scope)
        provider.broadcastChanges(
            notifier = catalogChangeNotifier,
            server = peerServer,
            client = syncClient,
            scope = scope,
        )
        return provider
    }

    private fun buildProjection(
        scope: CoroutineScope,
        peerServer: KtorPeerServer,
        provider: RemoteCatalogProvider,
        registry: SyncEngineRegistry,
    ): HostAnnotationProjection {
        val projection =
            HostAnnotationProjection(
                registry = registry,
                provider = provider,
                repository = createAnnotationRepository(),
            )
        HostHeadlessAnnotationHandler(
            server = peerServer,
            provider = provider,
            projection = projection,
        ).start(scope = scope)
        return projection
    }

    private fun startCatalogCoordinators(
        scope: CoroutineScope,
        peerServer: KtorPeerServer,
        syncClient: KtorSyncClient,
        queue: SqlDelightPendingDeltaQueue,
    ) {
        RemoteCatalogClientCoordinator(client = syncClient, cache = remoteCatalogCache)
            .start(scope = scope)
        RemoteCatalogHostCoordinator(server = peerServer, cache = remoteCatalogCache)
            .start(scope = scope)
        DocumentStatusCoordinator(client = syncClient, registry = remoteDocumentStatusRegistry)
            .start(scope = scope)
        CatalogDiffOrphanDetector(
            catalogs = remoteCatalogCache.catalogs,
            queue = queue,
            registry = remoteDocumentStatusRegistry,
        ).start(scope = scope)
        LocalCachedDocumentCleaner(
            receivedPdfDir = receivedDir,
            pendingCounts = queue.pendingCounts(),
            catalogsFlow = remoteCatalogCache.catalogs,
            openDocuments = openDocumentRegistry,
            documentIdRegistry = localDocumentIdRegistry,
        ).start(scope = scope)
    }

    private fun wireSession(
        scope: CoroutineScope,
        peerServer: KtorPeerServer,
        syncClient: KtorSyncClient,
        registry: SyncEngineRegistry,
        projection: HostAnnotationProjection,
        registrar: JmDnsServiceRegistrar,
    ) {
        // Demultiplex incoming stroke deltas by documentId — one engine per doc.
        scope.launch {
            peerServer.incomingMessages.collect { peerMessage ->
                val msg = peerMessage.message
                if (msg is NetworkMessage.StrokeDeltaMessage) {
                    registry.get(msg.documentId).processPeer(msg.delta)
                    // Накапливаем штрих в headless-проекции сразу, не дожидаясь
                    // первого SaveRequest — иначе правки, сделанные на планшете до
                    // запроса сохранения, не попадут в сохранённый на хосте файл.
                    projection.ingestPeerDelta(msg.documentId, msg.delta, scope)
                }
            }
        }
        scope.launch {
            syncClient.incomingMessages.collect { hostMessage ->
                val msg = hostMessage.message
                if (msg is NetworkMessage.StrokeDeltaMessage) {
                    registry.get(msg.documentId).processPeer(msg.delta)
                    projection.ingestPeerDelta(msg.documentId, msg.delta, scope)
                }
            }
        }
        // Register/unregister mDNS service as the server lifecycle changes.
        scope.launch {
            peerServer.lifecycle.collect { state ->
                withContext(Dispatchers.IO) {
                    when (state) {
                        is ServerLifecycleState.Running ->
                            registrar.register(selfInfo.copy(host = state.host, port = state.port))
                        is ServerLifecycleState.Idle,
                        is ServerLifecycleState.Stopped,
                        -> registrar.unregister()
                        else -> Unit
                    }
                }
            }
        }
    }

    private suspend fun tearDown(s: SyncSession) {
        withContext(Dispatchers.IO) {
            runCatching { s.peerServer.stop() }
            runCatching { s.serviceRegistrar.unregister() }
            s.sessionJob.cancel()
            runCatching { s.httpClient.close() }
        }
    }
}

private fun createSyncHttpClient(): HttpClient {
    val wsJson = Json { classDiscriminator = "type" }
    return HttpClient(CIO) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(wsJson)
        }
        // Bound the TCP connect/handshake (NOT the long-lived WS session) so
        // a reconnect to a vanished host fails fast and the reconnect deadline is
        // honoured instead of hanging in "reconnecting".
        install(io.ktor.client.plugins.HttpTimeout) {
            connectTimeoutMillis = 4_000L
        }
    }
}

private fun createPendingDeltaQueue(syncDatabasePath: String): SqlDelightPendingDeltaQueue =
    SqlDelightPendingDeltaQueue(
        databaseProvider = { createSyncDatabaseJvm(syncDatabasePath) },
        ioDispatcher = Dispatchers.IO,
    )
