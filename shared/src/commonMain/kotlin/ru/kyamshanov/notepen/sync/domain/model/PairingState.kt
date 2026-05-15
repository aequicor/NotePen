package ru.kyamshanov.notepen.sync.domain.model

/** UI-facing state of the pairing / server lifecycle. */
sealed class PairingState {

    /** Server has not been started yet. */
    data object Idle : PairingState()

    /** Server is running and waiting for a client to connect. */
    data class AwaitingConnection(val code: String, val host: String, val port: Int) : PairingState()

    /** A client connected but hasn't sent a valid pairing code yet. */
    data object AwaitingCode : PairingState()

    /** Pairing is complete — channel is ready for sync. */
    data class Connected(val peer: DeviceInfo) : PairingState()

    /** An error occurred (server failed to start, connection dropped, etc.). */
    data class Error(val message: String) : PairingState()
}
