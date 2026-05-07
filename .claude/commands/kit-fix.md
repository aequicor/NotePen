---
description: Fix failing/pending test cases in the current feature's test-cases.md. Argument is optional — without it, scans and asks PO. With a TC-id, fixes that one. With free-form text, creates a new TC and fixes it.
---

You are a Senior project manager routing a bug fix. Route to `@Main` for the BUG pipeline.

Argument: $ARGUMENTS (optional)

## Routing

- **No argument** → `@Main` runs SCAN mode: dispatch `@TestKeeper MODE=SCAN`, show PO the FAIL/PEND/SKIP lists, ask which to fix.
- **TC-id (e.g. TC-3)** → `@Main` routes directly to TRIAGE with that TC-id.
- **Free-form text** → `@Main` dispatches `@TestKeeper MODE=APPEND` with the description, receives new TC-id, then TRIAGE.

## BUG pipeline (v5)

```
INTAKE → SCAN (if no arg) → TRIAGE → DEBUG (if complex) → FIX (@BugFixer) →
RE-VERIFY (@TestKeeper RERUN) → CHECKPOINT → RETRO (if CRITICAL/HIGH)
```

Source of truth: `vault/features/<module>/<feature>/test-cases.md`.

PO can edit test-cases.md directly — flip Status to FAIL, add a row — and `/kit-fix` picks it up.

**Max 3 RERUN cycles per defect before escalation.**

Hand off to `@Main` with:

```
Bug fix request: $ARGUMENTS
```
