package ru.kyamshanov.notepen.qrconnect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Desktop stub. There's no camera on the JVM target, so the slot only offers
 * the manual fallback and a way back to onboarding.
 */
@Composable
actual fun CameraScanSlot(
    viewModel: ClientQrScanViewModel,
    onConnected: () -> Unit,
    onConnectManually: () -> Unit,
    onPermissionDenied: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Сканирование QR доступно только на мобильном устройстве.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(
            onClick = onConnectManually,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Подключиться вручную") }
        TextButton(
            onClick = onPermissionDenied,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Назад") }
    }
}
