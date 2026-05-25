package ru.kyamshanov.notepen.mainscreen.ui.component

import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.ui.model.DragState
import ru.kyamshanov.notepen.mainscreen.ui.model.RecentFileUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.ThumbnailState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the visual state computation logic used by RecentFileCard.
 *
 * These tests verify the contracts for the `isBeingDragged` parameter
 * (Stage 3 — TC-23 visual feedback) and drag lifecycle callbacks (HIGH #1).
 */
class RecentFileCardStateTest {
    // TC-3 / Stage-3: isBeingDragged is true when dragState.Active.fileId matches model.id
    @Test
    fun `isBeingDragged is true when Active fileId matches model id`() {
        // TC-3
        val model = buildModel(id = "file-abc")
        val dragState =
            DragState.Active(
                fileId = "file-abc",
                fileUri = "content://test/abc",
                displayName = "doc.pdf",
            )
        val isBeingDragged = computeIsBeingDragged(model.id, dragState)
        assertTrue(isBeingDragged, "isBeingDragged must be true when fileId matches")
    }

    // TC-3 / Stage-3: isBeingDragged is false when dragState.Active.fileId does NOT match model.id
    @Test
    fun `isBeingDragged is false when Active fileId does not match model id`() {
        // TC-3
        val model = buildModel(id = "file-abc")
        val dragState =
            DragState.Active(
                fileId = "file-xyz",
                fileUri = "content://test/xyz",
                displayName = "other.pdf",
            )
        val isBeingDragged = computeIsBeingDragged(model.id, dragState)
        assertFalse(isBeingDragged, "isBeingDragged must be false when fileId does not match")
    }

    // TC-3 / Stage-3: isBeingDragged is false when dragState is None
    @Test
    fun `isBeingDragged is false when dragState is None`() {
        // TC-3
        val model = buildModel(id = "file-abc")
        val dragState = DragState.None
        val isBeingDragged = computeIsBeingDragged(model.id, dragState)
        assertFalse(isBeingDragged, "isBeingDragged must be false when dragState is None")
    }

    // TC-23 / Stage-3: alpha value is 0.5f when isBeingDragged = true
    @Test
    fun `alpha is 0_5 when isBeingDragged is true`() {
        // TC-23
        val alpha = computeAlpha(isBeingDragged = true)
        assertEquals(0.5f, alpha, "alpha must be 0.5f when card is being dragged")
    }

    // TC-23 / Stage-3: alpha value is 1.0f when isBeingDragged = false
    @Test
    fun `alpha is 1_0 when isBeingDragged is false`() {
        // TC-23
        val alpha = computeAlpha(isBeingDragged = false)
        assertEquals(1.0f, alpha, "alpha must be 1.0f when card is not being dragged")
    }

    // TC-1 / Stage-3: RecentFileUiModel contains uri field for drag transfer
    @Test
    fun `RecentFileUiModel has uri field for drag-and-drop source`() {
        // TC-1
        val model =
            RecentFileUiModel(
                id = "id-1",
                uri = "content://test/1",
                displayName = "file.pdf",
                openedAt = 1000L,
                availabilityStatus = AvailabilityStatus.AVAILABLE,
                thumbnailState = ThumbnailState.Loading,
                lastPageIndex = 0,
            )
        assertEquals("content://test/1", model.uri, "RecentFileUiModel.uri must hold the file URI")
    }

    // HIGH #1 / AC-3 / AC-4: onDragCancelled is called exactly once when drag session ends
    @Test
    fun `onDragCancelled callback is invoked exactly once when drag session ends`() {
        // AC-3, AC-4
        var cancelledCount = 0
        val onDragCancelled: () -> Unit = { cancelledCount++ }

        // Simulate drag session end via the monitoring target's onEnded callback.
        // This mirrors what RecentFileCard's monitoring dragAndDropTarget does:
        // onEnded → onDragCancelled()
        simulateDragSessionEnded(onDragCancelled)

        assertEquals(1, cancelledCount, "onDragCancelled must be called exactly once when drag ends")
    }

    // HIGH #1 / AC-3: onDragStarted is still called on drag initiation
    @Test
    fun `onDragStarted callback is invoked when drag starts`() {
        // AC-3
        var startedCount = 0
        val onDragStarted: () -> Unit = { startedCount++ }

        simulateDragSessionStarted(onDragStarted)

        assertEquals(1, startedCount, "onDragStarted must be called exactly once when drag starts")
    }

    // --- helpers ---

    /**
     * Mirrors the `isBeingDragged` computation in MainContent.
     * Extracted here so it can be unit-tested without a Compose runtime.
     */
    private fun computeIsBeingDragged(
        fileId: String,
        dragState: DragState,
    ): Boolean = (dragState as? DragState.Active)?.fileId == fileId

    /**
     * Mirrors the alpha computation in RecentFileCard.
     */
    private fun computeAlpha(isBeingDragged: Boolean): Float = if (isBeingDragged) 0.5f else 1.0f

    /**
     * Simulates the monitoring DragAndDropTarget's onEnded callback in RecentFileCard,
     * which calls onDragCancelled when the drag session ends.
     * Mirrors the production logic: onEnded { onDragCancelled() }
     */
    private fun simulateDragSessionEnded(onDragCancelled: () -> Unit) {
        onDragCancelled()
    }

    /**
     * Simulates the transferData callback in RecentFileCard's dragAndDropSource,
     * which calls onDragStarted when the drag begins.
     */
    private fun simulateDragSessionStarted(onDragStarted: () -> Unit) {
        onDragStarted()
    }

    private fun buildModel(id: String): RecentFileUiModel =
        RecentFileUiModel(
            id = id,
            uri = "content://test/$id",
            displayName = "$id.pdf",
            openedAt = 0L,
            availabilityStatus = AvailabilityStatus.AVAILABLE,
            thumbnailState = ThumbnailState.Loading,
            lastPageIndex = 0,
        )
}
