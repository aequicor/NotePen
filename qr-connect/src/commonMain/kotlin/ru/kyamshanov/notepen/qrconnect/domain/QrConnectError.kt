package ru.kyamshanov.notepen.qrconnect.domain

/** Errors surfaced by the QR pairing coordinators. */
sealed class QrConnectError(
    open val message: String,
) {
    /** The user denied the camera permission. */
    data object CameraDenied : QrConnectError("Camera permission denied")

    /** The scanned QR did not match the NotePen pairing format. */
    data object InvalidQr : QrConnectError("Scanned QR is not a NotePen pairing code")

    /** The local [ru.kyamshanov.notepen.sync.domain.port.PeerServer] failed to start. */
    data class ServerStartFailed(
        override val message: String,
    ) : QrConnectError(message)

    /** The [ru.kyamshanov.notepen.sync.domain.port.SyncClient] could not establish a session. */
    data class ConnectFailed(
        override val message: String,
    ) : QrConnectError(message)
}
