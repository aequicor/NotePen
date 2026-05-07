---
name: pre-mortem
description: Lightweight pre-execute risk pass. Optional in v5 — invoke on demand for non-trivial features when @Main / PO judges it useful. Forces an "imagine it failed in production — what was the cause?" lens before any code is written.
---

# Pre-Mortem Skill

A pre-mortem (Gary Klein) is the inverse of a post-mortem: assume the project has already failed, then work backwards to plausible causes. Five minutes of structured pessimism before any code is written.

## v5 status — optional

In v4 this skill was **mandatory** between PLAN and CONFIRM for every FEATURE and TECH task. v5 makes it optional: `@Main` invokes it on demand when a feature is risky enough to warrant it (high blast radius, irreversible changes, security-critical surface). PO can also request it explicitly with `/kit-config` (or by asking `@Main`).

The skill is light enough to live in `@Main`'s turn — there is no separate agent.

## When to use

Run pre-mortem when **any** of these is true:

- The feature touches schema migrations, payments, auth, PII, or cross-service contracts.
- The feature is the largest of the current quarter (e.g. > 6 implementation steps).
- A previous similar feature shipped with a serious defect.
- PO explicitly asks for it.

Skip when:

- Task is purely cosmetic (copy / styling / config rename) and modifies no business logic.
- Task is a tech-debt entry classified DIRECT in `/kit-techdebt`.
- Task is a small localized bug fix (`/kit-fix`).

## Inputs

`@Main` runs this skill itself, no external dispatch. It needs:

```
Feature / Task: [feature-name or task slug]
Module(s):      [list]
Feature doc:    vault/features/<module>/<feature>/plan.md
```

The Risks block is appended to the same `plan.md` (a new section); not a separate file.

## Steps

### Step 1 — Set the scene (10 seconds, internal reasoning)

State the goal in one sentence:

> "Six weeks from now, this feature is live. It failed catastrophically. Users are angry, PO is on a call, the on-call engineer is rolling back."

The job for the next four minutes is to **list plausible causes**, not to defend the plan.

### Step 2 — Run the 8 risk lenses

For each lens, write at most **two** concrete failure scenarios specific to this feature. If a lens has no plausible failure, write `n/a` and a one-clause reason. **Do not repeat scenarios already in plan.md § Edge Cases** — that is a separate document; this is about what the *plan and execution* might miss.

| # | Lens | Question to ask |
|---|------|-----------------|
| 1 | **Wrong target** | We built exactly what the spec said, but the spec was wrong / the user need was different. |
| 2 | **Hidden dependency** | The feature depends on a system / migration / data state we did not name. |
| 3 | **Unconsidered scale** | Works at scale 1, breaks at scale N. What is N realistically, and which step explodes first? |
| 4 | **Concurrency / ordering** | Two actors race. What is the worst interleaving and what does it produce? |
| 5 | **Failure recovery** | Step 3 of 5 fails after a side effect. What state are we left in? |
| 6 | **Rollout & migration** | Existing data / sessions hit the new code path. What breaks for them? |
| 7 | **Observability** | The feature degrades silently. How would we know? |
| 8 | **Reversibility** | We need to roll this back at 2am. Can we? Schema changes? Irreversible writes? |

Output for each lens is one row in a Risks table.

### Step 3 — Classify by likelihood × impact

For each risk, assign:

| Likelihood | Impact | Combined |
|------------|--------|----------|
| H / M / L | H / M / L | (H,H), (H,M), (M,H) → ACT NOW. Others → record. |

A risk in the ACT NOW bucket means the plan must already include a mitigation — either a step in the implementation plan, an Edge case entry, a How-it-works section, or an explicit deferral. If no mitigation exists, the plan is incomplete.

### Step 4 — Map to plan changes

For each ACT NOW risk, do exactly one of:

- **Add a plan step** — append a step to `plan.md § Implementation plan`. Cite the risk-id (R-N) in the step description.
- **Add a regression / boundary TC** — dispatch `@TestKeeper MODE=DRAFT` (or, after CONFIRM, `MODE=APPEND`) with the new scenario. Cite the risk-id in the TC Description prefix `[premortem-R3]`.
- **Add an observability hook** — note the metric / log line in the relevant step under "Observability".
- **Defer with explicit acceptance** — record `risk_accepted:` line in the active task file with rationale. PO must agree at CONFIRM.

For non-ACT-NOW risks: record in the Risks table but do not mandate action.

### Step 5 — Append the Risks block to plan.md

Append (do not overwrite) a `## Pre-mortem risks` section to `plan.md`:

```markdown
## Pre-mortem risks

| R# | Lens | Scenario | Likelihood | Impact | Mitigation |
|----|------|----------|------------|--------|------------|
| R1 | Concurrency | Two users add same item, both succeed past stale stock check | M | H | Step 4 — atomic stock decrement; TC-12 added |
| R2 | Reversibility | New `orders.status_v2` column not nullable — rollback needs backfill | L | H | Step 2 — nullable column + backfill; release plan note |
| R3 | Observability | Order-creation timeout silently 504s | M | M | Step 3 — emit `order.create.timeout` counter |
```

Then `knowledge-my-app_update_doc` on `plan.md`.

### Step 6 — Surface to PO at CONFIRM

The CONFIRM step shows PO the plan summary. Pre-mortem adds **one line**:

```
Pre-mortem: N risks, K acted-on, M deferred (see plan.md § Pre-mortem risks).
```

PO can then answer for each `risk_accepted:` line.

## Quality rules

- **Specific, not generic.** "Performance might suffer" → reject. "P95 of /orders search at 100k records exceeds 2s; no index on `customer_id`" → accept.
- **Domain-specific, not template-filling.** A pre-mortem listing "scaling issues" for every feature has not done the work.
- **Maximum 2 scenarios per lens.** If you have 5 ideas, the top 2 are usually right; the rest are noise.
- **No self-evident scenarios.** "What if there is a bug?" is not a risk — it is the default.
- **Distinct from edge cases.** Edge cases are domain invariants in `plan.md § Edge Cases`. Pre-mortem is at the plan/execution level — what the plan / rollout might miss.

## Anti-patterns

- Pre-mortem after EXECUTE is started. Useless — code is written.
- Pre-mortem that copies the Edge Cases section. Different layer.
- Pre-mortem run by a separate agent. Overkill — skill lives in the orchestrator's turn.
- Pre-mortem with no `Mitigation` cells filled. The whole point is mapping to action.
- Pre-mortem that only lists `H,H` risks. The combined matrix exists to filter — list the others, just do not act on them.

## Notes

- This skill is documentation + procedure. It does not write code, run tests, or modify the spec.
- The skill output is **the Risks table appended to plan.md** — that is the artifact `@DoDGate` ignores (info-only) but PO reads at CONFIRM.
