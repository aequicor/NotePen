---
description: Full AI-driven requirements pipeline: BA → CCR (business loop) → QA → Coverage → SA → CCR (technical loop) → Consistency → PO sign-off. Use ONLY when @Main starts a FEATURE task and needs a complete requirements package before implementation.
---

You are executing the requirements pipeline on behalf of @Main. You dispatch subagents and write checkpoints exactly as specified below. PO is the only human touchpoint — at input and final sign-off.

---

## Anti-Loop Rules (CRITICAL)

| Symptom | Action |
|---------|--------|
| Same agent dispatched 3× with identical input | STOP. Write `BLOCKED: loop on [agent]` to the active task file. Surface to PO. |
| Agent returned empty result 2× in a row | STOP. Report which agent and what was expected. |
| CornerCaseReviewer returned OPEN_QUESTIONS for 3rd iteration | STOP. Surface remaining questions to PO. |
| CoverageChecker returned GAPS for 2nd FIX iteration | STOP. Surface remaining gaps to PO. |
| ConsistencyChecker returned CONFLICTS for 2nd iteration | STOP. Surface remaining conflicts to PO. |

---

## Step 0 — INTAKE

Parse the feature name, module, and description passed from @Main. Validate:

```
Feature name: [short identifier, snake_case, 3–50 chars, e.g. order_cancellation]
Module: [must match a module name from the project manifest]
Description: [PO's full description — must be non-empty]
```

Validation rules:
- Feature name empty or not snake_case → ask PO to clarify before proceeding.
- Module not found in manifest → list available modules, ask PO to pick one.
- Description empty → ask PO to provide a description.

These are the ONLY clarifying questions allowed before the pipeline starts.

Read `.planning/CURRENT.md` → get `active_task`. Write to `.planning/tasks/<active_task>.md`:
```
## <timestamp>
- DONE: requirements pipeline intake complete
- NEXT: BA draft
- Feature: [name], Module: [module]
```

---

## Step 1 — BA DRAFT

Dispatch `@BusinessAnalyst` (mode=DRAFT):

```
Mode: DRAFT
Feature: [feature-name]
Module: [module]
Task description: [PO's full description]
```

Parse result. Extract and store in context:
- Requirements file path
- `BA_USER_STORIES` — User Stories count
- `BA_AC_COUNT` — Acceptance Criteria count
- Open Questions count

Write checkpoint. Proceed to Step 2.

---

## Step 2 — CORNER CASE REVIEW (Business) — loop

**Max 3 iterations.**

### Iteration N:

Dispatch `@CornerCaseReviewer` (mode=BUSINESS):

```
Mode: BUSINESS
Requirements file: [path]
Run: N
Previous questions: [list from previous iteration, or "none" for first run]
New ACs since last run: [BA_NEW_ACS list from previous BA UPDATE result, or "none" for first run]
```

Parse result.

**If Verdict = DONE:** proceed to Step 2.5.

**If Verdict = OPEN_QUESTIONS and N < 3:**

Dispatch `@BusinessAnalyst` (mode=UPDATE):

```
Mode: UPDATE
Requirements file: [path]
Questions: [open questions table from CornerCaseReviewer]
```

Parse result. Extract and store as `BA_NEW_ACS` the `**New Acceptance Criteria list:**` field from BA UPDATE result.
If any "needs PO" questions returned — collect them and surface to PO after the full loop, not immediately. Continue loop with N+1.

**If Verdict = OPEN_QUESTIONS and N = 3:**

STOP loop. Write checkpoint `BLOCKED: CornerCaseReviewer loop exhausted`. Surface to PO:

```
Requirements pipeline paused — @CornerCaseReviewer found questions that @BusinessAnalyst
could not resolve after 3 iterations. PO input required:

[list of unresolved questions]

Reply with answers, then type /kit-resume to continue the pipeline.
```

Wait for PO. On `/kit-resume`:
1. Dispatch `@BusinessAnalyst` (mode=UPDATE) with PO's answers as the questions list. This is **not** a loop iteration — PO is the authoritative resolver.
2. Exit the loop immediately after BA UPDATE completes (do not re-run CornerCaseReviewer).
3. Proceed to Step 2.5.

Write checkpoint after loop exit. Proceed.

---

## Step 2.5 — CORNER CASE REGISTER

Invoke the built-in skill `corner-case-refinement`:

```
Feature: [feature-name]
Module: [module]
Requirements file: [path from Step 1]
```

**Important:** Pass the same requirements file path from Step 1 — this file has been updated in-place by `@BusinessAnalyst UPDATE` during the Step 2 loop. The skill reads the **current (final) state** of that file. Do not create a new copy; pass the same path.

The skill writes the corner case register to:
`vault/concepts/[module]/plans/[feature]-corner-cases.md`

Verify the file exists before proceeding. Read it and count severity rows. Store in context:
- `CC_CRITICAL` — count of Critical rows
- `CC_HIGH` — count of High rows
- `CC_MEDIUM` — count of Medium rows

Write checkpoint:
```
- DONE: corner case register created — vault/concepts/[module]/plans/[feature]-corner-cases.md (Critical: CC_CRITICAL, High: CC_HIGH, Medium: CC_MEDIUM)
- NEXT: QA draft
```
Proceed.

---

## Step 3 — QA DRAFT (REQUIREMENTS phase)

Dispatch `@QA` (Phase=REQUIREMENTS):

```
Phase: REQUIREMENTS
Feature: [feature-name]
Module: [module]
Requirements file: [path]
Corner cases register: vault/concepts/[module]/plans/[feature]-corner-cases.md
```

Parse result. Extract and store:
- Test-cases file path (`vault/reference/[module]/test-cases/[feature]-test-cases.md`)
- Total test case count (from `**Total:** N` field) — store as `TEST_COUNT`

Write checkpoint:
```
- DONE: QA REQUIREMENTS draft — TEST_COUNT test cases, file at [path]
- NEXT: CoverageChecker
```
Proceed to Step 4.

---

## Step 4 ‖ Step 5 — COVERAGE CHECK and SYSTEM ANALYST DRAFT (parallel kickoff)

`@CoverageChecker` and `@SystemAnalyst DRAFT` consume **the same authoritative
inputs** (requirements file + corner cases register) and produce independent
outputs in different files. SA also reads test-cases.md, but only as
informational input — its spec content derives from requirements + CCs (which
are stable by this point: BA UPDATE loop closed in Step 2, register frozen in
Step 2.5). CoverageChecker may add TCs derived from CCs that SA already saw,
so re-running SA is unnecessary.

**Kickoff:** dispatch `@CoverageChecker` (iteration 1) and `@SystemAnalyst`
(mode=DRAFT) **in one turn as parallel calls**, then resolve them
independently as below.

### Step 4 — COVERAGE CHECK loop (resumes from parallel kickoff)

**Max 2 iterations.** Iteration 1 was dispatched in the parallel kickoff above.

Parse Coverage iteration 1 result.

**If Verdict = PASS:** Step 4 is done. Continue waiting for SA (Step 5).

**If Verdict = GAPS and N < 2:**

Dispatch `@QA` (Phase=REQUIREMENTS, Mode=FIX):

```
Phase: REQUIREMENTS
Mode: FIX
Test-cases file: [path]
Gaps: [gaps table from CoverageChecker]
```

Then dispatch `@CoverageChecker` for iteration N+1. Repeat until PASS or N=2.

**If Verdict = GAPS and N = 2:**

STOP Step 4 loop. Write checkpoint `BLOCKED: CoverageChecker gaps not resolved`. Surface to PO with remaining gaps.

Write checkpoint after loop exit.

### Step 5 — SYSTEM ANALYST DRAFT (resolves from parallel kickoff)

`@SystemAnalyst` was dispatched in the parallel kickoff. When its result returns:

```
Mode: DRAFT
Requirements file: [path]
Corner cases register: [path]
Test plan file: [path from Step 3]   # informational; spec derives from requirements + CCs
```

Parse result. Extract:
- Spec file path
- Unresolved items count and list
- `SA_ENDPOINTS` — API endpoints count
- `SA_DATA_MODELS` — Data models count

Write checkpoint:
```
- DONE: SA draft — SA_ENDPOINTS endpoints, SA_DATA_MODELS data models, N corner cases addressed
- NEXT: CornerCaseReviewer (technical)
- UNRESOLVED: [list each unresolved item, or "none"]
```

**Both Step 4 and Step 5 must complete before Step 6.** Proceed to Step 6 regardless of unresolved items — CornerCaseReviewer will surface architectural gaps explicitly.

---

## Step 6 — CORNER CASE REVIEW (Technical) — loop

**Max 3 iterations.**

### Iteration N:

Dispatch `@CornerCaseReviewer` (mode=TECHNICAL):

```
Mode: TECHNICAL
Spec file: [path from Step 5]
Run: N
Previous questions: [list from previous iteration, or "none" for first run]
```

Parse result.

**If Verdict = DONE:** proceed to Step 7.

**If Verdict = OPEN_QUESTIONS and N < 3:**

Dispatch `@SystemAnalyst` (mode=UPDATE):

```
Mode: UPDATE
Spec file: [path]
Questions: [open questions table from CornerCaseReviewer]
```

Parse result. If any "needs architectural decision" items — note in checkpoint. Continue loop with N+1.

**If Verdict = OPEN_QUESTIONS and N = 3:**

STOP loop. Write checkpoint `BLOCKED: CornerCaseReviewer (technical) loop exhausted`. Surface to PO:

```
Requirements pipeline paused — @CornerCaseReviewer (technical) found gaps in the technical spec
that @SystemAnalyst could not resolve after 3 iterations. PO/architect input required:

[list of unresolved questions]

Reply with decisions, then type /kit-resume to continue the pipeline.
```

Wait for PO. On `/kit-resume`:
1. Dispatch `@SystemAnalyst` (mode=UPDATE) with PO's answers. This is **not** a loop iteration.
2. Exit the loop immediately after SA UPDATE completes.
3. Proceed to Step 7.

Write checkpoint after loop exit. Proceed.

---

## Step 7 — CONSISTENCY CHECK — loop

**Max 2 iterations.**

### Iteration N:

Dispatch `@ConsistencyChecker`:

```
Requirements file: [path]
Technical spec file: [path]
Test plan file: [path]
```

Parse result.

**If Verdict = PASS:** proceed to Step 8.

**If Verdict = CONFLICTS and N < 2:**

Dispatch `@SystemAnalyst` (mode=UPDATE):

```
Mode: UPDATE
Spec file: [path]
Questions: [conflicts as open questions — rephrase each conflict as a question to resolve]
```

Continue loop with N+1.

**If Verdict = CONFLICTS and N = 2:**

STOP loop. Write checkpoint `BLOCKED: ConsistencyChecker conflicts not resolved`. Surface to PO:

```
Requirements pipeline paused — @ConsistencyChecker found contradictions that @SystemAnalyst
could not resolve after 2 iterations. Resolution required:

[list of unresolved conflicts]

Options:
- Provide decisions to resolve each conflict, then type /kit-resume to continue.
- Type /kit-approve-with-conflicts to accept conflicts as-is and proceed to sign-off.
```

Wait for PO. On `/kit-resume`:
1. Dispatch `@SystemAnalyst` (mode=UPDATE) with PO's decisions. This is **not** a loop iteration.
2. Proceed directly to Step 8. Do not re-run ConsistencyChecker.

On `/kit-approve-with-conflicts`:
1. Write checkpoint noting which conflicts are accepted as-is.
2. Proceed to Step 8.

Write checkpoint after loop exit. Proceed.

---

## Step 8 — PO SIGN-OFF

Compile the sign-off package and present to PO:

```
## Requirements Pipeline Complete — [Feature Name]

All automated checks passed. Ready for development.

### Artifacts

| Artifact | File | Summary |
|----------|------|---------|
| Business Requirements | vault/concepts/[module]/requirements/[feature].md | BA_USER_STORIES user stories, BA_AC_COUNT acceptance criteria |
| Corner Case Register | vault/concepts/[module]/plans/[feature]-corner-cases.md | CC_CRITICAL critical, CC_HIGH high, CC_MEDIUM medium |
| Test Cases (live)     | vault/reference/[module]/test-cases/[feature]-test-cases.md      | TEST_COUNT TCs (Status legend, Defects log) |
| Technical Spec | vault/reference/[module]/spec/[feature].md | SA_ENDPOINTS endpoints, SA_DATA_MODELS data models |

### Pipeline log
- BA iterations: N
- CornerCaseReviewer (business) iterations: N
- CoverageChecker iterations: N
- SA iterations: N
- CornerCaseReviewer (technical) iterations: N
- ConsistencyChecker iterations: N

### PO action required

Type `/kit-approve` to proceed to implementation planning.
Type `reject: Step N` to discard artifacts from Step N onward and restart from that step.
```

**On `/kit-approve`:** write checkpoint `DONE: requirements package approved`. Write artifact paths to `.planning/tasks/<active_task>.md`:
```
- requirements file: vault/concepts/[module]/requirements/[feature].md
- corner cases: vault/concepts/[module]/plans/[feature]-corner-cases.md
- test cases: vault/reference/[module]/test-cases/[feature]-test-cases.md
- spec: vault/reference/[module]/spec/[feature].md
```
Skill complete. Return control to @Main — proceed to step 2 (SEARCH).

**On `reject: Step N`:**
1. Verify N is a valid step number (0, 1, 2, 2.5, 3, 4, 5, 6, 7).
2. Discard all artifacts created at Step N and after. Artifacts from steps before N remain valid.
3. Write checkpoint: `REJECTED: restarting from Step N — [PO reason]`.
4. Restart from Step N using the retained inputs.

---

## Error Handling

| Situation | Action |
|-----------|--------|
| Agent returns empty result 2× in a row | STOP. Report which agent and what was expected. Surface to PO. |
| Requirements file not created after Step 1 | STOP. Report BA failure. Ask PO if they want to retry with a simpler description. |
| Corner case register is empty after Step 2.5 | WARNING: write checkpoint with 0 Critical, 0 High. Proceed — but surface to PO at sign-off that no corner cases were found. |
| PO does not respond to sign-off within the session | Write checkpoint `BLOCKED: awaiting PO sign-off`. Do NOT proceed to implementation. |
| Artifact file paths referenced by subagents don't exist | STOP. Report missing file path. Likely a previous step produced no output — restart from that step. |
| Manifest module not found | In Step 0: list available modules, ask PO to pick one. Do not proceed without valid module. |
