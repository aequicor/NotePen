# NotePen Testing

The single source of truth for how NotePen is tested. Three tiers, one governing principle, one gating policy.

## Governing principle

**Each tier tests only what the tier below physically cannot reach.** Determinism, speed, and trust fall — cost, flakiness, and human/hardware dependency rise — as you go up. So if a behavior can be asserted deterministically, it stays in **Tier 1** and never escalates: a golden-able pixel check (e.g. reflow-reader layout) belongs in Tier 1's Roborazzi suite, **not** in AI-vision. AI-vision exists only for renderers/gestures the headless tier cannot drive; manual exists only for real hardware that neither automated tier can touch.

| Tier | What | Determinism / trust | Cost / flakiness | Gate? |
|---|---|---|---|---|
| **1** | JVM logic + Roborazzi goldens | high | low | hard merge gate |
| **2** | AI-vision on the live app | non-deterministic | token-costly | advisory |
| **3** | Manual on real hardware | human-judged | slow, hardware-bound | release sign-off |

---

## Tier 1 — Automated / deterministic (the CI gate)

Pure-JVM unit & logic tests plus **Roborazzi golden screenshots** (deterministic, headless Compose-Desktop UI tests — they live here, not in Tier 2). Everything runs on the JVM via `commonTest`/`jvmTest`; there is **no instrumented `androidTest` source set anywhere**, so the Android-specific actuals (PdfRenderer, stylus, low-latency overlay) have no automated tests.

### What's covered (per-module inventory)

| Module | ~Test classes | Source sets | Covers |
|---|---|---|---|
| **sync** | 6 | commonTest, jvmTest | LWW merge / sync-engine controller, cache eviction, GitHub response parsing; host library-mutation handler + LAN client + filesystem manifest provider (jvm) |
| **drawing:api** | 8 | commonTest | stroke simplifier, shape recognizer/resampler, merge policy, page rotation, spread split, erase + undo on `PdfDrawingState` |
| **drawing:impl** | 1 | commonTest | magnifier segment mapping geometry |
| **reflow:api** | 1 | commonTest | reader-settings reducer |
| **reflow:impl** | 34 | commonTest, jvmTest | the heavy tier: reflow assembler/segmentation/XY-cut, lattice table detection+refine+morphology, code/footnote/dropcap/running-header/dehyphenation/cross-page-table heuristics, stroke↔text mapping, reader pagination/metrics/ergonomics/wheel; **jvm fixtures**: real-PDF invariants/discovery + Baranovskaya integration/lattice + **Roborazzi snapshots** |
| **library:api** | 1 | commonTest | library-connection serialization |
| **library:impl** | 6 | commonTest, jvmTest | registry persistence/mutation-target, local-folder identity (common); GitHub + Peer-LAN library backends (jvm) |
| **shared** | 11 | commonTest | document identity (+caching provider), URI normalizer, file-history manager + main-screen use cases (add/check-availability/open-recent), PDF page-data/info + port contract test (against fakes), app-settings serialization |
| **qr-connect** | 2 | commonTest | host QR pairing coordinator, pairing-URI parse/build |
| **rendering:api** | 0 | commonTest (empty) | — no tests |
| **rendering:impl** | 4 | commonTest | magnifier geometry/auto-scroll/zoom, PDF multi-page layout — all pure geometry/layout, **no bitmap compositing** |
| **app:byCompose:common** | 47 | commonTest, jvmTest | largest by count: ViewModels + UI-intent/state logic (main screen, folder, toolbar, pen settings, navigation wiring), tabs/workspace/undo, session + drawing + app-settings serialization; **jvm**: ebook layer (EPUB/FB2/CBZ parsers + ebook→PDF + book→reflow, real-book invariant fixtures), desktop repository actuals (folder/history/thumbnail/availability/session), reflow disk-cache + binary format, library/librarian-grant stores |

### Commands

| Task | Command |
|---|---|
| All unit tests | `./gradlew test` |
| One module's tests | `./gradlew :sync:jvmTest` |
| A single test class/method | `./gradlew :sync:jvmTest --tests "ru.kyamshanov.notepen.sync.domain.SyncEngineTest"` |
| Lint / static analysis | `./gradlew detekt` |
| Format check | `./gradlew ktlintCheck` |
| Full gate | `./gradlew check` (= build + tests + ktlintCheck + detekt) |

detekt and ktlint are auto-applied to every module; per-module `detekt-baseline.xml` is honored.

### Roborazzi goldens (the only rendered-pixel tests in the repo)

Module `:reflow:impl`, source set `jvmTest` (Compose Desktop UI test, no emulator). Two test classes produce **9 committed PNGs** in `reflow/impl/snapshots/`:

- `reflow/impl/src/jvmTest/.../reflow/ui/ReaderSnapshotTest.kt` — 3 goldens (`scroll_bar_hidden.png`, `paged_compact.png`, `paged_enlarged.png`); synthetic doc, scroll vs paged reader, typography remeasure.
- `reflow/impl/src/jvmTest/.../reflow/fixtures/BaranovskayaSnapshotTest.kt` — 6 goldens off a real PDF fixture (`baranovskaya_intro`, `intro_long`, `heading_hierarchy`, `list`, `table`, `exercises`); **gracefully skips if the fixture PDF is absent**.

| Flow | Command |
|---|---|
| Verify against committed goldens | `./gradlew :reflow:impl:jvmTest` |
| Record / update goldens | `./gradlew :reflow:impl:jvmTest -Proborazzi.test.record=true` |

### CI

Tier 1 is the merge gate, run on every push and PR by [`.github/workflows/ci.yml`](.github/workflows/ci.yml) (`ubuntu-latest`, provisions **jbrsdk 25**, runs `./gradlew check` incl. Roborazzi). Because `app:byCompose:desktop` resolves a `JETBRAINS`-vendor `languageVersion 25` toolchain eagerly at configuration time, any root invocation (`check`/`test`/`detekt`) fails unless a matching **jbrsdk 25** is registered as a Gradle toolchain — foojay cannot provision JetBrains, so the path is injected into `gradle.properties` (as in `release.yml`, but bumped to jbrsdk-25). Android SDK is preinstalled on `ubuntu-latest`; caching via `gradle/actions/setup-gradle@v4`.

---

## Tier 2 — AI-vision (advisory, not a merge gate)

Claude Code drives the **live** app on **real renderers** and judges visual/behavioral correctness the headless tier cannot reach — composited strokes over PDF bitmaps, low-latency overlay, Android `PdfRenderer` path, magnifier, gestures/zoom, library/editor/toolbar/pen screens, QR/peer catalog, and on-screen LAN-sync propagation. Non-deterministic and token-costly, so it **informs, never blocks**.

### Why it exists (the Tier-1 gaps it covers)

The **only** deterministic rendered-pixel goldens in the repo are the 9 reflow snapshots above; everything else UI-facing is logic-only:

- **Drawing/rendering compositing** — `:rendering:impl` `DrawablePdfPage` (PDF raster + stroke composite), low-latency overlay, multi-page viewer: only pure layout/magnifier-geometry math is tested; no pixel/golden of an actually-drawn stroke over a page.
- **Android `PdfRenderer` rasterization path** — Android actuals (rasterizer, document loader, stylus input, overlay) have zero tests; only the JVM/PDFBox path is exercised, and only via reflow/ebook logic, not rendered pixels.
- **Library & main-screen UI** — library list, folder, peer catalog, recents, dialogs, QR pairing: ViewModel/intent/state logic only, no rendered golden.
- **PDF editor / toolbar / pen-settings UI** — logic tests only; no composited golden of the editor canvas, floating toolbar, or marker/highlighter tool.
- **Magnifier & tablet/stylus interaction** — geometry computed, never rendered or driven by real input.
- **Two-instance sync visual** — LWW logic is unit-tested; on-screen propagation between two running instances is not.

### How to run it

- **Trigger:** any prompt containing the keyword **`ai-vision`**.
- **Desktop harness:** MCP `notepen-desktop` (`screenshot` / `click` / `drag` / `type_text` / `press_key` / …).
- **Android harness:** helper `.claude/tools/bin/notepen-android` (`shot` / `tap` / `swipe` / `install` / …).
- **Live-driving tools** live at `tools/uitest/` + `.claude/tools/` (do not relocate).
- **Reports:** `.claude/ux-reports/<runid>/`.
- **Scenario catalog:** [`/testing/ai-vision-scenarios.md`](testing/ai-vision-scenarios.md).

**Advisory only** — a Tier-2 run surfaces regressions but never blocks a merge.

---

## Tier 3 — Manual on real hardware (release sign-off)

Only what neither automated tier can reach. Required before tagging a release; full checklist at [`/testing/release-checklist.md`](testing/release-checklist.md).

- **Stylus** pressure/tilt + **palm rejection** per OS (WinTab / Cocoa / Android).
- **Perceived overlay latency** during active strokes.
- **Two physical devices syncing over real Wi-Fi + mDNS** — discovery, WebSocket, LWW with simultaneous draw, QR pairing, reconnect/offline-queue flush.
- **Installers** — Inno Setup + file assoc (Windows), DMG + Gatekeeper (macOS), DEB (Linux), signed APK (Android).
- **Custom JBR title bar** and OS file associations.

**Sign-off matrix:** Windows (10/11) · macOS (Apple Silicon/Intel) · Linux (DEB) · Android phone · Android tablet; sync cross-paired desktop↔desktop, desktop↔Android, Android↔Android.

---

## Gating policy

| Event | Required |
|---|---|
| **PR / push** | **Tier 1 green** (`./gradlew check`, incl. Roborazzi) — hard merge gate |
| **Pre-release / nightly** | Tier 2 AI-vision run + Roborazzi goldens — advisory, surfaces regressions, does **not** block |
| **Release** | Tier 3 hardware checklist signed off — required before tagging |

Release reminders: tag **`v1.0.0`+** only (jpackage rejects `0.x`; re-release = move the tag); bump `app.version`; set the `ANDROID_*` signing env; verify the **obfuscated** `createReleaseDistributable`, not just `runDesktop`.
