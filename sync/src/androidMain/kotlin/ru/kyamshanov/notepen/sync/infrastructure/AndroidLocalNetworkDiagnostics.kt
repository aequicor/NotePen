package ru.kyamshanov.notepen.sync.infrastructure

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import ru.kyamshanov.notepen.sync.domain.model.LocalNetworkHealth
import ru.kyamshanov.notepen.sync.domain.port.LocalNetworkDiagnostics

/**
 * The Android 17 (API 37) `ACCESS_LOCAL_NETWORK` runtime permission gating LAN
 * sockets and mDNS. Referenced by string: the constant doesn't exist in the
 * SDK 36 this module compiles against, but runtime checks resolve it by name.
 */
const val ACCESS_LOCAL_NETWORK_PERMISSION: String = "android.permission.ACCESS_LOCAL_NETWORK"

/** First SDK where [ACCESS_LOCAL_NETWORK_PERMISSION] is enforced for apps targeting it. */
const val LOCAL_NETWORK_PERMISSION_SDK: Int = 37

/**
 * [LocalNetworkDiagnostics] backed by ConnectivityManager and the runtime
 * permission state.
 *
 * Both signals explain *silent* LAN failures: a default-configured Android VPN
 * routes all app traffic (including LAN sockets) into the tunnel, and a denied
 * `ACCESS_LOCAL_NETWORK` makes outbound TCP time out with no permission error.
 */
class AndroidLocalNetworkDiagnostics(
    private val context: Context,
) : LocalNetworkDiagnostics {
    override fun snapshot(): LocalNetworkHealth =
        LocalNetworkHealth(
            localNetworkPermissionGranted = isLocalNetworkPermissionGranted(),
            vpnActive = isVpnActive(),
        )

    private fun isLocalNetworkPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_SDK) return true
        return context.checkSelfPermission(ACCESS_LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    private fun isVpnActive(): Boolean {
        val connectivity =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return runCatching {
            val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }.getOrDefault(false)
    }
}
