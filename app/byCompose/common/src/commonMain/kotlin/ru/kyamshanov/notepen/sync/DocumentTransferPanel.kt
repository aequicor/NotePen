package ru.kyamshanov.notepen.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import ru.kyamshanov.notepen.sync.domain.port.TransferProgress

/**
 * Compact panel shown in the editor when a peer is connected.
 *
 * Lets the host send the current document to the peer. Displays a
 * [LinearProgressIndicator] while the transfer is in progress.
 */
@Composable
fun DocumentTransferPanel(
    peerName: String,
    transferProgress: StateFlow<TransferProgress?>,
    onSendDocument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by transferProgress.collectAsState()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Подключено: $peerName",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onSendDocument,
                enabled = progress == null,
            ) {
                Text("Отправить PDF")
            }
        }

        val p = progress
        if (p != null) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { p.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${(p.fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
