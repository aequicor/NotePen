---
name: "doubt-driven-review"
description: "Adversarial fresh-context self-review of a just-committed step — re-read the diff as if a stranger wrote it, looking for what a confident author would defend instead of fix."
---
<skill name="doubt-driven-review">

<purpose>
Adversarial fresh-context self-review of a just-committed step — re-read the diff as if a stranger wrote it, looking for what a confident author would defend instead of fix.
</purpose>

<when_to_invoke>
| Stage | Trigger |
|---|---|
| Session 2 Stage 3 step 5 | after STEP SUMMARY is drafted, before replying `next` |
| Session 2 Stage 4 backstop | reviewing the cumulative `git diff <plan-commit>..HEAD` |
| Session 3 step 7 | after the fix commit, before END |

Especially load-bearing on `light` steps — they get the least review attention but are exactly where scope creep and shortcut-fixes hide.
</when_to_invoke>


<procedure>
Three questions, asked in order. Answer each in the persona of someone who did not write the code. The role-swap matters — your own confidence is the bias this skill defends against.

## 1. "What would I push back on in code review?"

Read the diff with hostile eyes. Look for the four moves an author defends but a reviewer flags:
- A magic constant ("why 47?") whose origin is not in the diff or a nearby comment.
- A name that mismatches its scope (`data` for a single user, `process` for a one-line transform).
- A control-flow shortcut (early return, swallowed error, fall-through) that saves three lines now at the cost of debugging next quarter.
- An abstraction that exists for exactly one caller.

Any push-back becomes a STEP SUMMARY Uncertain entry, or — if material — a `/kit-fix` candidate.

## 2. "What did I assume that the diff doesn't prove?"

Walk the diff against the plan's Invariants section. For each invariant, ask: does the diff demonstrate it holds, or does it just not violate it visibly? The two are not the same. If the answer is "not visibly," demote your confidence in the Uncertain section.

Common invisible assumptions:
- "The caller never passes null" — proven only by reading every caller.
- "This thread holds the lock" — proven only by reading the scheduling.
- "The cache is warm by then" — proven only by reading the init order.

## 3. "If this step regresses next sprint, where will the post-mortem land?"

Imagine the bug report: which line, which behavior, which test missed it. If the answer is "I'd add a test for that," add it now or write the missing test as the next step's Plan deviation. If the answer is "we'd revert the whole step," reconsider whether the step is too big.

## Anti-patterns this skill prevents

- Self-review that just re-reads the author's own justification. The skill works only if you change roles.
- Skipping on `light` because "it's small" — light is where unreviewed scope creeps in.
- Filing every micro-doubt as Uncertain. The bar is: would a fresh reviewer flag it? If not, drop it.
</procedure>

<output_format>
Findings fold into the STEP SUMMARY (or FIX SUMMARY) Uncertain section: one bullet per real concern, citing `path:line`. If a finding is material (would block a `next` reply), it becomes the Plan-deviations item for the next step instead.
</output_format>

</skill>
