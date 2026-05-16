package ru.kyamshanov.notepen.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.port.DeviceDiscovery
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

/**
 * Presentation state machine for the client-side pairing flow.
 *
 * Drives [SyncScreen]: discovers peers via [discovery], lets the user
 * pick one, enter a code, and connects via [client].
 */
class SyncViewModel(
    private val discovery: DeviceDiscovery,
    private val client: SyncClient,
    private val selfInfo: DeviceInfo,
    private val scope: CoroutineScope,
) {
    private val _peers = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val peers: StateFlow<List<DeviceInfo>> = _peers.asStateFlow()

    val connectionState: StateFlow<PairingState> get() = client.state as StateFlow<PairingState>

    fun startDiscovery() {
        scope.launch {
            discovery.peers.collect { found ->
                _peers.value = found.filter { it.id != selfInfo.id && it.name != selfInfo.name }
            }
        }
    }

    fun connect(server: DeviceInfo, code: String) {
        scope.launch {
            client.connect(server = server, pairingCode = code, selfInfo = selfInfo)
        }
    }

    fun disconnect() {
        scope.launch { client.disconnect() }
    }
}
