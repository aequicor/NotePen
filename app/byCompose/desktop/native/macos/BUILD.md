# macOS tablet bridge — build instructions

Source: [`tablet_bridge.m`](tablet_bridge.m)
Output: `libnotepen_tablet.dylib` (universal binary, x86_64 + arm64)
Bundle target: `app/byCompose/desktop/assets/macos/libnotepen_tablet.dylib`

## Why this exists

Compose Desktop / AWT on macOS does not surface `NSEvent.pressure` to
`PointerInputChange` (the AWT `MouseEvent` class has no pressure field, and
Skiko's macOS pointer adapter does not bridge it). This shim subscribes to
`NSEvent` via `[NSEvent addLocalMonitorForEventsMatchingMask:handler:]`
and forwards `(pressure, buttonMask)` to a Java callback through a plain C
ABI — JNA can consume it without dealing with Objective-C blocks.

## Build (one-liner, run on macOS with Xcode CLT installed)

```bash
clang -O2 -fobjc-arc -framework AppKit -dynamiclib \
      -arch x86_64 -arch arm64 \
      tablet_bridge.m -o ../../assets/macos/libnotepen_tablet.dylib
```

That places the universal binary directly in the location Compose Desktop's
`appResourcesRootDir` expects. `:app:byCompose:desktop:packageDmg` will then
copy it into `Contents/app/resources/` of the .app, and the dev `:run` task
exposes the same path via the `compose.application.resources.dir` system
property — `CocoaTabletInputController` prepends that to `jna.library.path`
before `Native.load("notepen_tablet", …)`.

## Verifying

After build, on macOS:

```bash
file ../../assets/macos/libnotepen_tablet.dylib
# Expect: Mach-O universal binary with 2 architectures: [x86_64] [arm64]
```

If the dylib is missing or unloadable, `CocoaTabletInputController` logs
once at `INFO` level and falls back to no-op — pressure stays at `1f` and
the app behaves like the pre-tablet mouse path.
