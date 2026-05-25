package ru.kyamshanov.notepen.drawing.api

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfDrawingStateUndoTest {
    private fun path(id: Float) = DrawingPath(points = listOf(DrawingPoint(id, id, true), DrawingPoint(id + 1f, id + 1f)))

    // -- restoreSnapshot --

    @Test
    fun `restoreSnapshot replaces currentPaths with given list`() {
        val state = PdfDrawingState()
        state.currentPaths.add(path(1f))
        val snapshot = listOf(path(2f), path(3f))
        state.restoreSnapshot(snapshot)
        assertEquals(snapshot, state.currentPaths.toList())
    }

    @Test
    fun `restoreSnapshot with empty list clears currentPaths`() {
        val state = PdfDrawingState()
        state.currentPaths.add(path(1f))
        state.restoreSnapshot(emptyList())
        assertTrue(state.currentPaths.isEmpty())
    }

    // -- global undo stack ordering (logic unit) --

    @Test
    fun `global stack undo single gesture on one page`() {
        val state0 = PdfDrawingState()
        val stack = ArrayDeque<Pair<Int, List<DrawingPath>>>()

        // gesture on page 0: snapshot empty, then draw
        stack.addLast(0 to state0.currentPaths.toList())
        state0.currentPaths.add(path(1f))

        // Ctrl+Z
        val (page, snap) = stack.removeLast()
        assertEquals(0, page)
        val drawingStates = mapOf(0 to state0)
        drawingStates[page]?.restoreSnapshot(snap)

        assertTrue(state0.currentPaths.isEmpty())
        assertTrue(stack.isEmpty())
    }

    @Test
    fun `global stack undo across two pages in order`() {
        val state0 = PdfDrawingState()
        val state1 = PdfDrawingState()
        val drawingStates = mapOf(0 to state0, 1 to state1)
        val stack = ArrayDeque<Pair<Int, List<DrawingPath>>>()

        // gesture on page 0
        stack.addLast(0 to state0.currentPaths.toList())
        state0.currentPaths.add(path(1f))

        // gesture on page 1
        stack.addLast(1 to state1.currentPaths.toList())
        state1.currentPaths.add(path(2f))

        // gesture on page 0 again
        stack.addLast(0 to state0.currentPaths.toList())
        state0.currentPaths.add(path(3f))

        // Ctrl+Z #1 — undoes last gesture on page 0 (had [path1])
        val (p1, s1) = stack.removeLast()
        drawingStates[p1]?.restoreSnapshot(s1)
        assertEquals(listOf(path(1f)), state0.currentPaths.toList())

        // Ctrl+Z #2 — undoes gesture on page 1
        val (p2, s2) = stack.removeLast()
        drawingStates[p2]?.restoreSnapshot(s2)
        assertTrue(state1.currentPaths.isEmpty())

        // Ctrl+Z #3 — undoes first gesture on page 0
        val (p3, s3) = stack.removeLast()
        drawingStates[p3]?.restoreSnapshot(s3)
        assertTrue(state0.currentPaths.isEmpty())

        assertTrue(stack.isEmpty())
    }

    @Test
    fun `global stack no-op when empty`() {
        val stack = ArrayDeque<Pair<Int, List<DrawingPath>>>()
        // should not throw
        if (stack.isNotEmpty()) {
            val (_, _) = stack.removeLast()
        }
        assertTrue(stack.isEmpty())
    }

    // -- redo logic --

    private fun simulateUndo(
        undoStack: ArrayDeque<Pair<Int, List<DrawingPath>>>,
        redoStack: ArrayDeque<Pair<Int, List<DrawingPath>>>,
        drawingStates: Map<Int, PdfDrawingState>,
    ) {
        if (undoStack.isEmpty()) return
        val (pageIndex, snapshot) = undoStack.removeLast()
        val current = drawingStates[pageIndex]?.currentPaths?.toList() ?: emptyList()
        redoStack.addLast(pageIndex to current)
        drawingStates[pageIndex]?.restoreSnapshot(snapshot)
    }

    private fun simulateRedo(
        undoStack: ArrayDeque<Pair<Int, List<DrawingPath>>>,
        redoStack: ArrayDeque<Pair<Int, List<DrawingPath>>>,
        drawingStates: Map<Int, PdfDrawingState>,
    ) {
        if (redoStack.isEmpty()) return
        val (pageIndex, snapshot) = redoStack.removeLast()
        val current = drawingStates[pageIndex]?.currentPaths?.toList() ?: emptyList()
        undoStack.addLast(pageIndex to current)
        drawingStates[pageIndex]?.restoreSnapshot(snapshot)
    }

    @Test
    fun `undo then redo restores original state`() {
        val state = PdfDrawingState()
        val drawingStates = mapOf(0 to state)
        val undoStack = ArrayDeque<Pair<Int, List<DrawingPath>>>()
        val redoStack = ArrayDeque<Pair<Int, List<DrawingPath>>>()

        undoStack.addLast(0 to state.currentPaths.toList())
        state.currentPaths.add(path(1f))

        simulateUndo(undoStack, redoStack, drawingStates)
        assertTrue(state.currentPaths.isEmpty())

        simulateRedo(undoStack, redoStack, drawingStates)
        assertEquals(listOf(path(1f)), state.currentPaths.toList())
    }

    @Test
    fun `new gesture clears redo stack`() {
        val state = PdfDrawingState()
        val drawingStates = mapOf(0 to state)
        val undoStack = ArrayDeque<Pair<Int, List<DrawingPath>>>()
        val redoStack = ArrayDeque<Pair<Int, List<DrawingPath>>>()

        undoStack.addLast(0 to state.currentPaths.toList())
        state.currentPaths.add(path(1f))
        simulateUndo(undoStack, redoStack, drawingStates)

        // new gesture — clears redo
        redoStack.clear()
        undoStack.addLast(0 to state.currentPaths.toList())
        state.currentPaths.add(path(2f))

        assertTrue(redoStack.isEmpty())
        assertEquals(listOf(path(2f)), state.currentPaths.toList())
    }

    @Test
    fun `redo no-op when redo stack empty`() {
        val state = PdfDrawingState()
        val drawingStates = mapOf(0 to state)
        val undoStack = ArrayDeque<Pair<Int, List<DrawingPath>>>()
        val redoStack = ArrayDeque<Pair<Int, List<DrawingPath>>>()
        state.currentPaths.add(path(1f))

        simulateRedo(undoStack, redoStack, drawingStates)
        assertEquals(listOf(path(1f)), state.currentPaths.toList())
    }
}
