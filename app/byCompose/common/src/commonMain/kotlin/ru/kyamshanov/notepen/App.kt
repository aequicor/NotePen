package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import ru.kyamshanov.notepen.qrconnect.ClientPairingPanel
import ru.kyamshanov.notepen.qrconnect.ClientQrScanViewModel
import ru.kyamshanov.notepen.qrconnect.HostQrPairingPanel
import ru.kyamshanov.notepen.qrconnect.HostQrPairingViewModel
import ru.kyamshanov.notepen.qrconnect.ManualConnectViewModel
import ru.kyamshanov.notepen.sync.domain.SyncEngine
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.ui.glass.GlassSurface

@Suppress("unused")
private val appLogger = KotlinLogging.logger {}

/**
 * Application shell.
 *
 * The pairing UI is now QR-only:
 * - **Desktop** (when [peerServer] + [qrEncoder] are passed): the Sync FAB
 *   opens a dialog with [HostQrPairingPanel] — the only way to invite a peer.
 *   Desktop intentionally does not expose any "dial out" UI.
 * - **Mobile** (when [peerClient] + [selfDeviceInfo] are passed): the FAB
 *   opens a dialog with [ClientPairingPanel] as the primary action plus a
 *   collapsible manual host/port/code form for users who declined the camera
 *   permission.
 */
@Composable
fun App(
    rootComponent: RootComponent,
    pdfDocumentLoader: PdfDocumentLoader,
    pdfPageRenderer: PdfPageRenderer,
    /**
     * Factory that resolves the [SyncEngine] for a given `documentId`.
     * Wired to [ru.kyamshanov.notepen.sync.domain.SyncEngineRegistry] at the
     * application root. Forwarded down to [RootContent] / [DetailsContent].
     */
    syncEngineFor: ((documentId: String) -> SyncEngine)? = null,
    peerServer: PeerServer? = null,
    peerClient: SyncClient? = null,
    /**
     * Stream of `documentId → pendingCount` from the offline buffer.
     * Forwarded to [DetailsContent] for the "Оффлайн, N правок ждут отправки"
     * banner.
     */
    pendingDeltaCounts: Flow<Map<String, Int>>? = null,
    /**
     * Drives [HostQrPairingPanel] on desktop. `null` hides the host pane.
     * Constructed by the platform entry point so commonMain doesn't pull in
     * ZXing or know the device name.
     */
    hostQrViewModel: HostQrPairingViewModel? = null,
    /** Drives [ClientPairingPanel] on mobile. `null` hides the client pane. */
    clientScanViewModel: ClientQrScanViewModel? = null,
    /** Drives manual host/port/code form. `null` hides the manual-connect option. */
    manualConnectViewModel: ManualConnectViewModel? = null,
    receivedPdfDir: String? = null,
    /** Реестр открытых документов; нужен `LocalCachedDocumentCleaner`-у. */
    openDocumentRegistry: ru.kyamshanov.notepen.sync.domain.port.OpenDocumentRegistry? = null,
    /** Реестр `localPath → documentId` для remote-кешированных PDF. */
    localDocumentIdRegistry: ru.kyamshanov.notepen.sync.domain.port.LocalDocumentIdRegistry? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    var showSyncPanel by remember { mutableStateOf(false) }

    val hostPaneEnabled = hostQrViewModel != null
    val clientPaneEnabled = clientScanViewModel != null

    ComposableAppTheme {
        Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                RootContent(
                    component = rootComponent,
                    pdfDocumentLoader = pdfDocumentLoader,
                    pdfPageRenderer = pdfPageRenderer,
                    syncEngineFor = syncEngineFor,
                    peerServer = peerServer,
                    peerClient = peerClient,
                    pendingDeltaCounts = pendingDeltaCounts,
                    receivedPdfDir = receivedPdfDir,
                    openDocumentRegistry = openDocumentRegistry,
                    localDocumentIdRegistry = localDocumentIdRegistry,
                    modifier = Modifier.fillMaxSize(),
                )

                if (hostPaneEnabled || clientPaneEnabled) {
                    val hostPeers = peerServer
                        ?.connectedPeers
                        ?.collectAsState(emptySet())
                        ?.value
                        ?: emptySet()
                    val hostLifecycle = peerServer
                        ?.lifecycle
                        ?.collectAsState(ServerLifecycleState.Idle)
                        ?.value
                    val clientHosts = peerClient
                        ?.connectedHosts
                        ?.collectAsState(emptySet())
                        ?.value
                        ?: emptySet()
                    val clientStates = peerClient
                        ?.pairingStates
                        ?.collectAsState(emptyMap())
                        ?.value
                        ?: emptyMap()
                    val indicator = syncIndicatorColors(hostPeers, hostLifecycle, clientHosts, clientStates)
                    GlassSurface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 88.dp, end = 16.dp),
                        shape = CircleShape,
                        tint = indicator.container,
                    ) {
                        IconButton(onClick = { showSyncPanel = true }) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Синхронизация",
                                tint = indicator.icon,
                            )
                        }
                    }
                }
            }
        }

        if (showSyncPanel && (hostPaneEnabled || clientPaneEnabled)) {
            Dialog(onDismissRequest = { showSyncPanel = false }) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .widthIn(min = 320.dp, max = 480.dp)
                        .heightIn(min = 200.dp, max = 720.dp),
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                    ) {
                        if (hostQrViewModel != null) {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "QR-подключение",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { showSyncPanel = false }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Закрыть",
                                    )
                                }
                            }
                            HostQrPairingPanel(
                                viewModel = hostQrViewModel,
                                onCloseDialog = { showSyncPanel = false },
                            )
                        }
                        if (clientScanViewModel != null && manualConnectViewModel != null && peerClient != null) {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Подключение",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { showSyncPanel = false }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Закрыть",
                                    )
                                }
                            }
                            ClientPairingPanel(
                                scanViewModel = clientScanViewModel,
                                manualViewModel = manualConnectViewModel,
                                peerClient = peerClient,
                                onClose = { showSyncPanel = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class SyncIndicatorColors(val container: Color, val icon: Color)

private val SyncConnectedGreen = Color(0xFF2E7D32)
private val SyncUnstableYellow = Color(0xFFF9A825)

/**
 * Aggregates host- and client-side connection status into a colour pair for
 * the sync FAB.
 *
 * - **Green** — host has at least one connected peer, or client is paired.
 * - **Yellow** — client is [PairingState.Reconnecting]/[PairingState.Error]
 *   or host lifecycle is [ServerLifecycleState.Error] (and no green).
 * - **Gray** (theme default) — idle / pairing / lost / both null.
 */
@Composable
private fun syncIndicatorColors(
    hostPeers: Set<DeviceInfo>,
    hostLifecycle: ServerLifecycleState?,
    clientHosts: Set<DeviceInfo>,
    clientStates: Map<String, PairingState>,
): SyncIndicatorColors {
    val anyConnected = hostPeers.isNotEmpty() || clientHosts.isNotEmpty()
    val hostUnstable = hostLifecycle is ServerLifecycleState.Error
    val anyClientUnstable = clientStates.values.any {
        it is PairingState.Reconnecting || it is PairingState.Error
    }
    return when {
        anyConnected -> SyncIndicatorColors(SyncConnectedGreen, Color.White)
        hostUnstable || anyClientUnstable -> SyncIndicatorColors(SyncUnstableYellow, Color.Black)
        else -> SyncIndicatorColors(
            container = MaterialTheme.colorScheme.secondaryContainer,
            icon = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
