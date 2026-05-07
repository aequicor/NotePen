---
name: session-handoff
description: Generate a copy-pasteable session-handoff block listing all planning artifacts after PLAN + QA IMPL DRAFT (or after TECH PLAN). Called by @Main before CONFIRM so the PO can start EXECUTE in a new session without context loss.
---

# Session Handoff Skill

All planning artifacts for the current task are now complete.
Generate a structured block the PO can copy to a new session.

## Step H1 — Collect artifact paths from current context

From task context already in memory, resolve each path that was created in this session.
Omit rows for files that do not exist for this task type (e.g. TECH tasks have no spec or test-cases).

| Slot | Path pattern |
|------|-------------|
| Active task pointer | `.planning/CURRENT.md` |
| Task state | `.planning/tasks/<task-slug>.md` |
| Plan | `vault/concepts/<module>/plans/<feature>-plan.md` |
| Stage files | `vault/how-to/<module>/plans/<feature>-stage-NN.md` (one row per stage) |
| Spec | `vault/reference/<module>/spec/<feature>.md` |
| Test cases | `vault/reference/<module>/test-cases/<feature>-test-cases.md` |
| Requirements | `vault/concepts/<module>/requirements/<feature>.md` |
| Corner cases | `vault/concepts/<module>/plans/<feature>-corner-cases.md` |

Count incomplete stages: read the plan file — count stages not yet marked done.

## Step H2 — Output the handoff block

Print EXACTLY this block. The `---` delimiter lines are part of the output — do not omit them.
Replace every `<…>` placeholder with the resolved value. Omit table rows for files that do not exist.

---
## Session Handoff — ready for EXECUTE

Planning is complete. To continue in a new session:

**Quick resume:** run `/kit-resume` — it reads `.planning/CURRENT.md` automatically.

**Manual context (copy this block):**

**Task:** `<task-slug>`
**Module:** `<module>`
**Feature:** `<feature>`
**Incomplete stages:** <N> of <M>
**Next step:** CONFIRM → EXECUTE

| File | Path |
|------|------|
| Active task pointer | `.planning/CURRENT.md` |
| Task state | `.planning/tasks/<task-slug>.md` |
| Plan | `<plan-path>` |
| Stage 1 | `<stage-01-path>` |
| Stage N | `<stage-N-path>` |
| Spec | `<spec-path>` |
| Test cases | `<test-cases-path>` |
| Requirements | `<requirements-path>` |
| Corner cases | `<corner-cases-path>` |

To continue: paste this block in a new session and say _"continue from CONFIRM"_, or just run `/kit-resume`.
---

Do not add anything after the closing `---`. Do not summarize. Return control to @Main — the calling step in Main directs which step to proceed to next (CONFIRM in both FEATURE and TECH pipelines).
