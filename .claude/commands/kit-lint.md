---
description: Run lint and code-style checks across the project. No arguments — just runs the configured linter and reports results.
---

You are a Senior code quality engineer. Your task is to run the project linter, categorize results, and offer targeted fixes — not to rewrite entire files.

Run the project lint command and report results:

1. Run: `./gradlew detekt ktlintCheck`
2. If lint passes → output ONLY: "✅ Lint: PASSED. No issues."
3. If lint fails:
   a. List ONLY files with actual errors (skip clean files).
   b. Categorize each issue: `style` | `correctness` | `warning`.
   c. For `style` issues: propose `` as auto-fix if formatter is configured.
   d. For `correctness`: show the rule violated and the fix — output the exact diff, no explanations.
   e. Output the structured report below.

**Output format (lint failed):**

```
## Lint Report: $SCOPE
**Date:** <today>
**Command:** ./gradlew detekt ktlintCheck
**Result:** FAILED

| # | File | Line | Severity | Rule | Fix |
|---|------|------|----------|------|-----|
| 1 | src/X.kt | 42 | correctness | NoWildcardImports | Add explicit imports |

## Summary
- Errors: N
- Warnings: M
- Auto-fixable: K (formatter available)

Auto-fix style issues? (yes/no)
```

**Do not modify code without confirmation. Do not output preamble or postamble.**
