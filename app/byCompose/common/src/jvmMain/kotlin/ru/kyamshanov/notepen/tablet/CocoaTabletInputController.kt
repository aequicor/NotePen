package ru.kyamshanov.notepen.tablet

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val logger = KotlinLogging.logger {}

/**
 * Bit inside `NSEvent.buttonMask` set when the right mouse button is down.
 * Wacom drivers map the stylus barrel (side) switch to right-click by
 * default; that is what we treat as the hold-to-erase signal.
 */
private const val RIGHT_BUTTON_BIT: Int = 1

/**
 * Pressure threshold above which we accept a `RIGHT_BUTTON_BIT` event as a
 * barrel press. AWT also delivers genuine mouse right-clicks via the same
 * NSEvent path — gating on `pressure > 0` filters those out without losing
 * legitimate stylus barrel presses (NSEvent.pressure is `> 0` whenever the
 * pen is in contact, even if pressure modulation isn't active on the device).
 */
private const val MIN_STYLUS_PRESSURE: Float = 0.001f

/**
 * Direct binding to the `libnotepen_tablet.dylib` shim built from
 * `app/byCompose/desktop/native/macos/tablet_bridge.m`. The shim wraps
 * `[NSEvent addLocalMonitorForEventsMatchingMask:handler:]` and forwards
 * (pressure, buttonMask) to a plain-C callback so JNA can consume it
 * without Objective-C block ABI gymnastics.
 */
internal interface NotepenTabletBridge : Library {
    fun interface OnSample : Callback {
        fun invoke(pressure: Float, buttons: Int)
    }

    @Suppress("FunctionName")
    fun notepen_tablet_start(cb: OnSample)

    @Suppress("FunctionName")
    fun notepen_tablet_stop()
}

/**
 * [TabletInputController] backed by an `NSEvent` monitor on macOS.
 *
 * The native shim runs on the AppKit main thread and invokes our callback
 * synchronously from each NSEvent. We must keep a strong reference to the
 * `Callback` instance for as long as the monitor is registered — otherwise
 * the GC will reclaim it and the next event will crash the JVM. That's why
 * [callbackRef] is a private field, not a local.
 *
 * Missing dylib (e.g. unsupported architecture, packaging mistake) is
 * treated as a soft failure: we log once and stay in no-op mode. macOS
 * users without a tablet are unaffected because the monitor reports a
 * mouse's mask + pressure=0 and we filter those out.
 */
class CocoaTabletInputController : TabletInputController {
    private val pressureFlow = MutableStateFlow(1f)
    private val buttonFlow = MutableStateFlow(false)

    override val latestPressure: StateFlow<Float> = pressureFlow.asStateFlow()
    override val barrelPressed: StateFlow<Boolean> = buttonFlow.asStateFlow()

    private var bridge: NotepenTabletBridge? = null
    private var callbackRef: NotepenTabletBridge.OnSample? = null

    /** Load the dylib and start the NSEvent monitor. No-op outside macOS. */
    fun attach() {
        if (!Platform.isMac()) {
            logger.info { "TabletInput: non-macOS platform, staying in no-op mode" }
            return
        }
        addComposeResourcesToJnaPath()
        val lib = try {
            Native.load("notepen_tablet", NotepenTabletBridge::class.java)
        } catch (e: UnsatisfiedLinkError) {
            logger.info { "TabletInput: libnotepen_tablet.dylib not on jna.library.path (${e.message}); pen pressure disabled" }
            return
        } catch (e: NoClassDefFoundError) {
            logger.warn { "TabletInput: JNA classes missing (${e.message}); pen pressure disabled" }
            return
        }
        bridge = lib

        val cb = NotepenTabletBridge.OnSample { pressure, buttons ->
            val clamped = pressure.coerceIn(0f, 1f)
            pressureFlow.value = clamped
            val rightDown = (buttons shr RIGHT_BUTTON_BIT) and 1 == 1
            val pressed = rightDown && clamped > MIN_STYLUS_PRESSURE
            if (pressed != buttonFlow.value) buttonFlow.value = pressed
        }
        callbackRef = cb
        lib.notepen_tablet_start(cb)
        logger.info { "TabletInput: Cocoa NSEvent monitor attached" }
    }

    /** Stop the monitor and release the dylib reference. Idempotent. */
    fun stop() {
        bridge?.notepen_tablet_stop()
        bridge = null
        callbackRef = null
    }

    /**
     * Compose Desktop exposes [appResourcesRootDir]-resolved bundle path via the
     * `compose.application.resources.dir` system property — works both in dev
     * (`:run`) and in packaged .app distributions. We prepend it to JNA's
     * library search path so `Native.load("notepen_tablet", …)` resolves the
     * bundled `libnotepen_tablet.dylib` without needing custom `-Djna.library.path`.
     */
    private fun addComposeResourcesToJnaPath() {
        val resourcesDir = System.getProperty("compose.application.resources.dir") ?: return
        val current = System.getProperty("jna.library.path").orEmpty()
        if (resourcesDir in current.split(java.io.File.pathSeparator)) return
        val updated = if (current.isEmpty()) resourcesDir else "$resourcesDir${java.io.File.pathSeparator}$current"
        System.setProperty("jna.library.path", updated)
    }
}
