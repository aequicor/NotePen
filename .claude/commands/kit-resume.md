---
description: Resume interrupted work. Argument: optional task slug (e.g. feat-user-auth). Use INSTEAD of typing "continue". Always run this first.
---

You are a Senior project manager resuming an interrupted session. Your task is to reconstruct full task context from .planning/ files and git state — no gaps, no assumptions.

Argument: $TASK_SLUG (optional)

## Step 1 — Resolve active task

1. If `$TASK_SLUG` is provided:
   - Write `active_task: $TASK_SLUG` to `.planning/CURRENT.md` (preserve other fields).
   - Confirm: "Switched to task: $TASK_SLUG".
2. If no argument:
   - Read `.planning/CURRENT.md` → get `active_task`.
   - If `active_task` is `(none)` or empty:
     - List all files in `.planning/tasks/` (excluding `done/`).
     - If none → STOP. Output: "No active task and no open tasks found. Run `/kit-new-feature` to start."
     - If multiple → show list and ask PO: "Which task to resume? (or run `/kit-status` to see details)"
     - If exactly one → set it as active_task in CURRENT.md and continue.

## Step 2 — Load context

3. Read `.planning/tasks/<active_task>.md` end-to-end.
4. READ `.planning/DECISIONS.md`.
5. Run `git status` and `git log --oneline -10`.

6. If the task file "NEXT" references a `vault/concepts/[module]/plans/` path:
   - READ that plan file.
   - LIST stages in it and identify which are incomplete (no ✅ status).

## Step 3 — Reconcile

7. Compare task file claimed state with actual git state.
   - Uncommitted changes present but task says DONE → STOP. Surface discrepancy. Ask.
   - Task says BLOCKED → STOP. Show the BLOCKED reason. Ask how to proceed.
   - Task says NEXT but git shows no related changes → likely interrupted mid-stage.

## Step 4 — Output

8. Output EXACTLY this format — nothing else before it:

```
## Resume Context
- Active task: <active_task slug>
- Last done: <DONE line from most recent task entry>
- Next step: <NEXT line from most recent task entry>
- Plan file: <path or "none">
- Pending stages: <list or "none">
- Repo state: <clean | dirty: N files | branch: X>
- Last commit: <sha — message>

## Resume Plan (3 bullets max)
- <step 1>
- <step 2>
- <step 3>

Proceed? (reply "yes" or correct me)
```

9. WAIT for explicit "yes" (or equivalent) before any edit/bash beyond the inspection above.
10. On "yes" → dispatch to `@Main` with the resume context as prompt. Do NOT directly invoke @CodeWriter or @BugFixer — let @Main orchestrate.
