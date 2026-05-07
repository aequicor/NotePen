---
name: corner-case-refinement
description: Systematic corner case discovery during business requirements phase. Runs BEFORE spec and design — surfaces domain invariants, boundary conditions, failure modes, and concurrency scenarios at the business planning level. Corner cases found during implementation are a planning failure.
---

# Corner Case Refinement Skill

Business-level corner case analysis. Runs during the requirements phase — after clarifying questions with PO, before spec, design, or implementation plan. The goal is to surface every meaningful "what if" at the business domain level so the spec can address them proactively.

## Invocation Interface

Called by `@Main` via the `requirements-pipeline` skill (Step 2.5) or directly with:

```
Feature: [feature-name, snake_case]
Module: [module name from manifest]
Requirements file: [path to vault/concepts/[module]/requirements/[feature].md]
```

The skill reads the requirements file, runs the 7-category analysis, and writes the corner case register to:
`vault/concepts/[module]/plans/[feature]-corner-cases.md`

where `[module]` and `[feature]` are substituted from the input fields above.

**Important:** The `Requirements file` input must point to the **final version** of the requirements — after all BA update iterations are complete. The skill reads whatever is currently in that file; it does not track history.

## When to Use

**Primary trigger — REQUIRED before every FEATURE spec:**
- Immediately after PO answers clarifying questions (Main step 0)
- Before any spec, design document, or implementation plan is written

**Secondary triggers:**
- Bug retrospective identifies systematic gaps in corner case analysis
- A feature delivered with surprise edge cases (process failure — run retro)

**Do NOT activate:**
- During implementation — corner case discovery during coding = planning failure
- As a gate on already-written plans — by then it's too late; start over

## Business Corner Case Taxonomy

These 7 categories are designed for the **business requirements** phase. Each question is framed at the domain level, not the code level.

**Balance rule (mandatory):** categories 4–6 (external dependencies, scale, concurrency)
describe infrastructure failure modes — they tend to dominate output if you let them.
Categories 1, 2, 3, 7 describe how everyday users actually break features. **At least
one third of the final corner case rows MUST come from categories 1/2/3/7, and
category 7 must produce at least one row for any feature with a user interface.**
If your register is mostly "server down", "rate limit hit", and "10k concurrent users",
you have produced an infrastructure threat model, not a user-journey corner case
register — re-run with category 7 explicitly in mind.

### 1. Input Integrity Boundaries

For every piece of data entering the system, ask at the business level:

| Business question | Why it matters |
|-------------------|----------------|
| What if the mandatory field is left blank by the user? | Form state, UX flow |
| What if the value is technically in range but nonsensical (e.g. birth date in 2099)? | Data quality |
| What if international characters break formatting assumptions? | Globalization |
| What if the user pastes formatted text where plain text is expected? | Data sanitization |
| What if two input fields contradict each other (e.g. startDate > endDate)? | Business rule enforcement |

### 2. Business Process Integrity

For every business operation, ask:

| Business question | Why it matters |
|-------------------|----------------|
| What if the user submits the same order twice by double-clicking? | Idempotency |
| What if a required prerequisite step was skipped? | Process ordering |
| What if the system crashes between step 2 and step 3 of a 5-step workflow? | Atomicity, recovery |
| What if the user reverses a decision (cancel order, undo approval)? | Reversibility |
| What if the same entity is modified by two users simultaneously? | Conflict resolution |
| What if the entity is in the wrong state for this operation? | State machine enforcement |

### 3. Business Rule Violations & Domain Invariants

For every business rule, ask:

| Business question | Why it matters |
|-------------------|----------------|
| What if the quantity goes negative? | Invariant enforcement |
| What if the balance falls below zero after an operation? | Financial integrity |
| What if a user tries to access another user's private data? | Authorization boundaries |
| What if a parent entity is deleted while children still exist? | Referential integrity |
| What if a unique constraint is violated (duplicate email, duplicate order ID)? | Identity enforcement |
| What if circular references form (manager → manager, folder → parent)? | Data integrity |
| What if a time-limited operation outlives its window? | Temporal constraints |

### 4. External Dependency Failure Modes

For every external system the feature depends on, ask:

| Business question | Why it matters |
|-------------------|----------------|
| What if the payment gateway is down for 30 minutes? | Business continuity |
| What if the external API returns an unexpected response format? | Defensive design |
| What if the rate limit is hit at peak traffic? | Graceful degradation |
| What if the external service responds but with wrong data? | Trust boundaries |
| What if the auth token expires mid-batch operation? | Session continuity |
| What if the third-party library has a breaking change? | Version lock strategy |

### 5. Scale & Capacity Boundaries

For every data flow, ask:

| Business question | Why it matters |
|-------------------|----------------|
| What is the business-expected max? The technical max? The pathological max? | Capacity planning |
| What if the user uploads a 2 GB file where 10 MB was expected? | Resource limits |
| What happens with zero results? One result? | Boundary UX |
| What if the batch size doesn't evenly divide total items? | Off-by-one logic |
| What if 10,000 concurrent users trigger this flow simultaneously? | Concurrency limit |
| Does degradation preserve core business function? | Graceful failure |

### 6. Temporal & Concurrency Scenarios

For every shared resource or time-sensitive operation:

| Business question | Why it matters |
|-------------------|----------------|
| What if two cashiers process the last item in inventory simultaneously? | Inventory integrity |
| What if a scheduled report runs while data is being updated? | Read consistency |
| What if a user's clock is significantly wrong? | Time-based business logic |
| What if the operation completes on the server but the confirmation never reaches the client? | Delivery guarantees |
| What if an operation succeeds in one region but fails in another? | Distributed consistency |

### 7. User Journey & Context

The everyday ways real users break features that have nothing to do with infrastructure failures.
This is the most commonly missed category and produces the highest-impact gaps in shipped products.
Apply this lens to every feature with a user interface.

| Business question | Why it matters |
|-------------------|----------------|
| What if the user navigates away mid-flow (browser back, closed tab, refresh, app switch) and returns? | State preservation, draft recovery |
| What if the user opens the same feature in two tabs / two devices and acts in both? | Cross-session consistency |
| What if a new user starts here without having completed an implicit prerequisite (onboarding, profile, KYC, prior feature)? | First-run UX |
| What if the user returns to an in-progress state hours or days later — is the data still valid (prices, stock, terms)? | Stale-state handling |
| What if the user's account is technically valid but in an unusual state (trial, suspended, recently migrated, role just changed)? | Account-state coverage |
| What if the user does the steps in a different but reasonable order than the happy path assumed? | Non-linear flows |
| What if the user is on mobile / small screen / slow connection / offline and the design assumed desktop? | Device & network reality |
| What if the user uses keyboard-only navigation, screen reader, browser autofill, or paste? | Accessibility & input methods |
| What if the user misreads UI labels and clicks a reasonable-but-wrong button (Save vs Submit, Cancel vs Close, Delete vs Archive)? | Confirmation & reversibility for ordinary mistakes |
| What if the user has a different language / locale / time zone / currency than the system default? | Localisation & formatting |
| What if the same user has multiple roles (e.g. admin in org A, member in org B) and switches context? | Multi-tenant identity |

## Discovery Process (Business Phase)

### Step 1: Extract business invariants from PO answers

From the PO's clarifying question answers, extract:
- All "must always be true" statements
- All "must never happen" statements
- All numerical limits mentioned (timeouts, sizes, counts)
- All state transitions described in the user story

### Step 2: Run the 7-category scan

For each category above, list every applicable business question. Answer each one to the best of your knowledge. Flag questions you can't answer — ask PO.

**Coverage check:** before moving on, confirm category 7 (User Journey & Context)
produced at least one row for any feature with a UI. If category 7 is empty,
your scan was incomplete — re-run it.

### Step 3: Classify by severity (BUSINESS impact)

| Severity | Definition | Example |
|----------|-----------|---------|
| **Critical** | Data loss, financial loss, security breach, regulatory violation | Double-charging a customer's card |
| **High** | User-visible incorrect result, broken business process | Order status stuck in "processing" forever |
| **Medium** | Degraded experience, workaround exists | Search returns no results when one was expected |
| **Low** | Cosmetic, edge of edge | Off-by-one pixel in a 1-in-10k scenario |

**Calibration rule:** If a corner case wouldn't make a business stakeholder care, it's Low. If they'd call an emergency meeting, it's Critical.

### Step 4: Define expected business behavior

For each corner case, write the **business decision** — not the technical implementation:

- "Return 400" → `BAD` (implementation, not business)
- "Notify user that order cannot be placed because inventory is depleted; offer waitlist" → `GOOD` (business behavior)

### Step 5: Produce the corner case register

Save to `vault/concepts/<module>/plans/<feature>-corner-cases.md`.

## Output Format

```markdown
## Corner Case Register — [Feature Name]

> **Generated during business requirements phase.**
> Critical and High items are mandatory inputs for spec and implementation plan.

### Critical (data loss, financial loss, security, legal)

| # | Category | Condition | Expected Business Behavior | Spec Addressed |
|---|----------|-----------|---------------------------|----------------|
| 1 | Business Integrity | Double-click submits duplicate order | Detect duplicate + confirm intent with user | ❌ |
| 2 | External Dependency | Payment gateway timeout > 30s | Notify user of delay, hold order in "pending payment" state, automatic retry | ❌ |

### High (user-visible incorrect behavior)

| # | Category | Condition | Expected Business Behavior | Spec Addressed |
|---|----------|-----------|---------------------------|----------------|
| 3 | Invariants | Inventory goes to 0 between add-to-cart and checkout | Reserve inventory at add-to-cart; release on timeout | ❌ |

### Medium (degraded experience, workaround exists)

| # | Category | Condition | Expected Business Behavior | Spec Addressed |
|---|----------|-----------|---------------------------|----------------|

### Low (cosmetic, extreme edge)

| # | Category | Condition | Expected Business Behavior | Spec Addressed |
|---|----------|-----------|---------------------------|----------------|
```

## Handoff to Spec & Plan

The corner case register IS the handoff contract:

1. **Spec MUST reference every Critical and High item.** If a Critical corner case has no corresponding section in the spec, the spec is incomplete — reject.
2. **Plan MUST include a test task for every Critical item.** No exceptions. High items get explicit decisions (test now or defer to backlog) — but decisions are documented.
3. **Medium and Low** items are listed for awareness; PO may promote them.

This is the only integration point. Writing-plans reads the register as input. Implementation never discovers corner cases — it only implements what was discovered in planning.

## Principles

- **Business phase, not spec phase.** This runs BEFORE the spec exists. Corner cases feed INTO the spec.
- **Domain language, not code.** "Reserve inventory atomically" → `BAD`. "Prevent oversell when multiple customers check out simultaneously" → `GOOD`.
- **Decisions, not details.** Expected business behavior is what the business decides, not how the code implements it.
- **Severity = stakeholder impact.** Not technical difficulty. Not implementation complexity.
- **No spec without corners.** A spec written without a corner case register is speculation written without due diligence.
- **YAGNI for corners.** Don't plan for scale-5 problems on a scale-1 system. But DO document the assumed scale.

## Error Handling

| Situation | Action |
|-----------|--------|
| Requirements file does not exist or is empty | STOP. Report: `Requirements file not found at [path] — BA must produce it first.` |
| 7-category scan produces zero corner cases | WARNING. Write register with 0 rows + a note explaining why: `No corner cases identified because [reason]. PO review recommended — every feature accepting input has at least input boundary corner cases.` |
| Cannot answer a category question | Flag it in the register row as `NEEDS_PO_DECISION`. Do not silently skip — PO must confirm or provide the answer. |
| Register file cannot be written (path invalid) | Fallback to `vault/concepts/[module]/plans/[feature]-corner-cases.md`. If that also fails, STOP and report the path error. |
| Previous corner case register exists for same feature | Overwrite only if PO confirmed rejection of previous version. Otherwise, append new findings with a `## Supplement — [date]` section. |

## Red Flags

- **Corner case register produced after spec is written** — Process failure. Spec was written blind.
- **"This feature has no corner cases"** — Every feature that accepts input has corner cases. Minimum: run input boundaries.
- **All items Medium or Low** — Calibration failure. At least one Critical or High exists for any non-trivial feature.
- **Technical implementation in Expected Behavior column** — Domain language only. Implementation decisions live in the plan, not the register.
- **Register not referenced by spec** — The register is the spec's source of truth for edge conditions. If the spec doesn't cite it, the analysis was wasted.
- **Register dominated by infrastructure failures** — If most rows are about server outages, rate limits, or concurrency, but few are about everyday user journeys (category 7), the analysis missed the way real users actually break the feature. Re-run with category 7 explicitly in mind.

## Self-Review (before handing to spec)

1. **Language check:** Are all Expected Behaviors in business/domain terms? No `return 400`, no `throw exception`, no `use mutex`?
2. **Severity calibration:** If a stakeholder wouldn't call a meeting about it, is it really Critical?
3. **Coverage:** Did you skip a category? If yes, write why in a note at the bottom. Category 7 (User Journey) MUST have at least one row for any feature with a UI.
3a. **Balance:** Count rows by category. If categories 4+5+6 outnumber categories 1+2+3+7 combined, your scan over-weighted infrastructure — re-run with everyday user journeys in mind.
4. **Answerability:** Could you answer every question? Flag unanswered ones for PO.
5. **No vagueness:** "Handle edge cases" → reject. "When payment fails, hold order for 24 hours, notify user via email, auto-cancel if not resolved" → accept.
