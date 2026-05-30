# NotePen — Tier-2 AI-vision scenario catalog

Versioned catalog of the **Tier-2 AI-vision** scenarios Claude Code runs by driving the **live** app on **real renderers**. See [`/TESTING.md`](../TESTING.md) for the three-tier model. This file is the Tier-2 entry referenced there.

## Purpose

These scenarios cover only what the deterministic Tier-1 gate (`./gradlew check`, incl. Roborazzi goldens) **physically cannot reach**: composited strokes over real PDF bitmaps, the Android low-latency overlay, the Android `PdfRenderer` path, the magnifier, gestures/zoom/pan, on-screen LAN-sync propagation, and the live library/editor/file-picker flows. If a behavior can be asserted deterministically (e.g. reflow layout), it belongs in Tier 1's Roborazzi suite — **not here**.

- **How to trigger:** include the keyword **`ai-vision`** (case-insensitive) in any prompt. A `UserPromptSubmit` hook injects the standing instruction to actually run the harness, capture real screenshots + ordered frame bursts for animations, and display the PNG artifacts in the reply (not just describe them).
- **Where output lands:** `/.claude/ux-reports/<runid>/` (gitignored). Run-id convention `YYYYMMDD-HHMM[-label]` (e.g. `20260529-1821-allplatforms`); `.claude/ux-reports/.current-run` holds the active run-id. Per-run layout: `REPORT.md` at the run root (mirror the format of `20260529-124456/REPORT.md` / `20260529-1547/REPORT.md` — top-line table + 🔴 Critical / 🟠 Regression / 🟡 Polish findings + coverage gap), platform/phase subfolders (`desktop/ android/ emulator/ huawei/ …`) holding ordered, descriptively-named PNGs (`05-baranovskaya-open.png`, `frame000.png …` for filmstrips).
- **Governance:** Tier 2 is **advisory, not a merge gate** (gating policy in `/TESTING.md`). Results are non-deterministic and token-costly; a human triages every finding. A flaky scenario gets **tightened** (better fixture, stricter wait, narrower region) — **never silently dropped**. When the headless tier could in fact golden a behavior surfaced here, move it down to Tier 1 rather than re-running it in Tier 2.

## Harness reference (scenario verbs)

### Desktop — MCP `notepen-desktop` (`mcp__notepen-desktop__*`)
Coordinates are logical points, top-left origin, 1:1 with the screenshot preview. No scroll / no context-menu helper — use `drag` for panning/scrolling, `press_key` `pageup`/`pagedown` for paging.

| Tool | Params |
|---|---|
| `screenshot` | `path?` (abs PNG, full-res), `region?` `{x,y,w,h}`, `maxPreview?` (px) |
| `click` | `x`, `y`, `button?` `"left"\|"right"`, `count?` (2 = double) |
| `double_click` | `x`, `y` |
| `move` | `x`, `y` |
| `drag` | `x`, `y`, `x2`, `y2` (24-step interpolated — strokes / page swipes) |
| `type_text` | `text` (newlines → Return) |
| `press_key` | `key` (`return tab escape left right up down pageup pagedown home end space delete f1-f12` or a char), `modifiers?` ⊆ `[command, control, option, shift]` |
| `activate_app` | `name` (`"NotePen"` = release bundle, `"java"` = `runDesktop` JVM) |
| `list_windows` | `name` (process name → titles + pos + size) |

### Android — `.claude/tools/bin/notepen-android`
Target = `NOTEPEN_SERIAL` env or the sole connected device. Pkg `ru.kyamshanov.notepen.debug`.

| Subcommand | Args |
|---|---|
| `devices` | — |
| `boot-emulator` / `kill-emulator` | AVD `Medium_Phone_API_36.1` → `emulator-5554` |
| `install` | `:app:byCompose:android:installDebug` |
| `launch` / `stop` | — |
| `shot` | `<file.png> [maxpx]` (screencap, optional downscale) |
| `tap` | `<x> <y>` |
| `swipe` | `<x1> <y1> <x2> <y2> [ms=300]` (drag / stroke) |
| `text` | `"<string>"` |
| `key` | `<KEYCODE\|name>` (e.g. `BACK`, `22`=DPAD_RIGHT) |
| `rotate` | `portrait\|landscape` (locks rotation) |
| `push` | `<local> [remoteDir=/sdcard/Download/]` (push a fixture) |
| `backup` | `<dir>` → `notepen-data.tar` (run before reinstall on Huawei — holds real annotations) |
| `logcat` | `[filterspec]` |
| `raw` | `-- <adb args…>` |

Known devices: Huawei `44RUN24B09G03494` (real stylus, **live user annotations — `backup` before any reinstall**), emulator `emulator-5554`.

### Deterministic fallback (no GUI / permission)
Roborazzi goldens: `./gradlew :reflow:impl:jvmTest --tests "*SnapshotTest"` (9 committed PNGs in `reflow/impl/snapshots/`; record with `-Proborazzi.test.record=true`). Use only as a fallback signal — it does **not** satisfy a Tier-2 scenario, which requires the live renderer.

### Fixtures
| Key | Path | Notes |
|---|---|---|
| `baranovskaya` | `/Users/kruz18/Documents/english/Барановская_Грамматика_англ_языка_202509160919_58958.pdf` | 383-page HYBRID/OCR scan; reflow torture-test; on Huawei pushed to `/sdcard/Download/`. Not committed → scenario `skip`s if absent. |
| `isiguro` | `/Users/kruz18/Downloads/Telegram Desktop/Isiguro_Klara-i-Solnce.…fb2` | 1.5 MB FB2, justified prose / em-dashes / TOC. Not committed. |
| `planeta-epub` | `planeta-ru.epub` | EPUB→PDF→reflow chain, embedded illustrations. Not committed. |
| `article-fb2` | `app/byCompose/common/src/jvmTest/resources/fixtures/article-small.fb2`, `article-yamshanov.fb2` | committed, safe to `push` / open. |
| `thesis-pdf` | `reflow/impl/src/jvmTest/resources/fixtures/thesis-mixed-content.pdf` | committed; only in-repo PDF with tables / Code / Footnote candidates. |
| `article-pdf` | `reflow/impl/src/jvmTest/resources/fixtures/article-org-risks.pdf` | committed. |

UI strings to drive: `Открыть` (Open), `+folder`, themes `Бумага / Сепия / Серый / Ночь / Яркий`, typography presets `Компактно-1 / Долгое чтение-1`. The file-picker is keyboard-layout-fragile (RU layout has blocked earlier runs) — see AV-LIB-02.

---

## Scenario catalog

> Severity legend (matches report findings): 🔴 critical · 🟠 regression · 🟡 polish.
> Platforms: `desktop` (PDFBox / JBR) · `android-phone` (Huawei, real stylus) · `android-emulator` (`emulator-5554`). `android-tablet` is a Windows-only harness target (`tools/uitest/`) — out of scope on this Mac, listed where relevant for the release matrix.

### Library / open flows

#### AV-LIB-01 — Launch to library
- **Area:** library · **Platform(s):** desktop, android-phone, android-emulator
- **Fixture/precondition:** desktop release bundle installed (`activate_app "NotePen"`) or `notepen-android install` + `launch`.
- **Steps:**
  1. desktop: `activate_app "NotePen"`, then `screenshot path=.../01-library.png`. android: `notepen-android launch` → `notepen-android shot .../01-library.png`.
  2. Read the top bar affordances (`Открыть`, `+folder`, layers/sessions, refresh, gear).
- **Expected:** library grid renders with recents/folders; top bar icons present and not overlapping; no blank/white window.
- **Pass/Fail:** PASS if the library screen composes with at least the open + folder affordances visible. FAIL on blank window, crash, or a >~90 s launch with no frame.
- **Severity if broken:** 🔴

#### AV-LIB-02 — Open via file-picker + path entry (keyboard-layout guard)
- **Area:** library · **Platform(s):** desktop, android-emulator
- **Fixture/precondition:** `thesis-pdf` (committed) or `baranovskaya` (if present).
- **Steps:**
  1. desktop: `click` `Открыть`; in the native dialog `type_text` the absolute fixture path; `press_key return`. emulator: invoke the SAF picker, navigate to the pushed `/sdcard/Download/` fixture (`notepen-android push <fixture>` first).
  2. `screenshot` the dialog **before** confirming, and the opened document after.
- **Expected:** typed path lands verbatim (no RU-layout transliteration); document opens to page 1.
- **Pass/Fail:** PASS if the path is entered correctly and the document opens. FAIL if the typed path is garbled by keyboard layout (known harness pain point — log it, do not silently retry) or the picker can't reach the fixture.
- **Severity if broken:** 🟠

#### AV-LIB-03 — Session restore
- **Area:** library · **Platform(s):** desktop
- **Fixture/precondition:** at least one document opened previously this run (so a session exists).
- **Steps:** `click` the layers/sessions icon (top-right; note: earlier runs found this is the **session manager** — `Сессии: Восстановить/Резюме/Удалить` — not the peer catalog, F-5); `screenshot`; `click` `Восстановить`.
- **Expected:** session list lists prior documents; restore reopens the last document at its saved position.
- **Pass/Fail:** PASS if restore reopens the prior doc. FAIL if the menu mislabels its function or restore loses position.
- **Severity if broken:** 🟡

### PDF render fidelity

#### AV-PDF-01 — PDF page-mode raster sharpness + cache
- **Area:** PDF render · **Platform(s):** desktop, android-phone, android-emulator
- **Fixture/precondition:** `baranovskaya` (or `thesis-pdf` if absent). Open in **PDF page mode** (not reflow).
- **Steps:**
  1. Open the fixture in page mode; `screenshot path=.../01-pdf-p1.png`.
  2. `press_key pagedown` ×3 (desktop) / `notepen-android swipe` page-up gesture ×3 (android), capturing each page.
  3. Page back to p1 (`pageup` / reverse swipe) and re-`screenshot` — exercises the page-bitmap cache.
- **Expected:** crisp raster, legible glyphs, no half-rendered/torn pages; re-visited p1 matches the first capture (cache hit, no re-blur).
- **Pass/Fail:** PASS if all pages render fully and the cached page is identical on return. FAIL on blank/torn pages, a **native crash** (regression watch — cf. `d65511e` PdfRenderer serialization on Android), or visibly degraded raster.
- **Severity if broken:** 🔴

#### AV-PDF-02 — Multi-page navigation continuity
- **Area:** PDF render · **Platform(s):** desktop, android-phone
- **Fixture/precondition:** `baranovskaya` open in page mode.
- **Steps:** page forward 8× via `press_key pagedown` (desktop) / `notepen-android swipe <x1> <y1> <x2> <y2>` (android), capturing `frame000…frame007`. Build a filmstrip.
- **Expected:** monotonic page advance (no skipped/repeated pages), consistent `fitWidthTopInset` framing, no flicker between pages.
- **Pass/Fail:** PASS if every step advances exactly one page with stable framing. FAIL on stuck pages, double-jumps, or layout jump between pages.
- **Severity if broken:** 🟠

### Stroke-follows-pen drawing

#### AV-DRAW-01 — Pen stroke tracks the pointer + commits on finish
- **Area:** drawing · **Platform(s):** desktop, android-phone, android-emulator
- **Fixture/precondition:** any document open in PDF page mode with the pen tool selected.
- **Steps:**
  1. Select pen. desktop: `drag x y x2 y2` (24-step interpolation) across the page. android: `notepen-android swipe <x1> <y1> <x2> <y2> 600`.
  2. **Burst-capture** during the stroke (`frame000.png …`) and one final `screenshot` after release.
- **Expected:** rendered path follows the pointer with no large lag/gap; on Android the **low-latency overlay** shows the live stroke and commits cleanly into the page bitmap on finish (no double-draw, no ghost overlay left behind).
- **Pass/Fail:** PASS if the committed stroke matches the drag path and no overlay artifact remains. FAIL on missing/offset stroke, overlay not committing, or a ghost/duplicate stroke.
- **Severity if broken:** 🔴

#### AV-DRAW-02 — Marker / highlighter + eraser
- **Area:** drawing · **Platform(s):** desktop, android-phone
- **Fixture/precondition:** document open with a visible pen/marker/eraser toolbar.
- **Steps:** select marker, `drag`/`swipe` over body text (capture); select eraser, `drag`/`swipe` over the marker + a prior pen stroke (capture before/after).
- **Expected:** marker is semi-transparent and lets text show through (highlighter compositing); eraser removes only the strokes it crosses, leaving the PDF raster intact.
- **Pass/Fail:** PASS if marker composites translucently and eraser removes strokes without touching the page raster. FAIL if marker is opaque, eraser deletes page content, or strokes survive erasing.
- **Severity if broken:** 🟠

### Magnifier / loupe

#### AV-MAG-01 — Magnifier during edge stroke
- **Area:** magnifier · **Platform(s):** android-phone, desktop
- **Fixture/precondition:** document open, pen tool selected. No golden exists — visual-only.
- **Steps:** start a stroke near a page edge (`drag`/`swipe` starting within ~5% of the page border) and **burst-capture** while the pointer is held near the edge; release and capture.
- **Expected:** the `:drawing:impl` magnifier/loupe appears, shows a zoomed inset of the region under the pointer, and tracks the pointer; it dismisses on release.
- **Pass/Fail:** PASS if the loupe appears, magnifies the correct region, and dismisses cleanly. FAIL if it never appears, magnifies the wrong region, or sticks after release.
- **Severity if broken:** 🟡

### Reflow reading mode

#### AV-REFLOW-01 — OCR/HYBRID reflow integrity (Baranovskaya)
- **Area:** reflow · **Platform(s):** desktop, android-phone
- **Fixture/precondition:** `baranovskaya` (skip if absent).
- **Steps:** open → enter reading mode (Book icon); `screenshot` the first content page; page through p1–p9 (`pagedown` / right-edge tap-zone), capturing each.
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
  3. android only: `notepen-android rotate landscape` then `rotate portrait`; capture both.
- **Expected:** each theme recolors text + background legibly; after preset switch **and** rotation the reader stays on the **same passage** (anchor-based, cf. F-10), not the same page index.
- **Pass/Fail:** PASS if every theme is legible and the reading position survives preset/orientation change. FAIL if Night contrast is unreadable or the anchor jumps on re-pagination.
- **Severity if broken:** 🟠

### Gestures / zoom / pan

#### AV-GEST-01 — Zoom / fit-width / pan
- **Area:** gestures · **Platform(s):** desktop, android-phone
- **Fixture/precondition:** `baranovskaya` (or any PDF) in page mode.
- **Steps:** desktop: `drag` to pan; use the zoom affordance / `press_key` zoom if available, capturing frames. android: pinch via two `swipe`s where supported, then a pan `swipe`; **burst-capture** zoom transitions.
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
- **Fixture/precondition:** desktop host running; `notepen-android boot-emulator` + `install` + `launch` as client on the same LAN; both opened to the **same** document.
- **Steps:**
  1. Pair the two instances (peer catalog — see AV-QR-01 for the entry point).
  2. Draw a pen stroke on instance A (`drag` / `swipe`); capture A.
  3. Within a few seconds, `screenshot`/`shot` instance B.
  4. Draw a conflicting/overlapping stroke on B and re-capture A — exercises last-writer-wins + tombstone.
- **Expected:** the stroke drawn on A appears on B (and vice-versa); concurrent edits resolve last-writer-wins with tombstones winning over concurrent adds, visible identically on both screens.
- **Pass/Fail:** PASS if strokes propagate both ways and the merged result matches on both instances. FAIL if a stroke never appears, appears duplicated, or the two instances diverge.
- **Severity if broken:** 🔴

### QR pairing / peer catalog

#### AV-QR-01 — Peer catalog + QR pairing entry point
- **Area:** peers · **Platform(s):** desktop (QR generation) + android-phone/emulator (camera scan)
- **Fixture/precondition:** two instances on the same LAN. NB: the peer-catalog entry point was **not located** from the library top bar in earlier runs (F-5) — finding/confirming it is part of this scenario.
- **Steps:** locate and open the mDNS peer / QR-pairing screen (audit the library top-bar affordances and any settings route); `screenshot` the desktop QR; on Android, surface the camera scanner and capture it.
- **Expected:** peer screen lists discovered mDNS peers; desktop renders a scannable QR; Android shows the camera scan UI.
- **Pass/Fail:** PASS if the peer screen is reachable and shows peers + QR/scan UI. FAIL if the entry point is undiscoverable (re-log F-5) or the QR/scanner doesn't render.
- **Severity if broken:** 🟡

---

## How to add a scenario

1. Pick the next free ID in the area prefix (`AV-<AREA>-NN`: `LIB PDF DRAW MAG REFLOW GEST CONV SYNC QR`). New area → new two-block section + a new prefix.
2. Justify Tier 2: confirm the behavior **cannot** be asserted deterministically in Tier 1. If a Roborazzi golden could cover it, add it there instead and do **not** put it here.
3. Write the block with all eight fields (ID, Area, Platform(s), Fixture/precondition, Steps, Expected, Pass/Fail, Severity). Steps must use only the real harness verbs in the reference table and real fixtures in the fixture table — invent no tools, tasks, or paths.
4. Prefer committed fixtures (`thesis-pdf`, `article-fb2`, `article-pdf`) so the scenario runs without the not-committed personal books; mark personal-book scenarios `skip` if absent.
5. Bump the changelog below.

## Changelog

- **v1** (2026-05-30) — initial catalog: 16 scenarios across 9 areas (library/open, PDF render, drawing, magnifier, reflow, gestures, EPUB/FB2 conversion, LAN sync, QR/peer), seeded from the `.claude/ux-reports/` runs `20260529-124456 / -1547 / -1821-allplatforms`.
