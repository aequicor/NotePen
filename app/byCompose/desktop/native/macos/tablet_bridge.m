// NotePen macOS tablet bridge.
//
// Compose Desktop / AWT on macOS does not surface NSEvent.pressure to
// PointerInputChange (AWT MouseEvent has no pressure field, and Skiko's
// macOS pointer adapter never reads it). This shim subscribes to NSEvent
// directly via [NSEvent addLocalMonitorForEventsMatchingMask:handler:]
// and forwards (pressure, buttons) to a Java callback via a plain C ABI
// — JNA can call this without dealing with Objective-C blocks.
//
// Build (universal binary for Intel + Apple Silicon):
//   clang -O2 -fobjc-arc -framework AppKit -dynamiclib \
//         -arch x86_64 -arch arm64 \
//         tablet_bridge.m -o libnotepen_tablet.dylib
//
// The .dylib is checked into native/macos/ and bundled into the .app
// via compose.desktop's appResourcesRootDir. JNA finds it via
// -Djna.library.path passed in dev mode and via the .app layout in
// packaged distributions.

#import <AppKit/AppKit.h>

typedef void (*notepen_tablet_cb)(float pressure, int buttons);

static id g_monitor = nil;
static notepen_tablet_cb g_cb = NULL;

/**
 * Install an NSEvent local monitor. Idempotent: calling twice replaces the
 * previous monitor (cheap; AppKit guarantees `removeMonitor:` is safe).
 *
 * The mask covers tablet-point events (the primary pressure source) plus
 * mouse-down/drag events — Wacom drivers route stylus input through both
 * NSTabletPoint *and* NSLeftMouseDown depending on stylus state, so we
 * read pressure from whichever NSEvent.pressure happens to be valid.
 *
 * `buttons` is forwarded raw as NSEvent.buttonMask; bit 1 = right mouse,
 * which is the default mapping for the Wacom barrel switch.
 */
void notepen_tablet_start(notepen_tablet_cb cb) {
    if (cb == NULL) return;
    g_cb = cb;

    if (g_monitor != nil) {
        [NSEvent removeMonitor:g_monitor];
        g_monitor = nil;
    }

    NSEventMask mask = NSEventMaskTabletPoint
                     | NSEventMaskLeftMouseDown
                     | NSEventMaskLeftMouseUp
                     | NSEventMaskLeftMouseDragged
                     | NSEventMaskRightMouseDown
                     | NSEventMaskRightMouseUp
                     | NSEventMaskRightMouseDragged;

    g_monitor = [NSEvent addLocalMonitorForEventsMatchingMask:mask
                                                      handler:^NSEvent *(NSEvent *e) {
        if (g_cb != NULL) {
            float pressure = (float)e.pressure;
            int buttons = (int)e.buttonMask;
            g_cb(pressure, buttons);
        }
        // Returning the event unchanged so AWT / Compose still see it
        // and the rest of the input pipeline behaves normally.
        return e;
    }];
}

/** Remove the monitor. Safe to call repeatedly or before start. */
void notepen_tablet_stop(void) {
    if (g_monitor != nil) {
        [NSEvent removeMonitor:g_monitor];
        g_monitor = nil;
    }
    g_cb = NULL;
}
