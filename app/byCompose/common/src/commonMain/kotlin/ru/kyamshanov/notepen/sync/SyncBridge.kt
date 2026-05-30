package ru.kyamshanov.notepen.sync

import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.PdfDrawingState
import ru.kyamshanov.notepen.annotation.domain.model.PageNote
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
    private val notes: SnapshotStateMap<Int, List<PageNote>>,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            engine.mergedDeltas.collect { delta ->
                when (delta) {
                    is StrokeDelta.Added -> {
                        val state = drawingStates.getOrPut(delta.pageIndex) { PdfDrawingState() }
                        delta.pageExtent?.let { ext ->
                            state.setExtent(state.extent.value.union(ext.toDomain()))
                        }
                        if (state.currentPaths.none { it.strokeId == delta.strokeId }) {
                            state.currentPaths.add(delta.path.toDomain())
                            state.markHistoryChanged()
                        }
                    }
                    is StrokeDelta.Removed -> {
                        val state = drawingStates.getOrPut(delta.pageIndex) { PdfDrawingState() }
                        val before = state.currentPaths.size
                        state.currentPaths.removeAll { it.strokeId == delta.strokeId }
                        if (state.currentPaths.size != before) state.markHistoryChanged()
                    }
                    is StrokeDelta.NoteUpserted -> {
                        // LWW already enforced upstream in processPeer; replace-by-id (local
                        // echo / edit) or append (new note) keeps this idempotent.
                        val incoming = delta.note.toDomain()
                        val page = delta.pageIndex
                        val existing = notes[page].orEmpty()
                        notes[page] =
                            if (existing.any { it.noteId == incoming.noteId }) {
                                existing.map { if (it.noteId == incoming.noteId) incoming else it }
                            } else {
                                existing + incoming
                            }
                    }
                    is StrokeDelta.NoteRemoved -> {
                        val page = delta.pageIndex
                        val before = notes[page].orEmpty()
                        val after = before.filterNot { it.noteId == delta.strokeId }
                        if (after.size != before.size) notes[page] = after
                    }
                }
            }
        }
    }
}
