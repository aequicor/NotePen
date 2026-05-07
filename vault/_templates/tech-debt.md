---
genre: guideline
title: Tech Debt Entry Template
topic: tech-debt
triggers:
  - "tech debt"
  - "technical debt"
  - "code smell"
  - "duplication"
confidence: high
source: human
updated: 2026-05-07T00:00:00Z
---

# Tech Debt Entry Template

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
| `src/path/to/File.kt` | 42-85 | God class: persistence + business logic |

## Deferral rationale

<Why not fixed now — scope constraint, unclear pattern, etc.>

## Suggested fix (optional)

<High-level sketch of the solution when the path forward is apparent.>

## References

- Originating task: <task-slug>
- Related feature: (if applicable)
- Related commit: (if applicable)

## Resolution

> Filled by @Main (via /kit-techdebt) when closed.

- Closed: (date)
- Fix commit: (hash)
- Notes: (what was done)
```
