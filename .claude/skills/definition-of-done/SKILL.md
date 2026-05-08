---
name: definition-of-done
description: Canonical 7-check Definition-of-Done used by @Verifier MODE=DOD as the last gate before @Main closes a FEATURE or TECH task. Each check is binary with required evidence. Use ONLY when @Verifier MODE=DOD evaluates a feature for closure.
---

# Definition of Done (v5)

The Definition of Done is the contract between `@Main` and PO that decides whether a feature is closeable. `@Verifier MODE=DOD` walks this 7-check list, records evidence per row, and returns a binary verdict: any BLOCK → BLOCK; otherwise PASS.

This file is the **single source of truth** for what "done" means. Do not duplicate the list elsewhere — agents read it from here.

## What changed in v5

The v4 checklist had **25 binary checks across 8 groups**, with `UNVERIFIED` rows that could be waived via `/kit-approve-with-dod-waiver`. Two problems:

1. PO could not reason about 25 checks at a glance.
2. Waiver paths invited "soft pass" culture: any UNVERIFIED row in coverage / consistency / spec metadata blocked CLOSE, so PO routinely waived rather than fixing the underlying tooling gap.

v5 collapses to **7 hard checks** plus an info-only diagnostic block. There is no waiver mechanism. If a check fails, fix it.

## When to use

- `@Verifier MODE=DOD` runs this skill at `@Main` step 5.9, after `@Verifier MODE=TRACE`.
- `@Main` re-dispatches `@Verifier MODE=DOD` after every fix cycle until verdict = PASS.
- No other agent invokes this skill.

## Verdict logic

```
Per-check status:  PASS | BLOCK
Overall verdict:   any BLOCK → BLOCK
                   all PASS → PASS
```

Evidence is **mandatory** for every PASS row. "Looks fine" or "I assume" → not PASS.

## The 7 checks

Walk in order. Stop walking only when you hit BLOCK — record it and continue: the report should list every failure, not the first one only.

| # | Group | Check | Evidence required |
|---|-------|-------|-------------------|
| 1 | ACs | Every Acceptance Criterion in `spec.md` has at least one TC in `test-cases.md` whose `Verifies` cell references it AND that TC has Status PASS | List AC-id → TC-id (verbatim cell), confirm Status PASS |
| 2 | Critical ECs | Every Critical Edge Case has at least one TC with Status PASS | List EC-id → TC-id, confirm Status PASS |
| 3 | TCs state | No TC has Status PEND or FAIL | Counts of PEND and FAIL must both be zero |
| 4 | Test run | The latest `@Verifier MODE=RECONCILE` verdict was `ALL_GREEN` | Quote the verdict line from the reconcile output |
| 5 | Reviewer | The last `@Verifier MODE=REVIEW` verdict for the feature was `CLEAN` (no open CRITICAL or HIGH) | Quote the verdict line from the latest reviewer output |
| 6 | Build & lint | Build PASS + lint clean from the latest `@Verifier MODE=EXECUTE` run | Quote the build/lint result |
| 7 | Plan complete | Every step in `plan.md` § "Implementation plan" is marked done | Count `[x]` checkboxes vs total steps |

## Info-only diagnostics (non-blocking)

These appear in the DoD report but **do not** affect the verdict:

- **Coverage** (line + branch). v4 made this hard-blocking, which broke projects without a coverage tool. In v5 it is reported when a tool is configured; absence of a tool is not a block.
- **WEAK_ASSERTION flags** from `@Verifier MODE=TRACE`. Reported as info; if PO cares, they re-dispatch `@Verifier MODE=TRACE` after fixes. The blocking concerns (`MISSING_IMPL`, `ENDPOINT_ORPHAN` on Critical/High) are subsumed by checks 1, 2, and the implication of check 6 (build+test green means handlers exist).
- **Open Tech-debt entries** created during this feature. Listed for visibility; not blocking — that is what `/kit-techdebt` is for.

## Reading the inputs

`@Verifier MODE=DOD` receives:

- `SPEC_DOC` — `vault/features/<module>/<feature>/spec.md` (FROZEN — AC/EC source)
- `PLAN_DOC` — `vault/features/<module>/<feature>/plan.md` (Implementation plan checkboxes; the verdict block is written into this file's § Definition of Done — never spec.md)
- `TEST_CASES` — `vault/features/<module>/<feature>/test-cases.md`
- `LAST_RECONCILE` — verdict from the most recent `@Verifier MODE=RECONCILE`
- `LAST_REVIEW` — verdict from the most recent `@Verifier MODE=REVIEW`
- `LAST_TRACE` — verdict from the most recent `@Verifier MODE=TRACE` (info only)

If any input is missing → the corresponding check returns BLOCK. There is no soft fallback.

## Self-check before returning verdict

1. Did every check get evidence? Empty Evidence = treat as BLOCK.
2. Is any check marked PASS without evidence? Demote to BLOCK.
3. Is the verdict PASS while any check is BLOCK? Logic error — set verdict to BLOCK.

## Anti-patterns this checklist prevents

- "Tests passed, ship it" with no link from passing tests to acceptance criteria → check 1.
- "Critical EC was discussed in the spec" with no PASS test enforcing it → check 2.
- "Build green" reported by the same agent that wrote the code, never independently rerun → check 4.
- Reviewer findings ignored → check 5.
- Lint warnings or build errors silenced locally → check 6.
- Step "marked done" without code merged → check 7.

## Notes

The skill is intentionally **read-only documentation**. `@Verifier MODE=DOD` reads it as a checklist; the skill does not "execute" anything itself.

In v5 there is no `@TestExecutor`, no `@CornerCaseReviewer IMPLEMENTATION` mode, no `@CodeReviewer + @SecurityReviewer` split, and no `@ConsistencyChecker`. Their roles are subsumed:

- `@Verifier MODE=EXECUTE` + `RECONCILE` cover Group 2 of v4.
- `@Verifier MODE=REVIEW` covers Group 4 of v4 (single verdict).
- `@Architect`'s self-reflection covers Group 7 of v4 (open questions).
- `@Verifier MODE=TRACE` is unchanged but its WEAK_ASSERTION findings are info-only.
