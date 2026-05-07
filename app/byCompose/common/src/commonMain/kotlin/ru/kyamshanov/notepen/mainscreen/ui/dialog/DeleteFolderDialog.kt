package ru.kyamshanov.notepen.mainscreen.ui.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Диалог подтверждения удаления папки.
 *
 * @param folderName Имя удаляемой папки для отображения.
 * @param onConfirm Обработчик подтверждения удаления.
 * @param onDismiss Обработчик отмены диалога.
 */
@Composable
fun DeleteFolderDialog(
    folderName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить папку?") },
        text = { Text("Папка «$folderName» будет удалена. Файлы из папки останутся в истории.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Удалить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}
