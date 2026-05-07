---
genre: reference
title: Test Plan Template
topic: testing
triggers:
  - "test plan"
  - "qa"
  - "test cases"
confidence: high
source: human
updated: 2026-05-07T00:00:00Z
---

# Test Plan: [Feature Name]

**Module:** [module-name]
**Status:** Draft | Final
**Date:** DD.MM.YYYY
**Author:** @QA
**Spec:** [[reference/<module>/spec/<feature>]]
**Requirements:** [[concepts/<module>/requirements/<feature>]]

---

## Scope

**In scope:**
- ...

**Out of scope:**
- ...

---

## Unit Tests

| Class / Function | Test Cases | Priority |
|-----------------|------------|----------|
| `ClassName.method` | success case; empty input → error; null → guard | HIGH |
| `ClassName.method` | edge case description | MEDIUM |

---

## Integration Tests

| Scenario | Components | Test Approach | File | Priority |
|----------|------------|---------------|------|----------|
| POST /api/x → creates DB record | API + DB | test server + in-memory DB | — (Draft) | HIGH |
| GET /api/x → returns paginated list | API | test server | — (Draft) | MEDIUM |

---

## Manual Scenarios

| # | Scenario | Steps | Expected Result | Status |
|---|----------|-------|-----------------|--------|
| 1 | Happy path | 1. Do X 2. Click Y | Z happens | ⬜ |
| 2 | Error case | 1. Do A without required B | Error message shown | ⬜ |

---

## Test Environment

- DB: in-memory / test container / staging
- Auth: test credentials / mocked
- External APIs: mocked / sandbox

---

## Coverage Summary (Final only)

| Type | Written | Planned | Coverage |
|------|---------|---------|----------|
| Unit | 0 | 0 | 0% |
| Integration | 0 | 0 | 0% |
| Manual | 0 | 0 | — |
