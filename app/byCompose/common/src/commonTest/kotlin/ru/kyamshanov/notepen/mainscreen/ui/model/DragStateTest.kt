package ru.kyamshanov.notepen.mainscreen.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DragStateTest {

    // TC-DRAG-1: DragState.None is the inactive drag state
    @Test
    fun `DragState None is singleton object`() {
        val state: DragState = DragState.None
        assertIs<DragState.None>(state)
    }

    // TC-DRAG-2: DragState.Active holds fileId, fileUri, displayName
    @Test
    fun `DragState Active stores fileId, fileUri, displayName`() {
        val state = DragState.Active(
            fileId = "file-123",
            fileUri = "content://example/123",
            displayName = "document.pdf",
        )
        assertEquals("file-123", state.fileId)
        assertEquals("content://example/123", state.fileUri)
        assertEquals("document.pdf", state.displayName)
    }

    // TC-DRAG-3: DragState.Active supports copy()
    @Test
    fun `DragState Active copy changes specified fields`() {
        val original = DragState.Active(
            fileId = "file-001",
            fileUri = "content://original/001",
            displayName = "original.pdf",
        )
        val copied = original.copy(displayName = "renamed.pdf")
        assertEquals("file-001", copied.fileId)
        assertEquals("content://original/001", copied.fileUri)
        assertEquals("renamed.pdf", copied.displayName)
    }

    // TC-DRAG-4: smart cast — type narrowing with 'as?'
    @Test
    fun `DragState None cast to Active returns null`() {
        val state: DragState = DragState.None
        val active = state as? DragState.Active
        assertNull(active)
    }

    // TC-DRAG-5: smart cast — Active cast succeeds
    @Test
    fun `DragState Active cast to Active returns instance`() {
        val state: DragState = DragState.Active("id", "uri", "name")
        val active = state as? DragState.Active
        assertEquals("id", active?.fileId)
    }
}
