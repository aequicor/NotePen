---
name: eval-collector
description: Auto-fills evals/runs/<kit_version>/<task-slug>.md at task CLOSE. Activated by presence of evals/runs/ directory — no-op if absent. Optional v5.2+ skill.
---

# Eval-Collector Skill (optional, v5.2+)

Automates telemetry collection at task completion. Fills ~70% of metrics from the filesystem; marks the remaining 30% as `(manual)`.

## Activation

The `evals/runs/` directory is the opt-in signal. If it exists → run this skill at task CLOSE. If absent → no-op silently.

`@Main` checks for this directory at step 6 (CLOSE) and at step 4 of the BUG pipeline.

## What gets automated

From the filesystem:

- Artifact sizes: feature.md, test-cases.md, retro.md (if present)
- AC count, EC count (Critical/High), TC count, PEND count
- TestKeeper RECONCILE verdict
- Reviewer verdict
- DoDGate verdict
- Checkpoint timestamps (for duration calculation)
- Replan event count (from task file)
- Plan step count vs completed step count

## What requires manual entry

Fields marked `(manual)` in the output:

- Token usage (not available from filesystem)
- Post-closure bugs found
- Subjective quality rating

## Process

1. Read `.planning/tasks/<active_task>.md` for timeline and metadata.
2. Read `vault/features/<module>/<feature>/feature.md` for AC/EC/step counts.
3. Read `vault/features/<module>/<feature>/test-cases.md` for TC counts and verdicts.
4. Compute duration from first DONE checkpoint to CLOSE timestamp.
5. Write to `evals/runs/<kit_version>/<task-slug>.md`.

## Output format

```markdown
---
kit_version: v5
task: <task-slug>
type: FEATURE | BUG | TECH
module: <module>
date: <ISO date>
---

# Eval — <task-slug>

## Metrics

| Metric | Value |
|--------|-------|
| Duration | <N> hours |
| AC count | <N> |
| Critical EC count | <N> |
| TC count | <N> |
| Replan events | <N> |
| Steps completed | <N>/<total> |
| TestKeeper RECONCILE | ALL_GREEN / RECONCILE_GAP |
| Reviewer | CLEAN / CRITICAL_OR_HIGH_FOUND |
| DoDGate | PASS / BLOCK |
| Token usage | (manual) |
| Post-closure bugs | (manual) |
```

## Hard constraints

- Never overwrite existing run records. Append a version suffix (`-v2`) instead.
- Make no external API calls.
- Mark non-derivable fields as `(manual)` — never invent values.
- Produce identical output on re-runs.
