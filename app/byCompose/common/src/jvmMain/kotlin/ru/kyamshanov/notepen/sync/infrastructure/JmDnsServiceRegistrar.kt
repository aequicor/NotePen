package ru.kyamshanov.notepen.sync.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

private const val SERVICE_TYPE = "_notepen._tcp.local."
private val logger = KotlinLogging.logger {}

/**
 * Registers and un-registers the local NotePen server as an mDNS service so
 * clients can discover it (Android: `NsdPeerDiscovery`) without scanning a QR.
 *
 * Intended to be created once the [KtorPeerServer] has started and a port is known.
 */
class JmDnsServiceRegistrar {
    private var jmdns: JmDNS? = null
    private var registered: ServiceInfo? = null

    /**
     * Registers the local server described by [device] on the LAN, advertising
     * only its id, name and port. The pairing code is deliberately **not** put on
     * the wire: a discovering client learns the host exists but must obtain the
     * code out-of-band (scan the host's QR or type it in) before it can pair.
     * Broadcasting the code would have let any LAN listener pair silently, so the
     * mDNS advert no longer carries the same trust as the QR.
     *
     * Binds JmDNS explicitly to [DeviceInfo.host] so the announcement goes
     * out on the LAN interface and not on a VPN / virtual adapter that JmDNS
     * would otherwise pick by default.
     */
    fun register(device: DeviceInfo) {
        val props = mapOf("id" to device.id, "name" to device.name)
        val serviceInfo =
            ServiceInfo.create(
                SERVICE_TYPE,
                device.name,
                device.port,
                0,
                0,
                props,
            )
        val bindAddress = runCatching { InetAddress.getByName(device.host) }.getOrNull()
        val jm = if (bindAddress != null) JmDNS.create(bindAddress, device.name) else JmDNS.create()
        jmdns = jm
        jm.registerService(serviceInfo)
        registered = serviceInfo
        logger.info { "mDNS registered: ${device.name} port=${device.port} bind=${bindAddress?.hostAddress ?: "default"}" }
    }

    /** Unregisters the service and releases resources. */
    fun unregister() {
        registered?.let { jmdns?.unregisterService(it) }
        jmdns?.close()
        jmdns = null
        registered = null
        logger.info { "mDNS unregistered" }
    }
}
