---
description: v6.2+ revert the current step via `git revert` (non-destructive). Creates a reverse commit that undoes the step's changes; the original commit stays in history. Marks step_commits[N].superseded=true, restores [ ] checkbox in plan.md, sets current_step_idx back. Does NOT trip the harness destructive-action gate (no `git reset --hard`). Used at 5.6 CHECKPOINT when PO decides the step went the wrong way.
---

You are a Senior project manager processing PO's request to undo the current step. v6.2 changed the underlying mechanism from `git reset --hard` (destructive, requires harness permission) to `git revert` (creates a reverse commit, non-destructive). The PO-visible semantics are similar: the step is undone, plan.md restores the `[ ]` checkbox, you can replan or re-execute. Differences:

- Working copy is updated by a NEW commit (the revert), not by rewriting history.
- Original step commit stays in `git log` (audit trail).
- `step_commits[N]` keeps its sha but is marked `superseded: true`; a new entry with `kind: revert` is appended.
- Cascade revert (multiple steps) creates one revert commit per reverted step (in reverse order). Squash on merge cleans this up.

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

4. Resolve commits to revert (the set of `step_commits[N].sha` for N in `[target..current_step_idx]`, latest non-superseded entry per step):
   - For each step in range, walk `step_commits[]` and pick the most recent entry with `superseded: false` and `kind != revert`.
   - If any step in range has no eligible commit → STOP. Output: "Cannot revert step <N>: no eligible commit (already superseded or only revert entries exist)."
   - If a step's commit is recorded as `(no-git)` (legacy task started without git) → STOP. Output: "Cannot revert without a git anchor. Task was started without git or step_commits[] is empty (legacy)."

## Step 2 — Pre-flight git safety

5. Run `git status --porcelain`. If output is non-empty:
   - Compute uncommitted files set.
   - If any uncommitted file exists → STOP. Output:
     ```
     ⚠️ /kit-revert-step requires a clean working tree (revert creates a new commit on top of HEAD).
     Uncommitted changes:
       - <path>
     Stash, commit, or discard them first, then re-run /kit-revert-step.
     ```
   - Rationale: unlike v6.1's `git reset --hard`, `git revert` does NOT discard uncommitted work. We refuse to proceed with dirty tree to avoid mixing PO's in-progress edits into the revert commit.

6. Run `git rev-parse HEAD` and `git log --oneline <step_commits[target-1].sha-or-task_start_commit>..HEAD` to show PO what will be reverted.

## Step 3 — Confirm with PO

7. Output:

```
↩️  /kit-revert-step — non-destructive (creates revert commit)

Target: step <target> (and any subsequent steps <target+1>..<current_step_idx>).

The following commits will be reverted in reverse order (newest first):
<output of git log --oneline showing each step_commits[N].sha with goal>

Mechanism: `git revert <sha>` per commit, no `--no-edit` (you'll see each
revert commit message; default is "Revert \"<original message>\""). Working
copy and history both keep the original commits — revert just adds inverse
commits on top. Squash on merge will clean this up.

Confirm with /kit-approve.
Cancel with anything else (or just don't reply with /kit-approve).
```

8. WAIT for PO `/kit-approve`. Anything else → STOP, do not modify state.

## Step 4 — Apply

9. For each step in `[current_step_idx down to target]` (i.e. reverse chronological — newest first to avoid merge conflicts):
   - Pick `sha = step_commits[N].sha` (the eligible non-superseded commit).
   - Run `git revert --no-edit <sha>`. Defaults are fine — auto-generated commit message "Revert \"step <N>: <goal>\"" is informative enough.
   - If conflict → ABORT the revert (`git revert --abort`) and STOP. Output:
     ```
     ⚠️ git revert hit a conflict on step <N> (<sha>).
     This typically means a later step modified the same lines, so the revert
     of an earlier step cannot apply cleanly. Options:
       (a) /kit-revert-step <N+1>..<current_step_idx>  — revert later steps first
       (b) Resolve manually, commit, then re-run /kit-revert-step.
     The aborted revert left no changes; working copy is clean.
     ```
   - On success: capture `revert_sha = git rev-parse HEAD`.
   - Append to `step_commits[]`:
     ```
     - step: <N>
       sha: <revert_sha>
       kind: revert
       reverts: <sha>
       goal: "Revert step <N>: <original goal>"
       changed_files: <files touched by this revert>
       superseded: false
     ```
   - Mark the original `step_commits[]` entry for step N (the one with `sha == <reverted sha>`) as `superseded: true`.

10. After all reverts succeed, update `.planning/tasks/<active_task>.md`:
    - Set `current_step_idx = target - 1`.
    - Replace `Last-checkpoint:` line: `<ISO> — NEXT: step <target> reverted by PO; replan or re-execute`.

11. Update `plan.md § Implementation plan`:
    - For each step in `[target..(highest step with [x])]`, replace `[x]` with `[ ]`.

12. Output to PO:

```
✅ Reverted via git revert. Working copy at <new HEAD sha>.
   Reverse commits added: <count> (one per reverted step, newest first).
   step_commits[] updated: <count> entries appended (kind: revert);
                            <count> originals marked superseded.
   plan.md: steps <target>..<highest> restored to [ ].

Choose next:
  /kit-approve            — re-run step <target> with the same plan-step description (no replan)
  /kit-rework <reason>    — pre-step replan via replan-on-discovery
  /kit-revert-step <N>    — cascade revert further back
```

13. WAIT for PO direction. Do NOT auto-proceed.

## What NOT to do

- DO NOT use `git reset --hard`. v6.2 deliberately switched away from this to avoid the harness destructive-action permission gate. If you find yourself reaching for it, STOP and report the situation to PO.
- DO NOT use `git revert --no-commit`. We want one commit per reverted step for clear audit trail and for `step_commits[]` schema integrity.
- DO NOT use `git reset` to "clean up the revert commits later" — that re-introduces destructive operation. If PO wants a clean linear history, that's a `git rebase -i` on the merge target before merging the feature branch, or a squash merge.
- DO NOT cascade revert past `start_commit`. /kit-revert-step is bounded to within the current task's commit range.
- DO NOT touch spec.md or test-cases.md. The contract is unchanged; only the implementation history is rewritten.
- DO NOT auto-invoke /kit-rework after the revert. PO chooses next; this command's job is the revert + restore, nothing more.
- DO NOT run in sleep mode. Sleep's destructive operation is BLOCKED-shutdown's reset, which has its own safety logic and harness permission requirements (`Bash(git reset --hard *)` allowlist must be configured before /kit-sleep is started). /kit-revert-step is interactive-only.
- DO NOT proceed without /kit-approve confirmation. Half-confirmed destructive commands are how data is lost — even though `git revert` is non-destructive, dirty-tree safety still requires explicit confirmation.
