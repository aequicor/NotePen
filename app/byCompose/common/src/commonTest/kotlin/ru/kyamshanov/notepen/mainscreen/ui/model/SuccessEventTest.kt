package ru.kyamshanov.notepen.mainscreen.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SuccessEventTest {
    // TC-SUCCESS-1: SuccessEvent.FileAddedToFolder carries folderName parameter (CRITICAL #1)
    @Test
    fun `SuccessEvent FileAddedToFolder carries folderName`() {
        val event: SuccessEvent = SuccessEvent.FileAddedToFolder(folderName = "Документы")
        assertIs<SuccessEvent.FileAddedToFolder>(event)
        assertEquals("Документы", event.folderName)
    }

    // TC-SUCCESS-2: SuccessEvent.FileAlreadyInFolder exists as singleton variant (CRITICAL #2)
    @Test
    fun `SuccessEvent FileAlreadyInFolder is singleton object`() {
        val event: SuccessEvent = SuccessEvent.FileAlreadyInFolder
        assertIs<SuccessEvent.FileAlreadyInFolder>(event)
    }

    // TC-SUCCESS-3: when expression exhaustiveness covers both variants
    @Test
    fun `when expression over SuccessEvent covers both variants`() {
        val events: List<SuccessEvent> =
            listOf(
                SuccessEvent.FileAddedToFolder(folderName = "Работа"),
                SuccessEvent.FileAlreadyInFolder,
                SuccessEvent.FileAddedToLibrary,
            )
        val results =
            events.map { event ->
                when (event) {
                    is SuccessEvent.FileAddedToFolder -> "added:${event.folderName}"
                    is SuccessEvent.FileAlreadyInFolder -> "already"
                    is SuccessEvent.FileAddedToLibrary -> "library"
                }
            }
        assertEquals(listOf("added:Работа", "already", "library"), results)
    }
}
