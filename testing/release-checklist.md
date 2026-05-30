---
genre: reference
title: Release Sign-off Checklist (Tier 3 — manual / hardware)
topic: testing
triggers:
  - "release"
  - "sign-off"
  - "manual testing"
  - "hardware"
confidence: high
source: human
updated: 2026-05-30T00:00:00Z
---

# Release Checklist: Tier 3 (manual, real hardware)

**Module:** all (release artifact)
**Status:** Final
**Tier:** 3 — manual on real hardware (release sign-off)
**Spec:** [[../TESTING.md]] § Tier 3 — Manual on real hardware
**Per-feature deep dives:** [[vault/_templates/test-plan.md]] § Manual Scenarios

---

## When to run

Run this **before tagging a `vX.Y.Z` release** — it is the **required gate for tagging** (see [[../TESTING.md]] § Gating policy). It validates **only what tiers 1 and 2 physically cannot reach**: real stylus digitizers, perceived overlay latency, two physical devices syncing over real Wi-Fi+mDNS, installers, the custom JBR title bar, and OS file associations. Anything golden-able or headless-drivable belongs in Tier 1/2 and must not be re-checked here.

**Prerequisites (must already be true before starting):**

- [ ] Tier 1 green — `./gradlew check` passes (build + tests + ktlintCheck + detekt + Roborazzi goldens).
- [ ] Tier 2 AI-vision run complete and findings triaged — advisory, but regressions surfaced there are resolved or accepted.
- [ ] Release notes drafted; `app.version` candidate decided.

**Tag rule (hard constraint):** release tags must be **`v1.0.0` or higher**. The macOS jpackage DMG build **rejects `0.x` versions** — never tag a `v0.x` release. Re-releasing the same version = **move the existing tag**, do not bump.

Each row below is a `manual`-type test case. The **Notes** column is reserved for human observations during testing — fill it on real hardware; agents never auto-fill it.

---

## Platform / device matrix

Sign off each variant. Sync is **cross-paired** — exercise desktop↔desktop, desktop↔Android, Android↔Android over real Wi-Fi.

| # | Platform | Variant | Built | Installed | Launches | Stylus | Sync | Signed off |
|---|----------|---------|-------|-----------|----------|--------|------|------------|
| 1 | Windows desktop | Win 10 | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 2 | Windows desktop | Win 11 | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 3 | macOS desktop | Apple Silicon | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 4 | macOS desktop | Intel | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 5 | Linux desktop | Debian/Ubuntu (DEB) | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 6 | Android phone | stylus-capable | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 7 | Android tablet | S-Pen / equivalent | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |

Cross-pair coverage (tick once each pairing has synced annotations live):

- [ ] desktop ↔ desktop
- [ ] desktop ↔ Android
- [ ] Android ↔ Android

---

## Manual Scenarios

### A. Real stylus — pressure / tilt / palm rejection (per-OS actual)

Each digitizer path is a separate platform actual; no automated tier can drive real hardware.

| # | Scenario | Steps | Expected Result | Status | Notes |
|---|----------|-------|-----------------|--------|-------|
| A1 | WinTab pressure + tilt (Windows) | Draw on a Wacom/touchscreen digitizer; vary pressure and pen tilt | Stroke width/opacity tracks pressure; tilt response feels correct | ⬜ | |
| A2 | Cocoa stylus (macOS) | Draw with an Apple Silicon/Intel Mac stylus path | Pressure/tilt events register; strokes render smoothly | ⬜ | |
| A3 | Android stylus (`MotionEvent`) | Draw on phone + tablet with S-Pen/active stylus | Pressure/tilt reflected; no dropped points | ⬜ | |
| A4 | Palm rejection | Rest hand on screen mid-stroke (Android tablet + touchscreen Windows) | Palm contact ignored; only the stylus stroke is drawn | ⬜ | |
| A5 | Magnifier under real stylus | Trigger magnifier gesture (`:drawing:impl`) while drawing | Magnifier tracks the pen and shows the correct region | ⬜ | |

### B. Perceived input latency / low-latency overlay

Latency is felt, not unit-measured. The Android overlay path is distinct from desktop.

| # | Scenario | Steps | Expected Result | Status | Notes |
|---|----------|-------|-----------------|--------|-------|
| B1 | Desktop overlay latency | Draw fast strokes on each desktop OS | Ink keeps up with the pen; no perceptible lag during the active stroke | ⬜ | |
| B2 | Android low-latency overlay | Draw fast strokes on phone + tablet | Active-stroke overlay (`:rendering:impl`) tracks the pen tip; commit is seamless | ⬜ | |

### C. Two physical devices syncing over real LAN + mDNS

Localhost tests cannot see router multicast filtering, AP isolation, or VPN interference.

| # | Scenario | Steps | Expected Result | Status | Notes |
|---|----------|-------|-----------------|--------|-------|
| C1 | mDNS discovery | Start host on one device; open peer catalog on a second over the same Wi-Fi | Host is discovered and listed | ⬜ | |
| C2 | WebSocket connect + propagate | Connect peers; draw on device A | Stroke appears on device B over the real network (Ktor WebSocket) | ⬜ | |
| C3 | Simultaneous draw (LWW) | Both users draw at the same time | Last-writer-wins merge converges; both devices show the same final state | ⬜ | |
| C4 | Erase / tombstone propagation | Erase a stroke on A | Erase (tombstone) propagates to B; stroke removed on both | ⬜ | |
| C5 | QR pairing end-to-end | Generate QR on a desktop screen (zxing); scan with Android camera (CameraX + ML Kit) | Pairing succeeds; devices connect | ⬜ | |
| C6 | Reconnect / offline-queue flush | Drop Wi-Fi or background the app mid-session; restore | Reconnects; queued offline deltas flush and converge | ⬜ | |

### D. Installers — actually install & launch on each OS

| # | Scenario | Steps | Expected Result | Status | Notes |
|---|----------|-------|-----------------|--------|-------|
| D1 | Windows Inno Setup installer | Run the Inno Setup installer (`installer/windows/notepen.iss`) | Installs, Start-menu entry present, app launches, uninstall works | ⬜ | |
| D2 | Windows portable ZIP | Unpack `packageReleasePortableZip` output; run with no install | App launches from the unpacked folder | ⬜ | |
| D3 | macOS DMG | Mount the jpackage `TargetFormat.Dmg`; drag to /Applications; launch | DMG mounts; Gatekeeper/notarization passes; app launches | ⬜ | |
| D4 | Linux DEB | Install the jpackage `TargetFormat.Deb`; launch | Installs and launches | ⬜ | |
| D5 | Android signed APK | Install the signed APK/AAB | Installs and opens | ⬜ | |

> Build the desktop release with **`createReleaseDistributable`** (ProGuard + obfuscation ON) and install/launch **that** artifact — not `runDesktop`. Obfuscation can break reflection-dependent paths (PDFBox, Ktor); confirm by actually launching the release build.

### E. Custom JBR title bar (per-OS chrome)

| # | Scenario | Steps | Expected Result | Status | Notes |
|---|----------|-------|-----------------|--------|-------|
| E1 | Windows custom title bar | On Windows (real JBR 25 runtime), drag / minimize / maximize / close / snap; test multi-monitor DPI | `setupJbrTitleBar` chrome renders and behaves correctly | ⬜ | |
| E2 | macOS / Linux fallback | Launch on macOS and Linux | Native window chrome is used; no broken custom title bar | ⬜ | |

### F. File associations / OS integration

| # | Scenario | Steps | Expected Result | Status | Notes |
|---|----------|-------|-----------------|--------|-------|
| F1 | Windows file assoc | Double-click a `.pdf` / `.epub` in Explorer; check "Open with" | Opens in NotePen; association registered by Inno Setup (`notepen.iss`), not jpackage | ⬜ | |
| F2 | macOS / Linux open-with | Double-click / "Open with" a PDF/EPUB in the file manager | Opens in NotePen | ⬜ | |

---

## Pre-tag release ops

Complete these in order; the tag step is last.

- [ ] **`app.version`** bumped to the release version (drives Android `versionName` + desktop package version).
- [ ] Version satisfies the **`v1.0.0`+** rule (no `0.x` — jpackage DMG rejects it).
- [ ] **Android signing env vars** set in the build environment: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEY_ALIAS`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_PASSWORD` (missing → unsigned/failed build).
- [ ] **JBR 25 toolchain** (jbrsdk 25.0.3) available on the desktop build machine — foojay cannot auto-provision JetBrains Runtime; if not auto-detected, set `org.gradle.java.installations.paths` in the machine's user `gradle.properties`.
- [ ] Desktop release built via **`createReleaseDistributable`** (obfuscated) and launch-verified (section D + obfuscation note above).
- [ ] All platform-matrix variants **signed off** and all three sync cross-pairings ticked.
- [ ] **Tag** `vX.Y.Z` (`v1.0.0`+). Re-release of an existing version = **move the existing tag**, do not bump.

---

## Sign-off

- [ ] Tier 3 complete — all blocking scenarios PASS, defects logged, release approved.

**Released by:** _______________  **Date:** ____________  **Version:** `vX.Y.Z`
