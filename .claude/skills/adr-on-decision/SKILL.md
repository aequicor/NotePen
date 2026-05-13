---
name: "adr-on-decision"
description: "Record an Architecture Decision Record (`.aikit/adr/<id>.md`) when a step or fix makes a non-trivial, hard-to-reverse choice — interface shape, persistence layer, dependency adoption, public API contract."
---
<skill name="adr-on-decision">

<purpose>
Record an Architecture Decision Record (`.aikit/adr/<id>.md`) when a step or fix makes a non-trivial, hard-to-reverse choice — interface shape, persistence layer, dependency adoption, public API contract.
</purpose>

<when_to_invoke>
| Stage | Trigger |
|---|---|
| Session 1 Stage 2 | the plan introduces a new public API, new persistence layer, or new top-level dependency |
| Session 2 Stage 3 step 2 | the step's `What would be wrong` line is itself a choice between two viable options |
| Session 3 Stage 2 | the fix selects one of multiple ways to repair the defect and others would have been defensible |

Skip when: the change is mechanically forced by the plan (rename, lift, format), or the alternatives were ruled out by an obvious constraint (e.g. licensing, platform). An ADR for a forced choice is bureaucratic ceremony.
</when_to_invoke>


<procedure>
One file. One screen. Eight sections. The point is the trail — a future reader should reconstruct the decision in 90 seconds.

## File location

`.aikit/adr/<NNNN>-<short-slug>.md` — `NNNN` is the next zero-padded integer in `.aikit/adr/`. Slug is kebab-case, ≤4 words, derived from the decision. The directory may not exist yet — create it.

## File template

```markdown
# ADR-<NNNN>: <short title>

**Status:** Proposed | Accepted | Superseded by ADR-<NNNN>
**Date:** <YYYY-MM-DD>
**Plan / commit:** <plan-id or commit short SHA>

## Context

<3–6 lines of facts the decision rests on. Stack constraints, prior code, deadlines.>

## Decision

<1–3 lines naming the chosen option in concrete terms — a class, library, schema, endpoint shape.>

## Alternatives considered

- **<Option A>** — <why rejected, one line>
- **<Option B>** — <why rejected, one line>

## Consequences

- <one line on what becomes easier>
- <one line on what becomes harder>
- <one line on what is now load-bearing and must not regress silently>

## Reversal cost

<one line: how hard is it to undo this in 6 months? data-migration, public-API break, dependency rip-out, or trivial?>
```

Keep it under 60 lines. ADRs that read like essays do not get read.

## Numbering

The number is monotonic — never reuse, never reorder. A superseded ADR keeps its number; the replacement ADR cites it under Status.

## Anti-patterns this skill prevents

- ADR-as-documentation. ADRs record *decisions*, not how code works. Code documentation lives next to the code.
- Retro-ADRs that justify what already shipped. The reversal-cost section forces forward thinking; backdating loses that value.
- Two ADRs for one decision. If the second is a refinement, supersede the first — don't fork.
- ADR for every step. The trigger table is the gate. A `light` step almost never deserves one.
</procedure>

<output_format>
A single markdown file at `.aikit/adr/<NNNN>-<slug>.md`, committed in the same step or fix commit that introduces the decision. The STEP / FIX SUMMARY's `Plan deviations` section cites the new ADR by number (`ADR-0007 created — chose Room over SQLDelight for shared persistence`).
</output_format>

</skill>
