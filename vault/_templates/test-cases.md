---
genre: reference
title: Test Cases Template (living document)
topic: testing
triggers:
  - "test cases"
  - "test execution"
  - "manual testing"
  - "test run"
confidence: high
source: human
updated: 2026-05-07T00:00:00Z
---

# Test Cases: [Feature Name]

**Module:** [module-name]  
**Feature:** [feature-slug]  
**Generated:** YYYY-MM-DD by @QA  
**Spec:** `[[reference/<module>/spec/<feature>]]`  
**Requirements:** `[[concepts/<module>/requirements/<feature>]]`  

---

## How this file works

This is a **living document**. Ownership is split:

- **@QA** (REQUIREMENTS phase) — creates this file from requirements + corner cases. Fills the table with one row per TC. All Status default to `PEND`. Notes empty.
- **@QA** (IMPLEMENTATION phase, DRAFT/FINAL) — appends impl-level TCs (unit-edge, integration, error). Append-only.
- **@TestRunner** — interactive walkthrough (mode `EXECUTE`). Updates **Status only**. When a TC fails, allocates DEF-id and appends one entry to the Defects log (which references the TC by id).
- **@BugFixer** — after a fix, updates Status `FAIL → PASS` and Defects log `OPEN → FIXED` for the row it fixed.
- **Manual tester** — fills **Notes** when a TC fails (bug root cause, remarks). May also copy the `TC-00: Template` block and fill it in for any TC where elaboration helps (typically failing cases).

AI agents do NOT touch the Notes column. AI agents do NOT generate per-TC detailed sections.

`/kit-fix` reads this file, scans for `FAIL` and `PEND` rows, asks PO which to fix, dispatches @BugFixer per chosen TC, then dispatches @TestRunner (RERUN) to verify.

---

## Status legend

`PEND`  •  `PASS`  •  `FAIL`  •  `SKIP`

## Defect lifecycle

`OPEN` → `FIXED` → `VERF`

---

> Filled by AI agents. Columns AI may edit: **Status only**.
> The **Notes** column is owned by the manual tester — written when a TC fails.

| ID    | Status | Notes | Type | Description | To be |
|-------|--------|-------|------|-------------|-------|
| TC-01 | PEND   | —     |      |             |       |

> **Description** = what to test and how (one-line summary).
> **To be** = expected outcome (observable, e.g. HTTP 200 + redirect).

---

> Everything below is written by the manual tester. The `TC-00: Template`
> block stays as-is — copy and fill it in for individual TCs only when you
> want to elaborate (typically a failing case). AI agents must NOT duplicate
> the table by generating `TC-NN` sections automatically.

---

## TC-00: Template

**Description:**
what to test, how to test it

**Steps:**

1. step number 1

**As is:**
as is

**To be:**
what to be

---

## Defects log

> Append-only. Each entry references a TC by id. AI agents (@TestRunner / @BugFixer) maintain this section.

- **DEF-001** — [HIGH] *<one-line summary>*. TC-NN. Status: OPEN. Reported: YYYY-MM-DD by @TestRunner.
