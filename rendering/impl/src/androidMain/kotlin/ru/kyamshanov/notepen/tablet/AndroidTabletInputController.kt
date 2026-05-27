package ru.kyamshanov.notepen.tablet

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Side-channel for Android stylus state that is not exposed via Compose's
 * commonMain `PointerInputChange` API — barrel-button state, eraser-tip
 * tool type, tilt, and hover position. Pressure is also republished here
 * for symmetry with the desktop controllers, even though the drawing
 * pipeline reads it directly from `PointerInputChange.pressure`.
 *
 * Fed by [Modifier.stylusEventSink] which attaches a passive
 * `pointerInteropFilter` to the drawing surface.
 */
class AndroidTabletInputController : TabletInputController {
    private val pressureFlow = MutableStateFlow(1f)
    private val barrelFlow = MutableStateFlow(false)
    private val eraserFlow = MutableStateFlow(false)
    private val tiltFlow = MutableStateFlow(0f)
    private val hoverFlow = MutableStateFlow<Offset?>(null)
    private val stylusSeenFlow = MutableStateFlow(false)

    /**
     * `elapsedRealtime()` ms of the last stylus / eraser event we saw, or
     * `0L` if none yet. Used to decide whether a finger event should be
     * treated as a "stylus disconnected, fall back to finger" recovery
     * signal — see [feed].
     */
    private var lastStylusEventAtMs: Long = 0L

    override val latestPressure: StateFlow<Float> = pressureFlow.asStateFlow()
    override val barrelPressed: StateFlow<Boolean> = barrelFlow.asStateFlow()
    override val eraserTipActive: StateFlow<Boolean> = eraserFlow.asStateFlow()
    override val tilt: StateFlow<Float> = tiltFlow.asStateFlow()
    override val hoverPosition: StateFlow<Offset?> = hoverFlow.asStateFlow()
    override val stylusEverSeen: StateFlow<Boolean> = stylusSeenFlow.asStateFlow()

    /**
     * Parse a single [event] and update the state-flows. `viewWidth` and
     * `viewHeight` are needed to normalise hover coordinates into the same
     * `[0..1]` space the drawing surface uses.
     *
     * Returns immediately for non-stylus events to keep the cost of a finger
     * tap negligible.
     */
    fun feed(
        event: MotionEvent,
        viewWidth: Int,
        viewHeight: Int,
    ) {
        val index = event.actionIndex.coerceAtLeast(0)
        val toolType = event.getToolType(index)
        val isStylus =
            toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        val nowMs = SystemClock.elapsedRealtime()

        if (!isStylus) {
            // Recovery edge: on some Samsung tablets the S-Pen BLE link
            // hangs and stylus events stop arriving entirely. Without this
            // branch, [stylusSeenFlow] stayed latched at `true` forever,
            // Pencil Mode kept palm-rejection on, and the user could not
            // draw with a finger either — the app appeared dead until a
            // device reboot. If a finger event arrives after the stylus
            // has been silent past [STYLUS_INACTIVITY_RECOVERY_MS], treat
            // that as "pen gone" and unlatch.
            if (stylusSeenFlow.value &&
                nowMs - lastStylusEventAtMs >= STYLUS_INACTIVITY_RECOVERY_MS
            ) {
                stylusSeenFlow.value = false
            }
            return
        }

        // Latch on first stylus / eraser-tip event — drives Pencil Mode
        // auto-enable in DetailsContent. Hover events count too: knowing a
        // stylus is present without contact is enough to flip the default.
        stylusSeenFlow.value = true
        lastStylusEventAtMs = nowMs

        val action = event.actionMasked

        // Tool flip: eraser tip is reported as TOOL_TYPE_ERASER from DOWN.
        eraserFlow.value = toolType == MotionEvent.TOOL_TYPE_ERASER &&
            action != MotionEvent.ACTION_UP &&
            action != MotionEvent.ACTION_HOVER_EXIT &&
            action != MotionEvent.ACTION_CANCEL

        // Barrel / side button. BUTTON_STYLUS_PRIMARY is the canonical bit;
        // some pens (Wacom EMR) also report SECONDARY on a second button.
        val buttonMask = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY
        barrelFlow.value = (event.buttonState and buttonMask) != 0

        // Pressure (latest pointer only; we don't aggregate).
        val p = event.getPressure(index)
        if (p in 0f..1f) pressureFlow.value = p

        // Tilt is reported in radians from the screen normal (0 = perpendicular,
        // PI/2 = parallel). Normalise to [0..1] with a clamp.
        val tiltRad = event.getAxisValue(MotionEvent.AXIS_TILT, index)
        val normalisedTilt = (tiltRad / TILT_FULL_RAD).coerceIn(0f, 1f)
        if (!normalisedTilt.isNaN()) tiltFlow.value = normalisedTilt

        // Hover: ENTER/MOVE publish position; EXIT/CANCEL clear it. DOWN/UP
        // are "contact" events; while contact is held the hover indicator
        // is hidden (the user can see their pen), so we clear too.
        when (action) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                if (viewWidth > 0 && viewHeight > 0) {
                    hoverFlow.value =
                        Offset(
                            x = (event.getX(index) / viewWidth).coerceIn(0f, 1f),
                            y = (event.getY(index) / viewHeight).coerceIn(0f, 1f),
                        )
                }
            }
            MotionEvent.ACTION_HOVER_EXIT,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            -> hoverFlow.value = null
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                // After lift-off the pen often re-enters hover state via
                // ACTION_HOVER_MOVE within a frame; clear here so a stale
                // contact point doesn't linger as a fake hover indicator.
                hoverFlow.value = null
                barrelFlow.value = false
                eraserFlow.value = false
            }
        }
    }

    private companion object {
        /** Tilt angle (radians) considered "fully tilted" for normalisation. */
        const val TILT_FULL_RAD: Float = (Math.PI / 2.0).toFloat()

        /**
         * After this long without any stylus event, the next finger event
         * is treated as a "pen disconnected" signal and clears the latched
         * stylus-seen flag. Chosen long enough to span thinking pauses
         * between strokes (so casual two-finger pinches between drawings
         * don't drop Pencil Mode), short enough to recover from a wedged
         * S-Pen within ~half a minute of the user reaching for the screen.
         */
        const val STYLUS_INACTIVITY_RECOVERY_MS: Long = 25_000L
    }
}
