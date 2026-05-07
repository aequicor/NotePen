package ru.kyamshanov.notepen.mainscreen.ui.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import ru.kyamshanov.notepen.mainscreen.ui.model.CreateFolderDialogState

/**
 * Диалог создания новой папки.
 *
 * @param state Текущее состояние диалога (имя папки, доступность кнопки подтверждения).
 * @param onNameChange Обработчик изменения текста в поле имени.
 * @param onConfirm Обработчик нажатия кнопки «Создать».
 * @param onDismiss Обработчик отмены диалога.
 */
@Composable
fun CreateFolderDialog(
    state: CreateFolderDialogState,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая папка") },
        text = {
            OutlinedTextField(
                value = state.currentName,
                onValueChange = onNameChange,
                label = { Text("Имя папки") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = state.isConfirmEnabled,
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}
