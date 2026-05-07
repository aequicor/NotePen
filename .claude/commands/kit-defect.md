---
description: v6.1+ report a defect found during manual verification at 5.6 CHECKPOINT. Argument: $DEFECT_DESCRIPTION (1–3 lines). Re-opens the current step, adds a failing TC, and returns the pipeline to 5.2 WRITE so @Main + @CodeWriter can fix it.
---

You are a Senior project manager processing PO-reported defects. Your task is to convert PO's defect report into a structured re-open of the current EXECUTE step. Do NOT touch git history (the existing step commit stays as the "before fix" baseline); do NOT skip @Reviewer / @TestKeeper for the re-run (the new commit must pass the same gates).

Argument: $DEFECT_DESCRIPTION (mandatory; 1–3 lines describing what PO found broken)

## Step 1 — Resolve target step

1. Read `.planning/CURRENT.md`:
   - If `active_task` is `(none)` → STOP. Output: "No active task. /kit-defect operates on the step that just completed at 5.6."
   - If `mode: sleep` → STOP. Output: "Sleep mode is autonomous; defects are caught by the self-validation loop. If you woke up and want to report a manual finding, run `/kit-resume --interactive` first to switch out of sleep, then `/kit-defect`."
   - If `status: SLEEP_BLOCKED` → STOP. Output: "Task is sleep-blocked. Resolve the block first (read MORNING_REPORT.md), then run /kit-resume."

2. Read `.planning/tasks/<active_task>.md`:
   - If `current_step_idx` is missing or `0` → STOP. Output: "No completed step to attach defect to. /kit-defect runs after 5.6 CHECKPOINT, not before."
   - Note `current_step_idx` (= the step that just machine-greened — the target).
   - Read `step_commits[<current_step_idx>]`. If `defect_count >= 3` → STOP. Output: "Step <N> has accumulated 3 PO-reported defects. Consider /kit-revert-step or replan via /kit-rework."

3. Confirm $DEFECT_DESCRIPTION is non-empty and ≥ 10 chars. If empty / too short → STOP. Output: "Defect description is required (1–3 lines describing what's broken). Example: `/kit-defect Save button stays disabled after entering valid email.`"

## Step 2 — Update test-cases.md

4. Read the active feature's `test-cases.md` (path is in the task file).

5. Compute next TC id (highest existing TC-N + 1).

6. Append a new TC row:

   ```
   TC-<next_id> | <module> | <step.Owned[0] or "AC-N/A"> | <DEFECT_DESCRIPTION> | FAIL | (PO-reported)
   ```

   - `module` = step's module from plan.md.
   - `Verifies` cell = step's first owned AC/EC id (so RECONCILE picks it up); fallback "AC-N/A" if step has no Owned.
   - `Status` = FAIL.
   - Source column = `(PO-reported)` literal.

7. Append a new entry to test-cases.md `## Defects log`:

   ```
   - **TC-<next_id>** (severity: <auto-derived>, status: OPEN)
     - Reported: <ISO timestamp>
     - Source: PO at 5.6 manual verification
     - Description: <DEFECT_DESCRIPTION>
   ```

   Severity derivation:
   - If step's Owned contains any Critical EC → `high`
   - Otherwise → `medium`

## Step 3 — Update plan.md

8. In plan.md § Implementation plan, find `[x] Step <current_step_idx>` and replace with `[ ] Step <current_step_idx>`. Leave the rest of the step's content untouched.

## Step 4 — Update task file

9. In `.planning/tasks/<active_task>.md.step_commits[<current_step_idx>]`:
   - Set `superseded: true` (5.10 diff-review will use the next non-superseded sha for this step).
   - Increment `defect_count` (initialize to 1 if missing).
   - Append to `notes:` field: `"<ISO> defect TC-<next_id>: <DEFECT_DESCRIPTION truncated to 80 chars>"`.

10. Update `Last-checkpoint:` line: `<ISO> — NEXT: re-execute step <current_step_idx> (PO defect TC-<next_id>)`.

## Step 5 — Hand off to @Main

11. Output to PO:

```
🔁 Defect TC-<next_id> recorded for step <current_step_idx>.
   - test-cases.md: new failing TC row + Defects log entry (severity: <severity>, status: OPEN).
   - plan.md: step <N> reopened ([ ]).
   - step_commits[<N>] marked superseded (existing commit stays as before-fix baseline).

Handing off to @Main to re-execute step <N>...
```

12. Dispatch to `@Main` with this prompt:

```
DEFECT RE-OPEN: step <current_step_idx>

Context:
  - Active task: <active_task>
  - Step: <current_step_idx>, "<step.Goal first line>"
  - Defect TC: TC-<next_id> (severity: <severity>) — "<DEFECT_DESCRIPTION>"
  - test-cases.md and plan.md already updated.
  - step_commits[<N>] marked superseded.

Action: re-enter EXECUTE at step 5.2 WRITE for step <N>. STEP_CONTEXT must include:
  - The original step's plan.md block (unchanged).
  - The new failing TC row (TC-<next_id>) — pass to @CodeWriter as part of "owned TCs".
  - The verbatim defect description so @CodeWriter knows what PO observed.

Skip step 5.1 READ (context loaded). Do NOT re-run @Analyst (spec.md is FROZEN).
After 5.4b COMMIT a new sha will replace step_commits[<N>].sha.
Then 5.6 CHECKPOINT runs again with full 3-way fork. Loop until /kit-approve.
```

## What NOT to do

- DO NOT git-revert anything. The existing step commit stays as the "before fix" baseline for diff comparison.
- DO NOT modify spec.md. Defects do not amend the contract; if the defect implies the AC is wrong, escalate to PO with proposal "spec amendment via @Analyst" — that's a separate cycle.
- DO NOT skip the new TC's path through @TestKeeper EXECUTE — the test must FAIL before the fix and PASS after, like any other TC.
- DO NOT auto-derive severity from defect text via heuristics. Use the rule: Critical-EC step → high; otherwise medium. Let @Reviewer escalate if the fix turns out to touch security surface.
- DO NOT loop more than 3 defects on the same step. After the 3rd, escalate to PO with /kit-revert-step or replan suggestion. The Anti-Loop rule in @Main enforces this independently.
