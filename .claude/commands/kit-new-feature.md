---
description: Start a new feature, bug fix, or tech task. Argument: $FEATURE_DESCRIPTION. Optional flags (any order, before description): `--sleep` (autonomous mode — equivalent to /kit-sleep), `--risk=trivial|standard|critical` (overrides @Main's auto-classification at Step 0a). Delegates to @Main for full orchestration.
---

You are a Senior project orchestrator. Your task is to start a new feature, bug fix, or tech task and hand it off to `@Main` for full pipeline execution.

Argument: $FEATURE_DESCRIPTION

## Step 0 — Parse leading flags

Inspect the head of `$FEATURE_DESCRIPTION` for leading flags. Multiple flags allowed in any order; strip them in turn from the start of the string until no more flags. Recognised flags:

- `--sleep` (v6.1+): switch to autonomous mode. After stripping all flags, **delegate to `/kit-sleep`** with the cleaned description.
- `--risk=<value>` (v7.0.0-alpha+): explicit risk classification. Valid values: `trivial`, `standard`, `critical`. Captured as `EXPLICIT_RISK`; passed to @Main below.
- `--risk trivial|standard|critical` (alternative space-separated form): same effect as `--risk=<value>`.

After parsing, `FEATURE_DESCRIPTION_CLEAN` = original string with all leading flags stripped, leading/trailing whitespace removed.

If `--sleep` was set:
- Output: "Detected --sleep flag, delegating to /kit-sleep..." (with risk note if --risk was also set: "Risk: <EXPLICIT_RISK>")
- v7.0.0-alpha+: if `EXPLICIT_RISK == critical` AND `manifest.lanes.critical_block_sleep: true` (default) → STOP. Output: "Critical risk + sleep mode is the highest blast-radius combination. Refused per manifest.lanes.critical_block_sleep. Run interactively (drop --sleep) or override manifest."
- Else → invoke /kit-sleep semantics (pass FEATURE_DESCRIPTION_CLEAN and EXPLICIT_RISK).

If `--sleep` was not set → proceed with the interactive flow below.

## Interactive flow

Hand off to `@Main` with the following prompt:

```
New task: <FEATURE_DESCRIPTION_CLEAN>

Type: FEATURE (or clarify if this is BUG/TECH)
Mode: interactive
Risk: <EXPLICIT_RISK or "(auto-classify)">    # v7.0.0-alpha+; @Main resolves at Step 0a
```

@Main routes through the matching lane variant (trivial / standard / critical) per `kit/_shared/agents/Main.body.md.template` § "Lane pipeline variants". Lanes are mandatory in v7.0.0+; the v7.0.0-alpha `lanes.enabled: false` opt-out is gone.

`@Main` will execute the v6 FEATURE pipeline:

1. **CLASSIFY & CLARIFY** — minimal questions (module, description, UI?, constraints). Records `start_commit` for the diff-review at step 5.10.
2. **ANALYSIS** — dispatch `@Architect` to write `spec.md` (Why / ACs / Edge Cases / How it works / Test plan / UI section if applicable — FROZEN at CONFIRM) plus a `plan.md` skeleton; then `@Verifier MODE=GENERATE` (input: spec.md) to create the live `test-cases.md`.
3. **PLAN** — `superpowers:writing-plans` writes the Implementation plan into `plan.md`. UI section is already part of spec.md from step 2 (no separate Designer dispatch). `@Verifier MODE=DRAFT` adds impl-level TCs to `test-cases.md`.
3a. **SLICE-CAP CHECK (P1)** — @Main reads `manifest.slice_caps`; if step count or per-step file count exceeds caps, asks PO to split before EXECUTE.
4. **CONFIRM** — show summary; wait for `/kit-approve` (or auto-proceed if `auto_approve.feature: true`). On PASS, `spec.md` is FROZEN.
5. **EXECUTE** — for each step in `plan.md`: `@CodeWriter` (TDD-first by default; configurable via `manifest.test_strategy`) → `@Verifier MODE=EXECUTE` → `@Verifier MODE=REVIEW` (Pass A–E + adversarial A* on Critical-EC steps) → fix loop → P11 unchanged-call-sites quick check → mark step done with diff-stat in plan.md (P6). After last step: `@Verifier MODE=RECONCILE` → `@Verifier MODE=TRACE` → `@Verifier MODE=DOD` (7 hard checks; verdict written to `plan.md`).
5.10. **DIFF-REVIEW (P2)** — @Main runs `git diff --stat` over the EXECUTE range, fills `plan.md § Diff-review`, waits for PO unless `auto_approve.diff_review: true` (separate from feature/tech/bug auto-approves; default false).
6. **CLOSE** — gated on `@Verifier MODE=DOD = PASS` AND step 5.10 = APPROVED. Update guidelines if patterns emerged.

**Output format:** After handoff, output ONLY the task type confirmation and the first clarifying question from `@Main`. No introductory text.

**Do not call `@CodeWriter`, `@BugFixer`, or other subagents directly — only `@Main`.**

## v6.1 sleep variant

Run `/kit-sleep "<description>"` (or `/kit-new-feature --sleep "<description>"`) when PO will be away during execution and wants autonomous run + a morning report. See `/kit-sleep` for full semantics. The two commands are equivalent at the @Main contract level: both set `.planning/CURRENT.md.mode: sleep` and run the autonomous pipeline.
