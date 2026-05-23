package ru.kyamshanov.notepen.qrconnect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.titlebar.LocalTitleBarInteraction

private val SyncConnectedGreen = Color(0xFF2E7D32)
private val SyncUnstableYellow = Color(0xFFF9A825)

/**
 * Кнопка синхронизации (иконка [Icons.Default.Sync]) и связанный с ней
 * QR-диалог подключения. Рендерится только когда передан хотя бы один из
 * pairing-вьюмоделей; иначе компонент ничего не рисует.
 *
 * Тинт иконки сигнализирует о статусе соединения через [syncIndicatorTint].
 *
 * @param hostQrViewModel Вьюмодель хост-панели QR (desktop). `null` — панель скрыта.
 * @param clientScanViewModel Вьюмодель сканирования QR на клиенте. `null` — панель скрыта.
 * @param manualConnectViewModel Вьюмодель ручного ввода host/port/code на клиенте.
 * @param peerServer Хост-сервер для индикатора статуса; `null` если хостинг не поддерживается.
 * @param peerClient Клиент синхронизации для индикатора статуса и панели подключения.
 */
@Composable
fun SyncPairingButton(
    hostQrViewModel: HostQrPairingViewModel?,
    clientScanViewModel: ClientQrScanViewModel?,
    manualConnectViewModel: ManualConnectViewModel?,
    peerServer: PeerServer?,
    peerClient: SyncClient?,
) {
    val syncPaneEnabled = hostQrViewModel != null || clientScanViewModel != null
    if (!syncPaneEnabled) return

    var showSyncPanel by remember { mutableStateOf(false) }

    val hostPeers = peerServer?.connectedPeers?.collectAsState(emptySet())?.value ?: emptySet()
    val hostLifecycle = peerServer?.lifecycle?.collectAsState(ServerLifecycleState.Idle)?.value
    val clientHosts = peerClient?.connectedHosts?.collectAsState(emptySet())?.value ?: emptySet()
    val clientStates = peerClient?.pairingStates?.collectAsState(emptyMap())?.value ?: emptyMap()

    val titleBarInteraction = LocalTitleBarInteraction.current
    IconButton(
        onClick = { showSyncPanel = true },
        modifier = titleBarInteraction?.interactive(Modifier) ?: Modifier,
    ) {
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = "Синхронизация",
            tint = syncIndicatorTint(hostPeers, hostLifecycle, clientHosts, clientStates),
        )
    }

    if (showSyncPanel) {
        Dialog(onDismissRequest = { showSyncPanel = false }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .widthIn(min = 320.dp, max = 480.dp)
                    .heightIn(min = 200.dp, max = 720.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                ) {
                    if (hostQrViewModel != null) {
                        DialogHeader(title = "QR-подключение", onClose = { showSyncPanel = false })
                        HostQrPairingPanel(
                            viewModel = hostQrViewModel,
                            onCloseDialog = { showSyncPanel = false },
                        )
                    }
                    if (clientScanViewModel != null && manualConnectViewModel != null && peerClient != null) {
                        DialogHeader(title = "Подключение", onClose = { showSyncPanel = false })
                        ClientPairingPanel(
                            scanViewModel = clientScanViewModel,
                            manualViewModel = manualConnectViewModel,
                            peerClient = peerClient,
                            onClose = { showSyncPanel = false },
                            onConnected = { showSyncPanel = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Закрыть")
        }
    }
}

/**
 * Тинт кнопки синхронизации, отражающий статус соединения:
 * - **Зелёный** — у хоста есть хотя бы один пир, либо клиент сопряжён.
 * - **Жёлтый** — клиент в [PairingState.Reconnecting]/[PairingState.Error]
 *   или хост в [ServerLifecycleState.Error] (и нет зелёного).
 * - **По умолчанию** (`onSurface`) — простой / сопряжение / потеря / оба null.
 */
@Composable
private fun syncIndicatorTint(
    hostPeers: Set<DeviceInfo>,
    hostLifecycle: ServerLifecycleState?,
    clientHosts: Set<DeviceInfo>,
    clientStates: Map<String, PairingState>,
): Color {
    val anyConnected = hostPeers.isNotEmpty() || clientHosts.isNotEmpty()
    val hostUnstable = hostLifecycle is ServerLifecycleState.Error
    val anyClientUnstable = clientStates.values.any {
        it is PairingState.Reconnecting || it is PairingState.Error
    }
    return when {
        anyConnected -> SyncConnectedGreen
        hostUnstable || anyClientUnstable -> SyncUnstableYellow
        else -> MaterialTheme.colorScheme.onSurface
    }
}
