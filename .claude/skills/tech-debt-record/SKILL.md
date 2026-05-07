---
name: tech-debt-record
description: Record a non-critical code smell, duplication, warning, or deprecation as a tech-debt entry in vault/tech-debt/<module>/<slug>.md for later batch fixing via /kit-techdebt.
---

# Tech Debt Record Skill

This skill captures **non-critical technical findings** discovered during work without expanding the current task's scope.

## When to record

**Record when:**
- The issue is outside your current task's scope.
- Fixing it now would cause scope creep.
- The issue is non-critical (not a bug, not a security vulnerability, not a build break).

Examples: unused imports, duplicated logic patterns, long methods, deprecated API with clear migration path, compiler warning.

**Do NOT record:**
- Bugs — fix or escalate.
- Security vulnerabilities — fix or escalate.
- Build breaks — fix immediately.
- Issues within your current task's scope — fix them.
- Vague observations without a concrete file location.

## Process

### Step 1 — Verify

Confirm: non-trivial, out-of-scope, has a concrete file location. If uncertain, skip.

### Step 2 — Check for existing entry

`knowledge-my-app_search_docs "tech-debt <module> <topic>"` — avoid duplicating an existing open entry.

### Step 3 — Classify

**Category:** `warning` | `duplication` | `smell` | `complexity` | `deprecation` | `todo`

**Severity:** `high` | `medium` | `low`

**Slug:** kebab-case, max 30 chars. Example: `foo-service-god-class`.

### Step 4 — Write entry

Create `vault/tech-debt/<module>/<slug>.md`:

```markdown
---
id: TD-<module>-<slug>
module: <module>
category: <warning|duplication|smell|complexity|deprecation|todo>
severity: <high|medium|low>
status: open
discovered: <ISO date>
discovered_by: <agent name>
task: <active task slug>
---

# TD-<module>-<slug>

## Problem

<2-4 sentences: what specifically is wrong, with technical precision.>

## Location

| File | Lines | Notes |
|------|-------|-------|
| `src/path/to/File.kt` | 42-85 | God class: persistence + business logic + formatting |

## Deferral rationale

<Why not fixed now — scope constraint, unclear pattern, etc.>

## Suggested fix (optional)

<High-level sketch of the solution when the path forward is apparent.>

## References

- Originating task: <task-slug>
- Related feature: (if applicable)
```

### Step 5 — Index

Call `knowledge-my-app_write_guideline` to index the entry.

### Step 6 — Mention in output

Add one line to your output:

```
Tech debt recorded: TD-<module>-<slug> — <category>, <severity>
```

## Critical constraints

- **Cap 5 entries per task.** More signals structural problems requiring escalation.
- **Search first.** Avoid duplicating existing entries.
- **No code changes.** Recording defers fixes entirely.
- **Keep it fast.** If analysis exceeds ~2 minutes, escalate instead.
