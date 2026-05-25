package ru.kyamshanov.notepen.qrconnect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.qrconnect.application.ClientQrPairingCoordinator
import ru.kyamshanov.notepen.qrconnect.domain.port.QrScanner
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

/**
 * Drives [ClientQrScanPanel].
 *
 * The scanner is platform-specific (it owns the camera) and must be built by
 * the view once a [androidx.camera.view.PreviewView]-equivalent is mounted —
 * the view passes it via [start]. The VM owns the resulting
 * [ClientQrPairingCoordinator] run and exposes its state as [state].
 */
class ClientQrScanViewModel(
    private val syncClient: SyncClient,
    private val selfInfo: DeviceInfo,
    private val scope: CoroutineScope,
) {
    private val _state =
        MutableStateFlow<ClientQrPairingCoordinator.State>(
            ClientQrPairingCoordinator.State.Scanning,
        )
    val state: StateFlow<ClientQrPairingCoordinator.State> = _state.asStateFlow()

    private var runJob: Job? = null

    /**
     * Starts a new scan + connect cycle using [scanner]. Cancels any
     * previously started cycle. The returned [Job] completes when the flow
     * terminates (Connected or Failed).
     */
    fun start(scanner: QrScanner): Job {
        runJob?.cancel()
        _state.value = ClientQrPairingCoordinator.State.Scanning
        val coordinator =
            ClientQrPairingCoordinator(
                syncClient = syncClient,
                scanner = scanner,
                selfInfo = selfInfo,
            )
        val job =
            scope.launch {
                coordinator.run().collect { s -> _state.value = s }
            }
        runJob = job
        return job
    }

    /** Aborts the current cycle; UI should call this when leaving the screen. */
    fun stop() {
        runJob?.cancel()
        runJob = null
    }
}
