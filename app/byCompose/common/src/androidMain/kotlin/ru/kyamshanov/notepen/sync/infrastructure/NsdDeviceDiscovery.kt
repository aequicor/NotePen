package ru.kyamshanov.notepen.sync.infrastructure

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.port.DeviceDiscovery

private const val SERVICE_TYPE = "_notepen._tcp"
private val logger = KotlinLogging.logger {}

/**
 * Android [DeviceDiscovery] backed by [NsdManager].
 *
 * Discovers peer NotePen servers advertising [SERVICE_TYPE] on the LAN.
 */
class NsdDeviceDiscovery(private val context: Context) : DeviceDiscovery {

    override val peers: Flow<List<DeviceInfo>> = callbackFlow {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val known = mutableMapOf<String, DeviceInfo>()

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
                logger.warn { "NSD resolve failed: $code for ${info.serviceName}" }
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress ?: return
                val port = info.port
                val name = info.serviceName
                known[name] = DeviceInfo(id = name, name = name, host = host, port = port)
                trySend(known.values.toList())
                logger.debug { "NSD resolved: $name @ $host:$port" }
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, code: Int) {
                logger.warn { "NSD discovery start failed: $code" }
            }

            override fun onStopDiscoveryFailed(serviceType: String, code: Int) {
                logger.warn { "NSD discovery stop failed: $code" }
            }

            override fun onDiscoveryStarted(serviceType: String) {
                logger.info { "NSD discovery started: $serviceType" }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                logger.info { "NSD discovery stopped" }
            }

            override fun onServiceFound(info: NsdServiceInfo) {
                nsdManager.resolveService(info, resolveListener)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                known.remove(info.serviceName)
                trySend(known.values.toList())
                logger.debug { "NSD lost: ${info.serviceName}" }
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        trySend(emptyList())

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        }
    }
}
