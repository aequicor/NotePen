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
import ru.kyamshanov.notepen.LiquidGlassAlertDialog
import ru.kyamshanov.notepen.qrconnect.application.HostQrPairingCoordinator
import ru.kyamshanov.notepen.qrconnect.domain.PairingUri
import ru.kyamshanov.notepen.qrconnect.domain.port.QrMatrix
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
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val s = state) {
            null ->
                Button(
                    onClick = viewModel::start,
                    modifier = Modifier.widthIn(min = 180.dp),
                ) { Text("Разрешить подключение по QR") }

            HostQrPairingCoordinator.State.Preparing -> {
                CircularProgressIndicator()
                Text("Запуск сервера…")
            }

            is HostQrPairingCoordinator.State.ShowingQr ->
                ShowingQrContent(
                    state = s,
                    canGrantLibrarian = viewModel.canGrantLibrarian,
                    cableState = if (viewModel.cableSupported) viewModel.cableState?.collectAsState()?.value else null,
                    encodeCableQr = viewModel::encodeCableQr,
                    onStartCable = { port, serial -> viewModel.startCable(port, serial) },
                    onStopCable = { port, serial -> viewModel.stopCable(port, serial) },
                    onApprove = { peerId -> viewModel.approve(peerId) },
                    onApproveAsLibrarian = { peerId -> viewModel.approveAsLibrarian(peerId) },
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
    canGrantLibrarian: Boolean,
    cableState: CableState?,
    encodeCableQr: (PairingUri) -> QrMatrix,
    onStartCable: (Int, String?) -> Unit,
    onStopCable: (Int, String?) -> Unit,
    onApprove: (String) -> Unit,
    onApproveAsLibrarian: (String) -> Unit,
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

    if (cableState != null) {
        Spacer(Modifier.height(4.dp))
        CablePairingSection(
            uri = state.uri,
            cableState = cableState,
            encodeCableQr = encodeCableQr,
            onStart = onStartCable,
            onStop = onStopCable,
        )
    }

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
            canGrantLibrarian = canGrantLibrarian,
            onAcceptAsReader = { onApprove(peer.id) },
            onAcceptAsLibrarian = { onApproveAsLibrarian(peer.id) },
            onReject = { onReject(peer.id) },
        )
    }
}

@Suppress("DEPRECATION") // LocalClipboard migration needs per-platform ClipEntry; deferred
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
                modifier =
                    Modifier
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

/**
 * USB-cable pairing section. Renders the current [CableState]: a button to set
 * up `adb reverse`, device-picker for multiple devices, or — once [CableState.Ready]
 * — a `127.0.0.1` QR + manual string the tethered tablet scans/pastes. The host
 * server binds `0.0.0.0`, so the loopback payload reaches it over the cable.
 */
@Composable
private fun CablePairingSection(
    uri: PairingUri,
    cableState: CableState,
    encodeCableQr: (PairingUri) -> QrMatrix,
    onStart: (Int, String?) -> Unit,
    onStop: (Int, String?) -> Unit,
) {
    val cableUri = remember(uri) { uri.copy(host = "127.0.0.1") }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Подключение по кабелю (USB)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        when (cableState) {
            CableState.Idle ->
                Button(onClick = { onStart(uri.port, null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Включить подключение по кабелю")
                }

            CableState.NoTool ->
                Text(
                    text =
                        "adb не найден. Установите Android platform-tools, подключите " +
                            "планшет по USB и включите «Отладку по USB».",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )

            CableState.NoDevice -> {
                Text(
                    text = "Планшет не обнаружен. Подключите его по USB и разрешите отладку на устройстве.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { onStart(uri.port, null) }) { Text("Повторить") }
            }

            is CableState.MultipleDevices -> {
                Text("Выберите устройство:", style = MaterialTheme.typography.bodySmall)
                cableState.devices.forEach { device ->
                    TextButton(onClick = { onStart(uri.port, device.serial) }) { Text(device.serial) }
                }
            }

            is CableState.Ready ->
                CableReadyContent(
                    cableUri = cableUri,
                    encodeCableQr = encodeCableQr,
                    onStop = { onStop(cableState.port, cableState.serial) },
                )

            is CableState.Error -> {
                Text(
                    text = "Ошибка: ${cableState.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { onStart(uri.port, null) }) { Text("Повторить") }
            }
        }
    }
}

/** [CableState.Ready] content: the loopback QR + manual string + disconnect action. */
@Composable
private fun CableReadyContent(
    cableUri: PairingUri,
    encodeCableQr: (PairingUri) -> QrMatrix,
    onStop: () -> Unit,
) {
    QrCodeImage(matrix = remember(cableUri) { encodeCableQr(cableUri) }, sizeDp = 200.dp)
    Text(
        text = "Отсканируйте этот QR на планшете, подключённом по USB.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
    ManualConnectionDetails(payload = cableUri.encode())
    TextButton(onClick = onStop) { Text("Отключить кабель") }
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
    canGrantLibrarian: Boolean,
    onAcceptAsReader: () -> Unit,
    onAcceptAsLibrarian: () -> Unit,
    onReject: () -> Unit,
) {
    LiquidGlassAlertDialog(
        onDismissRequest = onReject,
        title = { Text("Разрешить подключение?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(client.name, style = MaterialTheme.typography.titleMedium)
                if (client.host.isNotEmpty() && client.port != 0) {
                    Text(
                        text = "${client.host}:${client.port}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (canGrantLibrarian) {
                    Text(
                        text =
                            "«Читатель» — только просмотр библиотеки. " +
                                "«Библиотекарь» — также добавление и замена книг.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        // Reader is the safe default (first / confirm button). Librarian is the
        // explicit elevated choice, offered only when this host can grant it.
        confirmButton = { TextButton(onClick = onAcceptAsReader) { Text("Как читателя") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canGrantLibrarian) {
                    TextButton(onClick = onAcceptAsLibrarian) { Text("Как библиотекаря") }
                }
                TextButton(onClick = onReject) { Text("Отклонить") }
            }
        },
    )
}
