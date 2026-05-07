---
genre: feature-plan
title: Feature Plan Template (mutable across EXECUTE)
topic: feature
triggers:
  - "implementation plan"
  - "feature plan"
  - "DoD"
confidence: high
source: human
updated: 2026-05-07T17:37:42Z
---

# Implementation plan & DoD — <feature>

> Spec: ./spec.md (FROZEN at CONFIRM)
> Test cases (live): ./test-cases.md
> Status: PLANNING | EXECUTING | DONE

<!--
  This file is mutable across EXECUTE. v6.1 rule: only @Main, @CodeWriter
  (via @Main), replan-on-discovery skill, and @DoDGate edit it. The sibling
  spec.md is FROZEN at CONFIRM and is the immutable contract this plan
  implements.

  Plan amendments (replan-on-discovery) write `<!-- REPLAN-N -->` markers in
  § Implementation plan only. They never touch spec.md.

  v6.1 — § Step-level diff stats was REMOVED. The single source of truth
  for per-step diff history is now `.planning/tasks/<slug>.md.step_commits[]`,
  which @Main appends at 5.4b COMMIT. plan.md's § Diff-review (5.10) reads
  that history to render its summary; do NOT also write a per-step table here.
-->

## Slice budget (filled by @Main at PLAN)

| Cap | Limit (from manifest.slice_caps) | Current |
|-----|----------------------------------|---------|
| max_steps | <N> | <count> |
| max_files_per_step (any step) | <N> | <max observed> |
| max_lines_per_step (any step) | <N> | <max observed> |

If any "Current" exceeds its limit at CONFIRM, @Main asks PO to split before EXECUTE.

## Implementation plan

Filled by `@Main` via `superpowers:writing-plans` after spec.md is approved. Each step is a contract:

```
- [ ] Step 1: <goal>
      Owned ACs/ECs/TCs: AC-1, EC-1, TC-1, TC-3
      Files: src/orders/Service.kt, src/orders/Repository.kt
      Public signatures:
        - fun saveOrder(o: Order): Long
      Guidelines: [[guidelines/<module>/transactions]]
      Test strategy: tdd_first | test_after | mixed     <!-- v6.0+, optional override; otherwise inherits manifest.test_strategy -->
      Runnable: <one line — what PO will see in a local dev run after this step lands;
                 OR "internal — <reason>" iff manifest.allow_internal_steps=true>     <!-- v6.1+ runnable-slice gate -->

- [ ] Step 2: <goal>
      ...
```

Steps live as sections inside this file, not as separate stage-NN.md files.

`Runnable:` is mandatory for every step (v6.1+). At step 3a SLICE-CAP CHECK, @Main BLOCKs PLAN if any step is missing this field. If `manifest.allow_internal_steps: true`, `Runnable: internal — <reason>` is allowed; otherwise such a value also BLOCKs and PO is asked to split the step into a vertical slice.

## Replan log

Replan amendments appear here as `<!-- REPLAN-N -->` HTML-comment markers paired with new step bullets. Hard cap from `replan-on-discovery` skill: max 2 replan events per feature in interactive mode (max 4 in sleep mode), max 3 new steps per event.

## Diff-review (P2 — filled at step 5.10, before CLOSE)

| Field | Value |
|-------|-------|
| Total files changed | <N> |
| Total +/-lines | +<A> / -<B> |
| Files NOT in any step.Files declaration | <list, or (none)> |
| Out-of-module touches | <list, or (none)> |
| PO verdict | APPROVE | REVERT <files> | REWORK <reason> |

### Per-step diff (v6.1+, optional detailed block)

When `manifest.auto_commit_per_step: true` (default), @Main also lists per-step diff using `git diff --stat <step_commits[N-1].sha>..<step_commits[N].sha>` for finer-grained PO inspection:

| Step | Sha | Files | +/-lines |
|------|-----|-------|----------|
| 1 | abc123 | 3 | +180/-22 |
| 2 | def456 | 5 | +210/-40 |

Default verdict gate for CLOSE. `auto_approve.diff_review: true` bypasses this — do not enable lightly. Sleep mode (`.planning/CURRENT.md.mode: sleep`) auto-approves with a "auto-approved (sleep mode)" mark in the task file's step_commits[].

## Definition of Done

Filled by `@DoDGate` at CLOSE. Walked verdict from the canonical 7-check list (see `definition-of-done` skill). Left empty until then.
