package ru.kyamshanov.notepen.sync.domain.projection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

/**
 * State machine for the viewer side of projection mode bound to a single
 * `hostId`. Multi-host clients spin up one controller per host that drives
 * the local viewport.
 *
 * **Follow** mode: applies incoming [NetworkMessage.ProjectionFrame]s from
 * the bound host to [currentFrame] so the presentation layer can drive
 * scroll/zoom.
 *
 * **Free** mode: ignores frames, lets the user scroll independently.
 */
class ProjectionViewerController(
    private val client: SyncClient,
    private val hostId: String,
    private val scope: CoroutineScope,
) {
    private val _following = MutableStateFlow(true)

    /** True while the viewer is following the host's viewport. */
    val following: StateFlow<Boolean> = _following.asStateFlow()

    private val _currentFrame = MutableStateFlow<NetworkMessage.ProjectionFrame?>(null)

    /** Latest projection frame from the host; null if none received yet. */
    val currentFrame: StateFlow<NetworkMessage.ProjectionFrame?> = _currentFrame.asStateFlow()

    init {
        scope.launch {
            client.incomingMessages.collect { hostMessage ->
                if (hostMessage.host.id != hostId) return@collect
                val msg = hostMessage.message
                if (msg is NetworkMessage.ProjectionFrame && _following.value) {
                    _currentFrame.value = msg
                }
            }
        }
    }

    /** Detach from host viewport — enter free-scroll mode. */
    fun detach() {
        _following.value = false
        scope.launch { client.send(hostId, NetworkMessage.ProjectionDetach) }
    }

    /** Re-attach to host viewport — resume following. */
    fun attach() {
        _following.value = true
        scope.launch { client.send(hostId, NetworkMessage.ProjectionAttach) }
    }
}
