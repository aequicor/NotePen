package ru.kyamshanov.notepen.mainscreen.ui.component

import android.content.ClipData
import androidx.compose.ui.draganddrop.DragAndDropTransferData

/**
 * Android-реализация: оборачивает URI файла в [ClipData],
 * чтобы он мог быть передан через Android drag-and-drop механизм.
 */
internal actual fun createFileUriTransferData(fileUri: String): DragAndDropTransferData =
    DragAndDropTransferData(
        clipData = ClipData.newPlainText("file_uri", fileUri),
    )
