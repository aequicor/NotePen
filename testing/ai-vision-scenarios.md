# NotePen — Tier-2 AI-vision scenario catalog

Versioned catalog of the **Tier-2 AI-vision** scenarios Claude Code runs by driving the **live** app on **real renderers**. See [`/TESTING.md`](../TESTING.md) for the three-tier model. This file is the Tier-2 entry referenced there.

## Purpose

These scenarios cover only what the deterministic Tier-1 gate (`./gradlew check`, incl. Roborazzi goldens) **physically cannot reach**: composited strokes over real PDF bitmaps, the Android low-latency overlay, the Android `PdfRenderer` path, the magnifier, gestures/zoom/pan, on-screen LAN-sync propagation, and the live library/editor/file-picker flows. If a behavior can be asserted deterministically (e.g. reflow layout), it belongs in Tier 1's Roborazzi suite — **not here**.

- **How to trigger:** include the keyword **`ai-vision`** (case-insensitive) in any prompt. A `UserPromptSubmit` hook injects the standing instruction to actually run the harness, capture real screenshots + ordered frame bursts for animations, and display the PNG artifacts in the reply (not just describe them).
- **Where output lands:** `/.claude/ux-reports/<runid>/` (gitignored). Run-id convention `YYYYMMDD-HHMM[-label]` (e.g. `20260529-1821-allplatforms`); `.claude/ux-reports/.current-run` holds the active run-id. Per-run layout: `REPORT.md` at the run root (mirror the format of `20260529-124456/REPORT.md` / `20260529-1547/REPORT.md` — top-line table + 🔴 Critical / 🟠 Regression / 🟡 Polish findings + coverage gap), platform/phase subfolders (`desktop/ android/ emulator/ huawei/ …`) holding ordered, descriptively-named PNGs (`05-baranovskaya-open.png`, `frame000.png …` for filmstrips).
- **Governance:** Tier 2 is **advisory, not a merge gate** (gating policy in `/TESTING.md`). Results are non-deterministic and token-costly; a human triages every finding. A flaky scenario gets **tightened** (better fixture, stricter wait, narrower region) — **never silently dropped**. When the headless tier could in fact golden a behavior surfaced here, move it down to Tier 1 rather than re-running it in Tier 2.

## Harness reference (scenario verbs)

The harness is [`tools/uitest/`](../tools/uitest/) on this **Windows** machine — desktop via
**computer-use**, Android via **`adb`**, animations via the pure-PowerShell GIF/filmstrip encoders.
(The former `notepen-desktop` MCP and `.claude/tools/bin/notepen-android` helper were removed — do
not reference them.) Field shape for every scenario block is governed by
[`TEST-CASE-STANDARD.md`](TEST-CASE-STANDARD.md). Read [`tools/uitest/README.md`](../tools/uitest/README.md)
before a run.

### Desktop — `tools/uitest/` + computer-use
Launch/locate the app window, then drive it with computer-use. Grant **`java.exe`** (the JBR
`runDesktop` window titled **`NotePen`**), **not** the installed "NotePen" bundle.

| Step | Command / tool |
|---|---|
| Launch + locate window | `tools/uitest/Launch-Desktop.ps1` (prints window handle + screen bounds) |
| Screenshot | computer-use `screenshot` |
| Click / double-click | computer-use `left_click` / `double_click` (logical points, top-left origin) |
| Stroke / pan / page-swipe | computer-use `left_click_drag` (press-move-release) |
| Type / keys | computer-use `type`, `key` (`Return Tab Escape Left Right Up Down Page_Up Page_Down …`) |
| Scroll / zoom | computer-use `scroll` (Ctrl+wheel for zoom where the app supports it) |
| **Record animation** | `tools/uitest/Capture-DesktopAnim.ps1 -DurationMs <ms> -Fps <n>` → `out/desktop/<ts>/anim.gif` + `anim.gif.filmstrip.png` (GDI window capture; trigger the motion right as it starts) |

### Android — `tools/uitest/` + `adb`
Pkg `ru.kyamshanov.notepen.debug`. With more than one target attached, **always pass `-Serial`**.

| Step | Command |
|---|---|
| List targets | `adb devices` |
| Boot tablet AVD (one-time create) | `tools/uitest/New-TabletAvd.ps1`; then `tools/uitest/Start-AndroidTarget.ps1 -Avd NotePen_Tablet_API_36_1` |
| Boot / install / launch | `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>` (`-Build` to rebuild APK, `-NoInstall` to relaunch) |
| Screenshot | `adb -s <serial> exec-out screencap -p > shot.png` |
| Tap | `adb -s <serial> shell input tap <x> <y>` |
| Swipe / stroke | `adb -s <serial> shell input swipe <x1> <y1> <x2> <y2> <ms>` |
| Push a fixture | `adb -s <serial> push <local> /sdcard/Download/` |
| **Record animation** | `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial> -DurationMs <ms> [-PreTap "x,y"]` → GIF + filmstrip (screencap burst, ~5–8 fps: conveys animation *shape*) |

Targets on this machine: desktop (Windows, JBR dev window), phone AVD `Medium_Phone_API_36.1`
(`emulator-5554`), tablet AVD `NotePen_Tablet_API_36_1` (1600×2560 → `sw600dp+` layouts), and any
attached physical device (e.g. Huawei `44RUN24B09G03494`, real stylus — **back up real annotations
before any reinstall**).

### Animation framing (the capture contract)
Any scenario verifying **motion** (stroke-follows-pen, page turn, zoom/pan transition, magnifier
track, sync propagation) records an **ordered frame burst → GIF + PNG filmstrip** via the
`Capture-*Anim.ps1` encoders (or `tools/uitest/Capture-Gif.ps1` on a folder of PNGs). Name frames
`frameNNN.png` and stills `NN-<what>.png`. The **filmstrip** (`anim.gif.filmstrip.png`) is the
reliable, diffable artifact — GIF playback varies by viewer.

### Deterministic fallback (no GUI / permission)
Roborazzi goldens: `./gradlew :reflow:impl:jvmTest --tests "*SnapshotTest"` (9 committed PNGs in `reflow/impl/snapshots/`; record with `-Proborazzi.test.record=true`). Use only as a fallback signal — it does **not** satisfy a Tier-2 scenario, which requires the live renderer.

### Fixtures
| Key | Path | Notes |
|---|---|---|
| `baranovskaya` | machine-local (e.g. `C:\Users\kruz18\Documents\english\Барановская_…pdf`; path varies) — push to Android with `adb push <file> /sdcard/Download/` | 383-page HYBRID/OCR scan; reflow torture-test. Not committed → scenario `skip`s if absent. |
| `isiguro` | machine-local FB2 (`Isiguro_Klara-i-Solnce.…fb2`; path varies) | 1.5 MB FB2, justified prose / em-dashes / TOC. Not committed. |
| `planeta-epub` | `planeta-ru.epub` | EPUB→PDF→reflow chain, embedded illustrations. Not committed. |
| `article-fb2` | `app/byCompose/common/src/jvmTest/resources/fixtures/article-small.fb2`, `article-yamshanov.fb2` | committed, safe to `push` / open. |
| `thesis-pdf` | `reflow/impl/src/jvmTest/resources/fixtures/thesis-mixed-content.pdf` | committed; only in-repo PDF with tables / Code / Footnote candidates. |
| `article-pdf` | `reflow/impl/src/jvmTest/resources/fixtures/article-org-risks.pdf` | committed. |

UI strings to drive: `Открыть` (Open), `+folder`, themes `Бумага / Сепия / Серый / Ночь / Яркий`, typography presets `Компактно-1 / Долгое чтение-1`. The file-picker is keyboard-layout-fragile (RU layout has blocked earlier runs) — see AV-LIB-02.

---

## Scenario catalog

> Severity legend (matches report findings): 🔴 critical · 🟠 regression · 🟡 polish.
> Platforms: `desktop` (PDFBox / JBR, this Windows machine) · `android-phone` (physical, real stylus) · `android-emulator` (`emulator-5554`) · `android-tablet` (AVD `NotePen_Tablet_API_36_1`, `sw600dp+`). All reachable from the `tools/uitest/` harness on this machine.

### Library / open flows

#### AV-LIB-01 — Launch to library
- **Area:** library · **Platform(s):** desktop, android-phone, android-emulator
- **Fixture/precondition:** launch the desktop app via `tools/uitest/Launch-Desktop.ps1` (then drive with computer-use), or install + launch Android via `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>`.
- **Steps:**
  1. desktop: launch the desktop app via `tools/uitest/Launch-Desktop.ps1`, then computer-use `screenshot` (save to .../01-library.png). android: `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>` → `adb -s <serial> exec-out screencap -p > .../01-library.png`.
  2. Read the top bar affordances (`Открыть`, `+folder`, layers/sessions, refresh, gear).
- **Expected:** library grid renders with recents/folders; top bar icons present and not overlapping; no blank/white window.
- **Pass/Fail:** PASS if the library screen composes with at least the open + folder affordances visible. FAIL on blank window, crash, or a >~90 s launch with no frame.
- **Severity if broken:** 🔴

#### AV-LIB-02 — Open via file-picker + path entry (keyboard-layout guard)
- **Area:** library · **Platform(s):** desktop, android-emulator
- **Fixture/precondition:** `thesis-pdf` (committed) or `baranovskaya` (if present).
- **Steps:**
  1. desktop: computer-use `left_click` `Открыть`; in the native dialog computer-use `type` the absolute fixture path; computer-use `key Return`. emulator: invoke the SAF picker, navigate to the pushed `/sdcard/Download/` fixture (`adb -s <serial> push <fixture> /sdcard/Download/` first).
  2. computer-use `screenshot` the dialog **before** confirming, and the opened document after.
- **Expected:** typed path lands verbatim (no RU-layout transliteration); document opens to page 1.
- **Pass/Fail:** PASS if the path is entered correctly and the document opens. FAIL if the typed path is garbled by keyboard layout (known harness pain point — log it, do not silently retry) or the picker can't reach the fixture.
- **Severity if broken:** 🟠

#### AV-LIB-03 — Session restore
- **Area:** library · **Platform(s):** desktop
- **Fixture/precondition:** at least one document opened previously this run (so a session exists).
- **Steps:** computer-use `left_click` the layers/sessions icon (top-right; note: earlier runs found this is the **session manager** — `Сессии: Восстановить/Резюме/Удалить` — not the peer catalog, F-5); computer-use `screenshot`; computer-use `left_click` `Восстановить`.
- **Expected:** session list lists prior documents; restore reopens the last document at its saved position.
- **Pass/Fail:** PASS if restore reopens the prior doc. FAIL if the menu mislabels its function or restore loses position.
- **Severity if broken:** 🟡

### PDF render fidelity

#### AV-PDF-01 — PDF page-mode raster sharpness + cache
- **Area:** PDF render · **Platform(s):** desktop, android-phone, android-emulator
- **Fixture/precondition:** `baranovskaya` (or `thesis-pdf` if absent). Open in **PDF page mode** (not reflow).
- **Steps:**
  1. Open the fixture in page mode; computer-use `screenshot` (save to .../01-pdf-p1.png).
  2. computer-use `key Page_Down` ×3 (desktop) / `adb -s <serial> shell input swipe <x1> <y1> <x2> <y2>` page-up gesture ×3 (android), capturing each page.
  3. Page back to p1 (computer-use `key Page_Up` / reverse swipe) and re-`screenshot` — exercises the page-bitmap cache.
- **Expected:** crisp raster, legible glyphs, no half-rendered/torn pages; re-visited p1 matches the first capture (cache hit, no re-blur).
- **Pass/Fail:** PASS if all pages render fully and the cached page is identical on return. FAIL on blank/torn pages, a **native crash** (regression watch — cf. `d65511e` PdfRenderer serialization on Android), or visibly degraded raster.
- **Severity if broken:** 🔴

#### AV-PDF-02 — Multi-page navigation continuity
- **Area:** PDF render · **Platform(s):** desktop, android-phone
- **Fixture/precondition:** `baranovskaya` open in page mode.
- **Steps:** page forward 8× via computer-use `key Page_Down` (desktop) / `adb -s <serial> shell input swipe <x1> <y1> <x2> <y2>` (android), recording an ordered frame burst via `tools/uitest/Capture-DesktopAnim.ps1` (desktop) or `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>` (android) → GIF + filmstrip (`frame000…frame007`).
- **Expected:** monotonic page advance (no skipped/repeated pages), consistent `fitWidthTopInset` framing, no flicker between pages.
- **Pass/Fail:** PASS if every step advances exactly one page with stable framing. FAIL on stuck pages, double-jumps, or layout jump between pages.
- **Severity if broken:** 🟠

### Stroke-follows-pen drawing

#### AV-DRAW-01 — Pen stroke tracks the pointer + commits on finish
- **Area:** drawing · **Platform(s):** desktop, android-phone, android-emulator
- **Fixture/precondition:** any document open in PDF page mode with the pen tool selected.
- **Steps:**
  1. Select pen. desktop: computer-use `left_click_drag x y x2 y2` (24-step interpolation) across the page. android: `adb -s <serial> shell input swipe <x1> <y1> <x2> <y2> 600`.
  2. Record an ordered frame burst during the stroke via `tools/uitest/Capture-DesktopAnim.ps1` (desktop) or `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>` (android) → GIF + filmstrip (`frame000.png …`), plus one final computer-use `screenshot` after release.
- **Expected:** rendered path follows the pointer with no large lag/gap; on Android the **low-latency overlay** shows the live stroke and commits cleanly into the page bitmap on finish (no double-draw, no ghost overlay left behind).
- **Pass/Fail:** PASS if the committed stroke matches the drag path and no overlay artifact remains. FAIL on missing/offset stroke, overlay not committing, or a ghost/duplicate stroke.
- **Severity if broken:** 🔴

#### AV-DRAW-02 — Marker / highlighter + eraser
- **Area:** drawing · **Platform(s):** desktop, android-phone
- **Fixture/precondition:** document open with a visible pen/marker/eraser toolbar.
- **Steps:** select marker, computer-use `left_click_drag` (desktop) / `adb -s <serial> shell input swipe ...` (android) over body text (capture); select eraser, computer-use `left_click_drag` / `adb -s <serial> shell input swipe ...` over the marker + a prior pen stroke (capture before/after).
- **Expected:** marker is semi-transparent and lets text show through (highlighter compositing); eraser removes only the strokes it crosses, leaving the PDF raster intact.
- **Pass/Fail:** PASS if marker composites translucently and eraser removes strokes without touching the page raster. FAIL if marker is opaque, eraser deletes page content, or strokes survive erasing.
- **Severity if broken:** 🟠

### Magnifier / loupe

#### AV-MAG-01 — Magnifier during edge stroke
- **Area:** magnifier · **Platform(s):** android-phone, desktop
- **Fixture/precondition:** document open, pen tool selected. No golden exists — visual-only.
- **Steps:** start a stroke near a page edge (computer-use `left_click_drag` (desktop) / `adb -s <serial> shell input swipe ...` (android) starting within ~5% of the page border) and record an ordered frame burst via `tools/uitest/Capture-DesktopAnim.ps1` / `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>` → GIF + filmstrip while the pointer is held near the edge; release and capture.
- **Expected:** the `:drawing:impl` magnifier/loupe appears, shows a zoomed inset of the region under the pointer, and tracks the pointer; it dismisses on release.
- **Pass/Fail:** PASS if the loupe appears, magnifies the correct region, and dismisses cleanly. FAIL if it never appears, magnifies the wrong region, or sticks after release.
- **Severity if broken:** 🟡

### Reflow reading mode

#### AV-REFLOW-01 — OCR/HYBRID reflow integrity (Baranovskaya)
- **Area:** reflow · **Platform(s):** desktop, android-phone
- **Fixture/precondition:** `baranovskaya` (skip if absent).
- **Steps:** open → enter reading mode (Book icon); computer-use `screenshot` the first content page; page through p1–p9 (computer-use `key Page_Down` / right-edge tap-zone), capturing each.
- **Expected:** running prose reads as paragraphs — **not** a 1-char-per-cell grid (table-noise guard, cf. F-1/F-8); headers not garbled into letter-spaced fragments (cf. F-3); table header shading uses `row.isHeader`, not row index (cf. F-2).
- **Pass/Fail:** PASS if body text is continuous and tables are real tables. FAIL if any content page degrades into single-letter cells or letter-spaced headings.
- **Severity if broken:** 🔴

#### AV-REFLOW-02 — FB2 typography + TOC (Isiguro)
- **Area:** reflow · **Platform(s):** desktop
- **Fixture/precondition:** `isiguro` (skip if absent), else the `article-small.fb2` committed fixture.
- **Steps:** open → reading mode; capture body (justified, em-dashes, dialogue dashes, running header); open TOC sidebar and capture.
- **Expected:** justified body with correct em-/dialogue-dash glyphs; running header present; TOC shows named entries (no literal `...` placeholders for untitled sections — cf. F-4).
- **Pass/Fail:** PASS if typography matches an e-reader and TOC entries are labeled. FAIL on shredded spacing, wrong dashes, or `...` TOC rows.
- **Severity if broken:** 🟡

#### AV-REFLOW-03 — Themes + typography preset + anchor persistence
- **Area:** reflow · **Platform(s):** desktop, android-phone
- **Fixture/precondition:** any document in reading mode at a mid-document page.
- **Steps:**
  1. Cycle all 5 themes (`Бумага → Сепия → Серый → Ночь → Яркий`), capturing each; inspect `Ночь` contrast under zoom.
  2. Switch typography preset (`Компактно-1 → Долгое чтение-1`); capture before/after.
  3. android only: `adb -s <serial> shell settings put system user_rotation 1` (landscape) then `adb -s <serial> shell settings put system user_rotation 0` (portrait); capture both.
- **Expected:** each theme recolors text + background legibly; after preset switch **and** rotation the reader stays on the **same passage** (anchor-based, cf. F-10), not the same page index.
- **Pass/Fail:** PASS if every theme is legible and the reading position survives preset/orientation change. FAIL if Night contrast is unreadable or the anchor jumps on re-pagination.
- **Severity if broken:** 🟠

### Gestures / zoom / pan

#### AV-GEST-01 — Zoom / fit-width / pan
- **Area:** gestures · **Platform(s):** desktop, android-phone
- **Fixture/precondition:** `baranovskaya` (or any PDF) in page mode.
- **Steps:** desktop: computer-use `left_click_drag` to pan; use the zoom affordance / computer-use `scroll` (Ctrl+wheel) zoom if available, capturing frames. android: pinch via two `adb -s <serial> shell input swipe ...`s where supported, then a pan swipe. Record an ordered frame burst across the zoom transitions via `tools/uitest/Capture-DesktopAnim.ps1` (desktop) or `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>` (android) → GIF + filmstrip.
- **Expected:** smooth zoom with `fitWidthTopInset` honored; pan stays within page bounds; no jank/tearing during the transition frames.
- **Pass/Fail:** PASS if zoom + pan are smooth and framing is correct. FAIL on visible jank, lost framing, or content escaping bounds.
- **Severity if broken:** 🟡

### EPUB / FB2 conversion render

#### AV-CONV-01 — EPUB→PDF→reflow chain
- **Area:** conversion · **Platform(s):** desktop
- **Fixture/precondition:** `planeta-epub` (skip if absent); fallback `article-small.fb2` committed fixture for the FB2→PDF path.
- **Steps:** open the EPUB (or FB2) via `Открыть`; let it convert; enter reading mode; capture body + an embedded illustration page.
- **Expected:** conversion completes (no spinner stall); reflowed text is intact (not shredded); embedded illustrations render at correct size with no height jump.
- **Pass/Fail:** PASS if the converted document reads cleanly with figures placed. FAIL on conversion stall, shredded text, or broken/oversized figures.
- **Severity if broken:** 🟠

### LAN sync visual  *(highest-value new coverage — logic is `:sync:jvmTest`-only, never seen on screen)*

#### AV-SYNC-01 — Two-instance stroke propagation
- **Area:** LAN sync · **Platform(s):** desktop (host) + android-emulator (client), or two desktop instances
- **Fixture/precondition:** desktop host running (launch via `tools/uitest/Launch-Desktop.ps1`); boot + install + launch the emulator client via `tools/uitest/Start-AndroidTarget.ps1 -Avd Medium_Phone_API_36.1` (then `-Serial <serial>` to install + launch) on the same LAN; both opened to the **same** document.
- **Steps:**
  1. Pair the two instances (peer catalog — see AV-QR-01 for the entry point).
  2. Draw a pen stroke on instance A (computer-use `left_click_drag` / `adb -s <serial> shell input swipe ...`); capture A.
  3. Within a few seconds, capture instance B (computer-use `screenshot` / `adb -s <serial> exec-out screencap -p > B.png`).
  4. Draw a conflicting/overlapping stroke on B and re-capture A — exercises last-writer-wins + tombstone.
- **Expected:** the stroke drawn on A appears on B (and vice-versa); concurrent edits resolve last-writer-wins with tombstones winning over concurrent adds, visible identically on both screens.
- **Pass/Fail:** PASS if strokes propagate both ways and the merged result matches on both instances. FAIL if a stroke never appears, appears duplicated, or the two instances diverge.
- **Severity if broken:** 🔴

### QR pairing / peer catalog

#### AV-QR-01 — Peer catalog + QR pairing entry point
- **Area:** peers · **Platform(s):** desktop (QR generation) + android-phone/emulator (camera scan)
- **Fixture/precondition:** two instances on the same LAN. NB: the peer-catalog entry point was **not located** from the library top bar in earlier runs (F-5) — finding/confirming it is part of this scenario.
- **Steps:** locate and open the mDNS peer / QR-pairing screen (audit the library top-bar affordances and any settings route); computer-use `screenshot` the desktop QR; on Android, surface the camera scanner and capture it (`adb -s <serial> exec-out screencap -p > scanner.png`).
- **Expected:** peer screen lists discovered mDNS peers; desktop renders a scannable QR; Android shows the camera scan UI.
- **Pass/Fail:** PASS if the peer screen is reachable and shows peers + QR/scan UI. FAIL if the entry point is undiscoverable (re-log F-5) or the QR/scanner doesn't render.
- **Severity if broken:** 🟡

### Android viewer / input regressions

#### AV-ANDROID-01 — Резкий двухпальцевый pinch остаётся плавным (нет переверстки/ре-растеризации каждый кадр) (rapid-pinch-stays-smooth-filmstrip)
- **Area:** ANDROID · **Platform(s):** android-emulator, android-tablet
- **Fixture/precondition:** any multi-page PDF with ink strokes on visible pages
- **Source:** 8793f80 · regression guard for rcId RC-ANDROID-004
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial> -Build` to build+install, then open a multi-page PDF with ink strokes on the visible pages.
  2. Drive a fast two-finger pinch-out over the viewer (`adb -s <serial> shell input` multitouch / sendevent, or use a real pinch on a tablet).
  3. Capture the gesture: `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>` across the full pinch (start span → max span → finger-up settle) → GIF + filmstrip.
  4. Inspect the filmstrip for dropped/duplicated frames and stutter during the active pinch; on release confirm no visual jump as layout re-measures.
- **Capture:** Capture-AndroidAnim.ps1 ordered frame burst across the full pinch-out; frame the PDF viewer region with an inked page visible → frameNNN burst → filmstrip.
- **Expected:** Pinch zoom animates smoothly frame-to-frame on the GPU layer; on finger-up the page settles to the new zoom with no visible snap/jump.
- **Pass/Fail:** PASS if the filmstrip shows continuous smooth scaling during the gesture and a seamless settle on release; FAIL if frames stutter/freeze during pinch or the page visibly jumps when the gesture commits.
- **Severity if broken:** 🟠

#### AV-ANDROID-02 — Панель настроек инструмента сверху раскрывается вниз (expandDownward) и сидит ниже PageIndicatorAirbar (top-settings-panel-expands-downward)
- **Area:** ANDROID · **Platform(s):** android-phone, android-tablet
- **Fixture/precondition:** any document open
- **Source:** 9ebe08d · regression guard for rcId RC-ANDROID-006
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>`, open the PDF editor, select the Pen tool so `ToolSettingsFloatingPanel` becomes visible at TopCenter.
  2. `adb -s <serial> shell input tap <x> <y>` on a slot (e.g. line weight) to trigger the expansion panel.
  3. Capture entry + expansion: `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>` → GIF + filmstrip.
  4. Verify the panel slides in from the top, the expansion grows DOWNWARD below the main row, the panel clears the top page-indicator airbar (~56dp offset) and the status-bar inset, and nothing is clipped.
- **Capture:** Capture-AndroidAnim.ps1 frame burst of (a) panel slide-in from top after selecting Pen and (b) slot tap → downward expansion; frame the top quarter of the editor including the page-indicator airbar → frameNNN burst → filmstrip.
- **Expected:** Top-anchored settings panel: slides from top, expands downward, sits below the page indicator and under the status-bar inset, fully visible.
- **Pass/Fail:** PASS if the panel is top-anchored, expands downward, and is not clipped or overlapping the page indicator/status bar; FAIL if it expands upward off-screen, overlaps the indicator, or is clipped by the status bar.
- **Severity if broken:** 🟡

#### AV-ANDROID-03 — Одно-пальцевый скролл PDF идёт в направлении пальца (drag вниз = контент вниз) (single-finger-scroll-follows-finger)
- **Area:** ANDROID · **Platform(s):** android-phone, android-emulator, android-tablet
- **Fixture/precondition:** a multi-page PDF tall enough to scroll
- **Source:** c018b3a · regression guard for rcId RC-ANDROID-007
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>`, open a multi-page PDF tall enough to scroll.
  2. Perform a single-finger drag DOWNWARD on the viewer (`adb -s <serial> shell input swipe <x> <y1> <x> <y2> 400`); capture with `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>` → GIF + filmstrip.
  3. Observe the document content direction; repeat with an upward drag.
  4. Confirm content follows the finger: dragging down reveals content above (page moves down with the finger), dragging up reveals content below.
- **Capture:** Capture-AndroidAnim.ps1 frame burst of a single-finger downward swipe then an upward swipe over the PDF viewer; frame the full viewer so content displacement direction is visible → frameNNN burst → filmstrip.
- **Expected:** Content tracks the finger — downward drag moves the page down (reveals earlier content), upward drag moves it up; no inverted scroll.
- **Pass/Fail:** PASS if the page moves in the same direction as the finger on both up and down drags; FAIL if the page moves opposite to the finger (regression to reverseDirection=true).
- **Severity if broken:** 🟠

#### AV-ANDROID-04 — Конкурентный рендер/закрытие страниц 4 PdfRenderer-инстансов не падает с нативным SIGSEGV (FT_Done_Face) (concurrent-pdfrenderer-no-native-crash)
- **Area:** ANDROID · **Platform(s):** android-tablet, android-emulator
- **Fixture/precondition:** any document open
- **Source:** d65511e · regression guard for rcId RC-ANDROID-008
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>` (ideally a Huawei-class device per the crash report) and `adb -s <serial> logcat -c`.
  2. Open the library so the thumbnail generator rasterizes several PDFs/EPUBs concurrently; simultaneously open a multi-page/spread PDF in the editor (editor page renderer) and trigger a PDF export of another document — exercising all four PdfRenderer users at once.
  3. Drive this for an extended session (open/close documents, scroll spreads) while monitoring `adb -s <serial> logcat` for native crashes (SIGSEGV, tombstone, `FT_Done_Face` / `CPDF_Page::~CPDF_Page`).
  4. Capture logcat across the session; optionally a screen-record filmstrip via `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>` to correlate UI state with any crash.
- **Capture:** `adb -s <serial> logcat` capture over the concurrent-render session (filter for `*** *** native crash` / `signal 11` / `FT_Done_Face`); optional Capture-AndroidAnim.ps1 screen burst → filmstrip to correlate.
- **Expected:** No native crash: all concurrent PdfRenderer open/render/close run serialized through PdfiumRenderLock; thumbnails, editor pages, and export all complete.
- **Pass/Fail:** PASS if no SIGSEGV/tombstone (no FT_Done_Face / CPDF_Page destructor crash) appears in logcat through heavy concurrent rendering; FAIL if a native crash in the pdfium font destructor occurs (regression to per-renderer or missing lock).
- **Severity if broken:** 🔴

### Annotation persistence

#### AV-ANNOT-01 — Аннотации, открытые по content:// URI, сохраняются и восстанавливаются в приватное хранилище приложения (content-uri-sidecar-persists)
- **Area:** ANNOT · **Platform(s):** android-emulator, android-tablet
- **Fixture/precondition:** any multi-page PDF opened via SAF content:// URI
- **Source:** 0e3d4e9 · regression guard for rcId RC-ANNOT-001
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>`; launch NotePen and open a PDF via the system document picker (resulting in a content:// URI, not a file path).
  2. Draw 2-3 ink strokes on page 1 (`adb -s <serial> shell input swipe ...`).
  3. Trigger save (system back / leave editor) and fully close the document.
  4. Re-open the SAME document through the picker.
  5. `adb -s <serial> exec-out screencap -p > after-reopen.png` of page 1 after reload.
- **Capture:** Capture-AndroidAnim.ps1 single screenshots: `01-after-draw.png` and `02-after-reopen.png`; compare ink presence.
- **Expected:** The strokes drawn before close are present again on page 1 after reopening; no FileNotFoundException in logcat during save.
- **Pass/Fail:** PASS if the reloaded page shows the previously drawn strokes. FAIL if the page is blank (no persistence) or logcat shows FileNotFoundException on save.
- **Severity if broken:** 🔴

#### AV-ANNOT-02 — Имя документа для content:// URI берётся из ContentResolver, а не из непрозрачного сегмента URI (display-name-from-resolver)
- **Area:** ANNOT · **Platform(s):** android-emulator, android-tablet
- **Fixture/precondition:** a PDF with a recognizable display name picked via SAF
- **Source:** 0e3d4e9 · regression guard for rcId RC-ANNOT-003
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>`; open a PDF named e.g. 'Lecture Notes.pdf' via the system picker (content:// URI).
  2. Observe the tab label / title shown in the editor.
  3. `adb -s <serial> exec-out screencap -p > tab-bar.png` of the tab bar.
- **Capture:** `01-tab-bar.png` single screenshot of the tab bar after open.
- **Expected:** Tab/title shows the human-readable file name (e.g. 'Lecture Notes.pdf'), not a URL-encoded opaque id like 'document%3A12001'.
- **Pass/Fail:** PASS if the real display name appears. FAIL if the label is a percent-encoded/opaque URI segment.
- **Severity if broken:** 🟠

#### AV-ANNOT-03 — Системная кнопка/жест «назад» в редакторе сохраняет аннотации перед уходом (back-gesture-flushes-save)
- **Area:** ANNOT · **Platform(s):** android-emulator, android-tablet
- **Fixture/precondition:** any PDF
- **Source:** 0e3d4e9 · regression guard for rcId RC-ANNOT-004
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>`; open a document, draw a stroke (`adb -s <serial> shell input swipe ...`), then IMMEDIATELY (before the autosave debounce window elapses) press the Android system back gesture/button (`adb -s <serial> shell input keyevent 4`).
  2. Re-open the same document.
  3. `adb -s <serial> exec-out screencap -p > after-reopen.png` after reopen.
- **Capture:** Capture-AndroidAnim.ps1: `01-after-draw.png`, perform back, `02-after-reopen.png`.
- **Expected:** The stroke drawn right before pressing back is persisted and visible after reopening.
- **Pass/Fail:** PASS if the last stroke survives a back-immediately-after-draw. FAIL if the stroke is lost (debounce never flushed).
- **Severity if broken:** 🔴

#### AV-ANNOT-04 — Изменение настроек инструмента (толщина/цвет/ластик) без рисования всё равно сохраняется автосейвом (settings-only-edit-autosaves)
- **Area:** ANNOT · **Platform(s):** desktop, android-emulator
- **Fixture/precondition:** any PDF; observe pen/marker/eraser panel
- **Source:** 0e3d4e9 · regression guard for rcId RC-ANNOT-005
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` (or `Start-AndroidTarget.ps1 -Serial <serial>`), open a document, change ONLY a tool setting (e.g. pen thickness or color) via computer-use, do NOT draw any stroke.
  2. Wait past the debounce window, then close and reopen the document.
  3. Inspect the active pen/marker/eraser settings after reopen and screenshot the tool panel.
- **Capture:** Capture-DesktopAnim.ps1 / screenshot `01-tool-panel-after-reopen.png` of tool panel state after reopen.
- **Expected:** The changed tool setting (thickness/color/eraser mode) is restored after reopening.
- **Pass/Fail:** PASS if the settings-only change persists. FAIL if it reverts to the prior/default value (autosave never fired).
- **Severity if broken:** 🟠

#### AV-ANNOT-05 — Завершённые штрихи не исчезают во время пинч-зума — масштабируются вместе со страницей (strokes-visible-during-pinch)
- **Area:** ANNOT · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** a PDF page with pre-existing strokes
- **Source:** 0e3d4e9 · regression guard for rcId RC-ANNOT-006
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` (or `Start-AndroidTarget.ps1 -Serial <serial>`), open a page that already has several completed ink strokes.
  2. Perform a pinch-zoom gesture (in and/or out) on the page (Ctrl+wheel via computer-use on desktop, two-finger pinch on tablet).
  3. Capture an animation filmstrip across the whole pinch gesture via `Capture-DesktopAnim.ps1` / `Capture-AndroidAnim.ps1` → GIF + filmstrip.
- **Capture:** Capture-DesktopAnim.ps1 / Capture-AndroidAnim.ps1 ordered frame burst spanning pinch-start → mid → pinch-end → GIF + filmstrip.
- **Expected:** Strokes remain visible and scale together with the PDF page throughout the pinch; they may look slightly soft mid-gesture but never disappear.
- **Pass/Fail:** PASS if ink stays visible every frame of the pinch. FAIL if strokes blink out to invisible while the gesture is active.
- **Severity if broken:** 🟠

#### AV-ANNOT-06 — Открытие исписанной страницы: нет позднего скачка зума и нет длинного лага первого рендера (open-inked-page-no-lag-no-zoom-jump)
- **Area:** ANNOT · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** a stroke-heavy page saved at a non-default zoom/scroll position
- **Source:** 9662d35 · regression guard for rcId RC-ANNOT-010
- **Steps:**
  1. Use a page densely covered with many committed strokes (hundreds+), saved at a non-default zoom.
  2. `tools/uitest/Launch-Desktop.ps1` (or `Start-AndroidTarget.ps1 -Serial <serial>`) and open that document fresh (cold cache).
  3. Capture an animation filmstrip from the moment the page appears through the first ~1-2 seconds via `Capture-DesktopAnim.ps1` / `Capture-AndroidAnim.ps1` → GIF + filmstrip.
- **Capture:** Capture-DesktopAnim.ps1 / Capture-AndroidAnim.ps1 ordered frame burst across the open transition → GIF + filmstrip; watch for a zoom snap and frame stalls.
- **Expected:** The page appears at the saved zoom immediately (no late jump/re-rotate to a different scale after 1-2s), and the first render is smooth with no prolonged main-thread freeze while ink fills in.
- **Pass/Fail:** PASS if zoom is correct from the first frame and there is no multi-frame freeze on open. FAIL if zoom snaps later or the UI stalls re-rasterising all strokes per frame.
- **Severity if broken:** 🟠

### Shared top-bar / common chrome

#### AV-COMMON-01 — Портретный топ-бар: инструменты/делитель/сегмент B прижаты к правому краю, панель настроек выровнена вправо (portrait-topbar-right-align)
- **Area:** COMMON · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** any document open
- **Source:** 47d194c · regression guard for rcId RC-COMMON-001
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` and point computer-use at the `java.exe` dev window.
  2. Open a document, resize the window to a PORTRAIT proportion (height > width) so the PortraitTopBar is shown.
  3. `tools/uitest/Capture-DesktopAnim.ps1`: capture a frame of the top bar; then select a tool (ToolMode != NONE) via computer-use so VerticalDivider + segment B and the settings panel appear.
  4. Capture a frame of the top bar with the tool-settings expansion panel open.
- **Capture:** Capture-DesktopAnim.ps1 framed on the top strip: (1) bar with no tool selected — ToolSelector/divider/segment B grouped at the RIGHT edge, empty space on the left; (2) bar with a tool selected — `ToolSettingsExpansionContent` panel right-aligned (right edge ≈ under segment B, with PORTRAIT_BAR_PADDING_H inset), NOT centered → stills + filmstrip.
- **Expected:** Tools, divider and segment B are pinned to the right edge of the bar; the divider sits flush against the ToolSelector; the settings-expansion panel is right-aligned under segment B, not centered.
- **Pass/Fail:** PASS if the tools/divider/segmentB group is at the right edge and the settings panel is right-aligned; FAIL if they are centered/left or the panel is centered.
- **Severity if broken:** 🟡

#### AV-COMMON-02 — Вход в режим чтения сразу даёт фокус: первый Right/Space листает страницу (reading-mode-focus-on-enter)
- **Area:** COMMON · **Platform(s):** desktop
- **Fixture/precondition:** any document open
- **Source:** bf14335 · regression guard for rcId RC-COMMON-002
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, point computer-use at the `java.exe` dev window.
  2. Open a document/book on a fresh launch (mouse-only, no taps on the canvas).
  3. Enable reading mode (readingMode toggle).
  4. WITHOUT clicking the canvas, immediately press the `Right` key (or `Space`) once via computer-use `key`.
  5. `tools/uitest/Capture-DesktopAnim.ps1`: capture a filmstrip of the page turn on the first keypress.
- **Capture:** Capture-DesktopAnim.ps1 framed on the page area: frame burst from the moment of the Right/Space press — must capture a page turn to the next page on the FIRST press (no prior canvas click) → frameNNN burst → filmstrip.
- **Expected:** Right after activating reading mode the first Right/Space press turns the page — focus is already on the root Box handler.
- **Pass/Fail:** PASS if the first Right/Space turns the page with no prior click; FAIL if the first press is ignored / focus is on a chip/airbar and the page does not change.
- **Severity if broken:** 🟠

### Desktop viewer / window regressions

#### AV-DESKTOP-01 — Миниатюры PDF/изображений видимы на Windows-десктопе после отключения DnD (thumbnails-visible-windows)
- **Area:** DESKTOP · **Platform(s):** desktop
- **Fixture/precondition:** any document open
- **Source:** 044978e, c37bd01 · regression guard for rcId RC-DESKTOP-003
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` and drive the `java.exe` dev window via computer-use.
  2. Open the library/recents screen that shows PDF and image cards.
  3. computer-use `screenshot` of the card grid.
- **Capture:** Single screenshot `01-library-grid.png` of the populated library grid; no animation needed.
- **Expected:** Each PDF/image card shows its rendered thumbnail, not a blank/placeholder tile.
- **Pass/Fail:** PASS if thumbnails render in the cards; FAIL if cards are blank where a thumbnail should be.
- **Severity if broken:** 🟠

#### AV-DESKTOP-02 — Страница визуально отцентрована по горизонтали при первом открытии документа на десктопе (initial-center-visible)
- **Area:** DESKTOP · **Platform(s):** desktop
- **Fixture/precondition:** any document open
- **Source:** 0e16f1c, 788a4fc · regression guard for rcId RC-DESKTOP-006
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` and open a PDF for the first time via computer-use.
  2. computer-use `screenshot` of the editor immediately after the page loads.
- **Capture:** Single screenshot `01-first-open-center.png` right after first page render; compare left vs right margin.
- **Expected:** The page sheet sits horizontally centered in the viewport with symmetric left/right gaps.
- **Pass/Fail:** PASS if left and right margins around the page are visually equal on first open; FAIL if the page is hugging the left edge or off-center.
- **Severity if broken:** 🟠

#### AV-DESKTOP-03 — Скроллбары перетаскиваются мышью (вынесены из перехватывающего pointer-input Box) (scrollbars-draggable)
- **Area:** DESKTOP · **Platform(s):** desktop
- **Fixture/precondition:** any document open
- **Source:** be5aefa · regression guard for rcId RC-DESKTOP-010
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a multi-page PDF so the vertical scrollbar appears.
  2. Drag the scrollbar thumb downward with the mouse via computer-use `left_click_drag`.
  3. Record an ordered frame burst with `tools/uitest/Capture-DesktopAnim.ps1` and build the filmstrip.
- **Capture:** Capture-DesktopAnim.ps1 frame burst of the thumb-drag motion → GIF + filmstrip.
- **Expected:** Dragging the scrollbar thumb scrolls the document; the press is not swallowed by drag-to-pan.
- **Pass/Fail:** PASS if thumb-drag moves the page through the document; FAIL if the thumb does not respond and the canvas pans instead.
- **Severity if broken:** 🟠

#### AV-DESKTOP-04 — Длинный pen-штрих не даёт lag→прямая-линия артефакт (переиспользование Path вместо ~48k аллокаций/с) (pen-stroke-scratch-path-no-line-artefact)
- **Area:** DESKTOP · **Platform(s):** desktop
- **Fixture/precondition:** any document open
- **Source:** 93988e2 · regression guard for rcId RC-DESKTOP-014
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a PDF, select the pen tool via computer-use.
  2. Draw a long continuous curved stroke (200+ points) at speed via computer-use `left_click_drag`.
  3. Capture an ordered frame burst with `tools/uitest/Capture-DesktopAnim.ps1` → filmstrip.
- **Capture:** Capture-DesktopAnim.ps1 frame burst across the full stroke draw → GIF + filmstrip to inspect for a straight chord.
- **Expected:** The curve renders smoothly with no momentary freeze followed by a straight chord across part of the stroke.
- **Pass/Fail:** PASS if the long stroke stays curved with no straight-line gap; FAIL if any straight segment appears mid-stroke after a stall.
- **Severity if broken:** 🟠

#### AV-DESKTOP-05 — Резкий zoom-out не лагает: стейл-рендеры отменяются (collectLatest), большие битмапы дропаются проактивно (zoom-out-no-lag-cancel-renders)
- **Area:** DESKTOP · **Platform(s):** desktop
- **Fixture/precondition:** any document open
- **Source:** 86b5265 · regression guard for rcId RC-DESKTOP-018
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a PDF, zoom in to a high scale then rapidly zoom out to a low scale via Ctrl+wheel (computer-use `scroll`).
  2. Capture an ordered frame burst with `tools/uitest/Capture-DesktopAnim.ps1` during the zoom-out.
- **Capture:** Capture-DesktopAnim.ps1 frame burst across the rapid zoom-out → GIF + filmstrip; look for dropped/frozen frames.
- **Expected:** Zoom-out stays responsive with no multi-second freeze; the page resolves to a sharp render after the user pauses.
- **Pass/Fail:** PASS if zoom-out is smooth with no long GC stall; FAIL if the canvas freezes for seconds during rapid zoom-out.
- **Severity if broken:** 🟠

### Editor chrome / insets / focus

#### AV-EDITOR-01 — Верхний chrome редактора учитывает вырез камеры (notch), а не только статус-бар (top-chrome clears display cutout not just status bar)
- **Area:** EDITOR · **Platform(s):** android-emulator, android-phone
- **Fixture/precondition:** any multi-page PDF
- **Source:** 35b1a30 · regression guard for rcId RC-EDITOR-001
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial> -Build` onto a device/emulator whose display profile has a TOP camera cutout (e.g. Pixel punch-hole), in portrait.
  2. Open any PDF in the editor (immersive mode hides the system bars).
  3. `adb -s <serial> exec-out screencap -p > top-edge.png` of the top edge where the panel tab strips render.
  4. Compare the top-row tab strips against the cutout: their top edge must sit BELOW the notch, never under it.
- **Capture:** `01-top-edge-notch.png` single screenshot of editor top edge on a top-cutout device profile, portrait; crop to the notch region.
- **Expected:** In portrait with a top notch, the workspace grid and top-row panel tab strips are pushed down to clear the display cutout even though the status bar is hidden.
- **Pass/Fail:** PASS if the top tab strip / toolbar starts below the camera cutout with no overlap. FAIL if any tab strip pixel is occluded by or rendered under the notch.
- **Severity if broken:** 🟠

#### AV-EDITOR-02 — В ландшафте боковой вырез не перекрывает крайние tab-strip панелей (landscape side cutout cleared for tab strips)
- **Area:** EDITOR · **Platform(s):** android-emulator, android-tablet
- **Fixture/precondition:** any multi-page PDF
- **Source:** f2c261c · regression guard for rcId RC-EDITOR-002
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>` on a device/emulator profile with a camera cutout, rotated to LANDSCAPE so the cutout sits on a horizontal edge.
  2. Open a PDF in the editor.
  3. `adb -s <serial> exec-out screencap -p > side-edge.png` of the left/right edge where the outer panels' tab strips render.
  4. Inspect the tab-strip edges and the content below them against the side cutout.
  5. Verify the tool rail self-inset is NOT double-applied (rail not pushed in twice as far as the cutout width).
- **Capture:** `01-side-edge-cutout.png` screenshot of editor side edge, landscape, on a cutout device profile; crop to the cutout side.
- **Expected:** In landscape the whole editor is inset from the horizontal cutout so outer tab strips and their content never slide under the side notch; portrait/desktop/cutout-less screens are unaffected (no-op).
- **Pass/Fail:** PASS if outer tab strips clear the side cutout and the tool rail is inset exactly once. FAIL if a tab strip is under the side notch, or the rail is double-inset.
- **Severity if broken:** 🟠

#### AV-EDITOR-03 — После возврата фокуса (фон/шторка уведомлений) редактор снова принимает касания (immersive re-asserted on focus gain restores touch)
- **Area:** EDITOR · **Platform(s):** android-emulator, android-phone
- **Fixture/precondition:** any PDF
- **Source:** faa10c5 · regression guard for rcId RC-EDITOR-003
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>` and open a PDF in the editor.
  2. Pull down the notification shade (`adb -s <serial> shell cmd statusbar expand-notifications`) or background via Recents, then return to the editor so it regains window focus.
  3. Dispatch a draw gesture (`adb -s <serial> shell input swipe ...`) on the page and capture a before/after filmstrip with `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>`.
  4. Confirm a stroke is rendered, i.e. the touch was not swallowed.
- **Capture:** Capture-AndroidAnim.ps1 filmstrip of a draw swipe immediately after returning focus from the notification shade → frameNNN burst → filmstrip.
- **Expected:** After focus is regained following backgrounding or the notification shade, touches on the editor are accepted and drawing works; the stale transient touch layer is torn down.
- **Pass/Fail:** PASS if a stroke appears after the focus-regain gesture. FAIL if the gesture produces no stroke / no visible response (touches swallowed).
- **Severity if broken:** 🔴

#### AV-EDITOR-04 — При открытии PDF на сохранённой странице (page>0) на Android он центрируется по X, а не прилипает к левому краю (android restore re-centers X not pinned left)
- **Area:** EDITOR · **Platform(s):** android-emulator, android-phone
- **Fixture/precondition:** multi-page PDF (>1 page), saved at a non-zero page
- **Source:** b505080 · regression guard for rcId RC-EDITOR-006
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>`; open a PDF, scroll to a page > 0 and trigger a view-state save (background/return or wait for autosave debounce).
  2. Kill and re-open the app on the same document so the saved page is restored.
  3. `adb -s <serial> exec-out screencap -p > restored.png` of the restored page.
  4. Verify the page is horizontally centered in the viewport (equal left/right margins), not pinned to the left edge, and the restored vertical position (page/offset) is preserved.
- **Capture:** `01-restored-page.png` screenshot of the editor immediately after re-opening on a restored page>0; measure left vs right margin.
- **Expected:** Restoring a saved page>0 on Android shows the page centered along the X axis with the scrolled Y preserved; it does not stick to the left edge.
- **Pass/Fail:** PASS if the restored page is horizontally centered with the correct page/offset. FAIL if it is left-pinned (left margin ~0, content cut on the right).
- **Severity if broken:** 🟠

### Stylus / pencil-mode input

#### AV-INPUT-01 — После касания пером палец снова рисует на странице, если Pencil Mode выключен вручную (finger-draws-after-manual-pencil-off-on-page)
- **Area:** INPUT · **Platform(s):** android-tablet, android-phone
- **Fixture/precondition:** any document open
- **Source:** 2f2658a · regression guard for rcId RC-INPUT-001
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` (mouse-as-pen emulation) and/or `Start-AndroidTarget.ps1 -Serial <serial>`; open a PDF in the editor and select a drawing tool.
  2. Make one pen/stylus stroke on the page (latches stylusEverSeen=true and auto-enables Pencil Mode).
  3. Manually toggle Pencil Mode OFF in the editor (pencilModeManuallyTouched=true, pencilModeEnabled=false).
  4. Drag a finger across the page, attempting to draw a stroke (`adb -s <serial> shell input swipe ...`).
  5. Capture an ordered frame burst via `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>` (or `Capture-DesktopAnim.ps1`) → GIF + filmstrip.
- **Capture:** Filmstrip of 3 phases: (1) pen stroke, (2) Pencil Mode toggle = off, (3) finger stroke visible on the page. Capture-AndroidAnim.ps1 burst ~6 frames along the finger path; final frame cropped to the stroke region.
- **Expected:** After manually disabling Pencil Mode, the finger again leaves a stroke on the page; the stylus stroke drew, and after the off-toggle the finger draws.
- **Pass/Fail:** PASS if the final filmstrip frame shows a finger stroke on the page after disabling Pencil Mode. FAIL if the finger leaves no stroke (palm-reject stuck due to stylusEverSeen).
- **Severity if broken:** 🟠

#### AV-INPUT-02 — Внутри лупы палец снова пишет после касания пером при выключенном Pencil Mode (finger-draws-in-magnifier-after-manual-pencil-off)
- **Area:** INPUT · **Platform(s):** android-tablet, android-phone
- **Fixture/precondition:** any document open
- **Source:** 2f2658a · regression guard for rcId RC-INPUT-002
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>`; open a PDF and enable the magnifier/loupe (MagnifierState.enabled).
  2. Make one pen stroke inside the loupe panel (latches stylusEverSeen).
  3. Manually toggle Pencil Mode OFF.
  4. Drag a finger inside the loupe panel, attempting to draw (`adb -s <serial> shell input swipe ...`).
  5. Capture an ordered frame burst via `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>` → GIF + filmstrip, cropped to the loupe panel.
- **Capture:** Filmstrip ~6 frames along the finger path inside the loupe panel bounds; crop to the MagnifierContent panel. Confirm a stroke is present in the final frame.
- **Expected:** After disabling Pencil Mode, the finger again draws inside the loupe panel (externalInputController.onDown/onMove fire for the finger).
- **Pass/Fail:** PASS if the final frame shows a finger stroke inside the loupe panel after the off-toggle. FAIL if the finger does not draw inside the loupe (stuck stylusEverSeen).
- **Severity if broken:** 🟠

### Magnifier / loupe (mined regressions)

#### AV-MAG-02 — Lift-off авто-прокрутка сдвигает рамку в одну из 4 edge-зон на отрыве пера (liftoff-autoscroll-4dir)
- **Area:** MAG · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** single-page PDF, loupe enabled, autoscroll ON
- **Source:** 0681700 · regression guard for rcId RC-MAG-002
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a PDF, enable the loupe with autoscroll toggled ON.
  2. Note the dashed target-frame position on the page.
  3. Write a short stroke inside the panel that ENDS near the RIGHT edge (within the rightmost 20%) and lift the pen.
  4. Repeat ending near BOTTOM, near LEFT, and a diagonal stroke ending in the bottom-right corner.
- **Capture:** `tools/uitest/Capture-DesktopAnim.ps1 -DurationMs 1500 -Fps 30` around each pen-up to capture the frame advance; filmstrip `frameNNN.png` showing frame position before/after for right, bottom, left, diagonal endings; still `01-center-no-shift.png`.
- **Expected:** On pen-up the target frame jumps ~35% of its width toward the right (and ~35% of height for bottom etc.); diagonal end shifts both axes; a stroke ending in the panel center does NOT move the frame. The shift is a gentle advance (~1/3 window), not a hard 85% jump.
- **Pass/Fail:** PASS if the frame advances by ~1/3 of its size in the matching edge direction(s) only after lift-off and only when the last point was in an edge zone. FAIL if it advances mid-stroke, jumps ~85%, moves in the wrong direction, or fails to compose diagonals.
- **Severity if broken:** 🟠

#### AV-MAG-03 — Лупа держит per-frame бюджет: кончик пера не отстаёт на длинном штрихе (loupe-cached-ink-60fps)
- **Area:** MAG · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** single-page PDF, loupe enabled, pen tool, page pre-filled with strokes
- **Source:** 0791657 · regression guard for rcId RC-MAG-003
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a PDF, enable the loupe.
  2. Fill the page with many completed strokes inside the panel so the completed-layer is dense.
  3. Draw one long continuous stroke (several seconds) without lifting and observe the live tip vs pen position.
  4. Capture the motion as a frame burst.
- **Capture:** `tools/uitest/Capture-DesktopAnim.ps1 -DurationMs 3000 -Fps 60` during the long stroke → anim.gif + filmstrip; measure tip-to-pen offset across frames.
- **Expected:** The rendered ink tip stays glued to the pen at ~60fps regardless of how much ink is already on the page or how long the current stroke gets; no growing lag/trailing as the stroke lengthens; baked older segments stay crisp (panel-resolution, no upscale blur).
- **Pass/Fail:** PASS if tip-to-pen gap stays constant and small across a long stroke over a heavily-inked page. FAIL if the gap grows with stroke length or with accumulated ink, or baked ink looks blurry.
- **Severity if broken:** 🟠

#### AV-MAG-04 — Тонкий штрих не исчезает в лупе (минимум 1px) (min-rendered-stroke-1px)
- **Area:** MAG · **Platform(s):** desktop
- **Fixture/precondition:** any PDF, pen tool thinnest width
- **Source:** 0791657 · regression guard for rcId RC-MAG-004
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a PDF, select pen, set stroke-width slider to thinnest.
  2. Enable the loupe and draw a light/low-pressure stroke inside the panel.
  3. Inspect the rendered line in the panel and on the page.
- **Capture:** stills `01-thin-stroke-panel.png` and `02-thin-stroke-page.png` at high zoom of the drawn line.
- **Expected:** The stroke renders as a continuous visible line at least ~1px wide even at the thinnest slider position and minimum pressure; no gaps or invisible segments.
- **Pass/Fail:** PASS if the thinnest minimum-pressure stroke is continuously visible. FAIL if it disappears or breaks into dashes.
- **Severity if broken:** 🟡

#### AV-MAG-05 — Лупа сохраняет aspect-ratio страницы (без растяжения относительно рамки) (loupe-aspect-ratio)
- **Area:** MAG · **Platform(s):** desktop
- **Fixture/precondition:** non-square (A4) PDF, loupe enabled
- **Source:** 2959eb6 · regression guard for rcId RC-MAG-005
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a NON-square PDF (e.g. tall A4) large enough that one render axis would hit MAX_RENDER_DIM_PX.
  2. Enable the loupe, place the dashed target frame over text.
  3. Compare the magnified content in the panel against the dashed frame outline; resize the panel and re-check.
- **Capture:** still `01-frame-on-page.png` (dashed frame over a known shape) + `02-panel-content.png`; overlay/measure aspect of a reference glyph or circle in both.
- **Expected:** The magnified content inside the panel has the same aspect ratio as the region inside the dashed frame; circles stay circular, text is not anisotropically stretched; panel aspect matches the frame aspect after resize.
- **Pass/Fail:** PASS if panel content aspect == dashed-frame region aspect on a non-square page and after panel/frame resize. FAIL if content looks horizontally or vertically stretched vs the dashed frame.
- **Severity if broken:** 🟠

#### AV-MAG-06 — В pencil mode одиночный палец панорамирует рамку, стилус продолжает писать (single-finger-pan-ignores-stylus)
- **Area:** MAG · **Platform(s):** android-tablet
- **Fixture/precondition:** PDF, loupe enabled, pencil mode ON, stylus + finger
- **Source:** 3128f49 · regression guard for rcId RC-MAG-006
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>`; open a PDF, enable the loupe, enable pencil mode.
  2. With the stylus, draw a stroke inside the panel (real pen / S-Pen) and watch the target frame.
  3. Separately, drag with a single FINGER inside the panel (`adb -s <serial> shell input swipe ...`) and watch the frame.
- **Capture:** `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial> -DurationMs 2000` for the stylus stroke (frame must stay fixed) and a second burst for the finger pan (frame moves); filmstrips for each.
- **Expected:** A stylus (pen/eraser) stroke writes ink and the loupe frame stays put; a single finger drag pans the loupe frame (content-follows-finger). The frame does NOT move while the stylus writes.
- **Pass/Fail:** PASS if stylus single-pointer writes without moving the frame AND single finger pans the frame in pencil mode. FAIL if a stylus stroke drags/moves the loupe frame.
- **Severity if broken:** 🔴

#### AV-MAG-07 — При выключенном pencil mode палец рисует внутри панели лупы (finger-draw-panel-pencil-off)
- **Area:** MAG · **Platform(s):** android-tablet, android-phone
- **Fixture/precondition:** PDF, loupe enabled, finger input
- **Source:** 3fa547f · regression guard for rcId RC-MAG-007
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>`, open a PDF, enable the loupe, ensure pencil mode is OFF.
  2. Drag a single finger inside the panel content area (`adb -s <serial> shell input swipe ...`).
  3. Then enable pencil mode and repeat the finger drag.
- **Capture:** `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial> -DurationMs 1500` for finger-draw (ink appears) and for pencil-mode-on finger-pan (frame moves); filmstrips.
- **Expected:** With pencil mode OFF, a finger drag draws ink inside the panel; with pencil mode ON, the same finger drag pans the loupe frame (does not draw).
- **Pass/Fail:** PASS if finger draws when pencil mode off and pans when pencil mode on. FAIL if finger cannot draw inside the panel with pencil mode off.
- **Severity if broken:** 🟠

#### AV-MAG-08 — При активной лупе draw/erase/select на странице вне панели работают (page-draw-outside-panel)
- **Area:** MAG · **Platform(s):** desktop
- **Fixture/precondition:** PDF, loupe enabled, pen + eraser + select tools
- **Source:** 4d79c8e · regression guard for rcId RC-MAG-008
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a PDF, enable the loupe (floating panel visible).
  2. With the pen, draw a stroke on the PAGE in an area OUTSIDE the panel and outside the dashed frame via computer-use.
  3. Switch to eraser and erase a page stroke outside the panel; then switch to lasso/select outside the panel.
- **Capture:** still `01-page-stroke-outside.png` (ink committed outside panel); `tools/uitest/Capture-DesktopAnim.ps1 -DurationMs 1500` for the erase action filmstrip.
- **Expected:** Strokes drawn on the page outside the panel/frame are committed normally; erase and select also work on the page while the loupe is open; gestures inside the panel/frame still route to the loupe (not the page).
- **Pass/Fail:** PASS if draw, erase, and select all function on the page outside the loupe panel while the loupe is enabled. FAIL if any page gesture outside the panel is dead/ignored.
- **Severity if broken:** 🔴

#### AV-MAG-09 — Hit-тест панели лупы учитывает window-vs-viewer origin (перо и рамка не расходятся) (loupe-hittest-window-origin)
- **Area:** MAG · **Platform(s):** desktop
- **Fixture/precondition:** PDF opened in a tab (tab-strip visible), loupe enabled
- **Source:** 4d79c8e · regression guard for rcId RC-MAG-009
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a PDF in a tab so the tab-strip/reading inset is present, enable the loupe.
  2. Move the pen over the panel and verify the cursor/ink lands where the panel is drawn (not offset by the tab-strip height).
  3. Draw a stroke inside the panel near its top edge and confirm ink appears under the pen, not above/below it.
- **Capture:** `tools/uitest/Capture-DesktopAnim.ps1 -DurationMs 1500` of a stroke near the panel top edge; filmstrip showing pen-tip vs ink alignment.
- **Expected:** Pen input inside the panel maps 1:1 to the drawn panel region; ink and frame stay under the pen with no vertical offset equal to the tab-strip/reading inset.
- **Pass/Fail:** PASS if panel ink/hit-test align with the visible panel with the tab-strip present. FAIL if input is offset by the inset (pen and panel/frame diverge).
- **Severity if broken:** 🟠

#### AV-MAG-10 — Повторное выделение области лупы достижимо при уже открытой лупе (loupe-reselect-while-open)
- **Area:** MAG · **Platform(s):** desktop
- **Fixture/precondition:** PDF, loupe enabled, open-trigger available
- **Source:** 4d79c8e · regression guard for rcId RC-MAG-010
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a PDF, enable the loupe.
  2. Hold the loupe open-trigger (or arm quick-loupe) and drag a new selection rectangle on the page outside the existing panel via computer-use.
  3. Release.
- **Capture:** `tools/uitest/Capture-DesktopAnim.ps1 -DurationMs 1500` of the re-selection drag; filmstrip showing panel retargeting.
- **Expected:** A new loupe target region is selected and the panel re-targets to it; without the trigger held the same drag draws ink instead.
- **Pass/Fail:** PASS if a fresh region can be selected while the loupe is already open (trigger held) AND a plain drag draws. FAIL if re-selection is unreachable.
- **Severity if broken:** 🟠

#### AV-MAG-11 — При активной лупе прямой PDF-ввод полностью заблокирован (skipPage = enabled) (block-pdf-input-while-loupe)
- **Area:** MAG · **Platform(s):** android-tablet, android-emulator
- **Fixture/precondition:** PDF, loupe enabled, Android
- **Source:** 965bfe6 · regression guard for rcId RC-MAG-011
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>`, open a PDF, enable the loupe.
  2. With the pen, try to draw directly on the PDF page outside the panel/frame (`adb -s <serial> shell input swipe ...`).
- **Capture:** `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial> -DurationMs 1500` of a pen drag on the page outside the panel; filmstrip shows no ink committed.
- **Expected:** On Android (DetailsContent path) direct PDF strokes do not appear while the loupe is active; ink only registers inside the panel.
- **Pass/Fail:** PASS if direct page strokes are blocked while the loupe is enabled on Android. FAIL if pen-outside-panel produces stray page strokes or both pipelines contend (visible lag).
- **Severity if broken:** 🟠

#### AV-MAG-12 — Hover-кружок кончика пера скрыт во время активного штриха в лупе (hide-hover-dot-while-drawing)
- **Area:** MAG · **Platform(s):** android-tablet
- **Fixture/precondition:** PDF, pen tool, stylus that emits hover during contact
- **Source:** 965bfe6 · regression guard for rcId RC-MAG-012
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>` with an S-Pen/Pencil, open a PDF, select pen.
  2. Hover the stylus (dot should show) then start and continue a stroke.
  3. Observe whether the hover dot remains at the start point during the stroke.
- **Capture:** `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial> -DurationMs 1500` spanning hover→down→draw; filmstrip frame at stroke-start shows no dot.
- **Expected:** The hover indicator shows while hovering but disappears the moment a stroke is active; no ghost dot pinned at the stroke origin during drawing.
- **Pass/Fail:** PASS if hover dot is hidden during an active stroke. FAIL if a hover dot stays visible/pinned while drawing.
- **Severity if broken:** 🟡

#### AV-MAG-13 — В развороте viewport-rect рамки правой страницы смещён на pageLeftsPx (spread-targetrect-x-offset)
- **Area:** MAG · **Platform(s):** desktop
- **Fixture/precondition:** multi-page PDF in SPREAD layout, loupe on right page
- **Source:** d7725f7 · regression guard for rcId RC-MAG-015
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a multi-page PDF, switch to SPREAD (two-page) layout.
  2. Enable the loupe targeting the RIGHT page of a pair.
  3. With the pen, write on the LEFT page of the same sheet (away from the right-page frame) via computer-use.
- **Capture:** `tools/uitest/Capture-DesktopAnim.ps1 -DurationMs 1500` of the left-page stroke; filmstrip shows the right-page frame staying fixed while left ink is drawn.
- **Expected:** Writing on the left half draws ink on the left page; it does NOT grab or move the loupe target frame that sits on the right half. The frame's on-screen hit-rect aligns with the right page.
- **Pass/Fail:** PASS if a left-page stroke draws without disturbing the right-page loupe frame in SPREAD. FAIL if the left-half stroke moves/grabs the right-page frame.
- **Severity if broken:** 🔴

### Marker / highlighter compositing

#### AV-MARKER-01 — Маркер мультиплай-композитится с PDF: текст читается сквозь подсветку, чернила пера сверху (marker-multiply-composites-against-pdf-text-on-top)
- **Area:** MARKER · **Platform(s):** desktop
- **Fixture/precondition:** a PDF page with dark body text
- **Source:** 35a7f31 · regression guard for rcId RC-MARKER-001
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`; open a PDF page that contains dark body text (drive the JBR `java.exe` dev window via computer-use).
  2. Select the marker/highlighter tool from the tool rail and pick a bright semi-transparent color (e.g. yellow).
  3. Drag a marker stroke straight across a line of text so the highlight band fully covers several words.
  4. Then select the pen tool and draw a pen stroke crossing the same highlighted band.
  5. Capture a static screenshot of the highlighted region and the pen-over-highlight overlap.
- **Capture:** two static screenshots via computer-use: `01-marker-over-text.png` (marker band over text framing the glyphs) and `02-pen-over-highlight.png` (pen-stroke-over-highlight overlap region cropped tight).
- **Expected:** The dark text remains fully legible THROUGH the yellow highlight (text on top, multiply darkens only the white background); the highlight does not opaquely cover the glyphs. The pen ink renders ON TOP of the marker band, not hidden behind it.
- **Pass/Fail:** PASS if text glyphs are still readable under the highlight and pen ink sits visually above the highlight band. FAIL if the highlight obscures/covers the text (flat yellow over glyphs) or pen ink disappears under the marker.
- **Severity if broken:** 🟠

#### AV-MARKER-02 — Live-маркер в low-latency overlay рисуется chisel-лентой с multiply, а не круглым пером (marker-live-stroke-lowlatency-overlay-chisel-multiply)
- **Area:** MARKER · **Platform(s):** android-emulator, android-phone, android-tablet
- **Fixture/precondition:** a PDF page with dark text, API 29+ (low-latency overlay requires Q)
- **Source:** 73d98bf · regression guard for rcId RC-MARKER-002
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial> -Build` on an emulator/device with API 29+.
  2. Open a PDF page with dark text and select the marker tool with a bright semi-transparent color.
  3. Draw a marker stroke across a text line and HOLD mid-stroke (do not lift) to observe the live front-buffer render before finishDrawing.
  4. Record the live drag as a frame burst via `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>` into a GIF + filmstrip.
  5. Lift the pen and capture the committed stroke for comparison.
- **Capture:** Capture-AndroidAnim.ps1 ordered frame burst of the in-progress marker drag → GIF + filmstrip; plus one static frame of the committed stroke for side-by-side.
- **Expected:** While the stroke is in progress (low-latency overlay active), the marker renders as a chisel-edge ribbon (wide across the nib, thin along it) and multiplies so text stays readable — identical look to the committed stroke. No opaque round-nib pen line, no width pulsing from pressure/tilt.
- **Pass/Fail:** PASS if the live marker tail is a chisel ribbon with multiply (text readable, constant nib breadth) matching the committed stroke. FAIL if the live tail is an opaque round-cap line, pressure-varying width, or covers the text.
- **Severity if broken:** 🟠

#### AV-MARKER-03 — Live-маркер в лупе рисуется полной chisel-лентой каждый кадр (инкрементальный bake пропущен) (marker-live-stroke-magnifier-full-ribbon)
- **Area:** MARKER · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** a PDF with text, magnifier enabled
- **Source:** 73d98bf · regression guard for rcId RC-MARKER-003
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a PDF with text (drive the `java.exe` dev window via computer-use).
  2. Enable the magnifier/loupe and select the marker tool with a bright semi-transparent color.
  3. Inside the loupe input panel, draw a long marker stroke (long enough to exceed MAGNIFIER_LIVE_TIP_SEGMENTS) across text and hold mid-stroke.
  4. Record the in-loupe drag as a frame burst via `tools/uitest/Capture-DesktopAnim.ps1` → GIF + filmstrip.
  5. Inspect the magnified panel during the drag.
- **Capture:** Capture-DesktopAnim.ps1 frame burst of the marker drag inside the loupe panel → GIF + filmstrip, framed on the magnifier panel.
- **Expected:** Inside the magnifier, the in-progress marker appears as one continuous chisel ribbon with multiply blend for its full length every frame — no round-pen nib, no torn/ragged ribbon, no gap or double-darkened seam between an older baked portion and the live tip.
- **Pass/Fail:** PASS if the live marker in the loupe is a single continuous chisel multiply ribbon with text readable. FAIL if it renders as round-pen segments, shows a ragged/discontinuous ribbon, or a darker compounded seam where partial-range baking would have split it.
- **Severity if broken:** 🟡

### PDF gesture / live-stroke (mined regressions)

#### AV-PDF-03 — Android pinch: точка под пальцами остаётся на месте (atomic pinchUpdate, без edge-clamp pan) (android-pinch-anchor-stable-no-clamp)
- **Area:** PDF · **Platform(s):** android-tablet, android-emulator
- **Fixture/precondition:** multi-page PDF, zoomed so the page is wider than the viewport
- **Source:** 0148630 · regression guard for rcId RC-PDF-002
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>`, open a multi-page PDF, zoom so the page is wider than the viewport.
  2. Place two fingers on a distinctive visual landmark inside the page (e.g. a text-block corner); start a frame burst via `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>`.
  3. Pinch in/out over several ticks, keeping the centroid over the same landmark.
  4. Compare the landmark's position relative to the finger centroid in the start and end frames.
- **Capture:** Capture-AndroidAnim.ps1 filmstrip of the pinch gesture on the tablet; pinch-start frame and pinch-end frame, plus a GIF of the whole animation — the landmark must stay strictly under the centroid with no jerks toward the edge.
- **Expected:** The document point under the finger centroid stays visually fixed on every tick; the page does not snap to the viewport edge on re-render.
- **Pass/Fail:** PASS if the landmark stays under the centroid across all frames (<~4px drift). FAIL if the page jumps toward the viewport edge on a pinch tick or on re-render.
- **Severity if broken:** 🟠

#### AV-PDF-04 — Android высокий зум: нет OOM 'createGraphicBuffer failed' (gate low-latency overlay, MAX_RENDER_DIM 2400) (android-highzoom-no-oom)
- **Area:** PDF · **Platform(s):** android-tablet, android-emulator
- **Fixture/precondition:** any PDF, pinch-zoomable to > 2400px
- **Source:** 0148630 · regression guard for rcId RC-PDF-003
- **Steps:**
  1. `adb -s <serial> logcat -c`, then `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>`, open a PDF.
  2. Pinch-zoom to maximum (page > 2400px by measure); start `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>` while drawing with the stylus on the page at high zoom.
  3. In parallel, collect logcat and verify there is no 'createGraphicBuffer failed' / OutOfMemory.
  4. Draw a live stroke at max zoom — it must appear (via Compose Canvas), and the app must not crash.
- **Capture:** Capture-AndroidAnim.ps1 filmstrip of drawing a stroke at max zoom; save the logcat tail alongside as an artifact.
- **Expected:** At zoom above 2400px the live-stroke renders via Compose Canvas with no low-latency overlay; logcat shows no 'createGraphicBuffer failed'/OOM; the app does not crash and the stroke is visible.
- **Pass/Fail:** PASS if no 'createGraphicBuffer failed'/OOM in logcat at max zoom and the live stroke renders. FAIL if overlay buffer allocation crashes or the app OOMs at high zoom.
- **Severity if broken:** 🔴

#### AV-PDF-05 — Live-stroke: рост давления к концу не утолщает уже нарисованную часть (per-segment ширина) (livestroke-per-segment-width)
- **Area:** PDF · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** any document open, pen tool
- **Source:** b851504 · regression guard for rcId RC-PDF-004
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` (or `Start-AndroidTarget.ps1 -Serial <serial>` with a stylus), open a PDF.
  2. Start a stroke with light pressure, gradually increasing pressure toward the end (emulate via tablet input / a real stylus on desktop).
  3. Capture a filmstrip of the whole gesture (`tools/uitest/Capture-DesktopAnim.ps1` or `Capture-AndroidAnim.ps1`), especially the mid-gesture before pen-up.
  4. Compare the start-of-stroke width in a mid-gesture frame and in the final (committed) frame.
- **Capture:** Capture-DesktopAnim.ps1 / Capture-AndroidAnim.ps1 ordered burst during the pressure-increasing stroke; mid-gesture frame (pen still on surface) + final frame — the stroke start is thin in both, width grows only toward the end.
- **Expected:** The stroke start stays thin throughout the gesture; width varies per segment by local pressure; the live render matches the final committed render.
- **Pass/Fail:** PASS if the stroke start does not thicken as pressure rises toward the end and live ≈ committed in width profile. FAIL if the whole stroke is repainted with a single (growing) width before pen-up.
- **Severity if broken:** 🟠

#### AV-PDF-06 — Pencil Mode: переключатель форсирует palm-rejection без отмены активного жеста (rememberUpdatedState, не ключ pointerInput) (pencil-mode-toggle-no-gesture-cancel)
- **Area:** PDF · **Platform(s):** android-tablet
- **Fixture/precondition:** any document open, stylus + finger
- **Source:** b851504 · regression guard for rcId RC-PDF-006
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>`; open a PDF, enable the 'Stylus mode' toggle in the toolbar.
  2. Start `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>`; drag a finger across the page — the parent pan/pinch should run, NO stroke is drawn.
  3. Drag with the stylus — a stroke is drawn.
  4. During an active stylus stroke wait for auto-on pencilMode (first stylus event): confirm the current gesture is not interrupted/lost.
- **Capture:** Capture-AndroidAnim.ps1 filmstrip with the toggle ON — finger pans (document moves), stylus draws; frames of an active continuous stroke with no break when the auto-on fires.
- **Expected:** With Pencil Mode the finger only pans/zooms and only the stylus draws; enabling/auto-on of the mode does not cancel an active gesture.
- **Pass/Fail:** PASS if finger never draws under pencil mode (only pan/zoom) and an active stroke is not interrupted on toggle. FAIL if finger draws, or the active gesture is cancelled on flag change.
- **Severity if broken:** 🟠

### Reader chrome / airbar / tooling

#### AV-READER-01 — В режиме чтения зум-кластер (zoom in/label/out) скрыт из колеса настроек (reading-mode-hides-zoom-cluster)
- **Area:** READER · **Platform(s):** desktop
- **Fixture/precondition:** text-based PDF or converted FB2 with extractable text
- **Source:** 064fc87 · regression guard for rcId RC-READER-001
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`; open a text-based PDF/FB2.
  2. Drive via computer-use: open the settings/system wheel in editor mode, screenshot it (zoom-in / zoom-label / zoom-out present).
  3. Enable reading mode; reopen the settings wheel and screenshot it.
  4. Compare: in reading mode the sys_zoom_in / sys_zoom_label / sys_zoom_out entries are absent.
- **Capture:** `01-editor-wheel.png` (zoom cluster visible); `02-reading-wheel.png` (zoom cluster absent). Stills only, no animation.
- **Expected:** Editor mode shows the zoom +/label/- cluster; reading mode shows the wheel with the zoom cluster removed.
- **Pass/Fail:** PASS if zoom cluster is absent in reading mode and present in editor mode. FAIL if any of the three zoom entries renders while reading mode is on.
- **Severity if broken:** 🟡

#### AV-READER-02 — Airbar ридера не растягивается на всю ширину окна на десктопе (cap по AIRBAR_MAX_WIDTH) (airbar-width-capped-desktop)
- **Area:** READER · **Platform(s):** desktop
- **Fixture/precondition:** text-based PDF in reading mode, maximized desktop window
- **Source:** 2dcef23 · regression guard for rcId RC-READER-002
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, maximize the window (wide), open a text doc and enter reading mode so the reader airbar (collapsed pill) is shown.
  2. computer-use `screenshot` of the bottom airbar.
  3. Measure the pill width against window width.
- **Capture:** `01-airbar-wide-window.png` — full-window screenshot showing the centered, width-capped pill. Still only.
- **Expected:** Collapsed airbar pill is constrained to ~AIRBAR_MAX_WIDTH (560.dp) and centered horizontally, not spanning the full window width.
- **Pass/Fail:** PASS if airbar width <= AIRBAR_MAX_WIDTH and centered on a wide window. FAIL if the airbar stretches edge-to-edge.
- **Severity if broken:** 🟠

#### AV-READER-03 — Zoom-label занимает в колесе свою настоящую ширину (SCALE_LABEL_WIDTH=40dp), без фантомного хвостового затухания/скролла (zoom-label-true-width-in-wheel)
- **Area:** READER · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** editor-mode PDF, narrow/portrait window with room for the wheel
- **Source:** 4607c5c · regression guard for rcId RC-READER-003
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` in a portrait-ish narrow window (or `Start-AndroidTarget.ps1 -Serial <serial>` portrait), open a non-reading-mode PDF so the system wheel includes the zoom cluster.
  2. Open the tool/settings wheel with ample room.
  3. Screenshot the wheel strip; verify no trailing fade taper and no forced scroll when content fits.
- **Capture:** `01-wheel-no-phantom-fade.png` — portrait wheel with the zoom cluster, no trailing taper. Still only.
- **Expected:** With ample room the wheel sits at full size with no phantom trailing fade and no scroll; the zoom label slot is sized to 40.dp.
- **Pass/Fail:** PASS if the strip shows no trailing taper/scroll when content fits the available width. FAIL if a phantom fade or small scroll appears despite ample room.
- **Severity if broken:** 🟡

#### AV-READER-04 — В режиме чтения скрыты перо/лупа/экспорт, маркер-выделитель и ластик остаются (reading-mode-tool-gating)
- **Area:** READER · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** text-based PDF in reading mode
- **Source:** 518ccbe · regression guard for rcId RC-READER-010
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a text doc, enter reading mode.
  2. Open the tool rail / settings wheel; screenshot.
  3. Verify pen, magnifier, PDF-export entries are absent and marker (highlighter) + eraser are present.
- **Capture:** `01-reading-tools.png` — tool rail in reading mode (no pen/magnifier/export; marker+eraser present). Still only.
- **Expected:** Reading mode tool set excludes pen/magnifier/export and includes marker+eraser; editor mode shows all.
- **Pass/Fail:** PASS if pen/magnifier/export are hidden and marker+eraser remain in reading mode. FAIL if pen/magnifier/export appear or marker/eraser disappear.
- **Severity if broken:** 🟠

#### AV-READER-05 — Хром ридера (рельса, airbar, счётчик страниц, кнопка назад, вкладки) перекрашивается под тему ридера; focus mode прячет всё вместе (reader-chrome-theming-and-focus-hide)
- **Area:** READER · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** text-based PDF, dark reader theme, reading mode
- **Source:** 518ccbe · regression guard for rcId RC-READER-011
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a text doc, pick a dark reader theme, enter reading mode.
  2. Screenshot the chrome (tool rail, airbar, page counter, back button, tabs) — verify all match the reader theme background/text colors.
  3. Tap the text to enter focus mode; screenshot — verify back button and page counter hide together with the rest of the chrome.
  4. Tap again; verify all chrome restores.
- **Capture:** `01-chrome-themed.png`; then focus-toggle filmstrip via `tools/uitest/Capture-DesktopAnim.ps1 -DurationMs 1200 -Fps 15` across the hide tap (anim.gif + filmstrip showing back button/counter fading out together).
- **Expected:** Chrome adopts reader-theme colors in reading mode; focus tap hides back button + page counter + chrome together; second tap restores them.
- **Pass/Fail:** PASS if chrome is theme-tinted and the back button/page counter hide/restore in lockstep with chrome. FAIL if chrome stays app-accent colored, or back button/page counter linger when chrome hides.
- **Severity if broken:** 🟠

#### AV-READER-06 — Индикатор выбранного инструмента в режиме чтения перекрашен под тему ридера (reader-selected-tool-indicator-tint)
- **Area:** READER · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** text-based PDF, distinctive reader theme, reading mode, a tool selected
- **Source:** 5f68c6d · regression guard for rcId RC-READER-012
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a text doc, pick a distinctive reader theme, enter reading mode.
  2. Select pen/marker/eraser tool so a selection indicator shows.
  3. Screenshot the tool rail (both portrait and landscape if feasible) — verify the selected-tool pill/indicator fill and glyph use the reader theme content color, not the app accent.
- **Capture:** `01-selected-tool-reader-tint.png` — tool rail with a selected tool, reader theme active. Still only.
- **Expected:** Selected-tool indicator fill (~0.22 alpha of reader content color) and glyph (reader content color) match the chosen reader theme.
- **Pass/Fail:** PASS if the indicator is tinted to the reader theme in reading mode. FAIL if it shows the app accent color.
- **Severity if broken:** 🟡

#### AV-READER-07 — Кнопка режима чтения скрыта (не задизейблена) для документов без извлекаемого текста; PNG не зависает на 'Готовим режим чтения…' (reading-mode-button-hidden-no-text)
- **Area:** READER · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** image-only PDF, a PNG, and a text-based PDF
- **Source:** 77c4de3 · regression guard for rcId RC-READER-018
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`; open an image-only PDF and a PNG.
  2. Open the settings wheel; screenshot — verify the reading-mode button is absent for both (not present-but-disabled).
  3. Confirm no 'Готовим режим чтения…' hang occurs.
  4. Open a text-based PDF; verify the reading-mode button appears once the probe confirms a text layer.
- **Capture:** `01-image-pdf-no-reading-btn.png`; `02-png-no-reading-btn.png`; `03-text-pdf-reading-btn.png`. Stills only.
- **Expected:** Reading-mode button hidden for image-only PDF and PNG; appears for text-based docs after probe; no entry hang.
- **Pass/Fail:** PASS if the button is absent for textless docs and present for text docs with no hang. FAIL if it shows (disabled) for textless docs or the PNG hangs on the prepare-reading spinner.
- **Severity if broken:** 🟠

#### AV-READER-08 — Кромочный fade колеса пресетов подавлен у достигнутого упора прокрутки (fadeStart/fadeEnd по canScroll*) (preset-wheel-edge-fade-at-stop)
- **Area:** READER · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** reader airbar with enough custom presets to overflow the wheel
- **Source:** 77c4de3 · regression guard for rcId RC-READER-019
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, enter reading mode, open the airbar so the preset wheel is overflowing (scrollable).
  2. Scroll the preset wheel fully to the start; screenshot — leading (first) chip is not faded; trailing edge fades.
  3. Scroll fully to the end; screenshot — trailing (last) chip is not faded; leading edge fades.
- **Capture:** `01-wheel-at-start.png`; `02-wheel-at-end.png` — stills at each scroll stop showing the unfaded boundary chip.
- **Expected:** The edge at its scroll stop renders unfaded; only the side with remaining scroll fades.
- **Pass/Fail:** PASS if the stopped edge's chip is fully readable and only the scrollable side fades. FAIL if the first/last chip is shadowed at its stop.
- **Severity if broken:** 🟡

#### AV-READER-09 — Focus mode: одиночная полоса вкладок прячется вместе с хромом, фокус сохраняется при возврате с другой панели (focus-mode-single-tab-strip-hide)
- **Area:** READER · **Platform(s):** desktop
- **Fixture/precondition:** single text doc, reading mode; second panel available for the focus-return check
- **Source:** 77c4de3 · regression guard for rcId RC-READER-020
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open one text doc (single tab), enter reading mode.
  2. Tap text to hide chrome; capture a filmstrip of the transition.
  3. Verify the single-tab strip slides away with the chrome and the reader extends to the top of the panel.
  4. Open a second panel/another panel, return focus to the reader panel; verify chrome does not flicker visible for a frame nor stick on a stale value.
- **Capture:** Transition filmstrip via `tools/uitest/Capture-DesktopAnim.ps1 -DurationMs 1200 -Fps 15` over the hide-chrome tap (anim.gif + filmstrip showing the tab strip sliding up and reserve collapsing).
- **Expected:** Single-tab strip hides with chrome and reserve collapses to 0; on returning focus the chrome state tracks synchronously with no flicker/stick.
- **Pass/Fail:** PASS if the single-tab strip hides/restores in lockstep and no flicker on focus return. FAIL if the strip lingers, the reserve gap stays, or chrome flickers/sticks.
- **Severity if broken:** 🟠

#### AV-READER-10 — Хардварные клавиши перелистывания работают после скрытия панели тапом по полотну (фокус возвращается) (page-turn-keys-after-panel-hide)
- **Area:** READER · **Platform(s):** desktop
- **Fixture/precondition:** multi-page text-based doc, reading mode
- **Source:** b558aa6 · regression guard for rcId RC-READER-021
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a multi-page text doc, enter reading mode.
  2. Note the current page; tap the canvas to hide the settings panel.
  3. Press PageDown (or ArrowDown / Space) via computer-use `key` events.
  4. Capture before/after screenshots of the page indicator / content.
- **Capture:** `01-before-keypress.png` and `02-after-pagedown.png` showing the page/progress changed after the key press following the hide tap.
- **Expected:** After hiding the panel by tap, hardware PageDown/ArrowDown/Space still advances the page.
- **Pass/Fail:** PASS if the page advances on key press after a hide-panel tap. FAIL if key presses are ignored (focus lost).
- **Severity if broken:** 🟠

#### AV-READER-11 — Постоянная ширина процентной метки ('100%') — колесо пресетов не дёргается при смене цифр прогресса (preset-wheel-steady-on-progress-digits)
- **Area:** READER · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** reading mode, PERCENT progress format, multi-page doc
- **Source:** c462369 · regression guard for rcId RC-READER-022
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, enter reading mode with PERCENT progress format, open the collapsed airbar.
  2. Scroll the document so progress moves through 9%→10% and 99%→100%.
  3. Capture a filmstrip of the airbar while progress digits change.
  4. Verify the preset wheel's left edge does not shift as the percent label gains digits.
- **Capture:** Filmstrip via `tools/uitest/Capture-DesktopAnim.ps1 -DurationMs 1500 -Fps 20` while scrolling through the digit-count change; diff the wheel left edge across frames.
- **Expected:** Percent label slot stays at the '100%' width; the preset wheel does not re-center/jitter as digit count changes.
- **Pass/Fail:** PASS if the wheel left edge is stable across 9→10 and 99→100. FAIL if the wheel shifts/jitters when the percent gains a digit.
- **Severity if broken:** 🟡

#### AV-READER-12 — Края колеса пресетов используют барабанный эффект (minAlpha), а не chevron-указатели; крайняя плашка остаётся читаемой (preset-wheel-drum-edge-fade)
- **Area:** READER · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** reading mode, presets overflowing the wheel
- **Source:** c462369 · regression guard for rcId RC-READER-023
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, enter reading mode with enough presets to overflow the wheel.
  2. Open the airbar; screenshot the preset wheel edges.
  3. Verify no chevron carets are drawn and the edge chips are partially dimmed (~0.5 alpha) but still legible, not fully faded or hard-clipped.
- **Capture:** `01-wheel-drum-edges.png` — preset wheel showing dimmed-but-readable edge chips and no chevrons. Still only.
- **Expected:** No chevrons; edge chips are dimmed via the drum minAlpha and remain readable; thin 14dp fade only smooths the cut.
- **Pass/Fail:** PASS if edge chips are legible at reduced alpha with no chevrons. FAIL if chevrons appear or edge chips vanish/clip.
- **Severity if broken:** 🟡

#### AV-READER-13 — Переключатель режима скролла (BOTH/VERTICAL/NONE) скрыт в режиме чтения (scroll-mode-toggle-hidden-reading)
- **Area:** READER · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** text-based PDF; editor and reading mode
- **Source:** d2e04d7 · regression guard for rcId RC-READER-024
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a text doc; in editor mode confirm the quick-actions scroll-mode toggle is present.
  2. Enter reading mode; screenshot the quick-actions airbar.
  3. Verify the scroll-mode toggle is absent.
- **Capture:** `01-editor-quickactions.png` (toggle present); `02-reading-quickactions.png` (toggle absent). Stills only.
- **Expected:** Scroll-mode toggle present in editor mode, absent in reading mode.
- **Pass/Fail:** PASS if the scroll-mode button is hidden in reading mode and shown in editor mode. FAIL if it appears in reading mode.
- **Severity if broken:** 🟡

#### AV-READER-14 — Портретный счётчик страниц резервирует ширину под макс. число цифр totalPages — тулбар не дёргается при 9→10/99→100 (portrait-page-counter-stable-width)
- **Area:** READER · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** portrait layout, document with >=100 pages
- **Source:** 8c1839b · regression guard for rcId RC-READER-025
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` in portrait (or `Start-AndroidTarget.ps1 -Serial <serial>` tablet portrait), open a doc with >=100 pages, reading or editor portrait top bar showing the page counter.
  2. Navigate page 9→10 and 99→100.
  3. Capture a filmstrip of the top bar across the digit changes; verify the tool wheel / counter position does not shift.
- **Capture:** Filmstrip via `tools/uitest/Capture-DesktopAnim.ps1` (or `Capture-AndroidAnim.ps1`) `-DurationMs 1200 -Fps 15` across a 99→100 page change; diff the wheel left edge.
- **Expected:** Page counter reserves max-digit width; the tool wheel does not jump when the page number gains a digit.
- **Pass/Fail:** PASS if the wheel/counter stays put across 9→10 and 99→100. FAIL if the toolbar shifts when a digit is added.
- **Severity if broken:** 🟡

#### AV-READER-15 — Портретное колесо инструментов прижато к правому краю и не дёргает крайние иконки при скролле PDF (portrait-wheel-pinned-right-stable)
- **Area:** READER · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** portrait layout, scrollable PDF
- **Source:** 8caf0ad · regression guard for rcId RC-READER-026
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` in portrait, open a doc, show the portrait reader/editor top bar with the tool wheel.
  2. Scroll the PDF continuously (drag/scroll) while capturing a filmstrip of the tool wheel.
  3. Verify the wheel is right-aligned and the edge icons do not wobble/jitter during scroll.
- **Capture:** Filmstrip via `tools/uitest/Capture-DesktopAnim.ps1` (or Android) `-DurationMs 1500 -Fps 20` during continuous PDF scroll; diff the right-edge icon position across frames.
- **Expected:** Tool wheel is pinned to the right edge with stable width; edge icons stay steady during PDF scroll.
- **Pass/Fail:** PASS if the wheel stays right-pinned and edge icons are steady across scroll frames. FAIL if the wheel re-centers or edge icons wobble.
- **Severity if broken:** 🟡

#### AV-READER-16 — Крайние плашки airbar не дрожат при хвостовом переполнении (затухание считается от натуральной геометрии) (airbar-edge-chips-no-jitter)
- **Area:** READER · **Platform(s):** desktop
- **Fixture/precondition:** reading mode, preset wheel overflowing (trailing items hidden)
- **Source:** 9bffd19 · regression guard for rcId RC-READER-027
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, enter reading mode with the preset wheel overflowing (trailing chips off-edge).
  2. Move the mouse continuously over the reader text (forces per-frame relayout) without scrolling.
  3. Capture a filmstrip of the trailing edge chips.
  4. Verify the trailing chips hold a steady size/alpha (no oscillation) across frames; leading edge also steady.
- **Capture:** Filmstrip via `tools/uitest/Capture-DesktopAnim.ps1 -DurationMs 2000 -Fps 24` while moving the mouse over the reader; diff the trailing chip size/alpha across frames (must be flat).
- **Expected:** Trailing (and leading) edge chips stay steady; no per-frame jitter/oscillation while the mouse moves over the content.
- **Pass/Fail:** PASS if edge chips are visually static across the filmstrip during mouse movement. FAIL if trailing chips pulse/jitter in size or alpha.
- **Severity if broken:** 🟠

#### AV-READER-17 — Нет зазора между меткой прогресса и колесом пресетов (гаттер убран, плашки слева, процент справа в слоте) (airbar-close-progress-wheel-gap)
- **Area:** READER · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** reading mode, PERCENT progress format, presets present
- **Source:** a208704 · regression guard for rcId RC-READER-028
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, enter reading mode with PERCENT progress (short value like '0%'), open the airbar.
  2. Screenshot the airbar; measure the gap between the progress percent label and the first preset chip.
  3. Verify the chips sit flush right after the progress label with no large empty inset.
- **Capture:** `01-airbar-flush-progress.png` — airbar with a short percent ('0%') showing chips flush against the label. Still only.
- **Expected:** Progress text is right-aligned within its reserved slot and the preset chips start flush against it; no large gutter gap.
- **Pass/Fail:** PASS if the percent label sits flush against the wheel chips. FAIL if an empty gutter gap remains between them.
- **Severity if broken:** 🟡

### Reflow (mined regressions)

#### AV-REFLOW-04 — TableView красит фон по row.isHeader (а не rowIndex==0): пост-merge header при N>0 затеняется, ISBN-полоса в row0 — нет (tableview-shades-by-isheader)
- **Area:** REFLOW · **Platform(s):** desktop
- **Fixture/precondition:** a reflow book whose first rendered table has a non-header first row and a post-merge header at row index > 0
- **Source:** 0f4579d · regression guard for rcId RC-REFLOW-010
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` and grant `java.exe` to computer-use.
  2. Open a reflow book whose first rendered table has a non-header first row (e.g. ISBN strip) and a header row that, after cross-page merge, sits at row index > 0.
  3. `screenshot` the rendered TableView region.
  4. Verify the shaded (TABLE_HEADER_ALPHA) background lands on the semantic header row, NOT on row 0 when row 0 is not a header.
- **Capture:** stills `01-table-header-shading.png` of the TableView region on desktop (static, no animation needed).
- **Expected:** Header shading follows row.isHeader: the merged header row (index>0) is shaded; a non-header row 0 (ISBN strip) is transparent.
- **Pass/Fail:** PASS if the shaded cell background coincides with the isHeader row; FAIL if row 0 is shaded regardless or a header at index>0 is left unshaded.
- **Severity if broken:** 🟡

#### AV-REFLOW-05 — Reading-позиция сохраняется при смене ориентации (тот же ПАССАЖ, а не тот же индекс страницы) (reading-position-survives-rotation)
- **Area:** REFLOW · **Platform(s):** android-tablet, android-phone
- **Fixture/precondition:** a book in paged reflow reading mode
- **Source:** aca2211 · regression guard for rcId RC-REFLOW-011
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>` and open a book in paged reflow reading mode.
  2. Note the on-screen passage and page number in portrait.
  3. Rotate the device to landscape (`adb -s <serial> shell settings put system user_rotation 1` or orientation change).
  4. Capture a filmstrip across the rotation via `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>`.
  5. Verify the same passage remains on screen with the page number adapting (e.g. portrait 7 → landscape 8), not a different passage at the same index.
- **Capture:** `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial> -DurationMs 2500` spanning the rotation → anim.gif + filmstrip; stills `01-portrait-passage.png`, `02-landscape-passage.png`.
- **Expected:** The anchored content/passage stays on screen across portrait↔landscape; only the page number adapts to the new pagination.
- **Pass/Fail:** PASS if the same passage is visible after rotation (page number may change); FAIL if rotation lands on a divergent passage at the fixed old page index.
- **Severity if broken:** 🟠

### Render fidelity / spread split

#### AV-RENDER-01 — Разделение разворота (#4) не роняет окно редактора при переключении (stale-closure IndexOutOfBounds) (spread-split toggle does not crash editor (stale render closure))
- **Area:** RENDER · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** a multi-page PDF
- **Source:** 39376da · regression guard for rcId RC-RENDER-004
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` (drive the `java.exe` JBR window via computer-use). Open a multi-page PDF in the editor.
  2. Toggle the page-split (#4) control on, then off, then on again rapidly to provoke a transient logical/source index desync.
  3. Confirm the editor window stays open and pages keep rendering after each toggle.
- **Capture:** Capture-DesktopAnim.ps1 frame burst across each split toggle; before/after screenshots of the editor window confirming it stays open and pages render.
- **Expected:** Editor window survives repeated split toggling; no window destruction / blank crash; pages re-render correctly.
- **Pass/Fail:** PASS if the window remains open and shows rendered pages through several rapid toggles. FAIL if the editor window closes/crashes or pages go permanently blank.
- **Severity if broken:** 🔴

#### AV-RENDER-02 — Половинки повёрнутых страниц (/Rotate 90/270) не выходят широкими короткими полосами (split halves of rotated pages keep correct aspect)
- **Area:** RENDER · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** a PDF whose pages carry /Rotate 90 (or 270)
- **Source:** 39376da · regression guard for rcId RC-RENDER-005
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` (or `Start-AndroidTarget.ps1 -Serial <serial>`). Open a PDF whose pages carry /Rotate 90 (or 270).
  2. Enable page-split (#4).
  3. Inspect each rendered half-page on screen.
- **Capture:** Screenshot `01-rotated-split.png` of the split view of a /Rotate 90 PDF; compare half-page bounding boxes (expect ~half-width, full-height) vs the wide-strip regression.
- **Expected:** Each half is a tall half-width portrait slice with the correct aspect; no wide short strips and no degenerate empty pages between halves.
- **Pass/Fail:** PASS if rotated halves render at half visual width with correct aspect. FAIL if halves appear as wide short strips or extra blank pages appear.
- **Severity if broken:** 🟠

#### AV-RENDER-03 — Завершённые штрихи не пропадают при прерывистом письме (async ребилд ink-кэша) (finished strokes stay visible during async ink-cache rebuild)
- **Area:** RENDER · **Platform(s):** android-tablet, desktop
- **Fixture/precondition:** a heavy PDF page, pen tool
- **Source:** 446ded7 · regression guard for rcId RC-RENDER-006
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>` (or `Launch-Desktop.ps1`) and open a PDF in the editor with a pen tool.
  2. Write several short strokes in quick succession (discontinuous letter-by-letter) on a heavy page so the off-main cache lags.
  3. Record the motion as an ordered frame burst → GIF + filmstrip via `Capture-AndroidAnim.ps1` / `Capture-DesktopAnim.ps1`.
- **Capture:** Capture-AndroidAnim.ps1 frame burst over the multi-stroke write; filmstrip must show every finished stroke persisting frame-to-frame.
- **Expected:** All previously finished letters/strokes remain continuously visible while the next stroke is being drawn; nothing flickers out during the async rebuild.
- **Pass/Fail:** PASS if no finished stroke disappears between pen-downs in the filmstrip. FAIL if any earlier letter vanishes while drawing the next, reappearing only after a pause.
- **Severity if broken:** 🟠

#### AV-RENDER-04 — PDF-текст резкий при открытии (суперсэмплинг 2x + FilterQuality.High) (crisp PDF text at default open zoom (2x supersample))
- **Area:** RENDER · **Platform(s):** desktop, android-emulator
- **Fixture/precondition:** a text-heavy PDF
- **Source:** 4ea2a97 · regression guard for rcId RC-RENDER-007
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` (density 1.0). Open a text-heavy PDF at the default open zoom (no manual zoom).
  2. Screenshot a paragraph of body text at 1:1.
  3. Repeat on the Android emulator viewer (`Start-AndroidTarget.ps1 -Serial <serial>`).
- **Capture:** High-res screenshot `01-text-paragraph.png` of a text paragraph at default open zoom on desktop and Android; compare glyph edge sharpness against a known-soft baseline.
- **Expected:** Vector text edges are crisp/anti-aliased at the default open zoom, not soft/blurry.
- **Pass/Fail:** PASS if glyph edges are sharp at open zoom. FAIL if text looks soft/mushy (1:1 raster + Low filter regression).
- **Severity if broken:** 🟡

#### AV-RENDER-05 — requestRecenter сохраняет зум и применяет точный множитель компенсации (0.5/2/1) (requestRecenter preserves zoom with compensation factor)
- **Area:** RENDER · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** any PDF at a readable 100% zoom
- **Source:** 5b019c4 · regression guard for rcId RC-RENDER-008
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`. Open a PDF and set zoom to a readable 100%.
  2. Toggle page-split #4 ON: confirm the half-page shows at ~50% (same visual reading scale, centred), not blown up to fill the viewport.
  3. Toggle #4 OFF: confirm it round-trips back to 100% full page.
  4. Toggle book-spread #5 at 100%: confirm the pair stays at 100% centred (no fit-to-width rescale).
- **Capture:** Screenshots before/after each toggle measuring page width in px vs viewport; filmstrip of the split-on/off round-trip via Capture-DesktopAnim.ps1.
- **Expected:** Mode switches preserve the reading scale: split halves the visual size (×0.5 / ×2 round-trip), spread keeps it (×1); content never snaps to fill-the-screen.
- **Pass/Fail:** PASS if the on-screen content scale matches 100%→50%→100% on split round-trip and stays 100% on spread. FAIL if any toggle rescales the page to fit the viewport width.
- **Severity if broken:** 🟠

#### AV-RENDER-06 — Лупа: растеризация завершённого штриха off-main без per-pen-up фриза (magnifier ink raster off-main, no pen-up freeze)
- **Area:** RENDER · **Platform(s):** android-tablet, desktop
- **Fixture/precondition:** a stroke-heavy PDF, magnifier/loupe enabled
- **Source:** a894f82 · regression guard for rcId RC-RENDER-011
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>` (or `Launch-Desktop.ps1`), open a stroke-heavy PDF, enable the magnifier/loupe.
  2. Inside the loupe, draw and lift the pen repeatedly (many pen-up events).
  3. Capture the loupe region as a frame burst → filmstrip via `Capture-AndroidAnim.ps1` / `Capture-DesktopAnim.ps1`.
- **Capture:** Capture-AndroidAnim.ps1 frame burst of the loupe across several pen-up events; filmstrip must show smooth frame cadence and persistent ink.
- **Expected:** The loupe stays responsive on each pen-up (no frame-hold/freeze) and the finished stroke remains visible (tail draw covers the async rebuild).
- **Pass/Fail:** PASS if the loupe shows no per-pen-up freeze and no stroke flicker in the filmstrip. FAIL if the loupe hitches on pen-up or the just-finished stroke blinks out.
- **Severity if broken:** 🟠

### Session restore / sessions UI

#### AV-SESSION-01 — «Восстановить последнюю» возвращает рабочий стол ДО открытия редактора, а не только что переоткрытый (no-op) (restore-last recovers pre-session snapshot, not the live one)
- **Area:** SESSION · **Platform(s):** desktop
- **Fixture/precondition:** Any multi-page PDF; needs a real editor session so the debounced autosave runs.
- **Source:** 0e91c39 · regression guard for rcId RC-SESSION-001
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` (drive the JBR `java.exe` dev window via computer-use).
  2. Open a PDF, arrange a recognizable workspace A (specific tab + scroll/zoom), let the editor sit >1s so its autosave persists state A to `_autosave.json`.
  3. Close the editor and reopen the SAME workspace, then immediately make a clearly different change B (scroll far / switch tab / zoom) and wait >1s so the live autosave begins overwriting `_autosave.json` with B.
  4. Open the «Сессии» dropdown from the tab strip, click «Восстановить последнюю».
  5. Capture before/after screenshots of the editor viewport.
- **Capture:** Capture-DesktopAnim.ps1 burst around the restore-last click; stills of (1) workspace B before click and (2) restored workspace after click, framing the tab strip + page area.
- **Expected:** Restore-last restores the PRE-session snapshot (state A as it was at editor mount), not the current live workspace B; the button performs a visible restore, never a no-op.
- **Pass/Fail:** PASS if clicking restore-last visibly reverts to state A (the workspace as captured at mount). FAIL if it restores current state B / does nothing (no-op).
- **Severity if broken:** 🔴

#### AV-SESSION-02 — Сессии открываются как dropdown, привязанный к кнопке, а не как модальное окно (без лага и отдельного нативного окна) (sessions UI opens as anchored dropdown, not modal dialog)
- **Area:** SESSION · **Platform(s):** desktop
- **Fixture/precondition:** Any PDF open in the editor.
- **Source:** e05f6fa · regression guard for rcId RC-SESSION-002
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`; open a PDF in the editor.
  2. Click the «Сессии» button on the left edge of the tab strip.
  3. Observe that the menu appears inline anchored to the button (no second OS window, no taskbar entry, no resize/focus flash).
  4. Open the landscape tool-rail/wheel and confirm there is no longer a sessions entry there.
  5. Capture an open-animation filmstrip.
- **Capture:** Capture-DesktopAnim.ps1 from button press to fully-open menu; still of tool-rail contents proving the sessions entry is gone.
- **Expected:** A DropdownMenu anchored to the «Сессии» button opens promptly with no separate native window; the tool-rail no longer carries a sessions entry.
- **Pass/Fail:** PASS if sessions opens as an anchored in-window dropdown and the tool-wheel sessions entry is absent. FAIL if a separate modal/native window opens, or the tool-rail still shows a sessions entry.
- **Severity if broken:** 🟠

#### AV-SESSION-03 — Сессии-dropdown открывается мгновенно: поле имени показывается лениво, не композится на каждом открытии (sessions dropdown opens fast (lazy save field))
- **Area:** SESSION · **Platform(s):** desktop
- **Fixture/precondition:** Any PDF open in the editor.
- **Source:** 401850e · regression guard for rcId RC-SESSION-003
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`; open a PDF.
  2. Open the «Сессии» dropdown and confirm the default content shows restore-last + a «Сохранить текущую…» button, with NO text field visible.
  3. Tap «Сохранить текущую…» → confirm the name OutlinedTextField + «Сохранить»/«Отмена» row now appear.
  4. Tap «Отмена» → field collapses back; reopen the menu several times and capture open-latency filmstrips.
- **Capture:** Capture-DesktopAnim.ps1 across two consecutive menu opens (frame burst → GIF + filmstrip) to compare open responsiveness; stills of collapsed vs. expanded save UI.
- **Expected:** Menu opens without the text field present (fast); the OutlinedTextField appears only after «Сохранить текущую…» and collapses on «Отмена».
- **Pass/Fail:** PASS if the name field is absent until «Сохранить текущую…» is tapped and the menu opens without perceptible lag. FAIL if the text field renders by default on every open / the menu stalls on open.
- **Severity if broken:** 🟡

#### AV-SESSION-04 — Кнопка «Сессии» на тайтл-баре срабатывает с первого нажатия (вся якорная Box — non-drag зона) (tab-strip sessions button fires on first press)
- **Area:** SESSION · **Platform(s):** desktop
- **Fixture/precondition:** Editor with tab strip in the custom title bar (desktop only — title-bar hit-test).
- **Source:** 401850e · regression guard for rcId RC-SESSION-004
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` (custom JBR title bar active); open a PDF so the tab strip sits in the title-bar area.
  2. Single-click the «Сессии» button once, without moving the pointer.
  3. Observe whether the dropdown opens on that single press.
  4. Capture a press→open filmstrip.
- **Capture:** Capture-DesktopAnim.ps1 from the single mouse-down on the button through dropdown appearance; verify no window-drag occurs.
- **Expected:** A single press on the «Сессии» button opens the dropdown immediately; the press does not start a window drag.
- **Pass/Fail:** PASS if the dropdown opens on the first stationary click. FAIL if the click is swallowed as a window-drag and the menu only opens after the pointer moves off the button.
- **Severity if broken:** 🟠

### Sidebar filter chips

#### AV-SIDEBAR-01 — Чип фильтра в боковой панели: иконка и подпись не перекрываются (длинный лейбл «Надписи») (filter-chip icon and label do not overlap)
- **Area:** SIDEBAR · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** any document open
- **Source:** 985ff88 · regression guard for rcId RC-SIDEBAR-001
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` and open any PDF document in the editor (Details screen).
  2. Open the page-thumbnails sidebar (left rail of page thumbnails).
  3. Click the filter-mode chip and cycle it to the ANNOTATED mode so its label reads «Надписи» (the longest label, alongside the Edit icon).
  4. Screenshot the chip region; then cycle through ALL («Все», FilterList icon) and FAVORITES («Избр.», Star icon) and screenshot each.
  5. Inspect each screenshot: confirm the leading icon and the text glyphs occupy separate horizontal bands with the icon to the LEFT of the first character, no glyph drawn on top of the icon.
- **Capture:** a single computer-use screenshot of the sidebar filter-chip per mode (`01-chip-all.png` / `02-chip-favorites.png` / `03-chip-annotated.png`), framed tightly on the chip so icon/label horizontal positions are readable (static layout, no animation).
- **Expected:** For every filter mode the icon sits at the left, a small gap follows, then the label text — icon and label never overlap; the longest label «Надписи» stays clear of the Edit icon and is ellipsized rather than drawn over the icon if space runs out.
- **Pass/Fail:** PASS if in all three modes the icon and label are horizontally separated with no glyph-over-icon overlap. FAIL if any label (especially «Надписи») is rendered on top of / overlapping the leading icon.
- **Severity if broken:** 🟡

### Startup / cold launch

#### AV-STARTUP-01 — Главное окно/экран появляется до завершения инициализации sync/network стека (холодный старт не блокируется) (window-paints-before-sync-wired)
- **Area:** STARTUP · **Platform(s):** desktop, android-emulator
- **Fixture/precondition:** any document open
- **Source:** f90b9e7 · regression guard for rcId RC-STARTUP-004
- **Steps:**
  1. Cold-launch desktop via `tools/uitest/Launch-Desktop.ps1`; immediately record a frame burst with `tools/uitest/Capture-DesktopAnim.ps1` from launch through first paint.
  2. Confirm the main library window/content is visible in early frames (before sync stack finishes).
  3. On android-emulator cold-launch via `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>` and capture with `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>`; confirm the activity paints the main screen before peer/sync UI populates, then the sync/online-peer indicator appears once wiring completes.
- **Capture:** Capture-DesktopAnim.ps1 (launch→first-paint filmstrip→GIF) on desktop; Capture-AndroidAnim.ps1 on emulator; frame the whole window/activity to show main content present while the online-peer/sync indicator is still empty in early frames.
- **Expected:** First frame of the main window paints quickly with no sync stack; the peer/online indicators populate slightly later once the background coroutine publishes HeavyDeps — no blank/frozen pre-paint hang.
- **Pass/Fail:** PASS if the main UI is visible before sync wiring completes and indicators fill in afterward; FAIL if the window/activity stays blank until the full sync stack is constructed (regressed to synchronous wiring).
- **Severity if broken:** 🟠

### LAN sync (mined regressions)

#### AV-SYNC-02 — Реконнект к исчезнувшему хосту падает быстро (connect-timeout), индикатор не виснет жёлтым (connect-timeout-fast-fail-reconnect)
- **Area:** SYNC · **Platform(s):** android-tablet, desktop
- **Fixture/precondition:** tablet (client) paired to a desktop host so the sync indicator shows connected
- **Source:** 8d07493 · regression guard for rcId RC-SYNC-006
- **Steps:**
  1. Pair the tablet (client) to the desktop host so the sync indicator shows connected.
  2. Kill the host process / take it off the network so the host vanishes.
  3. Observe the client sync indicator while it attempts reconnect: it must fail fast (~within connect timeout) and the indicator must clear/stop 'reconnecting' rather than hang yellow indefinitely.
  4. Capture the indicator state transition as an ordered frame burst via `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>` (tablet) / `Capture-DesktopAnim.ps1` (desktop client).
- **Capture:** Filmstrip of the sync indicator from connected → host-killed → reconnect-attempt → cleared, via Capture-AndroidAnim.ps1 / Capture-DesktopAnim.ps1; frame the sync status chip.
- **Expected:** On a vanished host, the reconnect fails fast within the bounded connect timeout and the sync indicator clears instead of hanging yellow.
- **Pass/Fail:** PASS if the indicator clears within a few seconds of the host vanishing; FAIL if it stays 'reconnecting' (yellow) indefinitely.
- **Severity if broken:** 🟠

#### AV-SYNC-03 — Штрих на планшете не теряется при немедленном открытии документа на ПК (проекция как источник истины) (tablet-stroke-visible-on-pc-no-race)
- **Area:** SYNC · **Platform(s):** android-tablet, desktop
- **Fixture/precondition:** tablet (client) paired to a desktop host on the same LAN
- **Source:** 8fd672b · regression guard for rcId RC-SYNC-011
- **Steps:**
  1. Pair the tablet (client) to the desktop host on the same LAN.
  2. Draw a distinctive stroke on a page of a synced document on the tablet (`adb -s <serial> shell input swipe ...`).
  3. Within ~1s, open the SAME document on the PC (host) and observe that the just-drawn stroke is rendered, with no lost/dropped stroke.
  4. Capture the tablet draw → PC open transition as ordered frames via `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>` (tablet) and `Capture-DesktopAnim.ps1` (PC), framing the page region with the new stroke.
- **Capture:** Two-screen filmstrip: tablet stroke drawn (Capture-AndroidAnim.ps1) then PC document opened showing the same stroke (Capture-DesktopAnim.ps1); frame the affected page.
- **Expected:** A stroke drawn on the tablet appears on the PC when the document is opened moments later, because the host reads from the live projection rather than a possibly-stale disk file.
- **Pass/Fail:** PASS if the tablet stroke is visible on the PC immediately after open; FAIL if the stroke is missing (lost to the disk-flush race).
- **Severity if broken:** 🔴

#### AV-SYNC-04 — Рисование на хосте не лагает: дисковый IO и растеризация чернил вне main-thread (host-drawing-no-lag-offmain-io)
- **Area:** SYNC · **Platform(s):** desktop
- **Fixture/precondition:** desktop host app with a PDF document open
- **Source:** 8fd672b · regression guard for rcId RC-SYNC-012
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` (host) and open a PDF document.
  2. Draw a continuous fast stroke across the page (and finish strokes repeatedly to trigger ink rasterization + autosave) via computer-use.
  3. Observe that drawing stays smooth with no visible frame drops or a flicker/gap where the just-finished stroke disappears before the cache rebuilds.
  4. Capture the stroke + finish transition as an ordered frame burst via `tools/uitest/Capture-DesktopAnim.ps1` and inspect the filmstrip for dropped frames / flicker at stroke-finish.
- **Capture:** Capture-DesktopAnim.ps1 filmstrip of a fast continuous stroke and a stroke-finish on a PDF page; inspect for dropped frames and flicker at the cache-rebuild boundary.
- **Expected:** Drawing remains smooth (no dropped frames) and the finished stroke stays visible (anti-flicker tail-draw) while the bitmap cache rebuilds off the main thread.
- **Pass/Fail:** PASS if the filmstrip shows continuous smooth ink with no flicker at stroke-finish; FAIL if frames drop during the stroke or the stroke flickers/disappears on finish.
- **Severity if broken:** 🟠

### Tabs / split panels

#### AV-TABS-01 — Кнопка «Сессии» рисуется только на панели у левого края окна, а не в каждой панели сплита (sessions-button-only-on-left-edge-panel)
- **Area:** TABS · **Platform(s):** desktop
- **Fixture/precondition:** any document open
- **Source:** 17c87cc · regression guard for rcId RC-TABS-001
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` and open a document in the editor.
  2. Long-press a tab and choose «Открыть в новой панели» to create a 2-panel horizontal split (or use the layout preset picker).
  3. Observe the tab strips of BOTH panels in the JBR custom title bar.
  4. Capture a screenshot of the full title-bar row showing both panel strips.
- **Capture:** single screenshot `01-split-title-bar.png` of the title-bar strip across both split panels via computer-use (static).
- **Expected:** Exactly one «Сессии» (sessions) button is visible, in the strip of the left-edge panel; the inner/right panel's strip has no «Сессии» button.
- **Pass/Fail:** PASS if only the left-edge panel shows the «Сессии» button. FAIL if the inner/right panel also renders its own «Сессии» button (two buttons in one window).
- **Severity if broken:** 🟡

#### AV-TABS-02 — Фантомный отступ под кнопки окна остаётся только у крайней панели сплита, внутренние без зазора (no-phantom-traffic-light-gap-in-inner-panels)
- **Area:** TABS · **Platform(s):** desktop
- **Fixture/precondition:** any document open
- **Source:** 1b295cf · regression guard for rcId RC-TABS-002
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` and open a document.
  2. Create a horizontal 2-panel (or 3-panel) split so an inner panel's tab strip does NOT touch either window edge.
  3. Inspect the left padding of each panel's tab strip: leftmost panel, inner panel(s), rightmost panel.
  4. Capture a screenshot of the full title-bar row showing the leading padding of each strip.
- **Capture:** static screenshot `01-inner-panel-padding.png` of the title-bar row comparing first-tab x-offset of edge vs inner panel strips.
- **Expected:** Only the left-edge strip reserves the leading OS window-control inset and only the right-edge strip the trailing inset; inner panels start their tabs flush with no reserved gap.
- **Pass/Fail:** PASS if inner panels have zero reserved window-control inset (no phantom gap) and only edge panels reserve insets. FAIL if any inner panel shows a leading/trailing gap where caption/traffic-light buttons would sit.
- **Severity if broken:** 🟠

#### AV-TABS-03 — Открытие файла через «+» добавляет новую вкладку поверх восстановленного сплита, а не затирает её (plus-open-layers-over-restored-workspace)
- **Area:** TABS · **Platform(s):** desktop
- **Fixture/precondition:** a document A open in the editor + a second file B to open
- **Source:** c27db4f · regression guard for rcId RC-TABS-003
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` and open a document A in the editor.
  2. From the editor, use «+» to go to the library and pick a second file B to open as a new tab (this sets pendingTabUri while the workspace-restore effect also runs).
  3. Wait for the editor to settle and inspect the tab strip of the focused panel.
  4. Capture a screenshot of the tab strip showing the count and labels of open tabs.
- **Capture:** static screenshot `01-tabs-after-plus.png` of the tab strip after the «+»→library→open flow, asserting two distinct tab chips.
- **Expected:** Both A and B are present as separate tabs in the focused panel; B is added on top of the restored layout, not replacing A.
- **Pass/Fail:** PASS if the panel shows two tabs (A and B). FAIL if opening B replaces A (only one tab remains) because restore clobbered the freshly-added tab.
- **Severity if broken:** 🔴

#### AV-TABS-04 — Тап по вкладке в строке заголовка переключает её, а не трактуется ОС как перетаскивание окна (tab-tap-switches-not-window-drag)
- **Area:** TABS · **Platform(s):** desktop
- **Fixture/precondition:** two documents open as two tabs in one panel
- **Source:** c27db4f · regression guard for rcId RC-TABS-004
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` and open two documents as two tabs in one panel.
  2. With tab A active, perform a clean single tap (no drag) on tab B's chip in the title-bar strip via computer-use `left_click`.
  3. Observe whether B becomes the active tab and its document is shown.
  4. Capture before/after screenshots of the tab strip + content area to confirm the switch.
- **Capture:** computer-use single-tap on the chip; before/after screenshots `01-before-tap.png` / `02-after-tap.png` of the active-chip highlight and content area (no drag motion).
- **Expected:** A clean tap on an inactive tab chip activates it and shows its document; the tap is not consumed as a window drag.
- **Pass/Fail:** PASS if tapping the inactive chip switches the active tab. FAIL if the tap is swallowed as a window-drag and the active tab does not change.
- **Severity if broken:** 🔴

### Tool rail (landscape)

#### AV-TOOLRAIL-01 — Кнопки undo/redo остаются видимы при переполнении списка инструментов в альбомной ориентации (landscape-undo-redo-pinned-on-overflow)
- **Area:** TOOLRAIL · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** Any document open in landscape with the tool rail showing more tool entries than fit the rail height (short-height window).
- **Source:** 62bea5e · regression guard for rcId RC-TOOLRAIL-001
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`. Open a document so the editor (Details) screen with the tool rail is shown.
  2. Rotate/resize the window to a SHORT landscape height (constrain vertical space) so the vertical tool rail's tool list (WheelStrip entries) overflows the available island height.
  3. Observe the landscape glass rail island: undo/redo (PinnedHistoryButtons) must be visible.
  4. Scroll the tool wheel up and down to its extremes and re-check undo/redo visibility.
  5. Capture via `tools/uitest/Capture-DesktopAnim.ps1` a frame burst of the rail at a short window height + a scroll motion of the wheel.
- **Capture:** Capture-DesktopAnim.ps1 frame burst of the landscape rail island at a constrained (short) window height; one still showing undo/redo at top, plus a scroll filmstrip of the WheelStrip showing undo/redo staying fixed at top throughout.
- **Expected:** The undo and redo buttons are pinned at the TOP of the landscape rail island and remain fully visible (not clipped off either end of the centred fillMaxHeight box) regardless of how many tools are in the list or how far the wheel is scrolled.
- **Pass/Fail:** PASS if undo/redo buttons are visible at the top of the rail and never clipped while the tool wheel overflows/scrolls. FAIL if undo/redo are pushed off-screen (clipped) when the tool list is long or the window is short.
- **Severity if broken:** 🟠

### UI chrome / glass / theming

#### AV-UI-01 — Стрелка доезда до конца ленты колеса пресетов больше не дёргается (wheel-scroll-arrow-settles-at-end)
- **Area:** UI · **Platform(s):** desktop
- **Fixture/precondition:** reader preset wheel (ReaderAirbar) overflowing
- **Source:** 4537b9d · regression guard for rcId RC-UI-002
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` and open a document; open the reader preset wheel (ReaderAirbar).
  2. Repeatedly press the wheel scroll arrow toward the last preset until it reaches the end.
  3. Record the moment of arrival at the final stop with `Capture-DesktopAnim.ps1 -DurationMs 2000 -Fps 30`.
- **Capture:** `tools/uitest/Capture-DesktopAnim.ps1 -DurationMs 2000 -Fps 30` framing the reader preset wheel right edge; frameNNN.png burst → anim.gif + filmstrip.
- **Expected:** The wheel decelerates and stops cleanly on the final preset; no oscillation/back-and-forth at the docking edge.
- **Pass/Fail:** PASS if the filmstrip shows a stable final frame with no item-size oscillation. FAIL if the last items pulse/shrink-grow or the arrow keeps re-triggering scroll.
- **Severity if broken:** 🟠

#### AV-UI-02 — Хром/фон редактора не мигает дефолтной темой ридера до асинхронной загрузки настроек (reader-theme-gated-on-load-no-flicker)
- **Area:** UI · **Platform(s):** desktop
- **Fixture/precondition:** a saved reader theme that differs strongly from the default (e.g. dark sepia)
- **Source:** 46599eb · regression guard for rcId RC-UI-003
- **Steps:**
  1. Configure a saved reader theme that differs strongly from the default (e.g. dark sepia).
  2. `tools/uitest/Launch-Desktop.ps1`, open a document, and enter reading mode.
  3. Capture from the first frame of reading mode with `Capture-DesktopAnim.ps1 -DurationMs 1500 -Fps 30` to catch the load transition.
- **Capture:** `Capture-DesktopAnim.ps1 -DurationMs 1500 -Fps 30` over the editor chrome + reading placeholder; filmstrip to diff frame-by-frame for the palette jump.
- **Expected:** Chrome/background/placeholder stay neutral (MaterialTheme) until settings load, then switch to the saved reader theme in a single frame; no intermediate default-reader-theme frame.
- **Pass/Fail:** PASS if no frame shows the default reader palette before the saved one appears. FAIL if a default-reader-theme frame flashes before the saved theme.
- **Severity if broken:** 🟠

#### AV-UI-03 — В режиме чтения корневой фон редактора красится фоном ридера — нет «полоски» дефолтной палитры у статус-бара/таб-строки (root-paints-reader-bg-no-strip)
- **Area:** UI · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** dark saved reader theme, reading mode
- **Source:** 46599eb · regression guard for rcId RC-UI-004
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a document, enter reading mode with a dark saved reader theme, hide the airbar with a tap.
  2. Screenshot the top of the editor where the status-bar inset and reserved tab-strip row sit.
  3. Inspect the strip region above the page content.
- **Capture:** still `01-reader-top-strip.png` on desktop (and android-tablet for status-bar inset); compare strip colour to reader bg.
- **Expected:** The status-bar inset and reserved tab-strip area are filled with the reader theme background, blending with the page; no contrasting default-palette strip.
- **Pass/Fail:** PASS if the top inset/tab-strip region matches the reader background. FAIL if a default colorScheme.background strip is visible above the content.
- **Severity if broken:** 🟠

#### AV-UI-04 — Стеклянные панели над меньшим источником не имеют односторонней асимметрии (прозрачно слева/плотно справа) (glass-no-cross-source-asymmetry)
- **Area:** UI · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** a document open so the viewer is the glassSource
- **Source:** 55967ff · regression guard for rcId RC-UI-005
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a document so the viewer is the glassSource; show the editor toolrail/airbar that sits in chrome (its bounds extend past the viewer source on one side).
  2. Screenshot the glass panel close-up.
  3. Compare the left rim vs the right rim of the panel.
- **Capture:** still `01-glass-rims.png` close-up of toolrail/airbar over the viewer source; desktop + android-tablet.
- **Expected:** Both rims of the glass panel show the same tint density; no one-sided transparent vs dense-tint split.
- **Pass/Fail:** PASS if left and right rim tint match. FAIL if one side is noticeably transparent while the other is densely tinted.
- **Severity if broken:** 🟠

#### AV-UI-05 — PDF-текст под airbar размывается до рефракции — нет узнаваемых глифов-призраков под ободком стекла (glass-prerefraction-blur-no-glyph-ghosts)
- **Area:** UI · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** a text-dense PDF, rounded glass airbar over a paragraph
- **Source:** 55967ff · regression guard for rcId RC-UI-006
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a text-dense PDF; position a rounded glass airbar over a paragraph of body text.
  2. Screenshot the airbar rim where the underlying text is densest.
  3. Inspect the rim for legible glyph shapes.
- **Capture:** still `01-airbar-over-text.png` close-up of the rim over dense PDF text; desktop + android-tablet (API>=33).
- **Expected:** Under the rim the backdrop reads as a soft smudge; no recognisable glyphs/words are surfaced by the refraction lens.
- **Pass/Fail:** PASS if no legible text appears within the refracted rim band. FAIL if individual letters/words are recognisable along the rim.
- **Severity if broken:** 🟡

#### AV-UI-06 — LiquidGlass верхний бар тинтует область за статус-баром, а не показывает скролл-контент насквозь (topbar-glass-extends-behind-statusbar)
- **Area:** UI · **Platform(s):** android-phone, android-tablet
- **Fixture/precondition:** a screen using LiquidGlassTopBar with scrollable content beneath
- **Source:** 6cce59b · regression guard for rcId RC-UI-007
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>` on a device/emulator with a visible status bar; open a screen using LiquidGlassTopBar with scrollable content beneath.
  2. Scroll content under the top bar (`adb -s <serial> shell input swipe ...`).
  3. `adb -s <serial> exec-out screencap -p > statusbar.png` of the status-bar region at the top.
- **Capture:** still `01-topbar-statusbar.png` while scrolling; android-phone + android-tablet (status bar present).
- **Expected:** The status-bar area shows the glass top-bar tint (continuous with the bar); the scrolling content does not bleed through behind the status bar.
- **Pass/Fail:** PASS if the status-bar strip matches the top-bar tint. FAIL if scroll content is visible behind the status bar / colours don't match the bar.
- **Severity if broken:** 🟠

#### AV-UI-07 — Превью миниатюр сайдбара не мигают при автоскролле (кэш bitmap поднят на уровень сайдбара) (sidebar-thumbnails-no-blink-on-autoscroll)
- **Area:** UI · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** a multi-page PDF with the pages sidebar shown
- **Source:** c24bc4a · regression guard for rcId RC-UI-008
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a multi-page PDF and show the pages sidebar.
  2. Scroll the main PDF so the active page changes and the sidebar auto-scrolls (bottom-edge alignment).
  3. Capture the sidebar with `Capture-DesktopAnim.ps1 -DurationMs 2500 -Fps 30` during the auto-scroll.
- **Capture:** `Capture-DesktopAnim.ps1 -DurationMs 2500 -Fps 30` over the sidebar; filmstrip to spot blank frames; also android-tablet.
- **Expected:** Thumbnails that were already rendered remain stable; no preview goes blank/re-renders when the sidebar window slides.
- **Pass/Fail:** PASS if no thumbnail blinks to blank during auto-scroll. FAIL if previously-rendered previews flicker/blank and re-appear.
- **Severity if broken:** 🟡

#### AV-UI-08 — Скролл основного PDF плавный — рендер миниатюр сериализован mutex и гейтится по pdfIdle/дебаунсу (sidebar-thumbnail-render-gated-smooth-scroll)
- **Area:** UI · **Platform(s):** android-tablet, android-emulator
- **Fixture/precondition:** a multi-page PDF with the pages sidebar visible
- **Source:** c24bc4a · regression guard for rcId RC-UI-009
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>` on a tablet, open a multi-page PDF with the pages sidebar visible.
  2. Continuously fling/scroll the main PDF for ~2s (`adb -s <serial> shell input swipe ...`).
  3. Capture with `Capture-AndroidAnim.ps1 -Serial <serial> -DurationMs 2500` and observe the main-viewer frame cadence.
- **Capture:** `Capture-AndroidAnim.ps1 -Serial <serial> -DurationMs 2500` framing the main viewer during a fling; filmstrip for cadence.
- **Expected:** Main PDF scroll stays smooth during the fling; sidebar auto-scroll/highlight settle only after the scroll pauses; no stutter from concurrent thumbnail renders.
- **Pass/Fail:** PASS if the main-scroll filmstrip shows even cadence and sidebar updates only post-pause. FAIL if main scroll stutters while thumbnails render mid-scroll.
- **Severity if broken:** 🟠

#### AV-UI-09 — Активная страница в сайдбаре выровнена по нижнему краю вьюпорта — следующая миниатюра не торчит огрызком (sidebar-active-page-bottom-aligned)
- **Area:** UI · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** a multi-page PDF, sidebar visible
- **Source:** c24bc4a · regression guard for rcId RC-UI-010
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a multi-page PDF, sidebar visible.
  2. Scroll the PDF forward so the active page would fall below the sidebar window, then pause to let auto-scroll settle.
  3. Screenshot the sidebar.
- **Capture:** still `01-sidebar-bottom-align.png` after auto-scroll settles; desktop + android-tablet.
- **Expected:** The active (highlighted) thumbnail sits flush at the bottom edge of the sidebar viewport; the next thumbnail is not partially peeking below it.
- **Pass/Fail:** PASS if the active thumbnail is bottom-aligned with no next-thumbnail stub. FAIL if a sliver of the following thumbnail shows beneath the active one.
- **Severity if broken:** 🟡

#### AV-UI-10 — Quick-actions airbar (лупа/режим скролла) и плейсхолдер загрузки следуют теме ридера (quickactions-airbar-placeholder-reader-theme)
- **Area:** UI · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** a dark saved reader theme, reading mode
- **Source:** ca64c87 · regression guard for rcId RC-UI-011
- **Steps:**
  1. Set a dark saved reader theme; `tools/uitest/Launch-Desktop.ps1`, open a document and enter reading mode (so reflow extraction runs and the placeholder shows briefly).
  2. Screenshot the bottom-right quick-actions airbar and the loading placeholder.
  3. Inspect airbar tint/icon colours and placeholder bg/text.
- **Capture:** stills `01-quickactions-airbar.png` and `02-loading-placeholder.png` in reading mode with a dark reader theme; desktop + android-tablet.
- **Expected:** The quick-actions airbar tint and icon colours, plus the loading placeholder bg/text, all match the active reader theme — not the default surface/onSurface.
- **Pass/Fail:** PASS if airbar + placeholder use reader-theme colours. FAIL if either shows default MaterialTheme surface/onSurface in reading mode.
- **Severity if broken:** 🟡

#### AV-UI-11 — RectangleShape верхний бар не пробивает размытым контентом список под ним (на Android<13) (rectangle-topbar-no-bleed-android12)
- **Area:** UI · **Platform(s):** android-emulator, android-phone
- **Fixture/precondition:** Android<13 device/emulator, main screen with LiquidGlassTopBar over recents list
- **Source:** cd359a2 · regression guard for rcId RC-UI-013
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>` on an Android<13 device/emulator; open the main screen with the LiquidGlassTopBar over the recents list ('Recent files' header just below the bar).
  2. `adb -s <serial> exec-out screencap -p > seam.png` of the seam between the bar and the list header.
  3. Inspect for blurred content extending past the bar's bottom edge.
- **Capture:** still `01-topbar-list-seam.png` on android-emulator API<33; inspect the bar/header seam.
- **Expected:** The top bar's glass is clipped to its reported bounds; no blurred halo bleeds into the list or overlaps the 'Recent files' header.
- **Pass/Fail:** PASS if the bar's bottom edge is crisp with no bleed. FAIL if blurred content extends below the bar over the header.
- **Severity if broken:** 🟠

#### AV-UI-12 — Скруглённые pill-airbar'ы на Android<13 не имеют квадратного гало вокруг формы (rounded-airbar-no-square-halo-android12)
- **Area:** UI · **Platform(s):** android-emulator, android-phone
- **Fixture/precondition:** Android<13 device/emulator, reading mode with a rounded pill airbar
- **Source:** cd359a2 · regression guard for rcId RC-UI-014
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>` on an Android<13 device/emulator; enter reading mode so a rounded pill airbar is shown over content.
  2. `adb -s <serial> exec-out screencap -p > pill.png` of the pill airbar.
  3. Inspect the corners/edges for a rectangular blurred halo extending past the pill.
- **Capture:** still `01-pill-airbar-halo.png` on android-emulator API<33 close-up of a rounded airbar.
- **Expected:** The blur is clipped to the rounded pill shape; no square/rectangular halo around it.
- **Pass/Fail:** PASS if blur is confined to the pill outline. FAIL if a square halo is visible around the rounded airbar.
- **Severity if broken:** 🟠

#### AV-UI-13 — Кнопка восстановления последней сессии — primary Button (терракот), читается как CTA меню сессий (restore-session-primary-cta)
- **Area:** UI · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** sessions menu with a restorable last session present
- **Source:** cf7f90a · regression guard for rcId RC-UI-015
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`; open the sessions menu (with a restorable last session present) over the warm-beige glass surface.
  2. Screenshot the menu including the restore-last-session button.
  3. Inspect the button's contrast against the menu surface.
- **Capture:** still `01-sessions-menu-cta.png` of the open sessions menu; desktop + android-tablet.
- **Expected:** The restore-last-session button renders as a filled primary (terracotta) button that stands out clearly as the menu's CTA.
- **Pass/Fail:** PASS if the button is a high-contrast primary button. FAIL if it blends into the tonal glass tint (low contrast FilledTonalButton look).
- **Severity if broken:** 🟡

#### AV-UI-14 — Текст стеклянной панели остаётся читаемым над фоном того же цвета (чёрный текст над чёрным PDF-текстом) (glass-text-legible-over-samecolor-backdrop)
- **Area:** UI · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** a text-dense PDF, glass panel with dark text over black body text (light theme)
- **Source:** dd0c483 · regression guard for rcId RC-UI-017
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a text-dense PDF; place a glass panel with dark text over a region of black body text (light theme).
  2. Screenshot the panel where its own text overlaps bled-through PDF text.
  3. Inspect panel text legibility against the backdrop.
- **Capture:** still `01-glass-text-over-text.png` close-up; desktop + android-tablet.
- **Expected:** The bled-through backdrop text is compressed to mid-grey haze; the panel's own dark text reads cleanly without colliding with same-coloured backdrop glyphs.
- **Pass/Fail:** PASS if panel text stays legible and backdrop text is a soft haze. FAIL if backdrop black text collides with the panel's dark text reducing legibility.
- **Severity if broken:** 🟠

### UIKit wheel / rail components

#### AV-UIKIT-01 — Тул-рейл/wheel на планшете в ландшафте не мерцает (footprint слотов не зависит от прокрутки) (tool-rail-no-flicker-landscape)
- **Area:** UIKIT · **Platform(s):** desktop, android-tablet, android-emulator
- **Fixture/precondition:** Editor screen with a tool-rail WheelStrip longer than the viewport on a large landscape surface
- **Source:** 3d18b89 · regression guard for rcId RC-UIKIT-001
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` (or `Start-AndroidTarget.ps1 -Serial <serial>` tablet landscape) to the PDF editor with a vertical tool-rail wheel on a large landscape screen where the list is longer than the viewport.
  2. Hover/leave the pointer over the rail without scrolling and record an ordered frame burst via `tools/uitest/Capture-DesktopAnim.ps1` (desktop) or `Capture-AndroidAnim.ps1` (tablet) for ~1.5s at rest.
  3. Lightly force a relayout (mouse movement over content / a light hover) and capture a second frame burst.
  4. Build GIF + filmstrip and compare adjacent frames for jitter of the rail's edge slots.
- **Capture:** Capture-DesktopAnim.ps1 / Capture-AndroidAnim.ps1 burst of the rail at rest and under hover → GIF + filmstrip.
- **Expected:** The rail's edge slots stand still; no per-frame jitter/flicker (tail-overflow and canScrollForward stable). The drum effect (scale+fade at the edges) is visually preserved.
- **Pass/Fail:** PASS if the filmstrip at rest and under hover shows no per-frame edge jitter and the drum fade is present. FAIL if the edge slots oscillate/flicker frame-to-frame.
- **Severity if broken:** 🟠

#### AV-UIKIT-02 — Стрелки колеса прокручивают ровно на один элемент, а не на страницу (70% вьюпорта) (chevron-steps-one-item)
- **Area:** UIKIT · **Platform(s):** desktop
- **Fixture/precondition:** Horizontal WheelStrip on desktop with chevron WheelScrollButtons and content wider than viewport
- **Source:** 7d11a78 · regression guard for rcId RC-UIKIT-003
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` to a screen with a horizontal WheelStrip where chevron buttons (desktop-only) are visible and the strip is wider than the viewport.
  2. Record a reference frame, then click the 'forward' chevron once and capture a burst of the settle animation via `tools/uitest/Capture-DesktopAnim.ps1`.
  3. Repeat the click 1-2 more times, capturing frames each time.
  4. From the filmstrip, count how many slots the central element shifted per press.
- **Capture:** Capture-DesktopAnim.ps1: frame burst per chevron press → GIF + filmstrip; frame the whole horizontal strip to count the slot shift.
- **Expected:** Each chevron press shifts the strip by exactly one item (center moves to the adjacent slot), not by ~70% of the visible strip.
- **Pass/Fail:** PASS if one press == a one-slot shift. FAIL if one press scrolls several items / nearly a page.
- **Severity if broken:** 🟡

#### AV-UIKIT-03 — После доводки стрелкой правый край ленты не дёргается (animateScrollBy дельтой, не animateScrollToItem) (chevron-settle-no-edge-jitter)
- **Area:** UIKIT · **Platform(s):** desktop
- **Fixture/precondition:** Horizontal WheelStrip on desktop scrolled to its trailing stop via chevron taps
- **Source:** 7d11a78 · regression guard for rcId RC-UIKIT-004
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` to a horizontal WheelStrip longer than the viewport.
  2. Repeatedly press the 'forward' chevron to the trailing stop, capturing the last settle animation frames via `tools/uitest/Capture-DesktopAnim.ps1`.
  3. After it stops, capture another frame burst of the strip at rest at the right stop.
  4. Compare frames at the right edge for jitter after settle.
- **Capture:** Capture-DesktopAnim.ps1: frame burst on the final settle and at rest at the right stop → GIF + filmstrip; frame the right edge of the strip.
- **Expected:** After arriving via the chevron the right edge is motionless; no post-motion jitter of the edge slot.
- **Pass/Fail:** PASS if the right edge is stable after settle (no per-frame jitter). FAIL if the edge slot/right rim jitters after stopping.
- **Severity if broken:** 🟡

### UX flows / pinned controls / loading

#### AV-UX-01 — Пинённые кнопки Undo/Redo всегда видны в тулбаре при активном инструменте (pinned-undo-redo-buttons-visible)
- **Area:** UX · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** any document open
- **Source:** 1623c81 · regression guard for rcId RC-UX-002
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` and open a PDF in the editor.
  2. Select the pen tool and draw one stroke on the page.
  3. Observe the tool rail (landscape) / top bar (portrait): the Undo button is present and enabled, Redo dimmed.
  4. Tap the pinned Undo button → the stroke disappears; tap pinned Redo → the stroke returns.
  5. With pen still active and its settings/presets expanded, confirm Undo/Redo remain visible (do not scroll off behind settings).
- **Capture:** Capture-DesktopAnim.ps1 framing the tool rail: frames at [tool selected], [stroke drawn — Undo enabled], [Undo tapped — stroke gone], [Redo tapped — stroke back]; GIF + filmstrip.
- **Expected:** Undo/Redo are always-visible pinned buttons adjacent to the wheel; enabled state mirrors canUndo/canRedo (dimmed when empty), and tapping them undoes/redoes the stroke composited over the PDF raster.
- **Pass/Fail:** PASS if both buttons stay on-screen with an active tool and drive undo/redo of the rendered stroke. FAIL if either button scrolls off behind tool settings, never enables after a stroke, or does nothing on tap.
- **Severity if broken:** 🟠

#### AV-UX-02 — Системные кнопки (чтение/миниатюры) остаются в колесе при активном инструменте (system-controls-always-appended)
- **Area:** UX · **Platform(s):** desktop, android-phone, android-tablet
- **Fixture/precondition:** any document open
- **Source:** 1623c81 · regression guard for rcId RC-UX-003
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a PDF in the editor.
  2. Note that reading-mode and thumbnails buttons are present in the wheel with no tool active.
  3. Select the marker tool (which brings up its settings + presets in the wheel).
  4. Scroll/inspect the wheel and confirm reading-mode and thumbnails buttons are still reachable (scrolled, not removed).
- **Capture:** Capture-DesktopAnim.ps1: frame [no tool — system buttons visible], frame [marker active — settings shown], frame [wheel scrolled to system group still present]; filmstrip.
- **Expected:** The system control group (reading mode, thumbnails, magnifier, zoom, export) stays in the wheel even while a drawing tool with settings/presets is active; overflow scrolls rather than disappears.
- **Pass/Fail:** PASS if reading-mode/thumbnails remain reachable with the marker active. FAIL if any system button vanishes until the tool is deselected.
- **Severity if broken:** 🟠

#### AV-UX-03 — Режим чтения резервирует место под плавающий хром — первая строка/заголовок не перекрыты (reflow-chrome-inset)
- **Area:** UX · **Platform(s):** desktop, android-tablet, android-phone
- **Fixture/precondition:** an EPUB/PDF in reading mode
- **Source:** 1623c81 · regression guard for rcId RC-UX-005
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open an EPUB/PDF and toggle reading mode.
  2. In landscape: confirm the first heading/line clears the page-chip top bar and the line's first characters clear the left tool rail.
  3. In portrait: confirm the first heading clears the full-width top bar.
  4. Toggle chrome visibility (tap) and confirm the reserve is STATIC (no re-pagination / text reflow jump on hide/show).
- **Capture:** Capture-DesktopAnim.ps1: landscape frame [reading mode top — H1 fully clear of chip + rail], portrait frame [H1 below top bar], chrome-toggle filmstrip showing no text reflow.
- **Expected:** Reader content is inset by a static top reserve (bar height) and start reserve (rail strip) so the first line/heading is fully visible and not occluded by chrome; hiding/showing chrome does not re-paginate.
- **Pass/Fail:** PASS if the first heading and first glyphs of lines are visible beside/below the chrome and no re-pagination occurs on chrome toggle. FAIL if the H1/first line slides under the bar, the rail clips first chars, or the page re-flows when chrome toggles.
- **Severity if broken:** 🟠

#### AV-UX-04 — Открытие документа: спиннер «Открываем книгу…» и подавление счётчика «1 / 0» (loading-spinner-suppress-counter)
- **Area:** UX · **Platform(s):** android-phone, desktop
- **Fixture/precondition:** a large EPUB so the book→PDF conversion is observable
- **Source:** 1623c81 · regression guard for rcId RC-UX-009
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>` (or `Launch-Desktop.ps1`) open a large EPUB so the book→PDF conversion is observable.
  2. During open: confirm a centered spinner with 'Открываем книгу…' is shown over the reader background instead of a blank page.
  3. Confirm the page counter does NOT show '1 / 0' while pages are unknown.
  4. After conversion completes: confirm the spinner disappears, the first page renders, and the counter shows the real total (e.g. '1 / N').
- **Capture:** Capture-AndroidAnim.ps1 over the EPUB open: frame [spinner + 'Открываем книгу…', no counter], frame [page rendered + '1 / N' counter]; GIF + filmstrip.
- **Expected:** While loading: spinner + label and no page counter; once the page count is known: counter appears with the real total and the page renders.
- **Pass/Fail:** PASS if the spinner shows during conversion and the counter is hidden until totalPages>0. FAIL if a blank page with '1 / 0' is shown while loading.
- **Severity if broken:** 🟡

#### AV-UX-05 — Эскизы recents для EPUB/FB2 рендерят обложку, а не битую картинку (ebook-thumbnail-cover)
- **Area:** UX · **Platform(s):** android-phone, android-tablet
- **Fixture/precondition:** an EPUB/FB2 opened once so it appears in recents
- **Source:** 1623c81 · regression guard for rcId RC-UX-010
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>`, open an EPUB/FB2 once so it appears in recents.
  2. Return to the library/recents grid.
  3. Confirm the EPUB/FB2 recent tile shows a rendered cover (page-0 raster), not the broken-image error placeholder.
- **Capture:** `adb -s <serial> exec-out screencap -p` of the recents grid showing the EPUB tile with a rendered cover (`01-ebook-recent-cover.png`); compare against the broken-image placeholder it replaced.
- **Expected:** EPUB/FB2 recents render a real cover thumbnail produced by converting the book to PDF and rasterising page 0.
- **Pass/Fail:** PASS if the ebook recent tile shows a rendered cover. FAIL if it shows the broken-image / ThumbnailState.Error placeholder.
- **Severity if broken:** 🟡

### Viewer (mined regressions)

#### AV-VIEWER-01 — Лупа на десктопе: ввод пера не уезжает от содержимого при ресайзе окна (magnifier-resize-drift-visual)
- **Area:** VIEWER · **Platform(s):** desktop
- **Fixture/precondition:** any document open, magnifier/loupe enabled
- **Source:** 10af41b · regression guard for rcId RC-VIEWER-002
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1` and open a PDF in the editor.
  2. Enable the magnifier/loupe; draw a short stroke and note it lands under the cursor.
  3. Resize the app window (change height notably), then draw again inside the loupe panel.
  4. Capture before/after with `tools/uitest/Capture-DesktopAnim.ps1` framing the loupe panel.
- **Capture:** Capture-DesktopAnim.ps1 filmstrip: frame 1 stroke pre-resize under cursor, frame 2 after window resize, frame 3 stroke post-resize still under cursor.
- **Expected:** After resize the freshly drawn ink stays exactly under the pen cursor inside the loupe; no vertical offset between cursor and ink.
- **Pass/Fail:** PASS if ink registers under the cursor after resize. FAIL if ink is shifted vertically relative to the cursor.
- **Severity if broken:** 🟠

#### AV-VIEWER-02 — Поворот страницы в редакторе: штрихи остаются поверх того же содержимого (page-rotation-ink-stays-on-content)
- **Area:** VIEWER · **Platform(s):** desktop, android-tablet
- **Fixture/precondition:** a PDF with a recognisable stroke over a specific word/figure
- **Source:** 10af41b · regression guard for rcId RC-VIEWER-004
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open a PDF, draw a recognisable stroke over a specific word/figure.
  2. Tap the rotate (↻) button to rotate the current page +90°; repeat to test cumulative 180/270/360.
  3. Capture each rotation step with `Capture-DesktopAnim.ps1` framing the page.
- **Capture:** Capture-DesktopAnim.ps1 4-frame filmstrip at 0/90/180/270 degrees, each framing the annotated word and overlaid stroke.
- **Expected:** After each +90° the drawn stroke remains over the same content it annotated; at 360° the page and ink return to the original orientation.
- **Pass/Fail:** PASS if ink tracks its content through every rotation. FAIL if the stroke drifts off the content or stroke width visibly distorts.
- **Severity if broken:** 🟠

#### AV-VIEWER-03 — Выделение текста в reflow на эмуляторе: тап выделяет именно ту строку, по которой тапнули (reflow-selection-correct-line)
- **Area:** VIEWER · **Platform(s):** android-emulator
- **Fixture/precondition:** a text-based PDF in reflow reading mode
- **Source:** 10af41b · regression guard for rcId RC-VIEWER-008
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial> -Build`, open a text-based PDF, enter reflow reading mode.
  2. Long-press/select a specific line of text near the middle of a paragraph; observe the highlighted line and the «Копировать»/«Выделить» confirm bar.
  3. Capture with `tools/uitest/Capture-AndroidAnim.ps1 -Serial <serial>` framing the selected line.
- **Capture:** Capture-AndroidAnim.ps1 frames: tap point on a known line → resulting highlight + confirm bar, framing both the touched and selected line to prove they match.
- **Expected:** The highlighted selection covers exactly the line the finger touched, not the line above/below.
- **Pass/Fail:** PASS if the selection lands on the touched line. FAIL if the selection is off by one line.
- **Severity if broken:** 🟠

#### AV-VIEWER-04 — Сайдбар миниатюр поверх тул-рейла и привязан к левому краю активной панели; airbar анимируется к центру (thumbnail-zorder-airbar-tracking)
- **Area:** VIEWER · **Platform(s):** desktop
- **Fixture/precondition:** one or more PDF panels open
- **Source:** 235516d · regression guard for rcId RC-VIEWER-011
- **Steps:**
  1. `tools/uitest/Launch-Desktop.ps1`, open one or more PDF panels, focus a panel.
  2. Open the thumbnail sidebar; observe it draws over (not behind) the tool rail at the focused panel's left edge.
  3. Switch focus between panels and watch the page-indicator airbar animate to the newly focused panel's centre.
  4. Capture with `Capture-DesktopAnim.ps1`.
- **Capture:** Capture-DesktopAnim.ps1 filmstrip: frame 1 sidebar over rail at panel left edge; frames 2-4 airbar spring travel after focusing a different panel.
- **Expected:** Thumbnail sidebar is fully visible above the tool rail at the panel's left edge; airbar smoothly springs to the focused panel centre on focus change.
- **Pass/Fail:** PASS if the sidebar is on top and correctly positioned and the airbar tracks focus. FAIL if the sidebar is clipped behind the rail or the airbar stays put / jumps without animation.
- **Severity if broken:** 🟡

#### AV-VIEWER-05 — Android: одним пальцем скроллим ЗА рамкой страницы при активном инструменте, рисуем ВНУТРИ (android-scroll-outside-page-with-tool)
- **Area:** VIEWER · **Platform(s):** android-phone, android-tablet
- **Fixture/precondition:** a PDF with a drawing tool active
- **Source:** cd9640a · regression guard for rcId RC-VIEWER-012
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>`, open a PDF, activate a drawing tool (e.g. marker).
  2. Drag one finger starting INSIDE the PDF page frame (`adb -s <serial> shell input swipe ...`) → expect a stroke is drawn (not scroll).
  3. Drag one finger starting OUTSIDE the PDF page frame (in the margin/gutter) → expect the document scrolls.
  4. Capture both with `Capture-AndroidAnim.ps1 -Serial <serial>` framing the start point relative to the page frame.
- **Capture:** Capture-AndroidAnim.ps1 two filmstrips: (a) inside-frame drag → ink appears; (b) outside-frame drag → page content scrolls, with the page-frame boundary visible.
- **Expected:** With a tool active: drag inside the page draws ink; drag outside the page frame scrolls the document (gated only by scrollMode, not by tool activity).
- **Pass/Fail:** PASS if the outside-frame drag scrolls while the inside-frame drag still draws. FAIL if the outside-frame drag is dead (no scroll) or the inside-frame drag scrolls instead of drawing.
- **Severity if broken:** 🟠

#### AV-VIEWER-06 — Android: страницы перерисовываются (резкость) ВО ВРЕМЯ непрерывного скролла, а не после остановки (android-render-during-scroll-sample)
- **Area:** VIEWER · **Platform(s):** android-phone, android-tablet
- **Fixture/precondition:** a multi-page PDF
- **Source:** d0c45b7 · regression guard for rcId RC-VIEWER-013
- **Steps:**
  1. `tools/uitest/Start-AndroidTarget.ps1 -Serial <serial>`, open a multi-page PDF.
  2. Flick/continuously scroll through several pages without stopping (`adb -s <serial> shell input swipe ...`).
  3. Capture an ordered frame burst during the continuous scroll via `Capture-AndroidAnim.ps1 -Serial <serial>` → GIF + filmstrip.
- **Capture:** Capture-AndroidAnim.ps1 filmstrip across a continuous scroll: frames must show an incoming page transitioning from stretched/low-scale to sharp WHILE the scroll offset is still changing (compare adjacent frames' page position + sharpness).
- **Expected:** Incoming pages sharpen within ~100ms while still scrolling; they do not stay stretched/blurry or blank until the scroll fully stops.
- **Pass/Fail:** PASS if pages re-rasterise to crisp during motion (within ~100ms). FAIL if pages remain blurry/blank until scrolling halts (debounce regression).
- **Severity if broken:** 🟠

---

## How to add a scenario

Field shape, ID scheme, and the tier rule are governed by [`TEST-CASE-STANDARD.md`](TEST-CASE-STANDARD.md) — read it first. In short:

1. Pick the next free ID in the area prefix (`AV-<AREA>-NN`, area codes per the standard § 2: `LIB PDF DRAW MARKER MAG REFLOW READER GEST RENDER EDITOR VIEWER TABS SESSION SYNC QR CONV UI UIKIT DESKTOP ANDROID INPUT STARTUP ANNOT`). New area → new section + prefix.
2. Justify Tier 2: confirm the behavior **cannot** be asserted deterministically in Tier 1 (standard § 1). If a Roborazzi golden could cover it, add it there instead and do **not** put it here.
3. Write the block with the standard's fields (ID, Area, Platform(s), Fixture/precondition, Source, Steps, Capture, Expected, Pass/Fail, Severity). Steps + Capture must use only the real harness verbs in the Harness reference above and real fixtures in the Fixtures table — invent no tools, tasks, or paths.
4. Prefer committed fixtures (`thesis-pdf`, `article-fb2`, `article-pdf`) so the scenario runs without the not-committed personal books; mark personal-book scenarios `skip` if absent.
5. If the scenario guards a shipped fix, set **Source** to the fix `sha` and cross-link the `RC-*` id; add a row to [`area-map.md`](area-map.md) so the edit-time hook surfaces it.
6. Bump the changelog below.

## Changelog

- **v1** (2026-05-30) — initial catalog: 16 scenarios across 9 areas (library/open, PDF render, drawing, magnifier, reflow, gestures, EPUB/FB2 conversion, LAN sync, QR/peer), seeded from the `.claude/ux-reports/` runs `20260529-124456 / -1547 / -1821-allplatforms`.
- v2 (2026-05-30) — +109 regression scenarios mined from shipped fixes (RC-* cross-referenced).
