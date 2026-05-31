package ru.kyamshanov.notepen.qrconnect

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryRegistry
import ru.kyamshanov.notepen.qrconnect.domain.PairingUri
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo

private val logger = KotlinLogging.logger {}

/**
 * Builds the durable [LibraryConnection.PeerLan] for a peer just paired via [uri].
 *
 * The peer id is the host's **resolved** [DeviceInfo.id] (from `PairAccepted`), not the QR's
 * host:port placeholder. Host, port and pairing code are taken from the QR so the library can later
 * reconnect by dialing the host directly — bypassing mDNS, which is the whole point of connecting by
 * QR under VPN / AP isolation. [PairingUri.libraryId] scopes the connection to the scanned library
 * (blank = the host's whole shelf).
 */
fun peerLanConnectionFor(
    uri: PairingUri,
    server: DeviceInfo,
): LibraryConnection.PeerLan =
    LibraryConnection.PeerLan(
        peerId = server.id,
        host = uri.host.ifBlank { server.host.ifBlank { null } },
        port = uri.port,
        libraryId = uri.libraryId,
        libraryName = uri.libraryName,
        pairingCode = uri.code,
    )

/**
 * The `onLibraryPaired` bridge for the client QR/manual flows: on a successful pair it registers the
 * peer's shared library (see [peerLanConnectionFor]) so a scanned library lands on the shelf in one
 * step; the existing catalog coordinator then fills in its books. [registryProvider] is lazy because
 * the registry may still be wiring (Android `HeavyDeps`). A registration failure is logged, never
 * thrown — it must not fail the pairing.
 */
fun peerLibraryRegistrar(registryProvider: () -> LibraryRegistry?): suspend (PairingUri, DeviceInfo) -> Unit =
    { uri, server ->
        val registry = registryProvider()
        if (registry == null) {
            logger.warn { "Paired with ${server.id} but the library registry is not ready — library not added" }
        } else {
            registry
                .connect(peerLanConnectionFor(uri, server))
                .onFailure { logger.warn(it) { "Auto-adding PeerLan library after pair failed" } }
        }
    }
