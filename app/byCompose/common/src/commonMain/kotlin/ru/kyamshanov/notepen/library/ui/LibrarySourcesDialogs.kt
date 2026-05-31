package ru.kyamshanov.notepen.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

/**
 * Overlay dialogs of the «Источники библиотек» screen, hoisted out of [LibrarySourcesContent] to keep
 * that composable small: the Google device-code prompt (shown while a Drive sign-in polls) and the
 * required-name dialog for a freshly picked local-folder library.
 *
 * @param googleDevicePrompt the active Google device-flow prompt, or `null` when no sign-in is polling.
 * @param pendingLocalFolder the folder path picked for a new local library awaiting its name, or `null`.
 * @param viewModel the screen ViewModel — used to cancel the Google sign-in and to create the local
 *   library once a name is confirmed.
 * @param onPendingLocalConsumed clears [pendingLocalFolder] after the name dialog is dismissed/confirmed.
 */
@Composable
internal fun LibrarySourcesDialogs(
    googleDevicePrompt: GoogleDeviceCodeUiModel?,
    pendingLocalFolder: String?,
    viewModel: LibrarySourcesViewModel,
    onPendingLocalConsumed: () -> Unit,
) {
    googleDevicePrompt?.let { prompt ->
        GoogleDeviceCodeDialog(prompt = prompt, onCancel = viewModel::cancelGoogleSignIn)
    }
    pendingLocalFolder?.let { path ->
        LocalLibraryNameDialog(
            suggestedName = path.substringAfterLast('/').substringAfterLast('\\').ifBlank { path },
            onDismiss = onPendingLocalConsumed,
            onConfirm = { name ->
                onPendingLocalConsumed()
                viewModel.addLocalLibrary(path, name)
            },
        )
    }
}

/**
 * Dialog requiring a name for a new local-folder library. The folder is already picked; only the
 * display name is collected here. Pre-fills [suggestedName] (the folder's basename); confirm is
 * enabled only when the name is non-blank.
 */
@Composable
private fun LocalLibraryNameDialog(
    suggestedName: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit,
) {
    var name by remember { mutableStateOf(suggestedName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Название библиотеки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Дайте библиотеке имя — оно будет видно на главном экране.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Имя") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/**
 * Shows the active Google device-flow prompt: the user code to type and the verification URL to
 * open. Stays up (non-dismissable except via «Отмена») while the ViewModel polls for authorization.
 */
@Composable
private fun GoogleDeviceCodeDialog(
    prompt: GoogleDeviceCodeUiModel,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Вход через Google") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Откройте в браузере:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(prompt.verificationUri, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "и введите код:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(prompt.userCode, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Ожидание подтверждения…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Отмена") }
        },
    )
}
