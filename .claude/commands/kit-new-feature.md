---
description: Start a new feature, bug fix, or tech task. Argument: $FEATURE_DESCRIPTION. Delegates to @Main for full orchestration.
---

You are a Senior project orchestrator. Your task is to start a new feature, bug fix, or tech task and hand it off to `@Main` for full pipeline execution.

Description: $FEATURE_DESCRIPTION

Hand off to `@Main` with the following prompt:

```
New task: $FEATURE_DESCRIPTION

Type: FEATURE (or clarify if this is BUG/TECH)
```

`@Main` will execute the v5 FEATURE pipeline:

1. **CLASSIFY & CLARIFY** — minimal questions (module, description, UI?, constraints).
2. **ANALYSIS** — dispatch `@Analyst` to write a single `feature.md` (Why / ACs / Edge Cases / How it works / Test plan); then `@TestKeeper MODE=GENERATE` to create the live `test-cases.md`.
3. **PLAN** — `superpowers:writing-plans` writes the Implementation plan as a section inside the same `feature.md`. UI features → `@Designer` appends a `## UI / UX` section. `@TestKeeper MODE=DRAFT` adds impl-level TCs.
4. **CONFIRM** — show summary; wait for `/kit-approve` (or auto-proceed if `auto_approve: true`).
5. **EXECUTE** — for each step in the plan: `@CodeWriter` (TDD-first) → `@TestKeeper EXECUTE` → `@Reviewer` → fix loop → mark step done. After last step: `@TestKeeper RECONCILE` → `@TraceabilityChecker` → `@DoDGate` (7 hard checks).
6. **CLOSE** — gated on `@DoDGate = PASS`. Update guidelines if patterns emerged.

**Output format:** After handoff, output ONLY the task type confirmation and the first clarifying question from `@Main`. No introductory text.

**Do not call `@CodeWriter`, `@BugFixer`, or other subagents directly — only `@Main`.**
