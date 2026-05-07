---
genre: feature
title: <Feature Name>
module: <module>
feature: <feature-slug>
status: DRAFT
updated: <ISO date>
---

# <Feature Name>

**Module:** `<module>`
**Feature:** `<feature-slug>`
**Status:** DRAFT → APPROVED → DONE

---

## Why

> 2–3 sentences: what problem this solves, for whom, why now. Plain English — PO-visible.

---

## Acceptance Criteria

> BDD-style table. Every AC must be testable by a human without reading code.

| AC-N | Given | When | Then |
|------|-------|------|------|
| AC-1 | | | |

---

## Edge Cases

> Severity: Critical (data loss, security, crash) / High (silent failure, irreversible) / Medium (recoverable) / Low (cosmetic).
> Every Critical EC must have at least one TC with Status PASS.

| EC-N | Scenario | Severity | Notes |
|------|----------|----------|-------|
| EC-1 | | Critical | |

---

## How it works

> Technical specification: data models, API signatures, algorithms, state machines.
> Concrete enough that @CodeWriter can implement without guessing.

---

## Test plan

> Coverage matrix. @TestKeeper GENERATE creates test-cases.md from this section.

| TC-N | Type | Description | Verifies |
|------|------|-------------|----------|
| TC-1 | unit | | AC-1 |
| TC-2 | unit-edge | | EC-1 |

**Type:** `unit` | `unit-edge` | `integration` | `e2e` | `manual`

---

## Implementation plan

> Filled by @Main via superpowers:writing-plans.
> Each step: Goal, Owned ACs/ECs/TCs, Files, Public signatures, Guidelines.

- [ ] Step 1: (placeholder)

---

## UI / UX

> Appended by @Designer if this is a UI feature. Leave blank otherwise.

---

## Pre-mortem risks

> Appended by pre-mortem skill if invoked. Leave blank otherwise.

---

## Definition of Done

> Filled by @DoDGate at close. Leave blank until then.

---

## Open questions

> Unknowns that block implementation. @Analyst fills this; PO resolves before EXECUTE.
