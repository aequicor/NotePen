package ru.kyamshanov.notepen.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.port.PeerServer

/**
 * Drives [HostScreen]: starts/stops the [PeerServer] and exposes its state.
 */
class HostViewModel(
    private val server: PeerServer,
    private val scope: CoroutineScope,
) {
    val serverState: StateFlow<PairingState> = server.state
        .stateIn(scope, SharingStarted.Eagerly, PairingState.Idle)

    fun startServer() {
        scope.launch { server.start() }
    }

    fun stopServer() {
        scope.launch { server.stop() }
    }
}
