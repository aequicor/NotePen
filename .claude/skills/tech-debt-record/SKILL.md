---
name: tech-debt-record
description: Record a non-critical code smell, duplication, warning, or deprecation as a tech-debt entry in the vault for later batch fixing via /kit-techdebt.
---

# Tech Debt Record Skill

Skill for capturing **non-critical** technical debt discovered during normal work — without expanding the current diff. Each entry becomes a self-contained markdown file under `vault/tech-debt/<module>/<slug>.md` that `/kit-techdebt` can later pick up, prioritize, and fix.

## When to use

Call this skill when, while doing your primary task, you notice something **outside the scope of the current task** that is:

- A compiler / linter / type-checker **warning** that does not affect runtime behavior.
- **Duplicated code** between 2+ files where the abstraction is not yet obvious.
- A **code smell** (long method, deep nesting, unclear naming, tight coupling) in a file you read but did not need to change.
- A **deprecation** notice (lib API or internal symbol) with a documented migration path.
- A **TODO** / `FIXME` left in code by someone else that has clear scope but no ticket.
- **Complexity hot-spot** (cyclomatic complexity, repeated conditionals) that slowed your reading.

The defining trait: **fixing it now would expand the diff beyond what the current task justifies.** Recording it preserves the finding without scope creep.

## When NOT to use

These are **never** tech debt — they require immediate action:

| Situation | Correct action |
|-----------|----------------|
| Bug, regression, or wrong behavior | Stop current task, escalate to @Main → BUG pipeline. |
| Security vulnerability (injection, leaked secret, missing auth check) | Stop, escalate to @Main with `BLOCKED: SECURITY`. |
| Build / test break | Fix in current task — non-negotiable. |
| Issue **inside the scope** of the current task | Fix in current task — that is what scope means. |
| Vague feeling ("this could be cleaner") with no concrete file:line | Drop. Vague entries pollute the backlog. |
| A finding from `@CodeReviewer` already classified CRITICAL/HIGH | Goes through review-fix loop, not tech-debt. |
| Duplicate of an existing open tech-debt entry | Append a new file reference to the existing entry, do not create a new one. |

If unsure between "tech debt" and "fix now" — ask yourself: *would shipping this leave the system worse than yesterday?* If yes, fix it. If no, record it.

## Process

### Step 1 — Verify the entry is non-trivial and out of scope

Before writing anything:

1. Confirm the issue is **not** in the file(s) you are currently editing for the active task.
   (If it is — fix it; do not record.)
2. Confirm a concrete `file:line` (or 2-3 file paths for duplication) exists.
3. Search for an existing entry — read `vault/tech-debt/<module>/` and grep for the symbol/keyword. If found, **update** that file (append a row to its Files table, mention the new occurrence) instead of creating a new one.

### Step 2 — Pick category and severity

| Category | Examples |
|----------|----------|
| `warning` | Compiler/linter warning, unused import, deprecated call |
| `duplication` | 2+ blocks of near-identical code |
| `smell` | Long method, deep nesting, unclear name, tight coupling |
| `complexity` | Cyclomatic / cognitive complexity hot-spot |
| `deprecation` | Deprecated API with migration path |
| `todo` | Stale `TODO` / `FIXME` with clear scope but no ticket |

Severity heuristic:

- `high` — affects correctness boundary (e.g. silent precision loss, deprecated security API), or duplication across 4+ sites.
- `medium` — slows reading or violates a project guideline; would be caught in review for new code.
- `low` — cosmetic / single-occurrence / documented compiler warning.

### Step 3 — Generate the slug and write the file

Slug format: `<short-kebab-name>` (max 40 chars). Examples: `dup-token-parsing`, `warn-unused-imports-server`, `deprecated-okhttp-interceptor`.

Path: `vault/tech-debt/<module>/<slug>.md`

Use the template at `vault/_templates/tech-debt.md`. Required frontmatter fields: `title`, `module`, `category`, `severity`, `status: open`, `discovered`, `discovered_by`. Required body sections: **Files**, **Description**, **Why not critical now**.

### Step 4 — Index in KnowledgeOS

After writing the file:

```
knowledge-my-app_write_guideline → vault/tech-debt/<module>/<slug>.md
```

This makes the entry searchable by `knowledge-my-app_search_docs("topic", genre="guideline")`.

### Step 5 — Note in current output (one line, no detour)

In the agent's normal output (review report / fix report / Changed Files table), append a single line:

```
Tech debt recorded: TD-<module>-<slug> — <category>, <severity>
```

Do **not** narrate, do not list multiple debt items prominently, do not pause the primary task. The `/kit-techdebt` command is the surface for batch action.

## Rules

1. **One entry per finding.** Do not bundle unrelated smells into a single file.
2. **Idempotent.** Recording the same finding twice is a bug — search first.
3. **No critical issues here.** If severity ≥ HIGH and the issue is a real defect (not just a smell) — escalate, do not record.
4. **No silent scope expansion.** Recording must take seconds, not minutes. If you find yourself analyzing for more than 2 minutes, stop and escalate to @Main — it is not tech debt.
5. **Do not modify code while recording.** The whole point is to defer the fix.
6. **Cap per task: 5 entries.** If the current task surfaces more than 5 distinct debt items, that is a signal to escalate to @Main with `OBSERVATION: this module has structural problems beyond tech-debt scope`.
