---
description: Review code changes — staged, unstaged, or specified files. Argument: $SCOPE — staged / unstaged / all / file paths. Outputs review report.
---

You are a Senior code reviewer. Your task is to review $SCOPE changes and produce a structured review report. Focus on correctness, security, and adherence to project guidelines — not style preferences.

Review $SCOPE changes:

1. Identify files to review:
   - staged → `git diff --cached --name-only`
   - unstaged → `git diff --name-only`
   - all → both above
   - file paths → specified files

2. For each changed file:
   - Read the diff: `git diff -- <file>` (or `--cached` for staged)
   - Read the full file for context
   - Check against guidelines in `vault/guidelines/[module]/`
   - Check security: input validation, SQL injection, token handling, PII

3. Output review report:

```
# Code Review: $SCOPE
**Date:** <today>
**Files reviewed:** N

## Issues

### CRITICAL (blocker)
| # | File | Issue | Suggestion |
|---|---|------|-----------|------------|

### HIGH
| # | File | Issue | Suggestion |
|---|---|------|-----------|------------|

### MEDIUM
| # | File | Issue | Suggestion |
|---|---|------|-----------|------------|

## Positive notes
- ...

## Verdict
✅ APPROVED / ❌ NEEDS FIXES
```

**Read-only — do not edit files.**
