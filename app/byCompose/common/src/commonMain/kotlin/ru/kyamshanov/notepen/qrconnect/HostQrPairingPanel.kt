package ru.kyamshanov.notepen.qrconnect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.qrconnect.application.HostQrPairingCoordinator
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo

/**
 * Desktop-side QR pairing panel.
 *
 * Pure view: subscribes to [HostQrPairingViewModel] and forwards user
 * actions back to it. All lifecycle / approval logic lives in the VM and the
 * underlying [ru.kyamshanov.notepen.sync.domain.port.PeerServer].
 *
 * The panel does not include a title bar — the enclosing dialog renders the
 * title and close button. [onCloseDialog] is invoked when the user stops the
 * server via "Запретить подключения".
 */
@Composable
fun HostQrPairingPanel(
    viewModel: HostQrPairingViewModel,
    modifier: Modifier = Modifier,
    onCloseDialog: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val s = state) {
            null -> Button(
                onClick = viewModel::start,
                modifier = Modifier.widthIn(min = 180.dp),
            ) { Text("Разрешить подключение по QR") }

            HostQrPairingCoordinator.State.Preparing -> {
                CircularProgressIndicator()
                Text("Запуск сервера…")
            }

            is HostQrPairingCoordinator.State.ShowingQr -> ShowingQrContent(
                state = s,
                onApprove = { peerId -> viewModel.approve(peerId) },
                onReject = { peerId -> viewModel.reject(peerId) },
                onDisconnect = { peerId -> viewModel.disconnect(peerId) },
                onDisconnectAll = viewModel::disconnectAll,
                onStopServer = {
                    viewModel.stopServer()
                    onCloseDialog()
                },
            )

            HostQrPairingCoordinator.State.Stopped -> {
                Text("Подключения остановлены")
                Button(onClick = viewModel::start) { Text("Запустить снова") }
            }

            is HostQrPairingCoordinator.State.Failed -> {
                Text(
                    text = "Ошибка: ${s.error.message}",
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = viewModel::start) { Text("Повторить") }
            }
        }
    }
}

@Composable
private fun ShowingQrContent(
    state: HostQrPairingCoordinator.State.ShowingQr,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onDisconnectAll: () -> Unit,
    onStopServer: () -> Unit,
) {
    QrCodeImage(matrix = state.matrix, sizeDp = 240.dp)

    Text(
        text = "На клиенте откройте NotePen → Sync → «Сканировать QR».",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )

    ManualConnectionDetails(payload = state.uri.encode())

    Spacer(Modifier.height(4.dp))

    Text(
        text = "Подключённые клиенты",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.peers.isEmpty()) {
        Text(
            text = "Нет активных подключений",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        ConnectedPeersList(peers = state.peers, onDisconnect = onDisconnect)
    }

    Spacer(Modifier.height(4.dp))

    Button(
        onClick = if (state.peers.isEmpty()) onStopServer else onDisconnectAll,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.peers.isEmpty()) "Запретить подключение" else "Закрыть подключения")
    }

    state.pendingApproval?.let { peer ->
        ClientApprovalDialog(
            client = peer,
            onAccept = { onApprove(peer.id) },
            onReject = { onReject(peer.id) },
        )
    }
}

@Composable
private fun ManualConnectionDetails(payload: String) {
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { expanded = !expanded }) {
                Text("Данные для ручного подключения")
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }
        }
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = payload,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { clipboard.setText(AnnotatedString(payload)) }) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Скопировать строку подключения",
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedPeersList(
    peers: List<DeviceInfo>,
    onDisconnect: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        peers.forEach { peer ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(peer.name, style = MaterialTheme.typography.bodyMedium)
                    if (peer.host.isNotEmpty() && peer.port != 0) {
                        Text(
                            text = "${peer.host}:${peer.port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = { onDisconnect(peer.id) }) { Text("Отключить") }
            }
        }
    }
}

@Composable
private fun ClientApprovalDialog(
    client: DeviceInfo,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Разрешить подключение?") },
        text = {
            Column {
                Text(client.name, style = MaterialTheme.typography.titleMedium)
                if (client.host.isNotEmpty() && client.port != 0) {
                    Text(
                        text = "${client.host}:${client.port}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Принять") } },
        dismissButton = { TextButton(onClick = onReject) { Text("Отклонить") } },
    )
}

