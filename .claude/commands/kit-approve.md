---
description: Approve the pending plan or action that @Main is waiting on. Signals PO confirmation and continues the pipeline. v6.2+ optional flag `--no-ground-truth` overrides the 5.6 ground-truth artefact gate (logged as technical debt in Defects log; do not use lightly).
---

You are the Product Owner authorizing the next phase. Your task is to confirm approval and unblock the pipeline immediately — no re-summaries, no delays.

**PO approval received.**

You are @Main. You were waiting for PO confirmation at a CONFIRM-class gate. Act now:

1. Read `.planning/CURRENT.md` → get `active_task`. Append to `.planning/tasks/<active_task>.md`:
   ```
   ## <ISO timestamp>
   - DONE: PO approved via /kit-approve<flags>
   - NEXT: proceeding to next phase
   ```
   `<flags>` is `" --no-ground-truth"` if that flag was passed, empty otherwise.

2. Determine which gate is unblocked:
   - At step 4 CONFIRM (FEATURE/TECH) → continue to step 5 EXECUTE.
   - At step 5.6 CHECKPOINT 3-way fork → continue to step 5.5 UPDATE for next step (or RECONCILE if last step).
   - At step 5.10 DIFF-REVIEW → continue to step 6 CLOSE.
   - At a /kit-revert-step confirmation prompt → execute the revert per the command's Step 4.
   - At a /kit-defect re-open confirmation → re-enter EXECUTE at 5.2 WRITE.
   - At BUG pipeline → BUG has no CONFIRM step; if reached, report state and ask PO.

3. v6.2+ — `--no-ground-truth` flag handling (only meaningful at 5.6 CHECKPOINT):
   - If the flag was passed AND the 5.6 ground-truth gate was BLOCKED on missing artefact:
     - Append to test-cases.md § Defects log:
       ```
       - **GROUND-TRUTH-WAIVED step <N>** (severity: medium, status: OPEN, kind: technical-debt)
         - Reported: <ISO>
         - Source: ground-truth-waived (PO override at 5.6)
         - Description: Step <N> approved without ground-truth artefact (type: <REQUIRED_TYPE>).
                        Class of defect that this gate catches (per research): UI/UX broken
                        despite green AI checks; API contract mismatch with real client;
                        backend logic missing edge-case under mutation. Track here for
                        retro and consider adding a regression test post-CLOSE.
       ```
     - Set `step_commits[N].ground_truth = {type: <REQUIRED_TYPE>, path: null, summary: "WAIVED by /kit-approve --no-ground-truth", waived: true}`.
     - Output to PO: "⚠️  Ground-truth waived for step <N>. Logged as technical debt in test-cases.md Defects log. Proceeding to step <N+1>."
   - If the flag was passed but no 5.6 gate is pending → ignore flag, proceed normally.
   - If the flag was NOT passed AND 5.6 gate is BLOCKED on missing artefact → STOP. Output: "Step <N> ground-truth artefact still missing. Attach via /kit-attach <path>, or override with /kit-approve --no-ground-truth (logged as technical debt)."

4. Continue immediately. Do not ask for additional confirmation. Do not re-summarize the plan.

> If no CONFIRM-class gate was pending — report the current state from the active task file and ask PO what to do next.
