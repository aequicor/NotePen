---
genre: ground-truth-artefact
type: mutation-sample
step: 1
commit: b756ecf9d0160d76227347fead92fb22649d7c72
date: 2026-05-09
mode: ai-fallback (one-shot, equivalent to /kit-mutate --fallback-ai)
threshold: 3
mutants_total: 5
mutants_killed: 5
mutants_survived: 0
verdict: PASS
---

# Mutation-sample report — Step 1 (pdf-eraser-tool-settings)

> AI-fallback mutation testing on Step 1 CHANGED_FILES. Each mutant is a
> targeted change to a constant / boundary / call site. Test command:
> `./gradlew :app:byCompose:common:jvmTest --rerun-tasks`. A mutant is
> KILLED if any test fails after applying the mutation; SURVIVED if all
> pass. Source files were reverted between mutants and verified green at
> the end.

## Targets

- `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/PenSettings.kt`
- `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/EraserSettings.kt`
- `app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/AnnotationRepositoryJvm.kt`

## Mutants

| # | File:line | Mutation | Expected killer TC | Result |
|---|-----------|----------|--------------------|--------|
| 1 | `PenSettings.kt:22` | `DEFAULT_STROKE_WIDTH = 10f` → `11f` (constant boundary) | TC-10 | KILLED |
| 2 | `EraserSettings.kt:18` | default `shape: EraserShape = EraserShape.CIRCLE` → `EraserShape.SQUARE` (default-value flip) | TC-11 | KILLED |
| 3 | `AnnotationRepositoryJvm.kt:24` | `tools = ToolsBundle(pen = pen, eraser = eraser)` → `tools = null` (omit persistence block) | TC-15 (`"tools"` key check) | KILLED |
| 4 | `AnnotationRepositoryJvm.kt:43` | `pen = tools.pen` → `pen = PenSettings()` (drop loaded value, fall back to defaults) | TC-17 (strokeWidth=30f / alpha=0.6f / ARGB round-trip) | KILLED |
| 5 | `AnnotationRepositoryJvm.kt:44` | `eraser = tools.eraser` → `eraser = EraserSettings()` (drop loaded value) | TC-17 (`assertEquals(eraser, bundle.eraser)` for SQUARE / 0.12) | KILLED |

## Verdict

5/5 mutants KILLED ≥ threshold 3 → PASS.

The Step-1 backend (domain models + persistence schema) is covered by
test assertions strong enough to detect:
- constant drifts (mutant 1),
- default-value flips on serializable enums (mutant 2),
- absence of the `tools` block on save (mutant 3),
- silent fallback to defaults on load (mutants 4, 5).

## Notes

- Source files restored to commit `b756ecf` state after each mutant; final
  `./gradlew :app:byCompose:common:jvmTest --rerun-tasks` BUILD SUCCESSFUL.
- AI-fallback chosen per user direction (Variant A) — no native mutation
  tool integration required. Equivalent to `/kit-mutate --fallback-ai`
  one-shot, no manifest change.
- Android impl skipped: per task-level decision (deferred Android, see
  `vault/tech-debt/common/pdf-eraser-android-impl.md`); JVM is the
  verification target for the entire feature.
