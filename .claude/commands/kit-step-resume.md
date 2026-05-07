---
description: v6.1+ resume into a specific EXECUTE step in a clean session. Argument optional — defaults to the next pending step in the active task. Use after /clear when session_isolation=per_step prompts you to start step N+1.
---

You are a Senior project manager re-entering an active feature mid-EXECUTE. Your task is to assemble a *focused* per-step bundle — narrower than `/kit-resume` — and hand it off to `@Main` so it can immediately enter step 5.1 READ for the named step. Do not re-do CLASSIFY / ANALYSIS / PLAN / CONFIRM — those phases already completed; the active step is `current_step_idx + 1` (or `current_step_idx` if `/kit-defect` re-opened the same step).

Argument: $STEP_INDEX (optional integer; default = `current_step_idx + 1` from `.planning/tasks/<active_task>.md`)

## Step 1 — Resolve active task and target step

1. Read `.planning/CURRENT.md`:
   - If `active_task` is `(none)` → STOP. Output: "No active task. Run `/kit-resume` to switch tasks or `/kit-new-feature` to start one."
   - If `mode: sleep` AND `status: SLEEP_BLOCKED` → STOP. Output: "Active task is sleep-blocked. Read `.planning/MORNING_REPORT.md` first, then run `/kit-resume` to switch to interactive."
   - Otherwise note `active_task` and `mode`.

2. Read `.planning/tasks/<active_task>.md`:
   - If `current_step_idx` field is missing (legacy v6.0 task) → fall back to `/kit-resume` semantics: invoke its full reconstruction logic instead. Output one-line note: "Legacy task without per-step state — running /kit-resume."
   - Otherwise note `current_step_idx`, `step_commits[]`, `status`.

3. Resolve target step:
   - If `$STEP_INDEX` provided → use it.
   - Else if `step_commits[<current_step_idx>].superseded == true` → target = `current_step_idx` (the step was re-opened by `/kit-defect`).
   - Else → target = `current_step_idx + 1` (next pending step).
   - If target > total steps in plan.md § Implementation plan → STOP. Output: "All plan steps are done. Run `/kit-approve` to enter step 5.7 RECONCILE / 5.10 diff-review / CLOSE."

## Step 2 — Build the focused bundle

4. Read the feature paths from the task file (spec.md, plan.md, test-cases.md).

5. Extract per-step slice:
   - From `plan.md § Implementation plan` — the bullet block for the target step ONLY (Goal, Owned, Files, Public signatures, Guidelines, Test strategy, Runnable). Skip other steps.
   - From `spec.md § ACs / § Edge Cases` — only the rows referenced in the target step's `Owned` field. Do NOT load the entire AC/EC tables.
   - From `spec.md § How it works` — only subsections that mention symbols / paths from the step's `Files` line.
   - From `test-cases.md` — rows whose `Verifies` cell references the target step's Owned ids.

6. Read previous step's anchor (if `target > 1`):
   - Look up `step_commits[target - 1]` in the task file.
   - Note: sha, changed_files, runbook (verbatim block).

7. Read `.planning/REPO_MAP.md` if it exists (mtime < 7 days). Skip with a one-line note if older / missing — agent falls back to serena.

8. If `.planning/.session-bootstrap.md` exists, read it (it should contain a fresh briefing written by SessionStart hook on `/clear`). Use it to confirm the resolved step matches what the hook expected — if mismatch, prefer the resolved step from the task file (source of truth) and surface the discrepancy as a one-line note.

## Step 3 — Reconcile against git

9. Run `git status --porcelain` and `git rev-parse HEAD`.
10. Sanity checks (output as warnings, do NOT block):
    - HEAD sha != `step_commits[target - 1].sha` (or `start_commit` if target == 1) → "Working copy is ahead of last green step; the next step will commit on top of <head>".
    - `git status` shows uncommitted changes → "Uncommitted changes detected: <list>. They will be included in the next step's commit (5.4b)."

## Step 4 — Output

11. Output EXACTLY this format — nothing else before it:

```
## Step Resume — task <active_task>, step <target>

### Active step (target)
- Goal: <step.Goal>
- Owned: <Owned ACs/ECs/TCs>
- Files: <step.Files>
- Test strategy: <tdd_first | test_after | mixed>
- Runnable: <step.Runnable line>

### Previous step (anchor for diff)
- Step <target - 1>, sha <sha>, files: <changed_files count>
- Runbook (for regression check):
  <verbatim runbook block from step_commits[target - 1].runbook, or "(no runbook — legacy step)" if missing>

### Spec slice
- ACs in scope: <list with one-line summaries>
- ECs in scope: <list with one-line summaries>
- "How it works" subsections: <names, by symbol/path>

### Test cases in scope
- TC-<id>: <Status> — <one-line summary>
- ...

### Repo state
- HEAD: <sha — message>
- Uncommitted: <count or "clean">
- REPO_MAP: <fresh | stale (mtime <date>) | missing>

### Mode
- Task mode: <interactive | sleep>
- session_isolation.mode: <effective>

### Resume Plan (3 bullets max)
- Hand off to @Main with this bundle as STEP_CONTEXT
- @Main enters 5.1 READ for step <target>
- (if defect previously reported) @Main treats step as re-opened (skip 5.1, jump to 5.2)

Proceed? (reply "yes" or correct me)
```

12. WAIT for explicit "yes" before any edit/bash beyond the inspection above.

13. On "yes" → dispatch to `@Main` with the bundle as the prompt, instructing it to enter 5.2 WRITE for the target step (5.1 READ context is already loaded). Do NOT directly invoke `@CodeWriter` or `@Reviewer` — let `@Main` orchestrate.

## What NOT to do

- DO NOT run /kit-resume's full task-file dump — this command is narrower by design. Skip DECISIONS.md, skip non-target plan steps, skip ACs not in `Owned`.
- DO NOT regenerate REPO_MAP automatically — surface staleness, let PO decide. Stale REPO_MAP is fine as a starting hint; serena is authoritative.
- DO NOT proceed to dispatch @Main until PO confirms with "yes". The 3-way fork at 5.6 also relies on PO confirmation; this command preserves that contract.
- DO NOT run in sleep mode if `status: SLEEP_BLOCKED` — read MORNING_REPORT.md first.
