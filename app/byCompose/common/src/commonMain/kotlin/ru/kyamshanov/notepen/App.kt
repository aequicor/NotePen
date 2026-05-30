package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import ru.kyamshanov.notepen.appsettings.rememberAppSettings
import ru.kyamshanov.notepen.book.DocumentOutlineProvider
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import ru.kyamshanov.notepen.qrconnect.ClientQrScanViewModel
import ru.kyamshanov.notepen.qrconnect.HostDiscoveryViewModel
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
     * Поставщик оглавления документа для сайдбара «Содержание». Платформенные
     * конвертеры книг ([ru.kyamshanov.notepen.book.JvmEbookToPdfConverter] /
     * `AndroidEbookToPdfConverter`) реализуют его и отдают главы EPUB/FB2;
     * для обычных PDF возвращается пустой список.
     */
    outlineProvider: DocumentOutlineProvider,
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
    /** Drives mDNS LAN-discovery list. `null` hides the «Найти ПК в сети» option. */
    hostDiscoveryViewModel: HostDiscoveryViewModel? = null,
    receivedPdfDir: String? = null,
    /** Реестр открытых документов; нужен `LocalCachedDocumentCleaner`-у. */
    openDocumentRegistry: ru.kyamshanov.notepen.sync.domain.port.OpenDocumentRegistry? = null,
    /** Контроллер живой синхронизации документа (M4); `null` — sync-стек не поднят. */
    liveSyncController: ru.kyamshanov.notepen.sync.domain.LiveDocumentSyncController? = null,
    /** Реестр `localPath → documentId` для remote-кешированных PDF. */
    localDocumentIdRegistry: ru.kyamshanov.notepen.sync.domain.port.LocalDocumentIdRegistry? = null,
    /**
     * Content-addressed identity provider. Warms the canonical
     * `<basename>#<sha256-prefix>` wire id for open documents so the editor
     * advertises the same id a peer computes for the same bytes. `null` keeps
     * the legacy path-derived id (sync still works locally but won't match peers).
     */
    documentIdentityProvider: ru.kyamshanov.notepen.document.domain.port.DocumentIdentityProvider? = null,
    /** Host-side провайдер накопленных проекцией штрихов; см. [RootContent]. */
    hostAnnotationSnapshotFor: (suspend (documentId: String) -> List<StrokeDelta.Added>)? = null,
    /** Редактор публикует сюда открытые вкладки — хост раздаёт их пирам как «открыто на устройстве». */
    openDocumentsSink: ((List<ru.kyamshanov.notepen.sync.domain.model.OpenDocumentInfo>) -> Unit)? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val appSettings = rememberAppSettings()
    // Глобальный always-on-display: пока [App] в композиции, экран не гаснет.
    // На десктопе actual — no-op (см. [KeepScreenOn]).
    KeepScreenOn(appSettings.alwaysOnDisplay)
    ComposableAppTheme {
        Surface {
            RootContent(
                component = rootComponent,
                pdfDocumentLoader = pdfDocumentLoader,
                pdfPageRenderer = pdfPageRenderer,
                outlineProvider = outlineProvider,
                syncEngineFor = syncEngineFor,
                peerServer = peerServer,
                peerClient = peerClient,
                pendingDeltaCounts = pendingDeltaCounts,
                hostQrViewModel = hostQrViewModel,
                clientScanViewModel = clientScanViewModel,
                manualConnectViewModel = manualConnectViewModel,
                hostDiscoveryViewModel = hostDiscoveryViewModel,
                receivedPdfDir = receivedPdfDir,
                openDocumentRegistry = openDocumentRegistry,
                liveSyncController = liveSyncController,
                localDocumentIdRegistry = localDocumentIdRegistry,
                documentIdentityProvider = documentIdentityProvider,
                hostAnnotationSnapshotFor = hostAnnotationSnapshotFor,
                openDocumentsSink = openDocumentsSink,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
