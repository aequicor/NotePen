package ru.kyamshanov.notepen.sync.domain.projection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.PeerServer

/**
 * Host side of projection mode.
 *
 * Call [updateViewport] on every scroll/zoom event and [updatePointer] on
 * every pointer move. Frames are conflated to ≤30 fps before sending.
 *
 * @param server active, paired [PeerServer]
 * @param scope tied to the host device's lifecycle
 */
class ProjectionHostController(
    private val server: PeerServer,
    private val scope: CoroutineScope,
) {
    private val _pendingFrame = MutableStateFlow<NetworkMessage.ProjectionFrame?>(null)

    init {
        scope.launch {
            _pendingFrame
                .filterNotNull()
                .conflate()
                .collect { frame -> server.broadcast(frame) }
        }
    }

    /** Emit a new viewport snapshot. Thread-safe. */
    fun updateViewport(
        page: Int,
        viewportOffsetY: Float,
        viewportScale: Float,
    ) {
        val current = _pendingFrame.value
        _pendingFrame.value =
            NetworkMessage.ProjectionFrame(
                page = page,
                viewportOffsetY = viewportOffsetY,
                viewportScale = viewportScale,
                pointerX = current?.pointerX,
                pointerY = current?.pointerY,
            )
    }

    /** Emit a new pointer position (normalised to current page). Thread-safe. */
    fun updatePointer(
        page: Int,
        pointerX: Float,
        pointerY: Float,
    ) {
        val current = _pendingFrame.value
        _pendingFrame.value =
            (current ?: NetworkMessage.ProjectionFrame(page, 0f, 1f))
                .copy(pointerX = pointerX, pointerY = pointerY)
    }

    fun clearPointer() {
        _pendingFrame.value = _pendingFrame.value?.copy(pointerX = null, pointerY = null)
    }
}
