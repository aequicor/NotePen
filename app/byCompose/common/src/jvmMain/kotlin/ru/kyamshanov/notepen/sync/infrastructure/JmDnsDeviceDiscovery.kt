package ru.kyamshanov.notepen.sync.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.port.DeviceDiscovery
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

private const val SERVICE_TYPE = "_notepen._tcp.local."
private val logger = KotlinLogging.logger {}

/**
 * JVM [DeviceDiscovery] backed by JmDNS.
 *
 * Discovers other NotePen peers advertising [SERVICE_TYPE] on the LAN.
 * Registration of the local service (when acting as host) is handled by
 * [JmDnsServiceRegistrar].
 *
 * @param ioDispatcher dispatcher for blocking JmDNS I/O
 */
class JmDnsDeviceDiscovery(private val ioDispatcher: CoroutineDispatcher) : DeviceDiscovery {

    override val peers: Flow<List<DeviceInfo>> = callbackFlow {
        val jmdns = JmDNS.create()
        val known = mutableMapOf<String, DeviceInfo>()

        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                jmdns.requestServiceInfo(event.type, event.name, 1_000)
            }

            override fun serviceResolved(event: ServiceEvent) {
                val info = event.info
                val host = info.inetAddresses.firstOrNull()?.hostAddress ?: return
                val port = info.port
                val id = info.getPropertyString("id") ?: event.name
                val name = info.getPropertyString("name") ?: event.name
                known[event.name] = DeviceInfo(id = id, name = name, host = host, port = port)
                trySend(known.values.toList())
                logger.debug { "Peer discovered: $name @ $host:$port" }
            }

            override fun serviceRemoved(event: ServiceEvent) {
                known.remove(event.name)
                trySend(known.values.toList())
                logger.debug { "Peer lost: ${event.name}" }
            }
        }

        jmdns.addServiceListener(SERVICE_TYPE, listener)
        trySend(emptyList())

        awaitClose {
            jmdns.removeServiceListener(SERVICE_TYPE, listener)
            jmdns.close()
        }
    }.flowOn(ioDispatcher)
}
