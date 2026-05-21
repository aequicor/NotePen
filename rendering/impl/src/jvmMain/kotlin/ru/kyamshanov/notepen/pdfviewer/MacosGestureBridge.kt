package ru.kyamshanov.notepen.pdfviewer

import com.sun.jna.Callback
import com.sun.jna.Library

/**
 * JNA binding for `libnotepen_gesture.dylib` — a native macOS shim that
 * monitors [NSEventMaskMagnify] via `[NSEvent addLocalMonitorForEventsMatchingMask:handler:]`
 * and forwards the magnification delta to a plain-C callback so JNA can consume
 * it without Objective-C block ABI gymnastics.
 *
 * Skiko overrides `NSView.magnifyWithEvent:` at the ObjC level and converts
 * the event to a `SkikoGestureEvent`, which is not exposed through Compose's
 * `PointerInputScope`. This shim bypasses that layer entirely.
 */
internal interface MacosGestureBridge : Library {

    /**
     * Called on the AppKit main thread for every `NSEventTypeMagnify` event.
     *
     * @param magnification relative change: `> 0` = zoom in, `< 0` = zoom out.
     *   Typical per-event range is ±0.01…0.05 for a smooth trackpad gesture.
     * @param x centroid X in window coordinates (macOS bottom-left origin, points).
     * @param y centroid Y in window coordinates (macOS bottom-left origin, points).
     */
    fun interface OnMagnify : Callback {
        fun invoke(magnification: Float, x: Float, y: Float)
    }

    /** Install the NSEvent local monitor. Idempotent (replaces any previous monitor). */
    fun notepen_gesture_start(cb: OnMagnify)

    /** Remove the monitor. Safe to call before [notepen_gesture_start] or multiple times. */
    fun notepen_gesture_stop()
}
