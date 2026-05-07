---
genre: retro
title: Retrospective Template (per-feature, append-only)
topic: retro
triggers:
  - "retro"
  - "retrospective"
  - "post-mortem"
  - "bug report"
confidence: high
source: human
updated: 2026-05-07T17:37:42Z
---

# Retrospective — <feature>

**Module:** <module>
**Feature:** <feature-slug>
**Created:** YYYY-MM-DD

---

## How this file works

Optional per-feature file. Created on first bug fix or post-incident review for the feature; subsequent entries append below. v4 wrote each bug fix to a separate file in `guidelines/<module>/reports/`; v5 keeps everything per-feature so the history reads end-to-end.

Entries are written by `@BugFixer` after a fix, or by the `bug-retro` skill (mandatory for CRITICAL/HIGH defects).

---

## Bug fix: <name> (TC-NN, DEF-NN) — YYYY-MM-DD

**Status:** Fixed

### Description

Brief description + impact.

### Root cause

Technical breakdown. Include the abbreviated stacktrace (project lines only).

### Fix applied

What was changed.

| File | Change |
|------|--------|
| `src/...` | ... |

### Regression test

| Test file | Test name | Covers |
|-----------|-----------|--------|
| `tests/...` | `test name` | TC-NN, EC-N |

### Verification

- [x] Unit test passes
- [x] All module tests pass
- [x] @Reviewer verdict CLEAN
- [x] Build successful

### Lesson

One sentence — the pattern worth remembering.

---

(Subsequent bug fixes append below in the same shape.)
