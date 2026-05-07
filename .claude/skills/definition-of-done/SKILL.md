---
name: definition-of-done
description: Canonical 7-check Definition-of-Done checklist read by @DoDGate as the last gate before @Main closes a FEATURE or TECH task. Each row is a binary check with required evidence. No waiver mechanism in v5.
---

# Definition of Done (v5)

The Definition of Done is the contract between `@Main` and PO that decides whether a feature is closeable. `@DoDGate` walks this 7-check list, records evidence per row, and returns a binary verdict: any BLOCK → BLOCK; otherwise PASS.

This file is the **single source of truth** for what "done" means. Do not duplicate the list elsewhere.

## What changed in v5

The v4 checklist had **25 binary checks across 8 groups**, with `UNVERIFIED` rows that could be waived. v5 collapses to **7 hard checks** plus an info-only diagnostic block. There is no waiver mechanism. If a check fails, fix it.

## When to use

- `@DoDGate` runs this skill at `@Main` step 5.9, after `@TraceabilityChecker`.
- `@Main` re-dispatches `@DoDGate` after every fix cycle until verdict = PASS.
- No other agent invokes this skill.

## Verdict logic

```
Per-check status:  PASS | BLOCK
Overall verdict:   any BLOCK → BLOCK
                   all PASS → PASS
```

Evidence is **mandatory** for every PASS row. "Looks fine" or "I assume" → not PASS.

## The 7 checks

Walk in order. Record every failure — do not stop at the first BLOCK.

| # | Group | Check | Evidence required |
|---|-------|-------|-------------------|
| 1 | ACs | Every Acceptance Criterion in `feature.md` has at least one TC in `test-cases.md` whose `Verifies` cell references it AND that TC has Status PASS | List AC-id → TC-id (verbatim cell), confirm Status PASS |
| 2 | Critical ECs | Every Critical Edge Case has at least one TC with Status PASS | List EC-id → TC-id, confirm Status PASS |
| 3 | TCs state | No TC has Status PEND or FAIL | Counts of PEND and FAIL must both be zero |
| 4 | Test run | The latest `@TestKeeper RECONCILE` verdict was `ALL_GREEN` | Quote the verdict line from the reconcile output |
| 5 | Reviewer | The last `@Reviewer` verdict for the feature was `CLEAN` (no open CRITICAL or HIGH) | Quote the verdict line from the latest reviewer output |
| 6 | Build & lint | Build PASS + lint clean from the latest `@TestKeeper EXECUTE` run | Quote the build/lint result |
| 7 | Plan complete | Every step in `feature.md` § "Implementation plan" is marked done | Count `[x]` checkboxes vs total steps |

## Info-only diagnostics (non-blocking)

These appear in the DoD report but **do not** affect the verdict:

- **Coverage** (line + branch). Reported when a tool is configured; absence is not a block.
- **WEAK_ASSERTION flags** from `@TraceabilityChecker`. Reported as info.
- **Open Tech-debt entries** created during this feature.

## Reading the inputs

`@DoDGate` receives:

- `FEATURE_DOC` — `vault/features/<module>/<feature>/feature.md`
- `TEST_CASES` — `vault/features/<module>/<feature>/test-cases.md`
- `LAST_RECONCILE` — verdict from the most recent `@TestKeeper RECONCILE`
- `LAST_REVIEW` — verdict from the most recent `@Reviewer`
- `LAST_TRACE` — verdict from the most recent `@TraceabilityChecker` (info only)

If any required input is missing → the corresponding check returns BLOCK.

## Self-check before returning verdict

1. Did every check get evidence? Empty Evidence = treat as BLOCK.
2. Is any check marked PASS without evidence? Demote to BLOCK.
3. Is the verdict PASS while any check is BLOCK? Logic error — set verdict to BLOCK.

## Notes

The skill is intentionally **read-only documentation**. `@DoDGate` reads it as a checklist; the skill does not execute anything itself.

In v5 there is no `@TestExecutor`, no `@CornerCaseReviewer IMPLEMENTATION` mode, no `@CodeReviewer + @SecurityReviewer` split, and no `@ConsistencyChecker`. Their roles are subsumed by `@TestKeeper`, `@Reviewer`, and `@Analyst`.
