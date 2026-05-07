---
genre: guideline
title: Bug Report Template
topic: debugging
triggers:
  - "bug report"
  - "root cause"
  - "regression test"
confidence: high
source: human
updated: 2026-05-07T00:00:00Z
---

# Bug Fix Report: [Bug Name]

**Date:** DD.MM.YYYY
**Author:** @BugFixer
**Status:** Fixed
**Module:** [module-name]
**Priority:** Critical | High | Medium | Low

---

## Bug Description

Brief description of the bug and its user-visible impact.

---

## Root Cause

Technical breakdown of why the bug occurred.

```
<abbreviated stacktrace — only project lines>
```

Explanation of the root cause: what failed, why, and where.

---

## Fix Applied

Description of what was changed and why this fixes the root cause.

### Files Changed

| File | Change |
|------|--------|
| `path/to/file.ext` | Brief description of change |

---

## Regression Test

| Test File | Test Name | What it covers |
|-----------|-----------|----------------|
| `path/to/TestFile` | `test name` | Verifies the exact scenario that caused the bug |

---

## Verification

- [x] Failing test written before fix
- [x] Test now passes after fix
- [x] All module tests pass
- [x] Code review approved (no CRITICAL/HIGH issues)
- [x] Build successful
- [x] Lint passes

---

## Lessons Learned

- Concrete takeaway that can be applied to prevent similar bugs elsewhere

---

## Retrospective

*(filled by @Main using bug-retro skill)*

**Root cause category:** Missing tests | Guideline gap | Architectural problem | External dependency | Human error | Race condition | Data issue

**Root cause:** [systemic gap description]

### Actions

| # | Action | Status | File |
|---|--------|--------|------|
| 1 | Write regression test | ✅ Done | [path] |
| 2 | Update guideline | ⏳ Planned | — |
