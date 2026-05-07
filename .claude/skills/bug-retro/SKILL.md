---
name: bug-retro
description: Post-bug retrospective — root cause analysis, prevention measures, and guideline updates. Mandatory for CRITICAL/HIGH defects (auto-trigger; no PO request needed). Produces at least one regression test or guideline update before closing.
---

# Bug Retro Skill

Post-bug retrospective focused on preventing recurrence through systemic improvements.

## When to use

**Auto-trigger (no PO request needed):**
- Defect severity is CRITICAL or HIGH (from the Defects log in test-cases.md).
- `@BugFixer` detects a systemic gap (same root cause appeared in a previous fix).

**On PO request:**
- Any severity.

**Skip when:**
- The fix was a trivial typo / copy error with no underlying cause.
- Known external regression that was simply reverted with no code change.

## Process

### Step 1 — Root cause analysis (5 Whys)

Ask "Why?" five times, starting from the symptom:
1. Why did the test fail? (observable symptom)
2. Why did the code behave that way? (code-level cause)
3. Why was that code written that way? (design decision)
4. Why wasn't it caught earlier? (process / test gap)
5. Why did the process gap exist? (systemic cause)

The answer to Why #5 is the actionable systemic cause.

### Step 2 — Classification

Classify the root cause:
- `missing-test` — a scenario was never covered by tests.
- `guideline-gap` — the forbidden pattern isn't in CLAUDE.md or the module guideline.
- `architectural-issue` — the design made this class of bug likely (layering violation, shared mutable state, etc.).
- `external-regression` — upstream dependency changed behavior.
- `other` — none of the above.

### Step 3 — Action items

Define specific corrective measures:

| Priority | Action | Type | Owner |
|----------|--------|------|-------|
| 1 | Write regression test for TC-NN | test | @CodeWriter |
| 2 | Add pattern to CLAUDE.md | guideline | @Main |
| 3 | Refactor X to Y | code | @CodeWriter |

At least one action must be Type=test OR Type=guideline. This is non-negotiable.

### Step 4 — Implementation

Execute prioritized actions:

- **test** → dispatch `@CodeWriter` to write the regression test. Must be committed and green before retro closes.
- **guideline** → append to the relevant guideline file (`vault/guidelines/<module>/` or CLAUDE.md).
- **code** → create a tech-debt entry via `tech-debt-record` skill (do not refactor now unless trivial).

### Step 5 — Documentation

Append to `vault/features/<module>/<feature>/retro.md` (create if absent):

```markdown
## Retro — TC-NN — <ISO timestamp>

**Defect:** <one-line summary>
**Severity:** CRITICAL / HIGH / MEDIUM / LOW
**Root cause:** <result of 5 Whys>
**Classification:** <missing-test / guideline-gap / architectural-issue / ...>

### Fix applied

| File | Change |
|------|--------|

### Regression test

TC-NN → `<test file path>:<line>`

### Actions taken

| Action | Type | Done? |
|--------|------|-------|

### Lesson

<one sentence: what this teaches about the codebase>
```

Call `knowledge-my-app_update_doc` on retro.md after writing.

## Critical rules

- At least one `test` or `guideline` action must be **completed** before closing the retro. Not just listed — done.
- Focus on systemic improvement, not blame.
- Actions must be concrete and specific. "Be more careful" is not an action.
