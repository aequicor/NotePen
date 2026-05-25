package ru.kyamshanov.notepen.mainscreen.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MainScreenIntentTest {
    // TC-INTENT-1: DragStarted holds fileId, fileUri, displayName
    @Test
    fun `DragStarted stores fileId, fileUri, displayName`() {
        val intent =
            MainScreenIntent.DragStarted(
                fileId = "file-abc",
                fileUri = "content://example/abc",
                displayName = "file.pdf",
            )
        assertEquals("file-abc", intent.fileId)
        assertEquals("content://example/abc", intent.fileUri)
        assertEquals("file.pdf", intent.displayName)
    }

    // TC-INTENT-2: DragStarted supports copy()
    @Test
    fun `DragStarted copy changes specified fields`() {
        val original =
            MainScreenIntent.DragStarted(
                fileId = "f1",
                fileUri = "uri1",
                displayName = "original.pdf",
            )
        val copied = original.copy(displayName = "updated.pdf")
        assertEquals("f1", copied.fileId)
        assertEquals("uri1", copied.fileUri)
        assertEquals("updated.pdf", copied.displayName)
    }

    // TC-INTENT-3: DragCancelled is a singleton object
    @Test
    fun `DragCancelled is singleton object`() {
        val intent: MainScreenIntent = MainScreenIntent.DragCancelled
        assertIs<MainScreenIntent.DragCancelled>(intent)
    }

    // TC-INTENT-4: DropOnFolder stores folderId
    @Test
    fun `DropOnFolder stores folderId`() {
        val intent = MainScreenIntent.DropOnFolder(folderId = "folder-xyz")
        assertEquals("folder-xyz", intent.folderId)
    }

    // TC-INTENT-5: DropOnFolder supports copy()
    @Test
    fun `DropOnFolder copy changes folderId`() {
        val original = MainScreenIntent.DropOnFolder(folderId = "folder-1")
        val copied = original.copy(folderId = "folder-2")
        assertEquals("folder-2", copied.folderId)
    }

    // TC-INTENT-6: OnSuccessEventHandled is a singleton object
    @Test
    fun `OnSuccessEventHandled is singleton object`() {
        val intent: MainScreenIntent = MainScreenIntent.OnSuccessEventHandled
        assertIs<MainScreenIntent.OnSuccessEventHandled>(intent)
    }

    // TC-INTENT-7: new intents are subtypes of MainScreenIntent
    @Test
    fun `new intent variants are subtypes of MainScreenIntent`() {
        val dragStarted: MainScreenIntent = MainScreenIntent.DragStarted("id", "uri", "name")
        val dragCancelled: MainScreenIntent = MainScreenIntent.DragCancelled
        val dropOnFolder: MainScreenIntent = MainScreenIntent.DropOnFolder("fid")
        val onSuccessHandled: MainScreenIntent = MainScreenIntent.OnSuccessEventHandled

        assertIs<MainScreenIntent>(dragStarted)
        assertIs<MainScreenIntent>(dragCancelled)
        assertIs<MainScreenIntent>(dropOnFolder)
        assertIs<MainScreenIntent>(onSuccessHandled)
    }
}
