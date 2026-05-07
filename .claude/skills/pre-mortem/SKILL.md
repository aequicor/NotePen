---
name: pre-mortem
description: Lightweight pre-execute risk pass (optional in v5). Inverts post-mortem thinking — assume the feature failed in production and work backward to causes. Appends a Risks table to feature.md. Run on demand for risky features (schema migrations, payments, auth, PII, cross-service contracts).
---

# Pre-Mortem Skill

The pre-mortem is a lightweight risk-assessment technique that inverts post-mortem thinking: assume failure and work backward to causes. In v5, it is **optional** — run it on demand for risky features.

## When to use

**Run when the feature involves:**
- Schema migrations or database DDL changes
- Payments or financial calculations
- Authentication / authorization changes
- PII data retention or exposure
- Cross-service contracts or API changes
- Significant scope (3+ implementation steps)

**Skip when:**
- Cosmetic changes (color, layout, copy)
- Small isolated bug fixes
- Single-file refactors with no behavior change

## Process

### Step 1 — Frame the failure

Imagine: "This feature shipped, and in 30 days something went badly wrong in production."

What catastrophic outcome occurred? Write 1–2 sentences.

### Step 2 — Apply 8 risk lenses

For each lens, identify up to 2 specific risks (not generic). Vague → discard.

| Lens | Question |
|------|----------|
| 1. Wrong target | Did we solve the wrong problem? |
| 2. Hidden dependencies | What breaks if a dependency changes? |
| 3. Scale limits | At 10x data/traffic, what fails first? |
| 4. Concurrency | What race conditions exist? |
| 5. Recovery paths | What happens when it partially fails? |
| 6. Rollout | What goes wrong during gradual rollout? |
| 7. Observability | What will we miss in monitoring/logs? |
| 8. Reversibility | How painful is rollback? |

**Quality bar for a valid risk:** "P95 search latency exceeds 2s at 100k records due to missing index" — PASS. "Performance might suffer" — FAIL (too vague).

### Step 3 — Score risks

For each risk:
- **Likelihood:** Low / Medium / High
- **Impact:** Low / Medium / High / Critical
- **Priority:** High likelihood × High impact = ACT-NOW

### Step 4 — Map to mitigations

For each ACT-NOW risk, define one mitigation:
- Additional implementation step → add to feature.md § Implementation plan
- New test case → add to feature.md § Test plan
- Observability hook → note in § How it works
- Documented deferral → note in § Edge Cases

### Step 5 — Document

Append to `feature.md`:

```markdown
## Pre-mortem risks

| # | Lens | Risk | Likelihood | Impact | Priority | Mitigation |
|---|------|------|------------|--------|----------|------------|
| R1 | Scale | P95 latency > 2s at 100k records (missing index) | High | High | ACT-NOW | Add DB index in Step 2 |
| R2 | Recovery | Partial file upload leaves orphaned temp files | Medium | Medium | MONITOR | Add cleanup job + test |

**ACT-NOW count:** N
```

Call `knowledge-my-app_update_doc` on feature.md after writing.

## Notes

This skill is optional in v5 — it was mandatory in v4 but the ceremony cost outweighed the benefit for most features. Use it when the risk profile justifies it.

Cap at 2 risks per lens to prevent noise. More than 16 total risks in a pre-mortem is a sign the scope is too large — split the feature.
