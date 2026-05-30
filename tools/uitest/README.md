# NotePen UI-testing harness (Claude-Code-driven)

Scripts that let **Claude Code** drive and visually test the NotePen UI on every target this machine
can reach — the **desktop** app (Windows, this machine) and the Android **phone + tablet** emulators
(and any attached physical device) — and record **animations as GIF + PNG filmstrip**.

This is *not* a JUnit/Robolectric/instrumented suite. It's a live-driving harness: launch the real
app, drive it (computer-use for desktop, `adb` for Android), screenshot, and capture animations.
For deterministic component snapshots see the separate Roborazzi tests in `reflow/impl` (`jvmTest`).

Everything is dependency-free PowerShell — **no ffmpeg, no avdmanager required**. GIFs are encoded
with the built-in Windows WPF `GifBitmapEncoder` (`Capture-Gif.ps1`).

> Run scripts from anywhere; they resolve the repo root and SDK themselves. Captured artifacts land
> in `tools/uitest/out/` (git-ignored).

---

## Targets on this machine

| Target | How it's reached | Serial / window |
|---|---|---|
| Desktop | `./gradlew runDesktop` (JBR dev window) | window title **`NotePen`** |
| Phone   | emulator AVD `Medium_Phone_API_36.1` | usually `emulator-5554` |
| Tablet  | emulator AVD `NotePen_Tablet_API_36_1` (created by `New-TabletAvd.ps1`) | next free `emulator-55xx` |
| Physical device | attached over USB | e.g. `44RUN24B09G03494` |

`adb devices` lists what's currently attached. With more than one target attached, **always pass a
`-Serial`** to the Android scripts.

---

## Desktop

```powershell
# 1. Launch the app (starts ./gradlew runDesktop, waits for the "NotePen" window).
./Launch-Desktop.ps1                 # prints the window handle + screen bounds

# 2. Drive it (Claude Code): request_access for `java.exe` (the JBR dev window — NOT the
#    installed "NotePen"), then use computer-use screenshot / left_click / type.

# 3. Record an animation as a GIF (capture runs for -DurationMs from when it starts —
#    trigger the animation right before/after launching this):
./Capture-DesktopAnim.ps1 -DurationMs 2000 -Fps 15
# -> out/desktop/<timestamp>/anim.gif  (+ anim.gif.filmstrip.png)
```

Notes:
- The launcher leaves the Gradle process running while the app is open; it prints the PID and the
  `Stop-Process -Id <pid>` to close it.
- `Capture-DesktopAnim.ps1` grabs the window region with GDI (`Graphics.CopyFromScreen`) — faster and
  steadier than computer-use screenshots, so the framerate is tight.
- The window matcher is an **exact** title `NotePen` so it doesn't grab the IntelliJ IDEA project
  window (`NotePen – Commit: …`). Override with `-TitleLike` if needed.

## Android (phone, tablet, or device)

```powershell
# One-time: create the tablet AVD (clones the phone AVD's config; no avdmanager needed).
./New-TabletAvd.ps1

# Boot a target, install the debug APK, and launch NotePen:
./Start-AndroidTarget.ps1 -Serial emulator-5554              # phone (already running)
./Start-AndroidTarget.ps1 -Avd NotePen_Tablet_API_36_1       # boots the tablet AVD if needed
./Start-AndroidTarget.ps1 -Serial 44RUN24B09G03494           # physical device
#   add -Build to rebuild the APK first; -NoInstall to just relaunch.

# Drive it (Claude Code) with adb:
adb -s <serial> shell input tap <x> <y>
adb -s <serial> shell input swipe <x1> <y1> <x2> <y2> <ms>
adb -s <serial> exec-out screencap -p > shot.png            # screenshot (cmd `>` keeps bytes intact)

# Record an animation as a GIF (bursts screencap frames):
./Capture-AndroidAnim.ps1 -Serial emulator-5554 -DurationMs 2000
./Capture-AndroidAnim.ps1 -Serial <tablet-serial> -DurationMs 2000 -PreTap "800,1200"
# -> out/android/<serial>/<timestamp>/anim.gif  (+ filmstrip)
```

Notes:
- App id is `ru.kyamshanov.notepen.debug` (debug build). Launch uses `monkey` so there's no
  activity-name coupling.
- `screencap` runs ~5–8 fps, so Android GIFs convey animation *shape*, not frame-perfect timing.
  (mp4 via `screenrecord` is avoided — without ffmpeg it can't be turned into a GIF.)
- Tablet AVD is 1600×2560 @ 320 dpi → ~800 dp smallest width, so it exercises the `sw600dp+`
  tablet layouts. Verify with `adb -s <serial> shell wm size`.

---

## Animation capture (`Capture-Gif.ps1`)

Shared encoder used by both `Capture-*Anim.ps1` scripts; can also be called directly on any folder of
PNG frames:

```powershell
./Capture-Gif.ps1 -FramesDir <dir-of-pngs> -Out out/anim.gif -DelayMs 100 -LoopCount 0
```

For each run it writes:
- `anim.gif` — animated, looping GIF (NETSCAPE2.0 loop + per-frame delay patched into the stream).
- `anim.gif.filmstrip.png` — all frames side-by-side. This is the **reliable, diffable** artifact;
  animated-GIF playback varies by viewer.

---

## Files

| Script | Purpose |
|---|---|
| `Launch-Desktop.ps1` | Start `runDesktop`, wait for the app window, print its bounds. |
| `Capture-DesktopAnim.ps1` | Record a desktop animation (GDI window capture) → GIF + filmstrip. |
| `New-TabletAvd.ps1` | Create the tablet AVD by cloning the phone AVD's config (no avdmanager). |
| `Start-AndroidTarget.ps1` | Boot AVD / pick device, install debug APK, launch NotePen. |
| `Capture-AndroidAnim.ps1` | Record an Android animation (screencap burst) → GIF + filmstrip. |
| `Capture-Gif.ps1` | PNG frames → looping GIF + filmstrip (pure WPF, no ffmpeg). |
| `_Win32.ps1` / `_Android.ps1` | Shared helpers (window geometry; adb/emulator/SDK resolution). |

`out/` (captures + logs) is git-ignored.
