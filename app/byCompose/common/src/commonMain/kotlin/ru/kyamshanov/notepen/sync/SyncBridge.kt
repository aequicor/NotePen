package ru.kyamshanov.notepen.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.PdfDrawingState
import ru.kyamshanov.notepen.sync.domain.SyncEngine
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.model.toDomain

/**
 * Subscribes to [SyncEngine.mergedDeltas] and applies each incoming
 * [StrokeDelta] to the corresponding [PdfDrawingState].
 *
 * Call [start] once after the engine and drawing states are ready.
 * Cancellation of [scope] stops the bridge.
 */
class SyncBridge(
    private val engine: SyncEngine,
    private val drawingStates: MutableMap<Int, PdfDrawingState>,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            engine.mergedDeltas.collect { delta ->
                val state = drawingStates.getOrPut(delta.pageIndex) { PdfDrawingState() }
                when (delta) {
                    is StrokeDelta.Added -> {
                        if (state.currentPaths.none { it.strokeId == delta.strokeId }) {
                            state.currentPaths.add(delta.path.toDomain())
                            state.markHistoryChanged()
                        }
                    }
                    is StrokeDelta.Removed -> {
                        val before = state.currentPaths.size
                        state.currentPaths.removeAll { it.strokeId == delta.strokeId }
                        if (state.currentPaths.size != before) state.markHistoryChanged()
                    }
                }
            }
        }
    }
}
