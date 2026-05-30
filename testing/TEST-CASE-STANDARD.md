---
genre: reference
title: Test-Case Standard (single source of truth)
topic: testing
triggers:
  - "test case"
  - "test-case standard"
  - "тест-кейс"
  - "стандарт тест-кейсов"
  - "regression case"
  - "ai-vision scenario"
confidence: high
source: human
updated: 2026-05-30T00:00:00Z
---

# NotePen — Test-Case Standard

The **single source of truth for what a test case is** in this repo: its fields, its ID, which
tier it lives in, and how it is cross-referenced. Every catalog — the per-feature
`vault/**/test-cases.md`, the central [`regression-cases.md`](regression-cases.md), the
[`ai-vision-scenarios.md`](ai-vision-scenarios.md) catalog, and the Tier-3
[`release-checklist.md`](release-checklist.md) — obeys this one standard. The tier model and gating
policy live in [`/TESTING.md`](../TESTING.md); this file defines the *unit* those tiers are built from.

> One standard, three catalogs. If a rule about a test case's **shape** is anywhere, it is here.
> If a rule about **when a tier runs / blocks** is anywhere, it is in `/TESTING.md`.

---

## 1. The governing rule (which tier a case belongs to)

Inherited verbatim from `/TESTING.md` § Governing principle:

**Each tier tests only what the tier below physically cannot reach.**

| Tier | A case belongs here when… | Catalog |
|---|---|---|
| **1** | the behavior can be asserted **deterministically, headless** (JVM logic, or a Compose-Desktop Roborazzi golden) | a `*Test.kt` + (if visual) a committed golden; tracked from `regression-cases.md` / `vault/**/test-cases.md` |
| **2** | it needs a **real renderer / gesture / animation / on-screen propagation** the headless tier cannot drive (composited ink over a PDF raster, low-latency overlay, magnifier, pan/zoom, LAN-sync visible on two screens) | [`ai-vision-scenarios.md`](ai-vision-scenarios.md) (`AV-*`) |
| **3** | it needs **real hardware** (stylus pressure/tilt, palm rejection, perceived latency, two devices over real Wi-Fi, installers, OS chrome) | [`release-checklist.md`](release-checklist.md) (`MAN-*`) |

**Demotion is mandatory, not optional.** If a behavior currently verified by an `AV-*` scenario
*could* be pinned by a Roborazzi golden, move it to Tier 1 and delete the `AV-*`. A case never sits
in a higher tier "to be safe" — that is the disorder this standard exists to prevent. The lowest tier
that can actually assert the behavior wins.

---

## 2. ID scheme

One namespace, one prefix per catalog. IDs are **permanent** — never renumber; retire with a
`~~strikethrough~~` + `(retired: reason)` instead of reusing a number.

| Prefix | Catalog | Meaning | Example |
|---|---|---|---|
| `RC-<AREA>-NNN` | `regression-cases.md` | a regression guard mined from a shipped fix | `RC-MAG-004` |
| `AV-<AREA>-NN` | `ai-vision-scenarios.md` | a Tier-2 AI-vision scenario | `AV-DRAW-01` |
| `MAN-<AREA>-NN` | `release-checklist.md` | a Tier-3 manual / hardware case | `MAN-STYLUS-01` |
| `TC-N` | `vault/<module>/<feature>/test-cases.md` | a **feature-local** case bound to that feature's spec ACs/ECs | `TC-7` |

`TC-N` stays **feature-scoped** (numbered within its own file, bound to `spec.md` AC/EC ids) — it is
the per-feature working set during a `/kit` flow. The three `RC/AV/MAN` namespaces are
**repo-global** catalogs that outlive any one feature. A `TC-N` that proves durable graduates into an
`RC-*`/`AV-*`/`MAN-*` and back-links to its origin (`Source: vault/.../test-cases.md#TC-7`).

### Area codes

`AREA` is a stable short code. Current set (extend in the relevant catalog's "how to add" section,
never invent ad-hoc):

`LIB` library/open · `PDF` PDF render · `DRAW` drawing/pen · `MARKER` marker/eraser ·
`MAG` magnifier/loupe · `REFLOW` reflow reader · `READER` reader chrome/pagination ·
`GEST` gestures/zoom/pan · `RENDER` rendering pipeline · `EDITOR` editor view-state ·
`VIEWER` multi-page viewer · `TABS` tabs/workspace · `SESSION` sessions/restore ·
`SYNC` LAN/host sync · `QR` QR/peer pairing · `CONV` EPUB/FB2 conversion ·
`UI` glass/theme/chrome · `UIKIT` reusable components · `DESKTOP` desktop/JBR/packaging ·
`ANDROID` Android actuals · `INPUT` stylus/palm input · `STARTUP` cold-start/perf ·
`ANNOT` annotation persistence.

---

## 3. The required field set (every case, every catalog)

A case is complete only with all of these. Catalogs render them as table columns or as block
fields; the **set** is invariant.

| Field | Meaning | Notes |
|---|---|---|
| **ID** | per §2 | permanent |
| **Title** | one line, what it proves | bilingual where the catalog already is (RU title + EN slug ok) |
| **Tier** | `1` / `2` / `3` | per §1; if you wrote Tier ≥ 2, you asserted the tier below cannot reach it |
| **Type** | `unit` `unit-edge` `integration` `error` `e2e` `visual` `gesture` `manual` | matches `vault/_templates/test-cases.md` set, plus `visual`/`gesture` for Tier 2 |
| **Area** | area code per §2 | |
| **Preconditions / Fixture** | what must be true first; which fixture | use the canonical fixture keys in `ai-vision-scenarios.md` § Fixtures |
| **Steps** | ordered, executable | Tier 2/3 steps use **only** the real harness verbs (§5) — invent no tool |
| **Expected** | the correct observable result | |
| **Pass/Fail** | the explicit boundary | "PASS if … FAIL if …" — never leave failure implicit |
| **Severity** | 🔴 critical · 🟠 regression · 🟡 polish | shared legend across all catalogs |
| **Source** | provenance | fix `sha` (RC-*), or `spec.md AC-n/EC-n` (TC-N), or QA finding id (`F-7`, `DEF-003`) |
| **Coverage** | where it is actually asserted | Tier-1: `path/to/Test.kt:line`; Tier-2: the `AV-*` id; `needed` if not yet automated |
| **Status** | `PEND` · `PASS` · `FAIL` · `SKIP` | RC/TC live state; SKIP when a non-committed fixture is absent |

**`Notes` is human-only.** No agent ever auto-fills a `Notes` column — it is reserved for
observations made while running the case on real hardware/screens (inherited from
`vault/_templates/test-cases.md` and `release-checklist.md`).

### Tier-2 cases carry two extra fields

Because Tier-2 is visual, an `AV-*` case adds (see [`ai-vision-scenarios.md`](ai-vision-scenarios.md)):

- **Capture** — the exact screenshots + **animation framing** to record. Stills are PNGs; any
  motion (stroke-follows-pen, page turn, zoom transition, sync propagation) is captured as an
  **ordered frame burst → GIF + PNG filmstrip** via the `tools/uitest/Capture-*Anim.ps1` encoders.
  The filmstrip is the diffable artifact; name frames `frameNNN.png` and stills `NN-<what>.png`.
- **Platform(s)** — `desktop` · `android-phone` · `android-emulator` · `android-tablet`.

---

## 4. Status & defect lifecycle

Shared across `regression-cases.md` and `vault/**/test-cases.md`.

- **Status:** `PEND` (not run) → `PASS` / `FAIL` / `SKIP`.
- **Defects:** `OPEN` → `FIXED` → `VERF`. A defect entry always references a case by ID and a fix
  `sha` when one exists. Append-only defect logs (never edit history; add a new row).
- A **flaky Tier-2 scenario is tightened, never silently dropped** — better fixture, stricter wait,
  narrower capture region. If it became golden-able, demote it to Tier 1 (§1).

---

## 5. Harness verbs allowed in Steps (Tier 2/3)

Tier-2 steps may reference **only** the real, on-disk harness. The harness on this machine is
[`tools/uitest/`](../tools/uitest/) (Windows): **desktop via computer-use**, **Android via `adb`**,
**animations via the pure-PowerShell GIF/filmstrip encoders**. (The former `notepen-desktop` MCP and
the `.claude/tools/bin/notepen-android` helper were removed — do not reference them.)

| Surface | Drive it with | Capture |
|---|---|---|
| Desktop | `tools/uitest/Launch-Desktop.ps1` to launch/locate the `NotePen` JBR window; drive via **computer-use** (`screenshot` / `left_click` / `left_click_drag` / `type` / `key` / `scroll`) after granting **`java.exe`** | `tools/uitest/Capture-DesktopAnim.ps1 -DurationMs … -Fps …` → `anim.gif` + `anim.gif.filmstrip.png` |
| Android (phone/tablet/emulator) | `tools/uitest/Start-AndroidTarget.ps1 -Serial … \| -Avd …`; drive via `adb -s <serial> shell input tap/swipe`; screenshot via `adb -s <serial> exec-out screencap -p` | `tools/uitest/Capture-AndroidAnim.ps1 -Serial … -DurationMs …` → GIF + filmstrip |
| Any PNG frames | — | `tools/uitest/Capture-Gif.ps1 -FramesDir … -Out …` |

Deterministic fallback (does **not** satisfy a Tier-2 case, only a smoke signal):
`./gradlew :reflow:impl:jvmTest --tests "*SnapshotTest"` (9 committed goldens).

See [`tools/uitest/README.md`](../tools/uitest/README.md) for the full harness reference and
fixture/path details.

---

## 6. Writing a case — the checklist

1. **Pick the lowest tier that can assert it** (§1). If Tier 1 can, it goes to Tier 1 — full stop.
2. **Pick the next free ID** in that catalog's area (§2). Never reuse a retired number.
3. **Fill every required field** (§3). Steps must be executable with real harness verbs (§5) and
   real fixtures; an explicit Pass/Fail boundary; a severity.
4. **Set Source** to its provenance (fix `sha` / spec AC / QA finding) and **Coverage** to where it
   is asserted (`Test.kt:line` / `AV-*` id / `needed`).
5. **Wire the area→case map.** Add the touched paths/globs to [`area-map.md`](area-map.md) under the
   case's area so the edit-time hook (§7) can surface the case when that code is next changed.
6. **Bump the catalog's changelog.**

---

## 7. The edit-time discipline (how Claude Code uses this)

When Claude Code touches functionality, it must run the **related** cases for the area it changed —
not the whole suite. The link from *code* to *cases* is [`area-map.md`](area-map.md): a path/glob →
area → case-ID table. A `PostToolUse` hook (`tools/uitest/related-cases-hook.ps1`, wired in
`.claude/settings.local.json`) reads the edited file path, resolves its area via that table, and
injects the related `RC-*`/`AV-*` IDs as a standing instruction. The convention is also written in
[`/TESTING.md`](../TESTING.md) § Testing discipline (a committed, durable doc — not the AI-Kit-generated
`CLAUDE.md`) so it holds even if the hook is off.

The rule: **edited a file under an area → before finishing, run that area's Tier-1 `RC-*` cases
(`./gradlew` tests they map to); if the change is visual/gesture and an `AV-*` exists, run it (or
state why it was deferred — Tier 2 is advisory).** New behavior with no case → add one per §6.

---

## Changelog

- **v1** (2026-05-30) — initial standard. Unifies the `vault/**/test-cases.md` `TC-N` format and the
  `testing/` `AV-*` / `MAN-*` catalogs under one field set, ID scheme, tier rule, and lifecycle.
  Establishes `RC-*` regression catalog and the `area-map.md` edit-time link.
