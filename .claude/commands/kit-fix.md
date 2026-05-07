---
description: Fix failing/pending test cases in the current feature's test-cases.md. Argument is optional — without it, scans and asks PO. With a TC-id, fixes that one. With free-form text, creates a new TC and fixes it.
---

You are `@Main` routing a bug-fix request through the BUG pipeline. Argument: $ISSUE (optional).

The single source of truth is the live test-cases file:

```
vault/features/<module>/<feature>/test-cases.md
```

PO marks Status `FAIL` for known bugs and may add new TC rows there at any time. `/kit-fix` reads that file and acts on it. The BUG pipeline definition lives in `@Main` — this command is the entry point that figures out which TC(s) to feed it.

## Routing

1. **No argument** → SCAN mode:
   - Read `.planning/CURRENT.md` → get `active_task` → read `.planning/tasks/<active_task>.md` to find the current feature + module.
   - Dispatch `@TestKeeper MODE=SCAN` on the test-cases file.
   - Show PO the FAIL / PEND / SKIP lists (highlight PO-added rows).
   - Ask PO: "Fix all failing? Pick TC-ids? Or none?"
   - For each chosen TC-id → enter BUG pipeline.

2. **Argument matches `TC-\d+`** → direct TC mode:
   - Read that row from the test-cases file.
   - Enter BUG pipeline at TRIAGE with the TC-id.

3. **Argument is free-form text** → APPEND-then-fix mode:
   - Dispatch `@TestKeeper MODE=APPEND` with the text.
     The agent allocates the next TC id, sets `Status=FAIL`, fills `Description` and `Notes` from the text, allocates a DEF entry.
   - Enter BUG pipeline at TRIAGE with the new TC-id.

## BUG pipeline (per TC-id, executed by @Main)

```
TRIAGE   — clear stacktrace / self-evident steps → DISPATCH directly.
           complex / needs reproduction → DEBUG first.

DEBUG    — dispatch @BugFixer MODE=debug with TC-id + test-cases path.
           BugFixer in debug mode is read-only: produces a root-cause hypothesis +
           a failing test that pins the bug. Then re-dispatch as MODE=fix.

FIX      — dispatch @BugFixer MODE=fix.
           BugFixer: ANALYZE → REPRODUCE (failing test) → FIX → REGRESSION TEST →
             dispatch @Reviewer (single read-only pass: code + security + stub-scan)
             → BUILD → update test-cases.md (Status FAIL→PASS, Defects log OPEN→FIXED)
             → commit → append to vault/features/<module>/<feature>/retro.md.

RE-VERIFY — dispatch @TestKeeper MODE=RERUN with the TC-id.
           PO confirms PASS → DEF promoted FIXED → VERF.
           PO confirms FAIL → Status reverts, retry counter incremented. Max 3 retries per DEF.

REPORT   — to PO: list of TCs fixed (FAIL→PASS), defects closed (DEF-ids), link to retro.md.

RETRO    — call `bug-retro` skill if defect severity is CRITICAL or HIGH (auto-trigger).
           For MEDIUM/LOW, only on PO request.
```

## Stop rules

- **Max 2 fix attempts per same compile/test error** inside `@BugFixer` → STOP, escalate to PO with full error history.
- **Max 3 RERUN cycles per defect** → STOP, escalate to PO.
- **No active task in CURRENT.md or no test-cases file**, and no argument given → STOP. Tell PO: "No active feature. Run `/kit-new-feature` first, or pass a TC-id or description directly."

## Build verification commands (used by @BugFixer)

- Compile: `./gradlew compileKotlin`
- Lint:    `./gradlew detekt ktlintCheck`
- Tests:   `./gradlew :[module]:test`
