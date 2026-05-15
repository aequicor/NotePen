package ru.kyamshanov.notepen.sync.domain.projection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

/**
 * State machine for the viewer side of projection mode.
 *
 * **Follow** mode: applies incoming [NetworkMessage.ProjectionFrame]s to
 * [currentFrame] so the presentation layer can drive scroll/zoom.
 *
 * **Free** mode: ignores frames, lets the user scroll independently.
 * The viewer can re-attach by calling [attach].
 */
class ProjectionViewerController(
    private val client: SyncClient,
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
            client.incomingMessages.collect { msg ->
                if (msg is NetworkMessage.ProjectionFrame && _following.value) {
                    _currentFrame.value = msg
                }
            }
        }
    }

    /** Detach from host viewport — enter free-scroll mode. */
    fun detach() {
        _following.value = false
        scope.launch { client.send(NetworkMessage.ProjectionDetach) }
    }

    /** Re-attach to host viewport — resume following. */
    fun attach() {
        _following.value = true
        scope.launch { client.send(NetworkMessage.ProjectionAttach) }
    }
}
