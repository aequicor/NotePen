package ru.kyamshanov.notepen.tablet

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * The Tablet PC service per-window property. Windows looks this up on every
 * pen / touch event and toggles its built-in gesture recognisers accordingly.
 * Documented under "Disabling Visual Feedback" in the Windows Touch /
 * Tablet PC SDK.
 */
private const val TABLET_SERVICE_PROPERTY = "MicrosoftTabletPenServiceProperty"

// Canonical "disable everything that delays or steals pen events" set per
// Microsoft Tablet PC SDK. Earlier we OR'd extra ENABLE_* bits as well —
// removed because mixing ENABLE bits with DISABLE bits in the same property
// gave the Tablet Input Service ambiguous signals and brought back partial
// gesture recognition on some drivers.
private const val TABLET_DISABLE_PRESSANDHOLD: Long = 0x00000001
private const val TABLET_DISABLE_PENTAPFEEDBACK: Long = 0x00000008
private const val TABLET_DISABLE_PENBARRELFEEDBACK: Long = 0x00000010
private const val TABLET_DISABLE_FLICKS: Long = 0x00010000

/**
 * Disables Windows' "press-and-hold → right-click" and flick gestures for the
 * given window.
 *
 * Why this is critical for drawing apps: by default Windows holds pen-event
 * delivery to user-space for ~500 ms on stroke start to decide whether the
 * gesture is press-and-hold (and should be re-issued as a right-click) or a
 * regular drag. During that window AWT receives nothing; once the recogniser
 * gives up, the buffered events arrive in a burst. The visible artefact is a
 * straight line from the touch-down point to the current pen position before
 * the actual stroke begins — the classic "spiral defect" reproduced as:
 *
 *   1. place a dot, 2. start drawing a spiral — for ~500 ms nothing draws,
 *   then a straight segment appears connecting the dot to wherever the pen
 *   is now, after which the spiral renders normally.
 *
 * Setting this window property tells the Tablet Input Service to skip the
 * recogniser entirely for our HWND, so events flow without delay.
 */
/**
 * Direct binding to `user32!SetPropA`. JNA's bundled `User32` interface omits
 * `SetProp` in our version, so we wire it ourselves — a one-method library.
 */
@Suppress("FunctionName")
private interface User32Ext : Library {
    fun SetPropA(hWnd: HWND, lpString: String, hData: Pointer): Boolean
}

object WindowsPenFix {

    private val user32: User32Ext? by lazy {
        if (!Platform.isWindows()) {
            null
        } else {
            runCatching { Native.load("user32", User32Ext::class.java) }
                .onFailure { logger.warn(it) { "WindowsPenFix: cannot load user32.dll" } }
                .getOrNull()
        }
    }

    /** Apply the fix to [hwnd]. No-op on non-Windows platforms. */
    fun apply(hwnd: HWND) {
        val lib = user32 ?: return
        val flags = TABLET_DISABLE_PRESSANDHOLD or
            TABLET_DISABLE_PENTAPFEEDBACK or
            TABLET_DISABLE_PENBARRELFEEDBACK or
            TABLET_DISABLE_FLICKS
        try {
            val ok = lib.SetPropA(hwnd, TABLET_SERVICE_PROPERTY, Pointer.createConstant(flags))
            if (ok) {
                logger.info { "WindowsPenFix: applied (SetPropA returned true)" }
            } else {
                val err = Native.getLastError()
                logger.warn { "WindowsPenFix: SetPropA returned false (GetLastError=$err); pen-stroke start may lag" }
            }
        } catch (t: Throwable) {
            logger.warn(t) { "WindowsPenFix: SetProp threw; pen-stroke start may lag" }
        }
    }
}
