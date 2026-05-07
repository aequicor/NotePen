---
description: Start a new feature, bug fix, or tech task. Argument: $FEATURE_DESCRIPTION (optionally prefixed with `--sleep` to enter autonomous mode — equivalent to /kit-sleep). Delegates to @Main for full orchestration.
---

You are a Senior project orchestrator. Your task is to start a new feature, bug fix, or tech task and hand it off to `@Main` for full pipeline execution.

Argument: $FEATURE_DESCRIPTION

## Step 0 — Detect --sleep flag (v6.1+)

Check if `$FEATURE_DESCRIPTION` starts with `--sleep` (with any whitespace before/after):

- If yes → strip the `--sleep` flag and any leading/trailing whitespace from the description, then **delegate to `/kit-sleep`** with the cleaned description as its argument. Do NOT proceed with the interactive flow below.

  Output: "Detected --sleep flag, delegating to /kit-sleep..."

  Then invoke /kit-sleep semantics (read `kit-sleep.md` and follow it). Stop after that.

- If no → proceed with the interactive flow below.

## Interactive flow

Hand off to `@Main` with the following prompt:

```
New task: $FEATURE_DESCRIPTION

Type: FEATURE (or clarify if this is BUG/TECH)
Mode: interactive
```

`@Main` will execute the v6 FEATURE pipeline:

1. **CLASSIFY & CLARIFY** — minimal questions (module, description, UI?, constraints). Records `start_commit` for the diff-review at step 5.10.
2. **ANALYSIS** — dispatch `@Analyst` to write `spec.md` (Why / ACs / Edge Cases / How it works / Test plan / UI section if any — FROZEN at CONFIRM) plus a `plan.md` skeleton; then `@TestKeeper MODE=GENERATE` (input: spec.md) to create the live `test-cases.md`.
3. **PLAN** — `superpowers:writing-plans` writes the Implementation plan into `plan.md`. UI features → `@Designer` appends `## UI / UX` to `spec.md` BEFORE CONFIRM freezes it. `@TestKeeper MODE=DRAFT` adds impl-level TCs to `test-cases.md`.
3a. **SLICE-CAP CHECK (P1)** — @Main reads `manifest.slice_caps`; if step count or per-step file count exceeds caps, asks PO to split before EXECUTE.
4. **CONFIRM** — show summary; wait for `/kit-approve` (or auto-proceed if `auto_approve.feature: true`). On PASS, `spec.md` is FROZEN.
5. **EXECUTE** — for each step in `plan.md`: `@CodeWriter` (TDD-first by default; configurable via `manifest.test_strategy`) → `@TestKeeper EXECUTE` → `@Reviewer` (Pass A–D + adversarial A* on Critical-EC steps) → fix loop → P11 unchanged-call-sites quick check → mark step done with diff-stat in plan.md (P6). After last step: `@TestKeeper RECONCILE` → `@TraceabilityChecker` → `@DoDGate` (7 hard checks; verdict written to `plan.md`).
5.10. **DIFF-REVIEW (P2)** — @Main runs `git diff --stat` over the EXECUTE range, fills `plan.md § Diff-review`, waits for PO unless `auto_approve.diff_review: true` (separate from feature/tech/bug auto-approves; default false).
6. **CLOSE** — gated on `@DoDGate = PASS` AND step 5.10 = APPROVED. Update guidelines if patterns emerged.

**Output format:** After handoff, output ONLY the task type confirmation and the first clarifying question from `@Main`. No introductory text.

**Do not call `@CodeWriter`, `@BugFixer`, or other subagents directly — only `@Main`.**

## v6.1 sleep variant

Run `/kit-sleep "<description>"` (or `/kit-new-feature --sleep "<description>"`) when PO will be away during execution and wants autonomous run + a morning report. See `/kit-sleep` for full semantics. The two commands are equivalent at the @Main contract level: both set `.planning/CURRENT.md.mode: sleep` and run the autonomous pipeline.
