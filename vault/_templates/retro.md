---
genre: reference
title: Bug Retro Template
topic: retrospective
confidence: high
source: kit
updated: 2026-05-07T00:00:00Z
---

# Retrospective — <feature>

> Per-feature, append-only retrospective for bug fixes.
> Written by `@BugFixer` or `bug-retro` skill. Mandatory for CRITICAL/HIGH severity defects.

---

## Retro — TC-NN — <ISO timestamp>

**Defect:** <one-line summary>
**Severity:** CRITICAL / HIGH / MEDIUM / LOW
**Root cause:** <result of 5 Whys — specific file:line>
**Classification:** missing-test / guideline-gap / architectural-issue / external-regression / other

### Fix applied

| File | Change |
|------|--------|
| `src/path/to/File.kt` | <what changed> |

### Regression test

TC-NN → `tests/path/to/FileTest.kt:42`

### Actions taken

| Action | Type | Done? |
|--------|------|-------|
| Added TC-NN regression test | test | yes |
| Added pattern to vault/guidelines/ | guideline | yes |

### Lesson

<One sentence: what this teaches about the codebase.>

---

<!-- Add new retro entries above this line, newest last -->
