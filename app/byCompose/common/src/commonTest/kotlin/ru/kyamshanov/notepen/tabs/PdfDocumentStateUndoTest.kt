package ru.kyamshanov.notepen.tabs

import ru.kyamshanov.notepen.PdfDrawingState
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.StickyHighlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies the touch-reachable undo engine surfaced on [PdfDocumentState]
 * (Defect A): [PdfDocumentState.undo] / [PdfDocumentState.redo] pop their
 * stacks, restore the page snapshot (strokes + highlights), and keep the
 * Compose-derived [PdfDocumentState.canUndo] / [PdfDocumentState.canRedo]
 * enabled flags in sync.
 */
class PdfDocumentStateUndoTest {
    private fun path(id: Float) = DrawingPath(points = listOf(DrawingPoint(id, id, true), DrawingPoint(id + 1f, id + 1f)))

    private fun newState() = PdfDocumentState.create(filePath = "/tmp/doc.pdf", documentId = "doc")

    private fun stateWithStrokeOnPage0(): PdfDocumentState {
        val st = newState()
        val drawing = PdfDrawingState()
        st.drawingStates[0] = drawing
        // Gesture: snapshot the empty page, then draw.
        st.pushUndoSnapshot(0, drawing.currentPaths.toList())
        drawing.currentPaths.add(path(1f))
        return st
    }

    @Test
    fun `undo pops stack and restores empty snapshot`() {
        val st = stateWithStrokeOnPage0()
        assertTrue(st.canUndo)
        assertFalse(st.canRedo)

        st.undo()

        assertTrue(st.drawingStates[0]!!.currentPaths.isEmpty())
        assertFalse(st.canUndo)
        assertTrue(st.canRedo)
    }

    @Test
    fun `undo then redo restores the stroke`() {
        val st = stateWithStrokeOnPage0()

        st.undo()
        st.redo()

        assertEquals(listOf(path(1f)), st.drawingStates[0]!!.currentPaths.toList())
        assertTrue(st.canUndo)
        assertFalse(st.canRedo)
    }

    @Test
    fun `undo is a no-op when stack is empty`() {
        val st = newState()
        assertFalse(st.canUndo)
        // Should not throw.
        st.undo()
        assertFalse(st.canUndo)
    }

    @Test
    fun `pushUndoSnapshot clears redo stack`() {
        val st = stateWithStrokeOnPage0()
        st.undo()
        assertTrue(st.canRedo)

        // A new gesture must drop the redo history.
        st.pushUndoSnapshot(0, st.drawingStates[0]!!.currentPaths.toList())
        st.drawingStates[0]!!.currentPaths.add(path(2f))

        assertFalse(st.canRedo)
        assertTrue(st.canUndo)
    }

    @Test
    fun `undo restores sticky-marker highlights together with strokes`() {
        val st = newState()
        val drawing = PdfDrawingState()
        st.drawingStates[0] = drawing
        drawing.currentPaths.add(path(1f))

        // Sticky-marker swipe: snapshot stroke+highlight state, then turn the
        // stroke into a highlight (stroke removed, highlight added).
        st.pushUndoSnapshot(0, drawing.currentPaths.toList())
        drawing.currentPaths.clear()
        val highlight = StickyHighlight(strokeId = "h1")
        st.highlights[0] = listOf(highlight)

        st.undo()

        assertEquals(listOf(path(1f)), st.drawingStates[0]!!.currentPaths.toList())
        assertTrue(st.highlights[0].orEmpty().isEmpty())
    }
}
