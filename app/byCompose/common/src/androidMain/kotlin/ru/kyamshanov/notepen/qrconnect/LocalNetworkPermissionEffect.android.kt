package ru.kyamshanov.notepen.qrconnect

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import ru.kyamshanov.notepen.sync.infrastructure.ACCESS_LOCAL_NETWORK_PERMISSION
import ru.kyamshanov.notepen.sync.infrastructure.LOCAL_NETWORK_PERMISSION_SDK

/**
 * On Android 17+ (API 37) LAN sockets and mDNS are gated behind the
 * `ACCESS_LOCAL_NETWORK` runtime permission; a denial fails *silently*
 * (TCP timeout / EPERM), so we prompt up front when the pairing UI opens.
 * `rememberSaveable` keeps the auto-prompt one-shot across recompositions —
 * a user who denies can still be re-prompted from the actionable error hint.
 */
@Composable
actual fun LocalNetworkPermissionEffect() {
    if (Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_SDK) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* non-blocking */ }
    LaunchedEffect(Unit) {
        val granted =
            ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted && !asked) {
            asked = true
            launcher.launch(ACCESS_LOCAL_NETWORK_PERMISSION)
        }
    }
}
