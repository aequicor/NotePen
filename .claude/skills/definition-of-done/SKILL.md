---
name: definition-of-done
description: Canonical Definition-of-Done checklist read by @DoDGate as the last gate before @Main closes a FEATURE or TECH task. Each row is a binary check with required evidence. Use ONLY when @DoDGate evaluates a feature for closure.
---

# Definition of Done

The Definition of Done is the contract between `@Main` and PO that decides whether a feature is closeable. `@DoDGate` walks this checklist row by row, records evidence per row, and returns a binary verdict: any FAIL or unwaived UNVERIFIED row → BLOCK; otherwise PASS.

This file is the **single source of truth** for what "done" means. Do not duplicate the list elsewhere — agents read it from here.

## When to use

- `@DoDGate` runs this checklist at `@Main` step 7d, after `@TraceabilityChecker` returns PASS.
- `@Main` re-dispatches `@DoDGate` (which re-reads this skill) after every fix cycle until verdict = PASS or PO waiver.
- Other agents do **not** invoke this skill — only `@DoDGate` does.

## Verdict logic

For every check below the verdict logic is:

| Per-check status | What it means | Effect on overall verdict |
|------------------|---------------|---------------------------|
| ✅ PASS | Evidence present and acceptable | contributes to PASS |
| ❌ FAIL | Evidence present and **not** acceptable | overall = BLOCK; cannot be waived |
| ⚠️ UNVERIFIED | Evidence cannot be collected (missing tool / artifact / data) | overall = BLOCK unless PO waiver in active task file |

Evidence is **mandatory** for every PASS row. "Looks fine" or "I assume" → not PASS.

## Checklist

Walk groups in order. Within a group, walk rows in order. Stop walking only when you hit a FAIL — record it and continue: the report should list every failure, not the first one only.

### Group 1 — Test cases (live document `<feature>-test-cases.md`)

| # | Check | Evidence required |
|---|-------|-------------------|
| 1.1 | Zero TC rows have Status = `PEND`, **except** Type=`manual` rows that have an explicit `dod_waiver: 1.1 — TC-NN walkthrough deferred (<reason>)` line in the active task file. Non-manual PEND rows always FAIL this check (they should have been auto-verified at step 7.2b). | Count `PEND` rows by Type. Cross-check Type=manual PEND rows against `dod_waiver:` lines. |
| 1.2 | Zero TC rows have Status = `FAIL` | Count `FAIL` rows. Must be 0. |
| 1.3 | Every Critical corner case has ≥1 TC with Status = `PASS` and an `(impl: <path>)` reference in Description | List CC-id → TC-id → impl path for each. |
| 1.4 | Every High corner case has ≥1 TC with Status = `PASS` (with impl ref) OR a `deferred:` note in the To be cell | List CC-id → TC-id (or deferred reason) for each. |
| 1.5 | Every Acceptance Criterion has ≥1 TC with Status = `PASS` | List AC-id → TC-id for each. |
| 1.6 | Defects log has zero `OPEN` entries | Grep `Status: OPEN` in Defects log. Must be 0. |

### Group 2 — Independent test run (last `@TestExecutor` report)

| # | Check | Evidence required |
|---|-------|-------------------|
| 2.1 | Last `@TestExecutor` verdict is `ALL_GREEN` | Read `Verdict:` line of latest run report. |
| 2.2 | The last run is from the **current** stage / current code (not stale) | Run report's `Changed files` overlaps with current diff; timestamp after last code commit. |
| 2.3 | Build = PASS in the last run | Read `Build:` line. |
| 2.4 | Integration tests ran (or `NOT CONFIGURED` documented in spec's Dependencies section) | Read `Integration:` line; cross-check spec. |
| 2.5 | No `NOT_RUN` rows for any in-scope TC | Read TC mapping table. |

### Group 3 — Traceability (last `@TraceabilityChecker` report)

| # | Check | Evidence required |
|---|-------|-------------------|
| 3.1 | Trace verdict is `PASS` | Read `Verdict:` of trace report. |
| 3.2 | Zero `MISSING_IMPL` rows for Critical CCs | Trace report Critical-CC table. |
| 3.3 | Zero `MISSING_IMPL` rows for High CCs (unless explicitly deferred) | Trace report High-CC table. |
| 3.4 | Zero `ENDPOINT_ORPHAN` rows | Spec endpoint → handler table. |
| 3.5 | Zero `WEAK_ASSERTION` flags on Critical/High coverage | Trace report TC ↔ test file table. |
| 3.6 | Zero `TC_ORPHAN` (TC references AC/CC that does not exist) | Trace report TC ↔ AC/CC table. |

### Group 4 — Reviews

| # | Check | Evidence required |
|---|-------|-------------------|
| 4.1 | Last `@CodeReviewer` verdict is `APPROVED` (no open CRITICAL/HIGH) | Read latest review block from active task file or last `@CodeReviewer` output. |
| 4.2 | If `@SecurityReviewer` was dispatched at any point during this feature (presence of a SecurityReviewer output in the active task file checkpoints) → its last verdict must be `APPROVED`. If `@SecurityReviewer` was never dispatched, mark this row `N/A — @Main classified the changeset as not security-relevant`; N/A counts as PASS. (Caller `@Main` knows whether `@SecurityReviewer` fired in step 7.3 — pass that signal in the prompt body to make the decision unambiguous.) | Active task file: presence + last verdict of `@SecurityReviewer` block. |
| 4.3 | No CRITICAL/HIGH `pending clarification` items | Both review outputs scanned. |

### Group 5 — Build & lint

| # | Check | Evidence required |
|---|-------|-------------------|
| 5.1 | Latest build is PASS for every affected module | Build log timestamp after last commit. |
| 5.2 | Lint clean (warnings allowed if project configures them as warnings, not errors) | Lint output. |
| 5.3 | If language has a type-checker (TypeScript, Kotlin, Rust, Python+mypy) — type-check is clean | Type-checker output. |

### Group 6 — Coverage

| # | Check | Evidence required |
|---|-------|-------------------|
| 6.1 | Line coverage on changed files ≥ project threshold (default 70%) | Coverage report; threshold from project config. |
| 6.2 | Branch coverage on changed files ≥ project threshold (default 60%) | Same. |
| 6.3 | If no coverage tool is configured for the project → mark UNVERIFIED. PO can waive 6.1/6.2 only with `dod_waiver: 6.1 — coverage tool not configured` in the active task file | active task file. |

### Group 7 — Open questions

| # | Check | Evidence required |
|---|-------|-------------------|
| 7.1 | Zero `NEEDS_PO_DECISION` markers in requirements file | grep the file. |
| 7.2 | Zero `UNRESOLVED` items in spec | grep the file. |
| 7.3 | Zero open questions in any `@CornerCaseReviewer` (BUSINESS / TECHNICAL / IMPLEMENTATION) output for this feature | Read the latest CCR outputs from active task file or pipeline log. |
| 7.4 | Zero unresolved `@ConsistencyChecker` conflicts (or all accepted via `/kit-approve-with-conflicts`) | active task file. |

### Group 8 — Plan

| # | Check | Evidence required |
|---|-------|-------------------|
| 8.1 | Every stage in the plan file is marked complete | Read each stage row's status. |
| 8.2 | Every Critical corner case from the register has a corresponding test task in some stage | Cross-check register vs stage files. |
| 8.3 | The plan-level "Stage status" table has no `pending` or `in-progress` rows | Read plan file's Stage status table. |

## PO waiver mechanism

PO can waive a specific UNVERIFIED check by adding to `.planning/tasks/<active_task>.md`:

```
- dod_waiver: <check-id> — <reason>
```

Example:
```
- dod_waiver: 6.1 — coverage tool not configured for this module yet (tracked in TD-server-coverage)
```

Rules:

- Waivers apply only to UNVERIFIED checks. **A FAIL cannot be waived** — it must be fixed.
- A waiver covers only the listed check ID. To waive multiple checks, list each separately.
- Waived rows show under "PO waivers in effect" in the DoD report; they do not vanish.
- Waivers do not persist across features — they live on the active task file and disappear when the task is archived.

## Coverage threshold configuration

Default thresholds:

| Metric | Default | Where to override |
|--------|---------|-------------------|
| Line coverage | 70% | `.claude/_shared.md` → `Coverage thresholds` table (per-project) |
| Branch coverage | 60% | same |

If the project has stricter requirements (regulated industries, life-critical code), override per-module by adding a `Coverage threshold (override)` row to the module's guideline file. `@DoDGate` reads the most specific override available.

## Self-check before returning verdict

Before `@DoDGate` writes the result block:

1. Did every row in every group get an Evidence cell? Empty Evidence = treat as UNVERIFIED.
2. Is there a row marked PASS without evidence? Demote to UNVERIFIED.
3. Is the verdict PASS while at least one row is FAIL or unwaived UNVERIFIED? Logic error — set verdict to BLOCK.
4. Is the verdict BLOCK while all rows are PASS? Logic error — recompute.

## Anti-patterns this checklist exists to prevent

- "Tests passed, ship it" with no link from passing tests to acceptance criteria. → Group 3.
- "Critical CC was discussed in the spec" with no test enforcing it in code. → Group 1.3 + Group 3.2.
- "Build green" reported by the same agent that wrote the code, never independently rerun. → Group 2.
- Security review subsumed inside the general code review and starved of attention. → Group 4.2.
- Open questions in requirements or spec carried into closed feature. → Group 7.
- Stage marked complete while one of its tasks is still `pending`. → Group 8.

## Notes

- The skill is intentionally **read-only documentation**. `@DoDGate` reads it as a checklist; the skill does not "execute" anything itself.
- If the project ships without `@TraceabilityChecker` or `@TestExecutor` (e.g. a host that did not include `quality-gates`), `@DoDGate` cannot enforce Groups 2 or 3 — it marks them UNVERIFIED and the PO must waive each row knowing the implication.
