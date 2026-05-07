package ru.kyamshanov.notepen.mainscreen.ui.component

import androidx.compose.ui.draganddrop.DragAndDropTransferData

/**
 * Создаёт платформо-специфичный [DragAndDropTransferData] для передачи URI файла
 * в ходе операции drag-and-drop.
 *
 * @param fileUri Нормализованный URI файла для передачи.
 * @return Объект данных для начала drag-сессии.
 */
internal expect fun createFileUriTransferData(fileUri: String): DragAndDropTransferData
