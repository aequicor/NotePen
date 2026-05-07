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
updated: 2026-05-07T17:37:42Z
---

# Tech Debt: <short-title>

**ID:** TD-<module>-<slug>
**Module:** <module-name>
**Category:** warning | duplication | smell | complexity | deprecation | todo
**Severity:** low | medium | high
**Status:** open | in-progress | fixed | wont-fix
**Discovered:** YYYY-MM-DD
**Discovered by:** @AgentName during <task-slug>

---

## Files

| Path | Lines | Note |
|------|-------|------|
| `path/to/File.kt` | 42-78 | duplicates logic from `path/to/Other.kt:120-150` |

---

## Description

What the smell/duplication/warning is, in 2-4 sentences. Be specific — name functions, modules, libraries. A reader who has not seen this code should be able to grasp the issue.

---

## Why not critical now

One sentence on why this was deferred rather than fixed in place. Examples:

- Out of scope for the current task — would expand the diff beyond review tolerance.
- Compiler/linter warning that does not affect runtime behavior.
- Duplicated code is short and the abstraction is not yet clear.
- Deprecation with a documented migration path that requires a separate plan.

---

## Suggested fix (optional)

If the fix shape is already clear, sketch it in 2-5 bullets. Otherwise leave this section empty — `/kit-techdebt` will plan it.

---

## References

- Related feature: `[[features/<module>/<feature>/feature]]`
- Related TC (if applicable): TC-NN in `[[features/<module>/<feature>/test-cases]]`
- Originating commit / PR: `<sha>` / `#<num>`

---

## Resolution (filled by `/kit-techdebt`)

**Closed:** YYYY-MM-DD
**Fix commit:** `<sha>`
**Files changed:** see `[[features/<module>/<feature>/retro]]`
**Notes:** any deviation from the suggested fix.
