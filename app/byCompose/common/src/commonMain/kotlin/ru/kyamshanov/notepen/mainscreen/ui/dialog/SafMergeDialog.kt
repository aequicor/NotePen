package ru.kyamshanov.notepen.mainscreen.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.mainscreen.ui.model.RecentFileUiModel

/**
 * Диалог слияния SAF-записей: показывает существующую запись и новый URI.
 *
 * @param existing Существующая запись в истории.
 * @param newUri Новый URI, пришедший от SAF.
 * @param onMerge Обработчик подтверждения слияния.
 * @param onReject Обработчик отклонения слияния (добавить как отдельную запись).
 */
@Composable
fun SafMergeDialog(
    existing: RecentFileUiModel,
    newUri: String,
    onMerge: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Обновление файла") },
        text = {
            Column {
                Text(
                    "Существующая запись:",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(existing.displayName, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Новый URI:",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = newUri,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onMerge) {
                Text("Объединить")
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("Отдельная запись")
            }
        },
    )
}
