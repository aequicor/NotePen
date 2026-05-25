package ru.kyamshanov.notepen.mainscreen.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MainScreenUiStateTest {
    // TC-STATE-1: default value for dragState is DragState.None
    @Test
    fun `MainScreenUiState dragState defaults to DragState None`() {
        val state = MainScreenUiState()
        assertIs<DragState.None>(state.dragState)
    }

    // TC-STATE-2: default value for successEvent is null
    @Test
    fun `MainScreenUiState successEvent defaults to null`() {
        val state = MainScreenUiState()
        assertNull(state.successEvent)
    }

    // TC-STATE-3: copy() correctly updates dragState — no RecentFileUiModel involved here
    @Test
    fun `MainScreenUiState copy updates dragState to Active`() {
        val state = MainScreenUiState()
        val active =
            DragState.Active(
                fileId = "file-99",
                fileUri = "content://test/99",
                displayName = "test.pdf",
            )
        val updated = state.copy(dragState = active)
        assertIs<DragState.Active>(updated.dragState)
        assertEquals("file-99", (updated.dragState as DragState.Active).fileId)
    }

    // TC-STATE-4: copy() correctly updates successEvent
    @Test
    fun `MainScreenUiState copy updates successEvent to FileAddedToFolder`() {
        val state = MainScreenUiState()
        val updated = state.copy(successEvent = SuccessEvent.FileAddedToFolder(folderName = "TestFolder"))
        assertIs<SuccessEvent.FileAddedToFolder>(updated.successEvent)
    }

    // TC-STATE-5: copy() clears successEvent back to null
    @Test
    fun `MainScreenUiState copy clears successEvent to null`() {
        val state = MainScreenUiState(successEvent = SuccessEvent.FileAddedToFolder(folderName = "TestFolder"))
        val cleared = state.copy(successEvent = null)
        assertNull(cleared.successEvent)
    }

    // TC-STATE-6: dragState and successEvent coexist correctly
    @Test
    fun `MainScreenUiState dragState None and successEvent FileAddedToFolder coexist`() {
        val state =
            MainScreenUiState(
                dragState = DragState.None,
                successEvent = SuccessEvent.FileAddedToFolder(folderName = "TestFolder"),
            )
        assertIs<DragState.None>(state.dragState)
        assertIs<SuccessEvent.FileAddedToFolder>(state.successEvent)
    }
}
