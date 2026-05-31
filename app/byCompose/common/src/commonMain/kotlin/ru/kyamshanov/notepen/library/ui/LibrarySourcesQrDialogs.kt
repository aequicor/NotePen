package ru.kyamshanov.notepen.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.qrconnect.QrCodeImage

/**
 * Paste-to-connect dialog: the user pastes a `notepen://pair?…` payload (from a host's «Поделиться по
 * QR» window) and [connect] dials the host directly and registers the library. Closes on success.
 */
@Composable
internal fun ConnectByQrDialog(
    connect: suspend (payload: String) -> Result<String>,
    onDismiss: () -> Unit,
) {
    var payload by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!connecting) onDismiss() },
        title = { Text("Подключить библиотеку по QR") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text =
                        "Вставьте строку notepen://pair?… из окна «Поделиться по QR» на устройстве-хосте. " +
                            "Подключение идёт напрямую по адресу из QR — без поиска по сети.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = payload,
                    onValueChange = {
                        payload = it
                        error = null
                    },
                    label = { Text("notepen://pair?…") },
                    singleLine = false,
                    minLines = 2,
                    enabled = !connecting,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (connecting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Подключаемся…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !connecting && payload.isNotBlank(),
                onClick = {
                    connecting = true
                    error = null
                    scope.launch {
                        connect(payload.trim())
                            .onSuccess { onDismiss() }
                            .onFailure { e ->
                                connecting = false
                                error = e.message ?: "Не удалось подключиться"
                            }
                    }
                },
            ) { Text("Подключить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !connecting) { Text("Отмена") }
        },
    )
}

/**
 * Per-library share dialog (desktop host): asks [share] to enable LAN sharing for [model] and render
 * its QR + copyable `notepen://pair?…&l=…` string for a client to scan or paste.
 */
@Composable
internal fun ShareLibraryQrDialog(
    model: LibrarySourceUiModel,
    share: suspend (libraryId: String, libraryName: String) -> SharedLibraryQr?,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var qr by remember(model.id) { mutableStateOf<SharedLibraryQr?>(null) }
    var error by remember(model.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(model.id) {
        runCatching { share(model.id, model.displayName) }
            .onSuccess { result ->
                if (result != null) qr = result else error = "Не удалось подготовить QR"
            }.onFailure { error = it.message ?: "Не удалось подготовить QR" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Поделиться: ${model.displayName}") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text =
                        "Отсканируйте QR на планшете (NotePen → Sync → камера) " +
                            "или вставьте строку ниже в «Подключить по QR».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val current = qr
                val err = error
                when {
                    current != null -> {
                        QrCodeImage(matrix = current.matrix)
                        OutlinedTextField(
                            value = current.payload,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("notepen://pair?…") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    err != null -> Text(err, color = MaterialTheme.colorScheme.error)
                    else -> CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            val current = qr
            TextButton(
                enabled = current != null,
                onClick = { current?.let { clipboard.setText(AnnotatedString(it.payload)) } },
            ) { Text("Копировать ссылку") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
