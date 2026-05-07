---
name: bug-retro
description: Post-bug retrospective — root cause analysis, prevention measures, and guideline updates
---

# Bug Retro Skill

Skill for running a retrospective after a bug fix. Goal — not just close the bug, but **prevent recurrence** through systemic changes.

## When to use

The retrospective is **mandatory** for any defect classified `CRITICAL` or `HIGH` in the Defects log of `<feature>-test-cases.md`. `@Main` dispatches it automatically at BUG pipeline step 7 — PO action is **not** required for those severities. PO can also request it explicitly for lower severities.

**Auto-trigger criteria (any one fires the skill):**

- The fixed defect's severity in the Defects log is `CRITICAL` or `HIGH` (read at fix time, not assumed from the TC type).
- A systemic problem was identified by `@BugFixer` or `@CodeReviewer` during the fix (recurrence pattern, architectural smell, missing guideline).
- The same TC has been re-opened ≥2 times in the retry counter (bug returns after a "fix").
- Bug is linked to an architectural decision, missing tests, or a guideline gap.
- PO explicitly requests it.

**Skip only if:**

- Severity is `MEDIUM` or `LOW` AND the fix is purely a copy/typo correction with no underlying systemic cause.
- The defect was created by a known external regression (library upgrade rolled back, infrastructure outage) and the fix was a revert.

If both auto-trigger and skip criteria apply, **trigger wins** — the retrospective runs.

## Retrospective Process

### Step 1: Root Cause Analysis (5 Whys)

Ask yourself the **5 Whys** questions:

| Question | Example answer |
|----------|---------------|
| What broke? | `NullPointerException` in `EmployeeRepository` |
| Why? | Field `fullName` was `null` for a new employee |
| Why? | Parser did not handle a row without a photo |
| Why? | No validation of external input |
| Why? | Guidelines have no rule for external data validation |
| Why? | Nobody documented this requirement |

**Result:** Write the root cause — not the symptom, but the systemic gap.

### Step 2: Bug Classification

| Category | Signs | Measures |
|----------|-------|---------|
| **Missing tests** | Bug not caught by tests | Write unit/integration test, add to regression suite |
| **Guideline gap** | Code written "correctly" but wrong for this project | Update/create guideline |
| **Architectural problem** | Bug caused by wrong structure | Create refactor task, update spec |
| **External dependency** | Bug from library API change | Add contract tests, update guidelines |
| **Human error** | Typo, copy-paste, inattention | Add linter rule, code review checklist |
| **Race condition / Concurrency** | Flaky bug, timing-dependent | Update concurrency guidelines, add stress test |
| **Data issue** | Bug on specific data | Add edge-case tests, data validation |

### Step 3: Action Items

For each root cause, define a specific action:

| Action type | What to do | Where to save |
|------------|------------|--------------|
| **New test** | Write unit or integration test | Mirror of src/ in test/ |
| **Update guideline** | Add rule, pattern, anti-pattern | `vault/guidelines/[module]/` |
| **New guideline** | Create document on bug topic | `vault/guidelines/[module]/` |
| **Update spec** | Clarify contract, API, data model | `vault/reference/[module]/spec/` |
| **Review checklist** | Add item to code review checklist | `vault/guidelines/[module]/code-review-checklist.md` |
| **Refactor task** | Describe tech task | Return to PO |

### Step 4: Implement Actions (priority order)

1. **CRITICAL** — Write regression test (prevents recurrence)
2. **HIGH** — Update/create guideline (prevents recurrence by others)
3. **MEDIUM** — Update spec or checklist (improves process)
4. **LOW** — Refactor task (long-term improvement)

> **Rule:** At least one "test" or "guideline" action must be completed before closing the bug.

### Step 5: Update Report

Add "Retrospective" section to the fix report (`vault/guidelines/[module]/reports/`):

```markdown
## Retrospective

**Root cause:** [description of systemic gap]
**Category:** [category from table above]

### Actions

| # | Action | Status | File |
|---|--------|--------|------|
| 1 | [What was done] | ✅ Done | [path] |
| 2 | [What is planned] | ⏳ Backlog | — |

### Lessons

- [Concrete takeaway that can be applied to other parts of the code]
```

### Step 6: Index

Call `knowledge-my-app_write_guideline` (new doc) or `knowledge-my-app_update_doc` (update) for all created/updated documents.

## Rules

1. **Minimum one action.** Cannot close bug without a test or updated guideline.
2. **Systemize, don't blame.** Retrospective improves process, not finds scapegoats.
3. **Specific over vague.** "Add null-check in Client.parseRow()" beats "Improve validation".
4. **Link to guidelines.** Every lesson must be in a document, not just the report.
5. **Tests first.** Regression test is the first action. Guidelines second.
6. **Don't inflate.** For trivial bugs (typo in a string) — regression test is enough, no need for 5 Whys.
7. **Escalate.** If root cause requires architectural changes — create task and notify PO.
