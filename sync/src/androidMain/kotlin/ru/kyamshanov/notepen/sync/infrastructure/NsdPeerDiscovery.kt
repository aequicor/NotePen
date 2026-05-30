package ru.kyamshanov.notepen.sync.infrastructure

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.DiscoveredHost
import ru.kyamshanov.notepen.sync.domain.port.PeerDiscovery
import java.util.concurrent.ConcurrentLinkedQueue

private val logger = KotlinLogging.logger {}

/**
 * Android [PeerDiscovery] backed by [NsdManager] (DNS-SD), browsing the
 * `_notepen._tcp` service the desktop host advertises. Resolves each found
 * service to read its host/port and the pairing code from the `c` TXT record.
 *
 * Threading: NsdManager callbacks arrive on a binder thread; state is published
 * through a thread-safe [MutableStateFlow]. Resolves are serialized through a
 * queue because pre-Android-10 `resolveService` rejects a concurrent resolve
 * with `FAILURE_ALREADY_ACTIVE`. A Wi-Fi multicast lock is held while browsing
 * so OEMs that gate multicast (e.g. some Huawei/EMUI builds) still receive mDNS.
 */
class NsdPeerDiscovery(
    context: Context,
) : PeerDiscovery {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _discoveredHosts = MutableStateFlow<Set<DiscoveredHost>>(emptySet())
    override val discoveredHosts: StateFlow<Set<DiscoveredHost>> = _discoveredHosts.asStateFlow()

    private val lock = Any()
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    // serviceName → resolved host; lets onServiceLost remove the right entry.
    private val byServiceName = mutableMapOf<String, DiscoveredHost>()
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private var resolving = false

    override fun start() {
        synchronized(lock) {
            if (discoveryListener != null) return
            multicastLock =
                wifiManager?.createMulticastLock("notepen-nsd")?.apply {
                    setReferenceCounted(false)
                    runCatching { acquire() }
                }
            val listener = newDiscoveryListener()
            discoveryListener = listener
            runCatching {
                nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            }.onFailure { logger.warn { "discoverServices failed: ${it.message}" } }
        }
    }

    override fun stop() {
        synchronized(lock) {
            discoveryListener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
            discoveryListener = null
            resolveQueue.clear()
            resolving = false
            byServiceName.clear()
            _discoveredHosts.value = emptySet()
            multicastLock?.let { runCatching { if (it.isHeld) it.release() } }
            multicastLock = null
        }
    }

    private fun newDiscoveryListener(): NsdManager.DiscoveryListener =
        object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(
                serviceType: String,
                errorCode: Int,
            ) {
                logger.warn { "NSD start failed: $errorCode" }
            }

            override fun onStopDiscoveryFailed(
                serviceType: String,
                errorCode: Int,
            ) {
                logger.warn { "NSD stop failed: $errorCode" }
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains(SERVICE_TYPE_FRAGMENT)) enqueueResolve(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) = remove(service.serviceName)
        }

    private fun enqueueResolve(service: NsdServiceInfo) {
        synchronized(lock) {
            resolveQueue.add(service)
            resolveNext()
        }
    }

    private fun resolveNext() {
        synchronized(lock) {
            if (resolving) return
            val next = resolveQueue.poll() ?: return
            resolving = true
            runCatching {
                nsdManager.resolveService(next, newResolveListener())
            }.onFailure {
                logger.warn { "resolveService threw: ${it.message}" }
                resolving = false
                resolveNext()
            }
        }
    }

    private fun newResolveListener(): NsdManager.ResolveListener =
        object : NsdManager.ResolveListener {
            override fun onResolveFailed(
                serviceInfo: NsdServiceInfo,
                errorCode: Int,
            ) {
                logger.warn { "resolve failed for ${serviceInfo.serviceName}: $errorCode" }
                onResolveDone()
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                runCatching { addResolved(serviceInfo) }
                    .onFailure { logger.warn { "addResolved failed: ${it.message}" } }
                onResolveDone()
            }
        }

    private fun onResolveDone() {
        synchronized(lock) {
            resolving = false
            resolveNext()
        }
    }

    private fun addResolved(info: NsdServiceInfo) {
        val host = info.host?.hostAddress ?: return
        val port = info.port
        if (port <= 0) return
        val attrs = info.attributes
        val name = attrs["name"]?.toString(Charsets.UTF_8) ?: info.serviceName
        val code = attrs["c"]?.toString(Charsets.UTF_8).orEmpty()
        // Mirror PairingUri.toServerDeviceInfo: the real host id arrives in
        // PairAccepted, so a host:port placeholder is the correct seed here.
        val discovered =
            DiscoveredHost(
                deviceInfo = DeviceInfo(id = "$host:$port", name = name, host = host, port = port),
                code = code,
            )
        synchronized(lock) {
            byServiceName[info.serviceName] = discovered
            _discoveredHosts.value = byServiceName.values.toSet()
        }
    }

    private fun remove(serviceName: String) {
        synchronized(lock) {
            if (byServiceName.remove(serviceName) != null) {
                _discoveredHosts.value = byServiceName.values.toSet()
            }
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_notepen._tcp."
        const val SERVICE_TYPE_FRAGMENT = "_notepen._tcp"
    }
}
