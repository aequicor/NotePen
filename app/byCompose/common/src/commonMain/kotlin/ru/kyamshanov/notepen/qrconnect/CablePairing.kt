package ru.kyamshanov.notepen.qrconnect

import kotlinx.coroutines.flow.StateFlow

/** A USB device as reported by the platform bridge (`adb devices`). */
data class CableDevice(
    val serial: String,
    /** adb device state: `device` (usable), `unauthorized`, `offline`, … */
    val state: String,
)

/**
 * UI-facing state of the cable (USB) connection path.
 *
 * The host server already binds `0.0.0.0`, so a USB-tethered tablet only needs
 * an `adb reverse tcp:<port> tcp:<port>` tunnel to reach it at `127.0.0.1:<port>`.
 */
sealed interface CableState {
    /** Not started. */
    data object Idle : CableState

    /** adb executable not found — the user must install Android platform-tools. */
    data object NoTool : CableState

    /** adb present, but no usable (`device`-state) tablet attached. */
    data object NoDevice : CableState

    /** More than one device attached; the user must pick one. */
    data class MultipleDevices(
        val devices: List<CableDevice>,
    ) : CableState

    /** The reverse tunnel is installed; the tablet can now connect to `127.0.0.1:[port]`. */
    data class Ready(
        val serial: String,
        val port: Int,
    ) : CableState

    /** Something went wrong setting up the tunnel. */
    data class Error(
        val message: String,
    ) : CableState
}

/**
 * Platform bridge that makes the desktop host reachable from a USB-tethered
 * Android tablet over the cable. Desktop supplies the `adb reverse`-backed
 * implementation; a `null` instance hides the cable UI on platforms without it.
 *
 * [start] must be called only once the host server is running (so [port] is the
 * bound port). Both methods are idempotent and run their process work off the
 * caller thread.
 */
interface CablePairing {
    /** Current cable state; drives the host pairing panel's USB section. */
    val state: StateFlow<CableState>

    /**
     * Installs `adb reverse tcp:[port] tcp:[port]`. When [serial] is null and
     * exactly one device is attached, that device is used; with several attached
     * the state becomes [CableState.MultipleDevices] for the user to disambiguate.
     */
    suspend fun start(
        port: Int,
        serial: String? = null,
    )

    /** Removes the reverse tunnel (best-effort) and resets [state] to [CableState.Idle]. */
    suspend fun stop(
        port: Int,
        serial: String? = null,
    )
}
