---
name: pre-mortem
description: Lightweight pre-execute risk pass. Run by @Main between PLAN (writing-plans) and CONFIRM, and again between writing-plans and EXECUTE for tasks marked HIGH risk. Forces an "imagine it failed in production — what was the cause?" lens before any code is written. Adds findings to the plan as risks; does not modify spec or requirements.
---

# Pre-Mortem Skill

A pre-mortem (Gary Klein) is the inverse of a post-mortem: assume the project has already failed, then work backwards to plausible causes. It is the single cheapest quality intervention available to a planning step — five minutes of structured pessimism before any code is written.

## When to use

`@Main` invokes this skill at FEATURE step **4.5** (between PLAN and CONFIRM) and at TECH step **2.5** (between PLAN and CONFIRM). Re-invoke after a major plan change.

Skip when:
- Task is purely cosmetic (copy / styling / config rename) and modifies no business logic.
- Task is a tech-debt entry classified DIRECT in `/kit-techdebt` (low/medium severity, ≤2 files).

## Invocation

`@Main` reads this skill and runs the steps below itself. There is **no separate agent** — the skill is light enough to live in the orchestrator's turn. Use approximately one model turn; do not let it grow into a second `requirements-pipeline`.

## Inputs

```
Feature / Task: [feature-name or task slug]
Module(s):      [list]
Plan file:      vault/concepts/[module]/plans/[feature]-plan.md
Stage files:    [list of stage paths]
Spec file:      vault/reference/[module]/spec/[feature].md  (FEATURE only — N/A for TECH)
Corner cases:   vault/concepts/[module]/plans/[feature]-corner-cases.md  (FEATURE only)
```

## Steps

### Step 1 — Set the scene (10 seconds, internal reasoning)

State the goal in one sentence:

> "Six weeks from now, this feature is live. It failed catastrophically. Users are angry, PO is on a call, the on-call engineer is rolling back."

The job for the next four minutes is to **list plausible causes**, not to defend the plan.

### Step 2 — Run the 8 risk lenses

For each lens, write down at most **two** concrete failure scenarios specific to this feature. If a lens has no plausible failure, write `n/a` and a one-clause reason. **Do not repeat scenarios already on the corner-case register** — that is a separate document; this is about what the *plan and execution* might miss.

| # | Lens | Question to ask |
|---|------|-----------------|
| 1 | **Wrong target** | We built exactly what the spec said, but the spec was wrong / the user need was different. What is the most likely way the spec misreads the user need? |
| 2 | **Hidden dependency** | The feature depends on a system / migration / data state we did not name. What is it? |
| 3 | **Unconsidered scale** | The feature works at scale 1, breaks at scale N. What is N realistically, and which step explodes first? |
| 4 | **Concurrency / ordering** | Two user / system actors race. What is the worst interleaving and what does it produce? |
| 5 | **Failure recovery** | Step 3 of 5 fails after a side effect. What state are we left in, and how does the user / next request recover? |
| 6 | **Rollout & migration** | Existing data / existing sessions hit the new code path. What breaks for them that does not break for fresh ones? |
| 7 | **Observability** | The feature degrades silently. How would we even know? What signal would alert us? |
| 8 | **Reversibility** | We need to roll this back at 2am. Can we? Schema changes? Irreversible writes? |

Output for each lens is one row in a Risks table.

### Step 3 — Classify by likelihood × impact

For each risk, assign:

| Likelihood | Impact | Combined |
|------------|--------|----------|
| H / M / L | H / M / L | (H,H), (H,M), (M,H) → ACT NOW. Others → record. |

A risk in the ACT NOW bucket means the plan must already include a mitigation — either a stage step, a corner-case register entry, a spec section, or an explicit deferral. If no mitigation exists, the plan is incomplete.

### Step 4 — Map to plan changes

For each ACT NOW risk, do exactly one of:

- **Add a stage task** — append a TODO row to a stage file (e.g. "stage 03 — add idempotency key on POST /orders"). Cite the risk-id.
- **Add a regression / boundary TC** — dispatch `@QA` (Phase=IMPLEMENTATION, Mode=DRAFT) with the new scenario. Cite the risk-id in the Description prefix `[premortem-R3]`.
- **Add an observability hook** — note the metric / log line in the relevant stage file under "Observability".
- **Defer with explicit acceptance** — record a `risk_accepted:` line in the active task file with rationale. PO must agree at CONFIRM.

For non-ACT-NOW risks: record on the plan but do not mandate action.

### Step 5 — Append the Risks block to the plan file

Append (do not overwrite) a `## Pre-mortem risks` section to the plan file:

```markdown
## Pre-mortem risks

| R# | Lens | Scenario | Likelihood | Impact | Mitigation |
|----|------|----------|------------|--------|------------|
| R1 | Concurrency | Two users add same item to cart simultaneously, both succeed past a stale stock check | M | H | Stage 04 — atomic stock decrement; TC-12 (premortem-R1) added |
| R2 | Reversibility | New `orders.status_v2` column is not nullable — rollback requires a backfill | L | H | Stage 02 — nullable column + backfill; release plan note |
| R3 | Observability | Order-creation timeout silently 504s | M | M | Stage 03 — emit `order.create.timeout` counter |
```

Then `knowledge-my-app_update_doc` on the plan file.

### Step 6 — Surface to PO at CONFIRM

The CONFIRM step (Main FEATURE step 6) shows PO the plan summary. Pre-mortem must add **one line** to that summary:

```
Pre-mortem: N risks, K acted-on, M deferred (see plan → "Pre-mortem risks").
```

PO can then answer for each `risk_accepted:` line in the active task file. This is the only PO touch-point for the skill — it does not loop or block.

## Quality rules

- **Specific, not generic.** "Performance might suffer" → reject. "P95 of /orders search at 100k records exceeds 2s based on no index on `customer_id`" → accept.
- **Domain-specific, not template-filling.** A pre-mortem that lists "scaling issues" for every feature regardless of context has not done the work.
- **Maximum 2 scenarios per lens.** If you have 5 ideas for one lens, the top 2 are usually right; the rest are noise.
- **No self-evident scenarios.** "What if there is a bug?" is not a risk — it is the default. Pre-mortem captures *non-obvious specific* failure modes.
- **Distinct from corner-case register.** Corner-case register is at the requirements/spec level — domain invariants. Pre-mortem is at the plan/execution level — what the plan / rollout might miss.

## Anti-patterns

- Pre-mortem after EXECUTE is started. Useless — code is written, plan is locked.
- Pre-mortem that copies the corner-case register. Different layer.
- Pre-mortem run by a separate agent dispatched by `@Main`. Overkill — skill lives in the orchestrator's turn.
- Pre-mortem with no `Mitigation` cells filled. The whole point is mapping to action.
- Pre-mortem that only lists `H,H` risks. The combined matrix exists to filter — list the others, just do not act on them.

## Notes

- This skill is documentation + procedure. It does not write code, run tests, or modify the spec.
- The skill output is **the Risks table appended to the plan file** — that is the artifact `@DoDGate` and reviewers see at the end.
- Pre-mortem is run **before** `@AutoApprover` (in `AUTO_APPROVE=true` mode); `@AutoApprover` reads the Risks table and may reject if it is empty for non-trivial features.
