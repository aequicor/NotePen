package ru.kyamshanov.notepen.mainscreen.ui.component

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import java.awt.datatransfer.StringSelection

/**
 * Desktop-реализация: оборачивает URI файла в AWT [StringSelection],
 * чтобы он мог быть передан через AWT drag-and-drop механизм.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun createFileUriTransferData(fileUri: String): DragAndDropTransferData =
    DragAndDropTransferData(
        transferable = DragAndDropTransferable(StringSelection(fileUri)),
        supportedActions = listOf(DragAndDropTransferAction.Copy),
    )
