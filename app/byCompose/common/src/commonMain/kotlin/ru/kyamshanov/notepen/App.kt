package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import ru.kyamshanov.notepen.sync.HostScreen
import ru.kyamshanov.notepen.sync.HostViewModel
import ru.kyamshanov.notepen.sync.SyncScreen
import ru.kyamshanov.notepen.sync.SyncViewModel
import ru.kyamshanov.notepen.sync.domain.SyncEngine
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

@Suppress("unused")
private val appLogger = KotlinLogging.logger {}

@Composable
fun App(
    rootComponent: RootComponent,
    pdfDocumentLoader: PdfDocumentLoader,
    pdfPageRenderer: PdfPageRenderer,
    hostViewModel: HostViewModel? = null,
    syncViewModel: SyncViewModel? = null,
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
    @Suppress("UNUSED_PARAMETER")
    receivedPdfDir: String? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    var showSyncPanel by remember { mutableStateOf(false) }

    // Phase 3: removed the auto-navigate-on-receive loop. Document opens are
    // now driven by [ru.kyamshanov.notepen.sync.domain.RemoteDocumentOpener]
    // from the Remote section of the main screen, which delivers the path
    // straight into [MainScreenViewModel]'s navigation target.

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
                    modifier = Modifier.fillMaxSize(),
                )

                if (hostViewModel != null || syncViewModel != null) {
                    val hostState = hostViewModel?.serverState?.collectAsState()?.value
                    val clientState = syncViewModel?.connectionState?.collectAsState()?.value
                    val indicator = syncIndicatorColors(hostState, clientState)
                    FloatingActionButton(
                        onClick = { showSyncPanel = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 88.dp, end = 16.dp),
                        containerColor = indicator.container,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Синхронизация",
                            tint = indicator.icon,
                        )
                    }
                }
            }
        }

        if (showSyncPanel && (hostViewModel != null || syncViewModel != null)) {
            Dialog(onDismissRequest = { showSyncPanel = false }) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .widthIn(min = 320.dp, max = 480.dp)
                        .heightIn(min = 200.dp, max = 640.dp),
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                    ) {
                        if (hostViewModel != null) {
                            HostScreen(viewModel = hostViewModel)
                            if (syncViewModel != null) HorizontalDivider()
                        }
                        if (syncViewModel != null) {
                            SyncScreen(
                                viewModel = syncViewModel,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
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
 * Aggregates host- and client-side [PairingState] into a colour pair for the
 * sync FAB.
 *
 * - **Green** — any side reports [PairingState.Connected].
 * - **Yellow** — any side reports [PairingState.Reconnecting] or
 *   [PairingState.Error] (and no green).
 * - **Gray** (theme default) — idle / pairing / lost / both null.
 */
@Composable
private fun syncIndicatorColors(
    host: PairingState?,
    client: PairingState?,
): SyncIndicatorColors {
    val states = listOfNotNull(host, client)
    val anyConnected = states.any { it is PairingState.Connected }
    val anyUnstable = states.any { it is PairingState.Reconnecting || it is PairingState.Error }
    return when {
        anyConnected -> SyncIndicatorColors(SyncConnectedGreen, Color.White)
        anyUnstable -> SyncIndicatorColors(SyncUnstableYellow, Color.Black)
        else -> SyncIndicatorColors(
            container = MaterialTheme.colorScheme.secondaryContainer,
            icon = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
