package ru.kyamshanov.notepen.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kyamshanov.notepen.sync.domain.model.PairingState

/**
 * Host-side pairing screen.
 *
 * Shows the 6-digit pairing code for clients to enter in [SyncScreen].
 * Driven by [HostViewModel].
 */
@Composable
fun HostScreen(
    viewModel: HostViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.serverState.collectAsState()

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Хост (сервер)", style = MaterialTheme.typography.titleLarge)

        when (state) {
            is PairingState.Idle -> {
                Button(onClick = viewModel::startServer) { Text("Запустить сервер") }
            }

            is PairingState.AwaitingConnection -> {
                val s = state as PairingState.AwaitingConnection
                Text("Сервер запущен", style = MaterialTheme.typography.bodyMedium)
                Text("${s.host}:${s.port}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("Код сопряжения:", style = MaterialTheme.typography.labelLarge)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = s.code,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
                        fontSize = 48.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Button(onClick = viewModel::stopServer) { Text("Остановить") }
            }

            is PairingState.AwaitingCode -> {
                CircularProgressIndicator()
                Text("Клиент подключается...", style = MaterialTheme.typography.bodyMedium)
            }

            is PairingState.Connected -> {
                val s = state as PairingState.Connected
                Text("Подключено: ${s.peer.name}", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = viewModel::stopServer) { Text("Отключить") }
            }

            is PairingState.Error -> {
                Text(
                    "Ошибка: ${(state as PairingState.Error).message}",
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = viewModel::startServer) { Text("Повторить") }
            }
        }
    }
}
