package ru.kyamshanov.notepen.qrconnect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.DiscoveredHost
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

/**
 * Which screen the client pairing dialog is currently showing.
 *
 * The panel auto-picks a sensible default based on whether the user has any
 * active connections and whether camera permission is granted; the user can
 * then navigate explicitly via the action buttons.
 */
enum class ClientPairingScreen {
    /** Pre-permission rationale (Google-guideline-compliant onboarding). */
    Onboarding,

    /** Live camera + QR scan. Requires camera permission. */
    Camera,

    /** Manual host / port / code entry. */
    Manual,

    /** mDNS-discovered hosts on the LAN — one-tap connect, no QR. */
    Discovery,

    /** List of currently connected hosts with per-host disconnect. */
    Connections,
}

/**
 * Multi-host client pairing dialog.
 *
 * Owns the screen state machine (onboarding → camera | manual → connections).
 * The actual camera widget is platform-specific and provided by [CameraScanSlot].
 *
 * @param scanViewModel drives the active QR-scan attempt
 * @param manualViewModel drives the manual host/port/code form
 * @param peerClient multi-host client; backs the connections list and disconnect actions
 * @param onClose called when the user dismisses the dialog via "Отмена"
 * @param onConnected called when a new host connection is established
 */
@Composable
fun ClientPairingPanel(
    scanViewModel: ClientQrScanViewModel,
    manualViewModel: ManualConnectViewModel,
    peerClient: SyncClient,
    onClose: () -> Unit,
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
    discoveryViewModel: HostDiscoveryViewModel? = null,
) {
    val connectedHosts by peerClient.connectedHosts.collectAsState(emptySet())
    val coroutineScope = rememberCoroutineScope()

    var screen by remember {
        mutableStateOf(
            when {
                connectedHosts.isNotEmpty() -> ClientPairingScreen.Connections
                // Prefer one-tap LAN discovery as the entry point when available.
                discoveryViewModel != null -> ClientPairingScreen.Discovery
                else -> ClientPairingScreen.Onboarding
            },
        )
    }
    // When a new host appears mid-flow, the pairing succeeded — close the dialog.
    val prevHostCount = remember { mutableStateOf(connectedHosts.size) }
    LaunchedEffect(connectedHosts.size) {
        if (connectedHosts.size > prevHostCount.value) {
            onConnected()
        }
        prevHostCount.value = connectedHosts.size
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (screen) {
            ClientPairingScreen.Onboarding ->
                OnboardingScreen(
                    onContinueWithCamera = { screen = ClientPairingScreen.Camera },
                    onConnectManually = { screen = ClientPairingScreen.Manual },
                    onClose = onClose,
                )
            ClientPairingScreen.Camera ->
                CameraScanSlot(
                    viewModel = scanViewModel,
                    onConnected = { screen = ClientPairingScreen.Connections },
                    onConnectManually = { screen = ClientPairingScreen.Manual },
                    onPermissionDenied = { screen = ClientPairingScreen.Onboarding },
                )
            ClientPairingScreen.Manual ->
                ManualScreen(
                    viewModel = manualViewModel,
                    onBackToScan = { screen = ClientPairingScreen.Camera },
                    onConnected = { screen = ClientPairingScreen.Connections },
                )
            ClientPairingScreen.Discovery ->
                if (discoveryViewModel != null) {
                    DiscoveryScreen(
                        viewModel = discoveryViewModel,
                        onConnected = { screen = ClientPairingScreen.Connections },
                        onScanQr = { screen = ClientPairingScreen.Camera },
                        onManual = { screen = ClientPairingScreen.Manual },
                    )
                } else {
                    screen = ClientPairingScreen.Onboarding
                }
            ClientPairingScreen.Connections ->
                ConnectionsScreen(
                    hosts = connectedHosts.toList(),
                    onDisconnect = { hostId ->
                        coroutineScope.launch { peerClient.disconnect(hostId) }
                    },
                    onDisconnectAll = {
                        coroutineScope.launch { peerClient.disconnectAll() }
                    },
                    onAddViaQr = { screen = ClientPairingScreen.Camera },
                    onAddManually = { screen = ClientPairingScreen.Manual },
                    onDiscover = if (discoveryViewModel != null) ({ screen = ClientPairingScreen.Discovery }) else null,
                )
        }
    }
}

@Composable
private fun OnboardingScreen(
    onContinueWithCamera: () -> Unit,
    onConnectManually: () -> Unit,
    onClose: () -> Unit,
) {
    Icon(
        imageVector = Icons.Default.QrCodeScanner,
        contentDescription = null,
        modifier = Modifier.size(72.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = "Сканирование QR для подключения",
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    Text(
        text =
            "Чтобы подключиться к компьютеру, наведите камеру на QR-код. " +
                "Камера используется только для распознавания QR — ничего не записывается, " +
                "ничего не отправляется наружу.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Button(
        onClick = onContinueWithCamera,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.PhotoCamera, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Разрешить доступ к камере")
    }
    OutlinedButton(
        onClick = onConnectManually,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Подключиться вручную") }
    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
}

@Composable
private fun ManualScreen(
    viewModel: ManualConnectViewModel,
    onBackToScan: () -> Unit,
    onConnected: () -> Unit,
) {
    val payload by viewModel.payload.collectAsState()
    val canConnect by viewModel.canConnect.collectAsState()
    val status by viewModel.status.collectAsState()

    LaunchedEffect(status) {
        if (status is ManualConnectViewModel.Status.Connected) onConnected()
    }

    Text(
        text = "Подключиться вручную",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Text(
        text = "Скопируйте строку из диалога «Данные для ручного подключения» на ПК и вставьте сюда.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
    OutlinedTextField(
        value = payload,
        onValueChange = viewModel::onPayloadChange,
        label = { Text("notepen://pair?…") },
        singleLine = false,
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    when (val s = status) {
        is ManualConnectViewModel.Status.Connecting ->
            Text(
                text = "Подключаемся…",
                style = MaterialTheme.typography.bodySmall,
            )
        is ManualConnectViewModel.Status.Failed ->
            Text(
                text = "Ошибка: ${s.message}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        else -> Unit
    }
    Button(
        onClick = viewModel::connect,
        enabled = canConnect,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Подключиться") }
    OutlinedButton(
        onClick = onBackToScan,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Назад к сканированию") }
}

/** List of mDNS-discovered hosts; each row connects on tap. */
@Composable
private fun DiscoveredHostsList(
    hosts: List<DiscoveredHost>,
    connectingHostId: String?,
    onConnect: (DiscoveredHost) -> Unit,
) {
    if (hosts.isEmpty()) {
        Text(
            text = "Ищем доступные ПК…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        hosts.forEach { host ->
            val connecting = connectingHostId == host.deviceInfo.id
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(host.deviceInfo.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${host.deviceInfo.host}:${host.deviceInfo.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { onConnect(host) }, enabled = !connecting) {
                    Text(if (connecting) "Подключение…" else "Подключиться")
                }
            }
        }
    }
}

/**
 * LAN discovery screen: browses for NotePen hosts via mDNS and connects with the
 * code carried in the host's advert. Browsing is bound to this screen's lifetime
 * via [DisposableEffect]. Falls back to QR / manual entry.
 */
@Composable
private fun DiscoveryScreen(
    viewModel: HostDiscoveryViewModel,
    onConnected: () -> Unit,
    onScanQr: () -> Unit,
    onManual: () -> Unit,
) {
    val hosts by viewModel.hosts.collectAsState()
    val status by viewModel.status.collectAsState()

    DisposableEffect(viewModel) {
        viewModel.startDiscovery()
        onDispose { viewModel.stopDiscovery() }
    }
    LaunchedEffect(status) {
        when (status) {
            is HostDiscoveryViewModel.Status.Connected -> onConnected()
            HostDiscoveryViewModel.Status.NeedsManualCode -> onManual()
            else -> Unit
        }
    }

    Text(
        text = "Поиск ПК в сети",
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    Text(
        text = "Убедитесь, что ПК и планшет в одной сети Wi-Fi, а на ПК включён QR-сервер.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )

    DiscoveredHostsList(
        hosts = hosts,
        connectingHostId = (status as? HostDiscoveryViewModel.Status.Connecting)?.hostId,
        onConnect = viewModel::connect,
    )

    (status as? HostDiscoveryViewModel.Status.Failed)?.let { failed ->
        Text(
            text = "Ошибка: ${failed.message}",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onScanQr, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("По QR")
        }
        OutlinedButton(onClick = onManual, modifier = Modifier.weight(1f)) { Text("Вручную") }
    }
}

@Composable
private fun ConnectionsScreen(
    hosts: List<DeviceInfo>,
    onDisconnect: (String) -> Unit,
    onDisconnectAll: () -> Unit,
    onAddViaQr: () -> Unit,
    onAddManually: () -> Unit,
    onDiscover: (() -> Unit)? = null,
) {
    Text(
        text = "Активные подключения",
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    if (hosts.isEmpty()) {
        Text(
            text = "Нет активных подключений",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            hosts.forEach { peer ->
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

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    Text(
        text = "Подключиться к ещё одному ПК",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.fillMaxWidth(),
    )
    onDiscover?.let { discover ->
        Button(onClick = discover, modifier = Modifier.fillMaxWidth()) { Text("Найти ПК в сети") }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onAddViaQr,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("По QR")
        }
        OutlinedButton(
            onClick = onAddManually,
            modifier = Modifier.weight(1f),
        ) { Text("Вручную") }
    }
    if (hosts.isNotEmpty()) {
        OutlinedButton(
            onClick = onDisconnectAll,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Отключиться от всех") }
    }
}

/**
 * Platform-provided camera + permission UI slot.
 *
 * The Android `actual` handles permission request flow + CameraX preview +
 * ML Kit scanner + connection status. The JVM `actual` shows a short notice
 * (no camera available).
 */
@Composable
expect fun CameraScanSlot(
    viewModel: ClientQrScanViewModel,
    onConnected: () -> Unit,
    onConnectManually: () -> Unit,
    onPermissionDenied: () -> Unit,
)
