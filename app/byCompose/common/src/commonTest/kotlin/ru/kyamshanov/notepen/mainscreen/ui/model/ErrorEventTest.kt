package ru.kyamshanov.notepen.mainscreen.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ErrorEventTest {
    // TC-ERROR-2: ErrorEvent.FileNotInHistory is a valid singleton variant
    @Test
    fun `ErrorEvent FileNotInHistory is singleton object`() {
        val event: ErrorEvent = ErrorEvent.FileNotInHistory
        assertIs<ErrorEvent.FileNotInHistory>(event)
    }

    // TC-ERROR-4: when expression matches FileNotInHistory
    @Test
    fun `when expression matches FileNotInHistory`() {
        val event: ErrorEvent = ErrorEvent.FileNotInHistory
        val label =
            when (event) {
                is ErrorEvent.FileNotFound -> "not_found"
                is ErrorEvent.FileError -> "file_error"
                is ErrorEvent.HistoryFlushFailed -> "history_flush_failed"
                is ErrorEvent.ThumbnailGenerationFailed -> "thumbnail_failed"
                is ErrorEvent.FolderLimitExceeded -> "folder_limit"
                is ErrorEvent.FolderNameCharsInvalid -> "folder_name_invalid"
                is ErrorEvent.FolderOperationFailed -> "folder_op_failed"
                is ErrorEvent.FileNotInHistory -> "not_in_history"
                else -> "other"
            }
        assertEquals("not_in_history", label)
    }
}
