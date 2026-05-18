package ru.kyamshanov.notepen.qrconnect

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import ru.kyamshanov.notepen.qrconnect.application.ClientQrPairingCoordinator
import ru.kyamshanov.notepen.qrconnect.infrastructure.MlKitQrScanner

/**
 * Android camera slot.
 *
 * Flow:
 * - On first composition, evaluates camera-permission state.
 * - If never asked → request immediately (one-tap from the Onboarding screen).
 * - If denied without "don't ask again" → show rationale + Try-again button +
 *   "Подключиться вручную" fallback. The user can always retry.
 * - If denied with "don't ask again" → show explainer pointing to system
 *   settings + manual fallback.
 * - Once granted → mount `PreviewView`, start [MlKitQrScanner], stream into
 *   [viewModel].
 */
@Composable
actual fun CameraScanSlot(
    viewModel: ClientQrScanViewModel,
    onConnected: () -> Unit,
    onConnectManually: () -> Unit,
    onPermissionDenied: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionEverAsked by remember { mutableStateOf(false) }
    var lastDeniedPermanently by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionEverAsked = true
        hasPermission = granted
        if (!granted && activity != null) {
            lastDeniedPermanently =
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        }
    }

    // Auto-prompt the very first time the slot is shown — onboarding already
    // explained why we need the permission, so launching the system dialog
    // is the expected next step.
    LaunchedEffect(Unit) {
        if (!hasPermission && !permissionEverAsked) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val state by viewModel.state.collectAsState()
    LaunchedEffect(state) {
        if (state is ClientQrPairingCoordinator.State.Connected) onConnected()
    }
    DisposableEffect(viewModel) { onDispose { viewModel.stop() } }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        if (!hasPermission) {
            PermissionDeniedBlock(
                deniedPermanently = lastDeniedPermanently,
                onAllowClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onConnectManually = onConnectManually,
                onBack = onPermissionDenied,
            )
            return@Column
        }

        Text(
            text = "Наведите камеру на QR-код",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )

        var previewView by remember { mutableStateOf<PreviewView?>(null) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(320.dp),
                factory = { ctx -> PreviewView(ctx).also { previewView = it } },
            )
            when (val s = state) {
                ClientQrPairingCoordinator.State.Scanning -> Unit
                is ClientQrPairingCoordinator.State.Connecting -> CircularProgressIndicator()
                is ClientQrPairingCoordinator.State.Connected -> Text("Подключено", color = Color.White)
                is ClientQrPairingCoordinator.State.Failed ->
                    Text(text = "Ошибка: ${s.error.message}", color = Color.White, textAlign = TextAlign.Center)
            }
        }

        val pv = previewView
        if (pv != null) {
            val lifecycleOwner: LifecycleOwner? = remember(pv) { pv.findViewTreeLifecycleOwner() }
            if (lifecycleOwner != null) {
                DisposableEffect(pv, lifecycleOwner) {
                    val scanner = MlKitQrScanner(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        previewView = pv,
                    )
                    val job = viewModel.start(scanner)
                    onDispose { job.cancel() }
                }
            }
        }

        OutlinedButton(
            onClick = onConnectManually,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Подключиться вручную") }
    }
}

@Composable
private fun PermissionDeniedBlock(
    deniedPermanently: Boolean,
    onAllowClick: () -> Unit,
    onConnectManually: () -> Unit,
    onBack: () -> Unit,
) {
    Icon(
        imageVector = Icons.Default.QrCodeScanner,
        contentDescription = null,
        modifier = Modifier.size(72.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = if (deniedPermanently) {
            "Камера запрещена в настройках"
        } else {
            "Нужен доступ к камере"
        },
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    Text(
        text = if (deniedPermanently) {
            "Откройте Настройки → Приложения → NotePen → Разрешения и включите Камеру, " +
                "либо подключитесь к ПК вручную, без сканирования."
        } else {
            "NotePen использует камеру только для распознавания QR — кадры обрабатываются на устройстве " +
                "и никуда не отправляются. Без доступа можно подключиться вручную."
        },
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    if (!deniedPermanently) {
        Button(
            onClick = onAllowClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Дать доступ к камере")
        }
    }
    OutlinedButton(
        onClick = onConnectManually,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Подключиться вручную") }
    androidx.compose.material3.TextButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Назад") }
}
