package ru.kyamshanov.notepen.sync.domain.model

/**
 * Lifecycle of the local [ru.kyamshanov.notepen.sync.domain.port.PeerServer].
 *
 * Distinct from per-peer state: the server can be [Running] with zero, one or
 * many connected peers — peer membership is exposed separately via
 * [ru.kyamshanov.notepen.sync.domain.port.PeerServer.connectedPeers].
 */
sealed class ServerLifecycleState {
    /** Server has not been started yet (or finished tearing down after [Stopped]). */
    data object Idle : ServerLifecycleState()

    /**
     * Server is up and accepting connections on [host]:[port]; any client that
     * presents the [code] is a pairing candidate (subject to manual approval).
     * The same [code] is valid for the lifetime of the current [Running] state.
     */
    data class Running(val host: String, val port: Int, val code: String) : ServerLifecycleState()

    /** Server has been asked to stop and is in the process of tearing down. */
    data object Stopped : ServerLifecycleState()

    /** Server failed to start or has aborted unexpectedly. */
    data class Error(val message: String) : ServerLifecycleState()
}
