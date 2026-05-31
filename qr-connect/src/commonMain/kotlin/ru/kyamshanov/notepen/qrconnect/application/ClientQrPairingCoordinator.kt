package ru.kyamshanov.notepen.qrconnect.application

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import ru.kyamshanov.notepen.qrconnect.domain.PairingUri
import ru.kyamshanov.notepen.qrconnect.domain.QrConnectError
import ru.kyamshanov.notepen.qrconnect.domain.port.QrScanner
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * Drives one mobile-side pairing attempt:
 *
 * 1. Subscribes to [scanner] and waits for the first valid [PairingUri].
 *    Non-NotePen QRs are silently ignored — the camera stays open.
 * 2. Calls [SyncClient.connect] (which is suspending — it returns when pairing
 *    either succeeds or definitively fails) using the host coordinates from
 *    the QR.
 * 3. Mirrors the result into a UI-friendly [State].
 *
 * The flow terminates after [State.Connected] or [State.Failed]. The newly
 * paired host stays connected in [SyncClient] until the user explicitly
 * disconnects it via the connections list.
 */
class ClientQrPairingCoordinator(
    private val syncClient: SyncClient,
    private val scanner: QrScanner,
    private val selfInfo: DeviceInfo,
    /**
     * Invoked once pairing succeeds, with the scanned [PairingUri] and the host's resolved
     * [DeviceInfo] (whose `id` is the real peer id). Lets the caller register the peer's shared
     * library on the shelf in the same step (the library layer lives above `:qr-connect`, so the
     * actual `registry.connect` happens in the supplied callback). Defaults to a no-op for callers
     * that only want a transport pairing. A failure here never fails the pairing.
     */
    private val onLibraryPaired: suspend (PairingUri, DeviceInfo) -> Unit = { _, _ -> },
    private val connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
) {
    /** UI-facing state machine for the client-side scan flow. */
    sealed class State {
        /** Camera is open; waiting for the user to point at a valid QR. */
        data object Scanning : State()

        /** A valid pairing URI has been scanned; the client is dialling the host. */
        data class Connecting(
            val uri: PairingUri,
        ) : State()

        /** Pairing succeeded — sync messages now flow through [syncClient]. */
        data class Connected(
            val server: DeviceInfo,
        ) : State()

        /** Pairing failed (rejected code, network error, etc.). */
        data class Failed(
            val error: QrConnectError,
        ) : State()
    }

    fun run(): Flow<State> =
        flow {
            emit(State.Scanning)
            val uri =
                scanner
                    .scans()
                    .mapNotNull(PairingUri::parse)
                    .first()
            logger.info { "Scanned pairing URI for ${uri.host}:${uri.port}" }
            emit(State.Connecting(uri))

            val result =
                withTimeoutOrNull(connectTimeout) {
                    runCatching {
                        syncClient.connect(uri.toServerDeviceInfo(), uri.code, selfInfo)
                    }.getOrElse { e ->
                        if (e is CancellationException) throw e
                        Result.failure(e)
                    }
                }
            when {
                result == null -> {
                    emit(State.Failed(QrConnectError.ConnectFailed("Таймаут подключения. Проверьте сеть / VPN.")))
                }
                result.isSuccess -> {
                    val server = result.getOrThrow()
                    // Bridge transport pairing → library shelf: the scanned library is registered in
                    // one step, then filled in by the existing catalog coordinator. Registration must
                    // never fail the pairing itself.
                    runCatching { onLibraryPaired(uri, server) }
                        .onFailure { logger.warn { "Auto-adding library after pair failed: ${it.message}" } }
                    emit(State.Connected(server))
                }
                else -> {
                    val msg = result.exceptionOrNull()?.message ?: "Не удалось подключиться"
                    emit(State.Failed(QrConnectError.ConnectFailed(msg)))
                }
            }
        }

    companion object {
        val DEFAULT_CONNECT_TIMEOUT: Duration = 12.seconds
    }
}
