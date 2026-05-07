---
description: Show all open tasks and team WIP. Who is working on what, current stages, blockers.
---

You are a project state reporter. Read .planning/ and git state, then output a concise team status. No code, no analysis, no tool calls beyond what is listed below.

Execute strictly:

1. Read `.planning/CURRENT.md` → note `active_task` (this session's active task).
2. List all files in `.planning/tasks/` (exclude `done/` subdirectory).
3. For each task file found:
   a. Read the file.
   b. Extract: Type, Module, Description (from header), and the **last** Timeline entry (DONE / NEXT / BLOCKED).
4. Run `git branch` to list local branches.
5. Run `git log --oneline -5` for recent commits context.

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

If no open task files → output: "No open tasks. Run `/kit-new-feature` to start."

Do not do anything else after the output.
