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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import ru.kyamshanov.notepen.sync.HostScreen
import ru.kyamshanov.notepen.sync.HostViewModel
import ru.kyamshanov.notepen.sync.SyncScreen
import ru.kyamshanov.notepen.sync.SyncViewModel
import ru.kyamshanov.notepen.sync.domain.SyncEngine
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.sync.infrastructure.FileTransferReceiver

private val appLogger = KotlinLogging.logger {}

@Composable
fun App(
    rootComponent: RootComponent,
    pdfDocumentLoader: PdfDocumentLoader,
    pdfPageRenderer: PdfPageRenderer,
    hostViewModel: HostViewModel? = null,
    syncViewModel: SyncViewModel? = null,
    syncEngine: SyncEngine? = null,
    peerServer: PeerServer? = null,
    peerClient: SyncClient? = null,
    receivedPdfDir: String? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    var showSyncPanel by remember { mutableStateOf(false) }

    // Tablet (client) side: when a PDF arrives over the WebSocket, navigate
    // the editor to it. One-shot per pairing — receiver suspends until the
    // first full transfer is assembled, then loops back to wait for another.
    if (peerClient != null && receivedPdfDir != null) {
        LaunchedEffect(peerClient, receivedPdfDir) {
            while (currentCoroutineContext().isActive) {
                try {
                    val received = FileTransferReceiver(
                        incoming = peerClient.incomingMessages,
                        destDir = receivedPdfDir,
                    ).awaitFile()
                    rootComponent.openDetailsExternally(received.destPath)
                    showSyncPanel = false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    appLogger.warn { "Tablet PDF receiver: ${e::class.simpleName}: ${e.message}" }
                }
            }
        }
    }

    ComposableAppTheme {
        Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                RootContent(
                    component = rootComponent,
                    pdfDocumentLoader = pdfDocumentLoader,
                    pdfPageRenderer = pdfPageRenderer,
                    syncEngine = syncEngine,
                    peerServer = peerServer,
                    peerClient = peerClient,
                    modifier = Modifier.fillMaxSize(),
                )

                if (hostViewModel != null || syncViewModel != null) {
                    FloatingActionButton(
                        onClick = { showSyncPanel = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 88.dp, end = 16.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Синхронизация",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
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
