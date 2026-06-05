package ru.kyamshanov.notepen.sync.domain.projection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.drawing.api.ToolMode
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage

internal const val PROJECTION_FRAME_INTERVAL_MS = 34L

/**
 * Bidirectional projection state for one NotePen runtime.
 *
 * The active device publishes [NetworkMessage.ProjectionFrame] snapshots; passive
 * viewers collect [currentFrame] and apply only frames for the document they have
 * open. Stroke add/remove sync remains in [ru.kyamshanov.notepen.sync.domain.SyncEngine].
 */
class DocumentBroadcastController(
    incomingMessages: Flow<NetworkMessage>,
    private val sendFrame: suspend (NetworkMessage.ProjectionFrame) -> Unit,
    private val scope: CoroutineScope,
) {
    private val _pendingFrame = MutableStateFlow<NetworkMessage.ProjectionFrame?>(null)
    private val _currentFrame = MutableStateFlow<NetworkMessage.ProjectionFrame?>(null)
    private val jobs: List<Job>

    /** Latest frame received from a peer. */
    val currentFrame: StateFlow<NetworkMessage.ProjectionFrame?> = _currentFrame.asStateFlow()

    init {
        jobs =
            listOf(
                scope.launch {
                    _pendingFrame
                        .filterNotNull()
                        .conflate()
                        .collect { frame ->
                            try {
                                sendFrame(frame)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Throwable) {
                                // A transient transport failure must not permanently stop
                                // viewport/tool broadcasting for the document.
                            }
                            delay(PROJECTION_FRAME_INTERVAL_MS)
                        }
                },
                scope.launch {
                    incomingMessages.collect { message ->
                        if (message is NetworkMessage.ProjectionFrame && message.documentId.isNotBlank()) {
                            _currentFrame.value = message.sanitized()
                        }
                    }
                },
            )
    }

    fun updateFrame(
        documentId: String,
        page: Int,
        viewportOffsetX: Float,
        viewportOffsetY: Float,
        viewportScale: Float,
        viewportCenterX: Float? = null,
        viewportCenterY: Float? = null,
        toolMode: ToolMode,
    ) {
        if (documentId.isBlank()) return
        val current = _pendingFrame.value?.takeIf { it.documentId == documentId }
        _pendingFrame.value =
            NetworkMessage.ProjectionFrame(
                documentId = documentId,
                page = page.coerceAtLeast(0),
                viewportOffsetX = viewportOffsetX.finiteOrZero(),
                viewportOffsetY = viewportOffsetY.finiteOrZero(),
                viewportScale = viewportScale.positiveFiniteOrOne(),
                viewportCenterX = viewportCenterX?.normalizedPointer(),
                viewportCenterY = viewportCenterY?.finiteNonNegative(),
                pointerX = current?.pointerX,
                pointerY = current?.pointerY,
                toolMode = toolMode,
            )
    }

    fun updatePointer(
        documentId: String,
        page: Int,
        pointerX: Float,
        pointerY: Float,
    ) {
        if (documentId.isBlank()) return
        val current = _pendingFrame.value?.takeIf { it.documentId == documentId }
        _pendingFrame.value =
            (
                current
                    ?: NetworkMessage.ProjectionFrame(
                        documentId = documentId,
                        page = page.coerceAtLeast(0),
                        viewportOffsetY = 0f,
                        viewportScale = 1f,
                    )
            )
                .copy(
                    documentId = documentId,
                    page = page.coerceAtLeast(0),
                    pointerX = pointerX.normalizedPointer(),
                    pointerY = pointerY.normalizedPointer(),
                )
    }

    fun clearPointer() {
        _pendingFrame.value = _pendingFrame.value?.copy(pointerX = null, pointerY = null)
    }

    fun clearCurrentFrame() {
        _currentFrame.value = null
    }

    fun close() {
        jobs.forEach { it.cancel() }
    }
}

private fun NetworkMessage.ProjectionFrame.sanitized(): NetworkMessage.ProjectionFrame =
    copy(
        page = page.coerceAtLeast(0),
        viewportOffsetX = viewportOffsetX.finiteOrZero(),
        viewportOffsetY = viewportOffsetY.finiteOrZero(),
        viewportScale = viewportScale.positiveFiniteOrOne(),
        viewportCenterX = viewportCenterX?.normalizedPointer(),
        viewportCenterY = viewportCenterY?.finiteNonNegative(),
        pointerX = pointerX?.normalizedPointer(),
        pointerY = pointerY?.normalizedPointer(),
    )

private fun Float.finiteOrZero(): Float = takeIf { it.isFinite() } ?: 0f

private fun Float.positiveFiniteOrOne(): Float = takeIf { it.isFinite() && it > 0f } ?: 1f

private fun Float.normalizedPointer(): Float? = takeIf { it.isFinite() }?.coerceIn(0f, 1f)

private fun Float.finiteNonNegative(): Float? = takeIf { it.isFinite() }?.coerceAtLeast(0f)
