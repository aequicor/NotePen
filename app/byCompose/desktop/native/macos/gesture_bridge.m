// NotePen macOS gesture bridge.
//
// Compose Desktop / Skiko on macOS overrides NSView.magnifyWithEvent: at the
// native level and converts it to a SkikoGestureEvent. That event is NOT
// exposed through Compose's PointerInputScope, so Ctrl+scroll scroll-events
// never arrive in the Compose handler during a trackpad pinch.
//
// This shim mirrors the tablet_bridge.m approach: subscribe to NSEvent directly
// via [NSEvent addLocalMonitorForEventsMatchingMask:handler:] for
// NSEventMaskMagnify, and forward the magnification delta + centroid to a Java
// callback via a plain C ABI — JNA can call this without ObjC block gymnastics.
//
// Build (universal binary for Intel + Apple Silicon):
//   clang -O2 -fobjc-arc -framework AppKit -dynamiclib \
//         -arch x86_64 -arch arm64 \
//         gesture_bridge.m -o libnotepen_gesture.dylib

#import <AppKit/AppKit.h>

// magnification: relative change (>0 = zoom in, <0 = zoom out).
// x, y: centroid in window coordinates (macOS bottom-left origin, points).
typedef void (*notepen_gesture_magnify_cb)(float magnification, float x, float y);

static id g_magnify_monitor = nil;
static notepen_gesture_magnify_cb g_magnify_cb = NULL;

void notepen_gesture_start(notepen_gesture_magnify_cb cb) {
    if (cb == NULL) return;
    g_magnify_cb = cb;

    if (g_magnify_monitor != nil) {
        [NSEvent removeMonitor:g_magnify_monitor];
        g_magnify_monitor = nil;
    }

    g_magnify_monitor = [NSEvent addLocalMonitorForEventsMatchingMask:NSEventMaskMagnify
                                                              handler:^NSEvent *(NSEvent *e) {
        if (g_magnify_cb != NULL) {
            NSPoint loc = e.locationInWindow;
            g_magnify_cb((float)e.magnification, (float)loc.x, (float)loc.y);
        }
        // Return the event unchanged — Skiko / AWT still see it.
        return e;
    }];
}

void notepen_gesture_stop(void) {
    if (g_magnify_monitor != nil) {
        [NSEvent removeMonitor:g_magnify_monitor];
        g_magnify_monitor = nil;
    }
    g_magnify_cb = NULL;
}
