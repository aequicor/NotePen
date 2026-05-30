package ru.kyamshanov.notepen.qrconnect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.DiscoveredHost
import ru.kyamshanov.notepen.sync.domain.port.PeerDiscovery
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

/**
 * Drives the «Найти ПК в сети» (LAN discovery) screen on the client.
 *
 * Browses for NotePen hosts via [PeerDiscovery] and connects to a tapped host
 * using the pairing code carried in its mDNS TXT record — no QR scan needed.
 * Start/stop of browsing is bound to the screen's lifecycle by the UI.
 */
class HostDiscoveryViewModel(
    private val discovery: PeerDiscovery,
    private val syncClient: SyncClient,
    private val selfInfo: DeviceInfo,
    private val scope: CoroutineScope,
) {
    /** Outcome of the latest discovered-host connect attempt. */
    sealed class Status {
        data object Idle : Status()

        data class Connecting(
            val hostId: String,
        ) : Status()

        data class Connected(
            val peer: DeviceInfo,
        ) : Status()

        data class Failed(
            val message: String,
        ) : Status()

        /** The host advertised no code — the UI should route to manual entry. */
        data object NeedsManualCode : Status()
    }

    /** Discovered hosts, sorted by name for a stable list. */
    val hosts: StateFlow<List<DiscoveredHost>> =
        discovery.discoveredHosts
            .map { set -> set.sortedBy { it.deviceInfo.name.lowercase() } }
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var connectJob: Job? = null

    fun startDiscovery() = discovery.start()

    fun stopDiscovery() = discovery.stop()

    fun connect(host: DiscoveredHost) {
        if (host.code.isBlank()) {
            _status.value = Status.NeedsManualCode
            return
        }
        connectJob?.cancel()
        _status.value = Status.Connecting(host.deviceInfo.id)
        connectJob =
            scope.launch {
                val result =
                    runCatching { syncClient.connect(host.deviceInfo, host.code, selfInfo) }
                        .getOrElse { Result.failure(it) }
                _status.value =
                    result.fold(
                        onSuccess = { Status.Connected(it) },
                        onFailure = { e -> Status.Failed(e.message ?: "Не удалось подключиться") },
                    )
            }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
