---
description: Show all open tasks and team WIP. Who is working on what, current stages, blockers. v6.3+ also surfaces rolling gate signal_ratio over the last N tasks (deprecation candidates highlighted) when evals/runs/<kit_version>/ exists.
---

You are a project state reporter. Read `.planning/`, git state, and (v6.3+) `evals/runs/<kit_version>/` if present, then output a concise team status. No code, no analysis, no tool calls beyond what is listed below.

Execute strictly:

1. Read `.planning/CURRENT.md` → note `active_task` (this session's active task).
2. List all files in `.planning/tasks/` (exclude `done/` subdirectory).
3. For each task file found:
   a. Read the file.
   b. Extract: Type, Module, Description (from header), and the **last** Timeline entry (DONE / NEXT / BLOCKED).
4. Run `git branch` to list local branches.
5. Run `git log --oneline -5` for recent commits context.
6. v6.3+ — if `evals/runs/<kit_version>/gates.csv` exists, compute rolling gate signal_ratio:
   - Read manifest for `kit_version`, `telemetry.evaluation_window_tasks` (default 30), `telemetry.signal_ratio_threshold` (default 0.05).
   - Read gates.csv rows. Filter to last `evaluation_window_tasks` distinct `task_slug` values (most-recent-first by file order or first timestamp per task).
   - Group by `gate`. For each gate compute:
       fires = row count
       blocks = count where `verdict == block`
       signal_ratio = blocks / fires (0 if fires == 0)
       false_negatives = count of defects in defects.csv (same window) whose origin maps to this gate AND a `pass` row exists for the same (task, step) earlier
   - Order: by signal_ratio ASC (lowest first — deprecation candidates surface).
   - Highlight rows where `signal_ratio < signal_ratio_threshold` AND `false_negatives == 0` as `🟡 deprecation_candidate`.
   - Highlight rows where `false_negatives > 0` as `🔴 missed_defects (<count>)`.
7. v6.3+ — if `evals/runs/<kit_version>/defects.csv` exists, compute origin histogram over the same window:
   - Group by `origin` column; count rows. Show top 5.

Output EXACTLY this format:

```
## Team Status — <current date>

Active in this session: <active_task or "(none)">

### Open Tasks

| Task | Type | Module | Last Done | Next | Blocked? |
|------|------|--------|-----------|------|----------|
| <slug> | FEATURE/BUG/TECH | <module> | <DONE line> | <NEXT line> | — or reason |
... one row per open task file

### Recent Commits
<last 5 git log lines>

### Branches
<git branch output>
```

Append, only if `evals/runs/<kit_version>/` exists:

```
### Gate signal — rolling over last <N> tasks (v6.3+)

| Gate | Fires | Blocks | Signal ratio | False negatives | Status |
|------|-------|--------|--------------|-----------------|--------|
| <gate> | <fires> | <blocks> | <ratio> | <fn count> | <emoji + label or empty> |
... rows ordered by signal_ratio ASC

Threshold: <signal_ratio_threshold> (manifest.telemetry.signal_ratio_threshold)
Window: last <evaluation_window_tasks> tasks (manifest.telemetry.evaluation_window_tasks)

Deprecation candidates: <count of 🟡 rows>
Gates that missed defects: <count of 🔴 rows>
```

Append, only if `evals/runs/<kit_version>/defects.csv` exists:

```
### Defect origin distribution — last <N> tasks (v6.3+)

| Origin | Count |
|--------|-------|
| <origin> | <count> |
... top 5 rows

Total defects in window: <total>
```

If no open task files AND no eval data → output: "No open tasks. Run `/kit-new-feature` to start."

If open tasks but no eval data → omit the "Gate signal" and "Defect origin" sections silently (no warning — telemetry is opt-in).

Do not do anything else after the output. Specifically:
- DO NOT analyze gate-signal rows (e.g. "this gate is bad — remove it"). Surface the data; PO + maintainer decide.
- DO NOT recommend deprecations in chat. The 🟡 emoji + label is the recommendation; further action is human.
- DO NOT modify gates.csv or defects.csv. Read-only.
