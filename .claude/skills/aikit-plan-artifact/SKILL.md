---
name: "aikit-plan-artifact"
description: "Authoritative format for `.aikit/plans/<id>.md` and the `Verify` verb vocabulary — the artifact frozen at end of Session 1 and consumed by Session 2 / 3."
---
<skill name="aikit-plan-artifact">

<purpose>
Authoritative format for `.aikit/plans/<id>.md` and the `Verify` verb vocabulary — the artifact frozen at end of Session 1 and consumed by Session 2 / 3.
</purpose>

<when_to_invoke>
| Stage | Trigger |
|---|---|
| Session 1 Stage 2 | writing the plan file before `kit: plan for <slug>` commit |
| Session 2 Initialization | reading the plan to recover invariants, step list, tiers, verify verbs |
| Session 3 step 2 | reading the plan after locating the upstream plan-commit |

The plan file is **frozen** after the Session 1 commit. Do not modify it from inside Session 2 or 3 — see the ban list in the orchestrator constitution.
</when_to_invoke>


<procedure>
## File template

```markdown
# <Task title>

**Created:** <YYYY-MM-DD>
**Branch:** <git branch name>
**Source task:** <verbatim user request, 1–3 lines>

## Context (digest)

<3–10 lines of facts the plan depends on: stack, conventions, constraints, related modules>

## Invariants

<3–5 plan-level boundary statements; each is re-asserted in every STEP SUMMARY. Violating one requires rationale in the step's Plan deviations.>

- <invariant 1>
- <invariant 2>
- ...

## Steps

### Step 1 — <slug>
- **Goal:** <what this step achieves>
- **DoD:** <one-line check>
- **Review:** light | standard | heavy
- **What would be wrong:** <one-line antipattern; required for standard / heavy; `(n/a)` for light>
- **Verify:** [<verb>, ...]   (verbs from `Verify verbs` table; default `[compile, test]`; for one-off `shell: "<cmd>"`)
- **Expect:** green             (v3.1 only accepts `green` here; deliberate red stops use `--keep-red` at execute time)
- **Shape:** (required for `light`, optional for standard / heavy — bounds the diff so a scope-creeping `light` is caught mechanically)
    - **files-glob:** "<glob>"        # paths the diff is allowed to touch
    - **max-diff-lines:** <N>          # additions + deletions cap
    - **no-test-changes:** true | false
- **Assumptions:** <if any>

### Step 2 — <slug>
...

## Out of scope

- <intentionally deferred items>
```

Keep it under 2 screens. Both Session 2 (execute) and Session 3 (fix) read this file — brevity matters.

## Verify verbs vocabulary

When a plan step declares `verify: [<verb>, ...]`, each verb resolves through the active language profile's `stack` block in the manifest. The vocabulary is intentionally minimal — three verbs cover the common gates; anything else uses `shell:` override.

| Verb | Resolves to | Purpose |
|---|---|---|
| `compile` | `stack.compile_command` | Fast type / syntax check without running |
| `test` | `stack.test_command` | Test suite the project considers stable |
| `lint` | `stack.lint_command` | Style and static-analysis checks (typically subsumes format-check in modern configs) |

Any `[module]` placeholder in a profile command is substituted with the manifest's `stack.module` value when set, or removed (with the surrounding `:` collapsed) when empty.

Escape hatch — `shell: "<command>"` runs the literal command verbatim. Use sparingly; a step that's mostly `shell:` indicates either a missing profile field or that the verb vocabulary needs expanding.

The `verify` field on each step is consumed by Session 2's per-step verify run (Stage 3 step 4) and by Session 3's post-fix verify (Session 3 step 6). Session 2's gate decision (Stage 3 step 7) blocks `next` on `BUILD: red` until `/kit-fix` resolves it or `--keep-red` overrides explicitly.

## Plan id convention

`<YYYY-MM-DD>-<short-slug>` — date is the day the plan was authored (UTC ok). Slug is kebab-case, ≤4 words, derived from the task title. Examples: `2026-05-12-stylus-channel`, `2026-05-12-rename-listener`.

The id is the argument both `/kit-do <id>` and `/kit-fix` (indirectly, via plan-commit walk) accept.
</procedure>

<output_format>
A single markdown file at `.aikit/plans/<id>.md` matching the template above, committed in Session 1 with message `kit: plan for <slug>`. No frontmatter. No deviations from the section headers — Session 2 parses them positionally.
</output_format>

</skill>
