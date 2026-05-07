---
description: v6.1+ revert the current step entirely. Runs `git reset --hard` to the previous step's commit, removes step_commits[N] from the task file, restores the [ ] checkbox in plan.md. Used at 5.6 CHECKPOINT when PO decides the step went the wrong way (not a point defect — a structural mistake).
---

You are a Senior project manager processing PO's request to discard the current step. This command is **destructive** — it `git reset --hard`s the working copy and discards the step's commit. Confirm with PO before applying.

Argument: $TARGET_STEP (optional integer; default = `current_step_idx` from task file). Allows reverting further back than just the most recent step.

## Step 1 — Resolve target

1. Read `.planning/CURRENT.md`:
   - If `active_task` is `(none)` → STOP. Output: "No active task."
   - If `mode: sleep` → STOP. Output: "Sleep mode is autonomous; /kit-revert-step is a PO command. Run `/kit-resume --interactive` first to switch out of sleep."
   - If `status: SLEEP_BLOCKED` → STOP. Output: "Task is sleep-blocked. Resolve the block first (read MORNING_REPORT.md), then run /kit-resume."

2. Read `.planning/tasks/<active_task>.md`:
   - If `current_step_idx` is missing or `0` → STOP. Output: "No step to revert. /kit-revert-step runs after at least one step has completed."
   - Note `current_step_idx` and `step_commits[]`.

3. Resolve target step:
   - If `$TARGET_STEP` provided → use it (must be > 0 and ≤ current_step_idx).
   - Else → target = `current_step_idx`.

4. Resolve anchor sha:
   - If target == 1 → anchor = task's `start_commit` field.
   - Else → anchor = `step_commits[target - 1].sha` (last green sha BEFORE the target step).
   - If anchor is `(no-git)` or missing → STOP. Output: "Cannot revert without a git anchor. Task was started without git or step_commits[] is empty (legacy)."

## Step 2 — Pre-flight git safety

5. Run `git status --porcelain`. If output is non-empty:
   - Compute uncommitted files set.
   - Compare against the union of `step_commits[N].changed_files` for N in [target..current_step_idx].
   - If any uncommitted file is OUTSIDE that union → STOP. Output:
     ```
     ⚠️ /kit-revert-step would lose changes outside the target step's scope.
     Uncommitted files outside step <target>..<current_step_idx>:
       - <path>
     Stash or commit them first, then re-run /kit-revert-step.
     ```
   - If uncommitted files are all inside scope → warn but continue ("Uncommitted in-scope files will be lost: <list>").

6. Run `git rev-parse HEAD` and `git log --oneline <anchor>..HEAD` to show PO what will be discarded.

## Step 3 — Confirm with PO

7. Output:

```
🗑️  /kit-revert-step — DESTRUCTIVE OPERATION

Target: step <target> (and any subsequent steps <target+1>..<current_step_idx>).
Anchor: <anchor sha> — <commit message>

The following commits will be DISCARDED (working copy reset to anchor):
<output of git log --oneline anchor..HEAD>

Uncommitted in-scope changes that will be lost: <list, or "none">

Confirm with /kit-approve.
Cancel with anything else (or just don't reply with /kit-approve).
```

8. WAIT for PO `/kit-approve`. Anything else → STOP, do not modify state.

## Step 4 — Apply

9. Run `git reset --hard <anchor>`. If error → STOP, surface the error.

10. Update `.planning/tasks/<active_task>.md`:
    - Remove all `step_commits[]` entries with `step >= target`.
    - Set `current_step_idx = target - 1`.
    - Replace `Last-checkpoint:` line: `<ISO> — NEXT: step <target> reverted by PO; replan or re-execute`.

11. Update `plan.md § Implementation plan`:
    - For each step in [target..(highest step with [x])], replace `[x]` with `[ ]`.

12. Output to PO:

```
✅ Reverted to <anchor sha>. Working copy clean at last green checkpoint.
   step_commits[] truncated to step <target - 1>.
   plan.md: steps <target>..<highest> restored to [ ].

Choose next:
  /kit-approve            — re-run step <target> with the same plan-step description (no replan)
  /kit-rework <reason>    — pre-step replan via replan-on-discovery
  /kit-revert-step <N>    — cascade revert further back
```

13. WAIT for PO direction. Do NOT auto-proceed.

## What NOT to do

- DO NOT use `git reset --hard` without an explicit anchor sha (the anchor must be a known step_commits sha or task start_commit; HEAD~N is forbidden — too easy to misalign).
- DO NOT cascade revert past `start_commit`. /kit-revert-step is bounded to within the current task's commit range.
- DO NOT touch spec.md or test-cases.md. The contract is unchanged; only the implementation history is rewritten.
- DO NOT auto-invoke /kit-rework after the revert. PO chooses next; this command's job is the revert + restore, nothing more.
- DO NOT run in sleep mode. Sleep's destructive operation is BLOCKED-shutdown's reset, which has its own safety logic. /kit-revert-step is interactive-only.
- DO NOT proceed without /kit-approve confirmation. Half-confirmed destructive commands are how data is lost.
