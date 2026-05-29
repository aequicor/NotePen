# NotePen full UI/UX testing pass

You are a fresh agent. The repo is `/Users/kruz18/IdeaProjects/NotePen` — a Kotlin
Multiplatform PDF/EPUB annotation app (Android + Desktop JVM) with an infinite-canvas
PDF viewer, stylus annotation, reflow reader, and peer-to-peer LAN sync. Your job is
a comprehensive UI/UX regression test of the current `master` HEAD.

## Why now

Recent wave 3 work added/changed:
- `ReflowBlock.Code` (monospace block, newlines preserved) and `ReflowBlock.Footnote`
  (smaller font + dimmed alpha) — new render paths.
- `TableRow.isHeader` flag — first row of detected tables.
- Running headers/footers stripping (cross-page repetition).
- Tagged PDF heading promotion + drop-cap filter + sub/super flags.
- Cross-page table merge + cross-page dehyphenation.

We need eyes on the rendered output across real documents, not just unit tests.

## Fixtures (read-only)

| Path | Type | Why |
|---|---|---|
| `reflow/impl/src/jvmTest/resources/fixtures/thesis-mixed-content.pdf` | TEXT_BASED, ~136K chars | clean tables, list items, no headings (lock'ed) |
| `reflow/impl/src/jvmTest/resources/fixtures/article-org-risks.pdf` | small, ~14K chars | smoke |
| `/Users/kruz18/Documents/english/Барановская_Грамматика_англ_языка_202509160919_58958.pdf` | HYBRID, 22 MB, 228p | OCR'd Russian grammar book — Russian list items, real heading hierarchy |
| `/Users/kruz18/Downloads/Grammarway_3 1 (2).pdf` | IMAGE_ONLY, 41 MB, 826p | scanned, Figure-per-page |
| `/Users/kruz18/Downloads/Telegram Desktop/Isiguro_Klara-i-Solnce.aQ1xpw.619657.fb2` | FB2 → PDF | 1.5 MB novel, exercises EbookAware path |

If a hardcoded-path fixture is missing on this machine, skip it and note that in the report.

## Architecture: use sub-agents in parallel

Most scenarios are independent. **Spawn sub-agents in a single message** (multiple
Agent tool calls in one assistant turn) so they run concurrently. Use
`subagent_type: "general-purpose"` unless a more specific agent fits.

Strict rule: each sub-agent prompt must be **fully self-contained** — it gets none
of this conversation. Repeat the project path, the fixture paths, the launch
commands, the success criteria, and the screenshot output directory each time.

Group the work into 5 phases. Within a phase, sub-agents run in parallel.
Between phases, you (the orchestrator) wait, read each sub-agent's report, and
synthesize.

## Phase 0 — orchestrator preflight (you, not a sub-agent)

1. `git log -1 --oneline` — verify current HEAD is the wave-3 commit (`1301d3a`
   or later, message starts with `feat(reflow): wave 3 quality`).
2. `./gradlew :reflow:impl:jvmTest :app:byCompose:common:jvmTest -Dorg.gradle.jvmargs="-Xmx4g"`
   — quick green check; if any test fails, stop and report — UI testing on a
   broken build is wasted time.
3. Create the screenshots directory: `mkdir -p .claude/ux-reports/$(date +%Y%m%d-%H%M%S)`.
   Set `REPORT_DIR` for your own use; tell each sub-agent its own subfolder.
4. Read [reflow/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/ui/ReflowReader.kt](reflow/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/ui/ReflowReader.kt)
   `codeStyle()`, `footnoteStyle()`, `headingStyle()`, `paragraphStyle()` so you
   know what the new block types should look like. This is *your* baseline — you
   judge the screenshots, not the sub-agents (they don't know what "correct" is).

## Phase 1 — launch & smoke (3 sub-agents in parallel)

### Sub-agent 1A: Desktop launch
- Run `./gradlew runDesktop` (alias for `:app:byCompose:desktop:run`) in the
  background, give it 90 s to finish JBR-21 toolchain warm-up + first paint.
- Use computer-use MCP: `request_access` for "NotePen", screenshot home/library
  screen. Save to `<REPORT_DIR>/1A-desktop-launch.png`. Window title should be
  visible.
- Open one of the in-repo fixtures via the UI (file picker). If picker not
  available, use the recents list or drag-drop equivalent. Screenshot the loaded
  PDF on the first page.
- Report: launch time (s), first-paint screenshot, whether anything broke.

### Sub-agent 1B: Android debug install
- `./gradlew :app:byCompose:android:installDebug` (requires connected device or
  emulator — if neither is available, report `skipped: no device` and stop).
- If installed, launch the app via adb (`adb shell monkey -p io.aequicor.notepen
  -c android.intent.category.LAUNCHER 1`). Wait 10 s. Screenshot via adb:
  `adb exec-out screencap -p > <REPORT_DIR>/1B-android-launch.png`.
- Report: installed? launched? main screen shown?

### Sub-agent 1C: existing snapshot suite
- Run `./gradlew :reflow:impl:jvmTest --tests "*BaranovskayaSnapshotTest*" -Dorg.gradle.jvmargs="-Xmx4g" --rerun-tasks`.
- Verify all 6 goldens in `reflow/impl/snapshots/baranovskaya_*.png` still pass
  (the test verifies them against committed PNGs). If any fail, that means the
  rendered output changed since they were recorded — bug or expected wave-3 shift?
- Copy the 6 snapshots to `<REPORT_DIR>/1C-snapshots/` for human review.
- Report: which goldens pass/fail, what changed visually if any.

Wait for all three reports before Phase 2.

## Phase 2 — reflow reader content rendering (4 sub-agents in parallel, Desktop)

App must be running from Phase 1. Each sub-agent opens a specific fixture and
navigates to specific block ranges. Computer-use MCP for screenshots + clicks.

### Sub-agent 2A: Code blocks
- Open thesis-mixed-content.pdf. Switch to reading mode (button in top-right
  toolbar usually). Search the document for monospace runs — `Файл подготовки`
  paragraphs may have them, or skim for table cells with `Times New Roman`.
- Expected: `ReflowBlock.Code` renders in monospace font. Find at least one Code
  block; screenshot it. If none, note that fixtures lack code content.
- Screenshot: `<REPORT_DIR>/2A-code-blocks/{page-N-screenshot}.png`.
- Verify: monospace font visible, newlines preserved (lines stack, not flow).

### Sub-agent 2B: Footnotes
- Open Baranovskaya. Reading mode. According to discovery, ~36 footnotes were
  detected. Page through and find a page where a Footnote block renders.
- Expected: smaller font (~0.85×) + dimmed text color. Screenshot.
- `<REPORT_DIR>/2B-footnotes/{page-N}.png`. Also screenshot a page WITHOUT
  footnote for contrast.

### Sub-agent 2C: Tables (header + cross-page merge)
- Open Baranovskaya. Find a grammar table (heuristic: pages with regular column
  alignment, "Tense", "Form" etc.). Screenshot the table.
- Expected: first row may have header styling. Check the rendered TableView in
  `reflow/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/ui/ReflowReader.kt`
  — search for `TableView` and `isHeader` to know what styling SHOULD apply.
  **If TableView currently ignores `isHeader`, that's a finding** — the data is
  there but UI hasn't been updated.
- Screenshot: `<REPORT_DIR>/2C-tables/{page-N}.png`.

### Sub-agent 2D: Heading hierarchy + drop caps
- Open Baranovskaya. Find a chapter opening — single large letter at paragraph
  start should NOT become a Heading entry in TOC. Verify by opening TOC
  (sidebar / outline button); look for nonsensical single-letter entries.
- Screenshot TOC + the rendered chapter opening.
- `<REPORT_DIR>/2D-headings/{toc.png, chapter-N.png}`.

Wait for all four reports.

## Phase 3 — interactive UX (3 sub-agents in parallel)

App must be running. These test interactions, not just render.

### Sub-agent 3A: Typography presets & themes
- Open any reflow document. Cycle through ReaderTheme: PAPER, SEPIA, GRAY, NIGHT,
  BRIGHT. Screenshot each.
- Switch typography preset (long-reading vs default). Screenshot before/after.
- Verify: background color changes, text color contrast remains legible.
- `<REPORT_DIR>/3A-themes/{paper,sepia,gray,night,bright}.png` + `{preset-default,preset-long}.png`.

### Sub-agent 3B: Page navigation & anchor persistence
- Open Baranovskaya in paged mode. Page to ~50% via swipe / arrow key (try both,
  note which works). Read the visible chapter title.
- Switch typography preset (changes pagination). Verify reader lands on the
  same chapter, not page 1.
- Switch theme. Verify reader stays put.
- Screenshot before/after each transition.
- `<REPORT_DIR>/3B-anchor/{before,after-preset,after-theme}.png`.
- This validates P6 anchor persistence end-to-end in UI.

### Sub-agent 3C: PDF viewer + drawing
- Open a PDF in non-reading (page) mode. Zoom in via pinch / scroll-wheel.
- Draw a stroke with the marker tool (toolbar). If using mouse: click-drag.
  Screenshot the stroke.
- Switch tool (eraser), erase part of the stroke. Screenshot.
- Switch pages. Switch back. Verify stroke persisted.
- `<REPORT_DIR>/3C-drawing/{drawn,erased,pageturn-back.png}`.

Wait for all three reports.

## Phase 4 — sync & document conversion (2 sub-agents, sequential where state matters)

### Sub-agent 4A: EPUB/FB2 → PDF conversion
- Open the Isiguro FB2 fixture (if present). Expected: app converts to PDF
  (first launch may take a few seconds — `JvmEbookToPdfConverter`'s file
  cache lives in `System.getProperty("java.io.tmpdir")/notepen-books`).
- Screenshot: cover page, mid-book page. Verify text is reflowable (use reading
  mode).
- Second open should be instant (cache hit). Measure both.
- `<REPORT_DIR>/4A-fb2/{cover, midbook, timing.txt}`.

### Sub-agent 4B: Peer catalog UI (no actual peer needed)
- Open peer catalog screen. Verify mDNS discovery UI displays without throwing.
- If no peers visible (no other instance running), that's fine — just verify the
  empty state. Screenshot.
- Optionally: spawn a second desktop instance via second `runDesktop` invocation
  (might fail due to single-instance enforcement — note that). If both run,
  verify they discover each other.
- `<REPORT_DIR>/4B-peers/{catalog-empty.png, [catalog-with-peer.png]}`.

## Phase 5 — Android (optional, only if 1B succeeded)

If 1B reported `installed: true`, spawn one sub-agent per Phase 2-3 scenario,
but on Android via adb. Use `adb exec-out screencap -p > path.png` for
screenshots. Interactions via `adb shell input tap X Y` / `input swipe`. Same
fixtures (need to copy via `adb push` first to /sdcard/Download/).

Specifically:
### Sub-agent 5A: Android reflow render parity
- Push Baranovskaya to device. Open via app's file picker. Screenshot a
  paragraph + a table + footnote zone. Compare visually against Desktop equivalents
  from Phase 2.

### Sub-agent 5B: Android stylus input (only if device has a real stylus)
- If S-Pen / similar available: draw with pressure. Screenshot. Verify the
  low-latency overlay path works (no input lag — subjective, just check that
  strokes appear).

Skip Phase 5 entirely if 1B was skipped.

## What "bug" means here

Three severity levels:

- **Critical**: rendered output unreadable, app crashes, data loss, sync hangs.
- **Regression**: behavior changed vs. snapshot or vs. obvious expectation (e.g.,
  Code blocks shown in proportional font, footnotes same size as body, drop cap
  treated as heading entry in TOC).
- **Polish**: rendering works but has minor visual issues (alignment off by a
  pixel, theme contrast too low for one theme, etc.).

Each finding needs: severity tag, screenshot path, one-line repro
(`open X, navigate to Y, observe Z`), and a guess at the source file/component
(skim the codebase to point at the right file — `:reflow:impl/ui/ReflowReader.kt`
for render, `:app:byCompose:common` for screen composition).

## Final orchestrator deliverable

Write `<REPORT_DIR>/REPORT.md`:

1. **Top-line table**: each sub-agent, status (✅ / ⚠️ / ❌), 1-line summary.
2. **Findings**: numbered list, severity-ordered, format above.
3. **Coverage gap**: what you couldn't test (no device, fixture missing, feature
   not in build, etc.).
4. **Recommended follow-ups**: 3–5 bullets, max.

Also dump the path to REPORT.md in your final message to the user.

## Hard constraints

- **Do not modify code.** This is a test pass, not a fix pass. Findings only.
- **Do not commit anything.** All artifacts under `.claude/ux-reports/...` which
  is gitignored by `.claude/`.
- **Do not run destructive git commands.**
- **Computer-use access**: each sub-agent that needs computer-use must call
  `request_access` for its target app(s) first. Browsers stay at tier "read";
  use that only for documentation lookup, not for app testing.
- **Token budget**: keep each sub-agent prompt under ~3000 words. Reports under
  ~500 words each. Final REPORT.md under ~2000 words.

Start with Phase 0. Don't spawn sub-agents until Phase 0 is green.
