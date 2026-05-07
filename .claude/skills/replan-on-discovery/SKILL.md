---
name: replan-on-discovery
description: Bounded adaptive replanning when an EXECUTE-phase agent surfaces a structural gap. 4 trigger patterns. Hard cap: max 2 replan events per feature, max 3 new steps each. Optional v5.2+ skill.
---

# Replan-on-Discovery Skill (optional, v5.2+)

Allows bounded plan amendments during the EXECUTE phase instead of immediate escalation to the Product Owner.

## When to use

This skill activates at **four trigger points** in the EXECUTE phase before falling through to escalation:

- **Pattern A** — `@Reviewer` flags an AC-violation rooted in the spec (code right, spec wrong).
- **Pattern B** — `@TestKeeper RECONCILE` finds a Critical/High EC uncovered and no plan step owns it.
- **Pattern C** — `@TraceabilityChecker` reports an `ENDPOINT_ORPHAN` on a Critical surface.
- **Pattern D** — `@CodeWriter` returns BLOCKED on a missing dependency from a future step.

## When NOT to use

- Fix-in-place issues (failed 3-cycle review cap).
- Code style/quality findings.
- When replan cap (2 events) is already exhausted.
- Scope creep disguised as replanning.

## Hard safeguards

- **Max 2 replan events per feature.** On the 3rd structural discovery → fall through to escalation.
- **Max 3 new steps per event.** Cannot add more than 3 steps in one replan.
- Both caps are tracked in `.planning/tasks/<active_task>.md`.

## Process

### Step 1 — Confirm structural nature

Is this a structural gap (missing spec, missing AC, missing design decision) or a fix-in-place issue?
- Fix-in-place → do not invoke this skill. Use the 3-cycle review-fix loop.
- Structural → continue.

### Step 2 — Classify amendment type

| Pattern | Amendment type |
|---------|---------------|
| A | Spec correction + optional new step |
| B | New implementation step for uncovered EC |
| C | New handler + TC for orphaned endpoint |
| D | New preparatory step to create the missing dependency |

### Step 3 — Verify caps

Read `.planning/tasks/<active_task>.md` for `replan_count`. If `replan_count >= 2` → do NOT invoke. Escalate to PO instead.

### Step 4 — Write the amendment

Insert new steps into `feature.md § Implementation plan` at the appropriate position. Mark with an HTML comment: `<!-- REPLAN-N -->`.

New steps must follow the same format as existing steps:
- Goal
- Owned ACs / ECs / TCs
- Files to create or modify
- Public signatures
- Guidelines to follow

### Step 5 — Record

Append to `.planning/tasks/<active_task>.md`:

```markdown
## Replan-N — <ISO timestamp>
- Trigger: Pattern <A|B|C|D>
- Reason: <one sentence>
- Steps added: <N>
- New steps: Step X (REPLAN-N), Step Y (REPLAN-N)
```

Increment `replan_count` in the task file.

### Step 6 — Notify PO

If `auto_approve.feature = false` → surface the amendment to PO and await `/kit-approve` before resuming EXECUTE.

If `auto_approve.feature = true` → log "auto-approved replan-N" and continue.
