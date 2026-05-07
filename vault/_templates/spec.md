---
genre: feature-spec
title: Feature Spec Template (frozen at CONFIRM)
topic: feature
triggers:
  - "feature spec"
  - "feature design"
  - "spec"
  - "requirements"
confidence: high
source: human
updated: 2026-05-07T17:37:42Z
---

# <Feature title — plain English, no jargon>

> Status: DRAFT | APPROVED | DONE
> Module: <module>
> Owner: <PO>

<!--
  ⚠ FROZEN at CONFIRM. v6.0+ rule: once @Main step 4 (CONFIRM) passes, this file
  is read-only for the rest of the FEATURE pipeline. EXECUTE-phase agents
  (@CodeWriter, @Reviewer, @TestKeeper, @TraceabilityChecker, @DoDGate,
  replan-on-discovery) MUST NOT edit this file. Mutable state — implementation
  steps, DoD verdict, replan markers — lives in the sibling plan.md.

  If a structural discovery requires changing AC / EC / How-it-works,
  @Main escalates to PO and re-dispatches @Analyst with TYPE=FEATURE
  EXISTING_DOCS=<this spec.md>. The amendment is a new DRAFT cycle, not
  an in-place edit. Spec rot is the failure mode this split prevents.
-->

## Why

2–3 sentences. The user-visible problem this solves. Plain language.
Skip if the feature is TECH (refactor / dependency update / no behaviour change).

## Acceptance Criteria

| ID | Given | When | Then |
|----|-------|------|------|
| AC-1 | <preconditions> | <action> | <observable outcome> |
| AC-2 | ... | ... | ... |

Skip if TECH.

## Edge Cases

| ID | Severity | Scenario | Expected behaviour |
|----|----------|----------|---------------------|
| EC-1 | Critical | <scenario> | <expected> |
| EC-2 | High | ... | ... |
| EC-3 | Medium | ... | ... |

**Severity ladder:**

- **Critical** — data loss, security hole, or system crash if mishandled.
- **High** — wrong result silently, or recovery requires manual intervention.
- **Medium** — visible but recoverable bug.
- **Low** — cosmetic.

Every Critical EC must have at least one PASS TC in the Test plan section.

## How it works

Technical specification: data models, API contracts, internal interfaces, error handling, security considerations. Sub-section as needed.

Reference public signatures (one line each, no method bodies). For example:

```
fun createOrder(req: CreateOrderRequest): Result<Order, OrderError>
class OrderRepository(private val db: Database) { fun save(o: Order): Long }
```

Endpoints (if any):

```
POST /api/v1/orders   → createOrderHandler
GET  /api/v1/orders/:id → getOrderHandler
```

## Test plan

| TC ID | Type | Description | Verifies |
|-------|------|-------------|----------|
| TC-1 | unit | <what + how> | AC-1, EC-1 |
| TC-2 | integration | ... | AC-2 |
| TC-3 | unit | ... | EC-1 |

**Type values:** `unit` | `integration` | `e2e` | `manual`.
**Verifies** lists AC/EC ids covered by this TC. Every Critical EC must appear at least once.

## UI / UX

(Filled by `@Designer` for UI features only. Otherwise omit this section. UI/UX is part of the spec — frozen at CONFIRM along with everything else.)

## Open questions

(Delete this section before APPROVED. Items here block CONFIRM.)
