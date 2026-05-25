package ru.kyamshanov.notepen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.flow.Flow
import ru.kyamshanov.notepen.book.DocumentOutlineProvider
import ru.kyamshanov.notepen.mainscreen.ui.folder.FolderContent
import ru.kyamshanov.notepen.mainscreen.ui.folder.FolderContentsComponentImpl
import ru.kyamshanov.notepen.mainscreen.ui.model.NavigationTarget
import ru.kyamshanov.notepen.mainscreen.ui.peer.PeerCatalogComponentImpl
import ru.kyamshanov.notepen.mainscreen.ui.peer.PeerCatalogContent
import ru.kyamshanov.notepen.mainscreen.ui.screen.MainContent
import ru.kyamshanov.notepen.mainscreen.ui.screen.MainScreenComponent
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import ru.kyamshanov.notepen.qrconnect.ClientQrScanViewModel
import ru.kyamshanov.notepen.qrconnect.HostQrPairingViewModel
import ru.kyamshanov.notepen.qrconnect.ManualConnectViewModel
import ru.kyamshanov.notepen.sync.domain.SyncEngine
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

@Composable
fun RootContent(
    component: RootComponent,
    pdfDocumentLoader: PdfDocumentLoader,
    pdfPageRenderer: PdfPageRenderer,
    /** Forwarded to [DetailsContent] to populate the TOC sidebar with chapters. */
    outlineProvider: DocumentOutlineProvider,
    /**
     * Factory that resolves the [SyncEngine] for a given `documentId`.
     * Wired to [ru.kyamshanov.notepen.sync.domain.SyncEngineRegistry] at the
     * application root. Forwarded to [DetailsContent] for per-document scoping.
     */
    syncEngineFor: ((documentId: String) -> SyncEngine)? = null,
    peerServer: PeerServer? = null,
    peerClient: SyncClient? = null,
    /** Forwarded to [DetailsContent] for the offline-pending banner. */
    pendingDeltaCounts: Flow<Map<String, Int>>? = null,
    /** Forwarded to [DetailsContent] to drive the host QR pane of the sync dialog. */
    hostQrViewModel: HostQrPairingViewModel? = null,
    /** Forwarded to [DetailsContent] to drive the client scan pane of the sync dialog. */
    clientScanViewModel: ClientQrScanViewModel? = null,
    /** Forwarded to [DetailsContent] to drive the manual-connect form of the sync dialog. */
    manualConnectViewModel: ManualConnectViewModel? = null,
    /** Forwarded to [DetailsContent] so it can detect remote-opened PDFs. */
    receivedPdfDir: String? = null,
    /** Forwarded to [DetailsContent] for open/close tracking. */
    openDocumentRegistry: ru.kyamshanov.notepen.sync.domain.port.OpenDocumentRegistry? = null,
    /** Forwarded to [DetailsContent] for remote-cached documentId lookup. */
    localDocumentIdRegistry: ru.kyamshanov.notepen.sync.domain.port.LocalDocumentIdRegistry? = null,
    /** Forwarded to [DetailsContent] to seed host-side projection strokes on local open. */
    hostAnnotationSnapshotFor: (suspend (documentId: String) -> List<StrokeDelta.Added>)? = null,
    modifier: Modifier = Modifier,
) {
    val childStack by component.stack.subscribeAsState()
    // Библиотека, открытая поверх документа (кнопкой «+»), не является корнем
    // стека — тогда показываем кнопку «назад» для возврата к документу.
    val libraryHasBack = childStack.backStack.isNotEmpty()
    Children(
        stack = component.stack,
        modifier = modifier,
        animation = stackAnimation(fade()),
    ) {
        when (val child = it.instance) {
            is RootComponent.Child.MainChild -> {
                // Safe cast: DefaultRootComponent always creates MainScreenComponent for MainChild.
                // RootContent lives in :common and may safely reference MainScreenComponent.
                val mainScreenComponent =
                    child.component as? MainScreenComponent
                        ?: error("MainChild.component must be MainScreenComponent — check DefaultRootComponent factory")
                val state by mainScreenComponent.viewModel.state.collectAsState()

                LaunchedEffect(state.navigationTarget) {
                    when (val target = state.navigationTarget) {
                        is NavigationTarget.Editor -> {
                            mainScreenComponent.onOpenEditor(target.uri, target.lastPageIndex)
                            mainScreenComponent.viewModel.onNavigationHandled()
                        }
                        NavigationTarget.FilePicker -> {
                            val pickedPath = mainScreenComponent.onOpenFilePicker()
                            mainScreenComponent.viewModel.onIntent(
                                ru.kyamshanov.notepen.mainscreen.ui.MainScreenIntent.FilePickerResult(
                                    uri = pickedPath,
                                    displayName =
                                        pickedPath
                                            ?.let { resolveDocumentDisplayName(it) }
                                            ?: "",
                                    fileSize = null,
                                ),
                            )
                        }
                        is NavigationTarget.PeerCatalog -> {
                            mainScreenComponent.onOpenPeerCatalog(target.peerId, target.displayName)
                            mainScreenComponent.viewModel.onNavigationHandled()
                        }
                        is NavigationTarget.Folder -> {
                            mainScreenComponent.onOpenFolder(target.folderId, target.folderName)
                            mainScreenComponent.viewModel.onNavigationHandled()
                        }
                        null -> {}
                    }
                }

                MainContent(
                    state = state,
                    onIntent = { mainScreenComponent.viewModel.onIntent(it) },
                    onBack =
                        if (libraryHasBack) {
                            { component.onBackClicked(toIndex = childStack.backStack.size - 1) }
                        } else {
                            null
                        },
                    hostQrViewModel = hostQrViewModel,
                    clientScanViewModel = clientScanViewModel,
                    manualConnectViewModel = manualConnectViewModel,
                    peerServer = peerServer,
                    peerClient = peerClient,
                    modifier = modifier,
                )
            }
            is RootComponent.Child.DetailsChild ->
                DetailsContent(
                    component = child.component,
                    loader = pdfDocumentLoader,
                    renderer = pdfPageRenderer,
                    outlineProvider = outlineProvider,
                    syncEngineFor = syncEngineFor,
                    peerServer = peerServer,
                    peerClient = peerClient,
                    pendingDeltaCounts = pendingDeltaCounts,
                    hostQrViewModel = hostQrViewModel,
                    clientScanViewModel = clientScanViewModel,
                    manualConnectViewModel = manualConnectViewModel,
                    receivedPdfDir = receivedPdfDir,
                    openDocumentRegistry = openDocumentRegistry,
                    localDocumentIdRegistry = localDocumentIdRegistry,
                    hostAnnotationSnapshotFor = hostAnnotationSnapshotFor,
                    modifier = modifier,
                )
            is RootComponent.Child.PeerCatalogChild -> {
                val impl =
                    child.component as? PeerCatalogComponentImpl
                        ?: error("PeerCatalogChild.component must be PeerCatalogComponentImpl — check DefaultRootComponent factory")
                PeerCatalogContent(component = impl, modifier = modifier)
            }
            is RootComponent.Child.FolderContentsChild -> {
                val impl =
                    child.component as? FolderContentsComponentImpl
                        ?: error("FolderContentsChild.component must be FolderContentsComponentImpl — check DefaultRootComponent factory")
                FolderContent(component = impl, modifier = modifier)
            }
        }
    }
}
