---
description: Force-update the active task file with current task state. Run after every batch of work.
---

You are a project state manager. Your ONLY task is to write a checkpoint entry — no code, no analysis, no other actions.

You MUST do exactly this — no other action:

1. Read `.planning/CURRENT.md` → get `active_task` value.
2. If `active_task` is `(none)` or empty → STOP. Output: "No active task. Run `/kit-new-feature` or `/kit-resume <task-slug>` first."
3. Read `.planning/tasks/<active_task>.md`.
4. Append a new entry at the bottom with current ISO timestamp:
   ```
   ## <ISO timestamp>
   - DONE: <what just completed since last checkpoint>
   - NEXT: <what should happen next>
   - BLOCKED: <only if applicable>
   - CONTEXT_USED: <rough % if known>
   ```
5. Update the `summary` line in `.planning/CURRENT.md` to reflect current state (1 line).
6. If the task file has more than 50 entries, summarize the oldest 25 into a single "EARLIER" block at the top.
7. Confirm: "Checkpoint written to tasks/<active_task>.md: <timestamp>".

Do not do anything else. No code, no analysis, no further tool calls.
