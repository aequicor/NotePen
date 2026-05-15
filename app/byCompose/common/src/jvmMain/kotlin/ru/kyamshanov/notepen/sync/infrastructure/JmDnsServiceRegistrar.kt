package ru.kyamshanov.notepen.sync.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

private const val SERVICE_TYPE = "_notepen._tcp.local."
private val logger = KotlinLogging.logger {}

/**
 * Registers and un-registers the local NotePen server as an mDNS service
 * so peers can discover it via [JmDnsDeviceDiscovery].
 *
 * Intended to be created once the [KtorPeerServer] has started and a port is known.
 */
class JmDnsServiceRegistrar {

    private var jmdns: JmDNS? = null
    private var registered: ServiceInfo? = null

    /** Registers the local server described by [device] on the LAN. */
    fun register(device: DeviceInfo) {
        val props = mapOf("id" to device.id, "name" to device.name)
        val serviceInfo = ServiceInfo.create(
            SERVICE_TYPE,
            device.name,
            device.port,
            0, 0,
            props,
        )
        val jm = JmDNS.create()
        jmdns = jm
        jm.registerService(serviceInfo)
        registered = serviceInfo
        logger.info { "mDNS registered: ${device.name} port=${device.port}" }
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
