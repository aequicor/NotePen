package ru.kyamshanov.notepen.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.PairingState

/**
 * Client-side pairing screen.
 *
 * Shows discovered peers, lets the user tap one, enter the 6-digit code
 * shown on the host, and initiates the WebSocket pairing handshake.
 */
@Composable
fun SyncScreen(
    viewModel: SyncViewModel,
    modifier: Modifier = Modifier,
) {
    val peers by viewModel.peers.collectAsState()
    val state by viewModel.connectionState.collectAsState()

    LaunchedEffect(Unit) { viewModel.startDiscovery() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Синхронизация", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        when (state) {
            is PairingState.Connected -> ConnectedBanner(
                peer = (state as PairingState.Connected).peer,
                onDisconnect = viewModel::disconnect,
            )
            is PairingState.Error -> ErrorBanner(message = (state as PairingState.Error).message)
            else -> Unit
        }

        Spacer(Modifier.height(8.dp))

        ManualConnectCard(
            onConnect = { host, port, code ->
                viewModel.connect(
                    server = DeviceInfo(id = "manual:$host:$port", name = "$host:$port", host = host, port = port),
                    code = code,
                )
            },
        )

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Устройства в сети", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = viewModel::startDiscovery) {
                Icon(Icons.Default.Refresh, contentDescription = "Обновить")
            }
        }

        if (peers.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Поиск...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(peers) { device ->
                    PeerCard(device = device, onConnect = { code ->
                        viewModel.connect(server = device, code = code)
                    })
                }
            }
        }
    }
}

/**
 * Manual host:port pairing form. Lets the user connect to a server directly
 * when mDNS discovery is unavailable (e.g. the host is on a VPN that captures
 * multicast, or the two devices are on different subnets).
 */
@Composable
private fun ManualConnectCard(onConnect: (host: String, port: Int, code: String) -> Unit) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    val portInt = port.toIntOrNull()
    val canConnect = host.isNotBlank() && portInt != null && portInt in 1..65_535 && code.length == 6

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Подключиться по адресу", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text("Хост") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { new -> port = new.filter { it.isDigit() }.take(5) },
                    label = { Text("Порт") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { new -> code = new.filter { it.isDigit() }.take(6) },
                label = { Text("Код сопряжения") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onConnect(host, portInt ?: return@Button, code) },
                enabled = canConnect,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Подключиться")
            }
        }
    }
}

@Composable
private fun PeerCard(device: DeviceInfo, onConnect: (code: String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DevicesOther, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(device.name, style = MaterialTheme.typography.bodyLarge)
                    Text("${device.host}:${device.port}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it },
                    label = { Text("Код сопряжения") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onConnect(code) },
                    enabled = code.length == 6,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Подключиться")
                }
            }
        }
    }
}

@Composable
private fun ConnectedBanner(peer: DeviceInfo, onDisconnect: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Подключено", style = MaterialTheme.typography.labelLarge)
                Text(peer.name, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onDisconnect) { Text("Отключить") }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}
