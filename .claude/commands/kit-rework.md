---
description: v7.0.0+ re-open EXECUTE with PO direction. Argument: $REASON (1-3 lines explaining what should be done differently). Used at step 5.6 CHECKPOINT or 5.10 DIFF-REVIEW when PO wants to redirect the agent rather than report a defect (/kit-defect) or undo a step (/kit-revert-step). Routes to replan-on-discovery skill if available, falls back to plan.md amendment otherwise.
---

You are a Senior project manager processing PO's request to redirect the active task. Different from `/kit-defect` (specific defect → adds failing TC) and `/kit-revert-step` (drop the step entirely). `/kit-rework` is the "rethink the approach" channel.

Argument: $REASON (mandatory; 1-3 lines describing what should change).

## Step 1 — Resolve context

1. Read `.planning/CURRENT.md`:
   - If `active_task` is `(none)` → STOP. Output: "No active task. /kit-rework re-directs an in-flight task; use /kit-new-feature to start a new one."
   - If `mode: sleep` → STOP. Output: "Sleep mode is autonomous; rework runs through replan-on-discovery automatically. To intervene manually, edit `.planning/CURRENT.md` and set `mode: interactive` first."
   - If `status: SLEEP_BLOCKED` → STOP. Output: "Task is sleep-blocked; read MORNING_REPORT.md and resolve the block first."

2. Read `.planning/tasks/<active_task>.md`:
   - Note `current_step_idx`. If 0, the task is in ANALYSIS / PLAN / CONFIRM — rework targets the planning phase, not execution.

3. Confirm $REASON is non-empty and ≥ 10 chars. If empty / too short → STOP. Output: "Reason required. Example: `/kit-rework The current step uses optimistic locking; we need pessimistic for the deferred-write path discovered in step 2.`"

## Step 2 — Determine rework class

4. Decide based on `current_step_idx`:
   - `current_step_idx == 0` → **Pre-EXECUTE rework.** Re-dispatch @Architect with EXISTING_DOCS pointing to the current spec.md/plan.md and the rework reason. Architect produces a new draft incorporating PO direction. Pipeline returns to step 4 CONFIRM.
   - `current_step_idx > 0` → **Mid-EXECUTE rework.** Invoke `replan-on-discovery` skill (Pattern E — PO-directed replan, distinct from Patterns A/B/C/D which are agent-discovered). Skill writes a bounded plan amendment (≤ 3 new steps) into `plan.md § Implementation plan` with a `<!-- REPLAN-PO-N -->` marker citing $REASON.
   - `current_step_idx > 0` AND `replan-on-discovery` skill is NOT installed → **Manual fallback.** @Main shows PO the current plan and asks them to specify which steps to add/replace, then writes the amendment by hand into plan.md § Implementation plan. Log `notes: "PO rework <ISO>: <reason>"` in step_commits[current_step_idx].

## Step 3 — Confirm with PO

5. Output (pre-EXECUTE class):

```
↩️  /kit-rework — pre-EXECUTE re-draft

Current state: ANALYSIS / PLAN / CONFIRM (current_step_idx = 0)
Reason: <REASON>

Action plan:
  1. Re-dispatch @Architect with TYPE=<existing>, EXISTING_DOCS=spec.md+plan.md,
     and PO direction = "<REASON>".
  2. Architect produces a revised spec.md / plan.md skeleton.
  3. Pipeline returns to step 4 CONFIRM.

Confirm with /kit-approve. Cancel with anything else.
```

6. Output (mid-EXECUTE class):

```
↩️  /kit-rework — mid-EXECUTE replan

Current state: EXECUTE step <current_step_idx>
Reason: <REASON>

Action plan:
  1. Invoke replan-on-discovery skill, Pattern E (PO-directed).
  2. Skill writes ≤ 3 new steps into plan.md § Implementation plan with
     <!-- REPLAN-PO-N --> marker citing the reason.
  3. Hard cap: max 2 PO replan events per feature; on the 3rd, fall through
     to escalation.
  4. After replan, @Main re-enters EXECUTE at the next pending step.

Confirm with /kit-approve. Cancel with anything else.
```

7. WAIT for PO `/kit-approve`. Anything else → STOP.

## Step 4 — Apply

8. Pre-EXECUTE class:
   - Re-dispatch @Architect with the inputs above. Wait for ARCHITECT DONE block.
   - Update task file: `Last-checkpoint: <ISO> — NEXT: CONFIRM (post-rework re-draft)`.
   - Output to PO: "Architect re-drafted spec/plan with your direction. Review in spec.md / plan.md, then /kit-approve to enter EXECUTE, or /kit-rework again with a different reason."

9. Mid-EXECUTE class with replan-on-discovery installed:
   - Invoke the skill with Pattern E inputs (REASON, current_step_idx, plan.md path).
   - Skill returns the new step block. Append to plan.md.
   - Increment task's replan counter; if ≥ cap → STOP and escalate.
   - Update `Last-checkpoint: <ISO> — NEXT: step <new_first_step_idx> (post-rework replan)`.
   - Output to PO: "Replan written: <count> new steps added. Working copy unchanged; @Main proceeds to step <new_first_step_idx> on next /kit-approve."

10. Mid-EXECUTE class without skill (fallback):
    - Show PO the current plan.md § Implementation plan and ask: "Which existing steps to add/replace? Reply with step numbers + new descriptions."
    - WAIT for PO answer.
    - Apply the amendment to plan.md by hand. Mark the change with `<!-- REWORK-MANUAL <ISO> -->`.
    - Output: "Manual amendment applied. /kit-approve to continue."

11. WAIT for PO direction.

## What NOT to do

- DO NOT auto-execute the rework. PO confirms via /kit-approve before any spec/plan/code change.
- DO NOT modify spec.md mid-EXECUTE — spec is FROZEN at CONFIRM. If REASON implies AC/EC change, escalate: "This rework requires spec amendment via @Architect DRAFT cycle (a fresh CLASSIFY → ANALYSIS → PLAN → CONFIRM). Confirm to start a new task, or revise the rework reason."
- DO NOT loop more than 2 PO replans per feature without escalation. The replan-on-discovery skill enforces this independently.
- DO NOT touch step_commits[] entries — rework rewrites plan.md only; per-step commits stay intact.
- DO NOT proceed without /kit-approve confirmation.
- DO NOT silently fall through to manual fallback when replan-on-discovery IS installed — invoke the skill (cleaner audit trail).
