package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import ru.kyamshanov.notepen.qrconnect.ClientQrScanViewModel
import ru.kyamshanov.notepen.qrconnect.HostQrPairingViewModel
import ru.kyamshanov.notepen.qrconnect.ManualConnectViewModel
import ru.kyamshanov.notepen.sync.domain.SyncEngine
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

@Suppress("unused")
private val appLogger = KotlinLogging.logger {}

/**
 * Application shell.
 *
 * The pairing UI is now QR-only and lives both on the main screen's top bar
 * (see [ru.kyamshanov.notepen.qrconnect.SyncPairingButton]) and in the editor's
 * quick-actions menu (see [DetailsContent]); the sync button opens a dialog with
 * [ru.kyamshanov.notepen.qrconnect.HostQrPairingPanel] on desktop or
 * [ru.kyamshanov.notepen.qrconnect.ClientPairingPanel] on mobile. The required
 * view models are forwarded down through [RootContent].
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
    /** Host-side провайдер накопленных проекцией штрихов; см. [RootContent]. */
    hostAnnotationSnapshotFor: (suspend (documentId: String) -> List<StrokeDelta.Added>)? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    ComposableAppTheme {
        Surface {
            RootContent(
                component = rootComponent,
                pdfDocumentLoader = pdfDocumentLoader,
                pdfPageRenderer = pdfPageRenderer,
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
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
