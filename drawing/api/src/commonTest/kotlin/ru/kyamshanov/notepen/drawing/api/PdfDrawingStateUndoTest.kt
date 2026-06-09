package ru.kyamshanov.notepen.drawing.api

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfDrawingStateUndoTest {
    private fun path(id: Float) = DrawingPath(points = listOf(DrawingPoint(id, id, true), DrawingPoint(id + 1f, id + 1f)))

    @Test
    fun `addPoint skips subpixel live samples`() {
        val state = PdfDrawingState()

        state.startDrawing(x = 0.1f, y = 0.1f)
        state.addPoint(x = 0.1001f, y = 0.1001f)
        state.addPoint(x = 0.101f, y = 0.101f)

        assertEquals(2, state.livePoints.size)
        assertEquals(0.101f, state.livePoints.last().x)
        assertEquals(0.101f, state.livePoints.last().y)
    }

    @Test
    fun `live stroke listener receives only accepted samples`() {
        val state = PdfDrawingState()
        val samples = mutableListOf<LiveStrokeSample>()
        val unregister = state.addLiveStrokeListener { samples += it }

        state.startDrawing(x = 0.1f, y = 0.1f, normalizedStrokeWidth = 0.003f)
        state.addPoint(x = 0.1001f, y = 0.1001f)
        state.addPoint(x = 0.101f, y = 0.101f)
        unregister()
        state.addPoint(x = 0.102f, y = 0.102f)

        assertEquals(2, samples.size)
        assertEquals(null, samples.first().previous)
        assertEquals(0.1f, samples.first().current.x)
        assertEquals(0.003f, samples.first().normalizedStrokeWidth)
        assertEquals(samples.first().current, samples.last().previous)
        assertEquals(0.101f, samples.last().current.x)
    }

    @Test
    fun `finishDrawing commits down-only stroke as short segment`() {
        val state = PdfDrawingState()

        state.startDrawing(x = 0.25f, y = 0.75f, normalizedStrokeWidth = 0.002f)

        val completed = state.finishDrawing()

        requireNotNull(completed)
        assertEquals(1, state.currentPaths.size)
        assertEquals(2, completed.points.size)
        assertEquals(0.25f, completed.points.first().x)
        assertEquals(0.75f, completed.points.first().y)
        assertTrue(completed.points.last().x > completed.points.first().x)
        assertEquals(completed.points.first().y, completed.points.last().y)
        assertTrue(state.livePoints.isEmpty())
    }

    @Test
    fun `finishDrawing keeps freehand samples unchanged`() {
        val state = PdfDrawingState()

        state.startDrawing(x = 0f, y = 0f)
        state.addPoint(x = 0.001f, y = 0.0002f)
        state.addPoint(x = 0.002f, y = -0.0002f)
        state.addPoint(x = 0.003f, y = 0.0002f)
        state.addPoint(x = 0.004f, y = 0f)

        val liveSnapshot = state.livePoints.toList()
        val completed = state.finishDrawing()

        requireNotNull(completed)
        assertEquals(liveSnapshot, completed.points)
    }

    @Test
    fun `finishDrawing keeps stair stepped samples unchanged`() {
        val state = PdfDrawingState()

        state.startDrawing(x = 0f, y = 0f)
        state.addPoint(x = 0.01f, y = 0f)
        state.addPoint(x = 0.01f, y = 0.01f)
        state.addPoint(x = 0.02f, y = 0.01f)
        state.addPoint(x = 0.02f, y = 0.02f)
        state.addPoint(x = 0.03f, y = 0.02f)
        state.addPoint(x = 0.03f, y = 0.03f)

        val liveSnapshot = state.livePoints.toList()
        val completed = state.finishDrawing()

        requireNotNull(completed)
        assertEquals(liveSnapshot, completed.points)
    }

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
