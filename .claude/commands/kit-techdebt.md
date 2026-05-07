---
description: Triage and fix accumulated technical debt entries. Without arguments — scans all open entries and asks PO what to fix. With TD-id — fixes that one. With "module=<name>" — scans only that module.
---

You are @Main routing a tech-debt cleanup batch through a hybrid TECH/BUG pipeline. Argument: $FILTER (optional).

The single source of truth is the per-module tech-debt folder:
```
vault/tech-debt/<module>/<slug>.md          ← open / in-progress
vault/tech-debt/<module>/done/<slug>.md     ← archived after successful fix
```
Entries are written by code-touching agents (@CodeWriter, @BugFixer, @CodeReviewer) via the `tech-debt-record` skill while doing other work. `/kit-techdebt` is the surface where PO drains that backlog in a controlled batch.

## Step 0 — Preconditions

1. Read `.planning/CURRENT.md` → if `active_task` is set and is **not** a tech-debt batch, STOP. Output:
   ```
   Cannot start tech-debt batch — active task is <slug>. Finish or pause it (/kit-resume) first.
   ```
2. List `vault/tech-debt/` directories. If empty → STOP. Output:
   ```
   No tech-debt entries found. Nothing to do.
   ```

## Routing — argument shapes

1. **No argument** → SCAN all modules.
2. **Argument matches `TD-[a-z0-9-]+`** → fix only that one entry.
3. **Argument matches `module=<name>`** → SCAN only that module.
4. **Argument matches `severity=<high|medium|low>`** → SCAN only entries at that severity.
5. **Free-form text** → treat as a substring search over titles; SCAN matching entries.

## Step 1 — SCAN

Read every `*.md` under `vault/tech-debt/<module>/` (skip `done/`). Filter by argument. Group:

```
## Tech Debt — open entries

### high severity (N)
| TD-id | Module | Category | Title | Files |

### medium severity (N)
| TD-id | Module | Category | Title | Files |

### low severity (N)
| TD-id | Module | Category | Title | Files |
```

If a single entry was requested by ID and not found → STOP, report missing.

## Step 2 — TRIAGE — PO picks the batch

Show PO the scan output and ask **in one message**:

```
What to fix? Pick one:
  - all                  — fix every open entry shown
  - high                 — fix every high-severity entry
  - <module-name>        — fix every entry in that module
  - <TD-id> [TD-id ...]  — fix specific entries (comma or space separated)
  - none                 — abort, no changes

Waiting for response.
```

Wait for PO. If `none` → STOP, report no action.

## Step 3 — Create batch task file

After PO confirms the picked list:

1. Generate `task_slug = techdebt-batch-<YYYYMMDD-HHMM>`.
2. Create `.planning/tasks/<task_slug>.md` with:
   ```
   # Tech Debt Batch — <ISO date>
   Type: TECH
   Module: <list of touched modules>
   Description: Tech-debt cleanup batch — <N> entries
   Selected: <list of TD-ids>
   Timeline:
   ```
3. Write to `.planning/CURRENT.md`:
   ```
   active_task: <task_slug>
   started: <ISO timestamp>
   summary: TECH — tech-debt batch (<N> entries)
   ```

## Step 4 — Per-entry classification

For each TD-id in the picked list, classify the fix shape:

| Path | When | Pipeline |
|------|------|----------|
| **DIRECT** | severity ∈ {low, medium} AND `Files` table lists ≤ 2 files AND category ∈ {warning, todo, deprecation, smell} | @BugFixer-style cycle: ANALYZE → FIX → REGRESSION TEST (if behavior could shift) → @CodeReviewer → BUILD |
| **PLAN** | severity = high OR ≥ 3 files OR category = duplication (cross-cutting refactor) | Standard TECH pipeline: write a stage file under `vault/how-to/<module>/plans/techdebt-<slug>-stage-NN.md`, then @CodeWriter → @CodeReviewer per stage |

Set the entry's `status: open` → `status: in-progress` (edit frontmatter only) before dispatching, so a parallel run of `/kit-techdebt` does not double-fix it.

## Step 5 — EXECUTE — fix loop (per entry, sequentially)

```
for each TD-id in selected:

  5.1 READ — full entry file. Read referenced files for context.

  5.2 FIX
       DIRECT path:
         dispatch @BugFixer with input shape B (free-form):
           Description: <entry Description + Suggested fix sections>
           Module: <entry module>
           Feature: techdebt-<slug>
           Tech-debt entry: vault/tech-debt/<module>/<slug>.md
         @BugFixer: ANALYZE → FIX → (REGRESSION TEST if behavior could shift; for pure-warning fixes a smoke-test reference is enough) → @CodeReviewer → BUILD → COMMIT.
         Note: do NOT update test-cases.md (no TC was assigned). Skip Defects log.

       PLAN path:
         a. Write vault/how-to/<module>/plans/techdebt-<slug>-stage-01.md (and -02, -03 ... if needed) — one file per turn.
         b. For each stage file: dispatch @CodeWriter → @CodeReviewer → fix loop (max 3 review-fix cycles, then escalate).
         c. After last stage: BUILD + LINT.

  5.3 ARCHIVE — on success:
         a. Set entry frontmatter: status: in-progress → status: fixed; append Resolution section (Closed date, Fix commit, Files changed).
         b. Move file: vault/tech-debt/<module>/<slug>.md → vault/tech-debt/<module>/done/<slug>.md
         c. knowledge-my-app_update_doc on the moved file.

  5.4 CHECKPOINT — append to .planning/tasks/<task_slug>.md:
         ## <ISO timestamp>
         - DONE: TD-<slug> — <commit sha>
         - NEXT: <next TD-id or "REPORT">

  5.5 COMPRESS context before next entry.
```

## Step 6 — REPORT

After all selected entries are processed (or stopped):

```markdown
## Tech Debt Batch Report — <ISO date>

| TD-id | Module | Category | Severity | Outcome | Commit | Notes |
|-------|--------|----------|----------|---------|--------|-------|
| TD-server-dup-token | server | duplication | high | ✅ fixed | abc1234 | extracted to TokenParser |
| TD-client-warn-imports | client | warning | low | ⏭ skipped | — | superseded by TD-client-warn-deps |

**Closed:** N entries → moved to `<module>/done/`.
**Skipped / failed:** M entries — see checkpoints in `.planning/tasks/<task_slug>.md`.
**Remaining open:** K entries (run `/kit-techdebt` again to continue).
```

## Step 7 — CLOSE batch task

When report is delivered:
- Move `.planning/tasks/<task_slug>.md` → `.planning/tasks/done/<task_slug>.md`.
- Reset `.planning/CURRENT.md`:
  ```
  active_task: (none)
  started:
  summary:
  ```

## Stop rules

- **Max 3 review-fix cycles per entry** → STOP that entry, mark its status as `wont-fix` with a Notes line `auto-stop: review-fix loop` and continue with the next entry.
- **Build break that the entry's fix did not introduce** (i.e. broken on master) → STOP entire batch immediately, escalate to PO. Do not chain-cascade fixes.
- **PO interrupts mid-batch (`/kit-checkpoint` with no pending entry left) or types `denied`** → write checkpoint, leave remaining entries with `status: in-progress` so they can be resumed by re-running `/kit-techdebt`.
- **No matching entries** for a specific argument → STOP, report empty.

## What NOT to do

- DO NOT fix entries marked `status: wont-fix` — skip them in SCAN.
- DO NOT mass-update entries' frontmatter without dispatching the actual fix — `in-progress` must always reflect a real running fix.
- DO NOT skip @CodeReviewer on the DIRECT path. Tech debt is the easy place for review to catch a wrong "obvious" fix.
- DO NOT delete tech-debt files — always move to `done/`. The history is the audit trail.
