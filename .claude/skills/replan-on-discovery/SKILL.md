---
name: replan-on-discovery
description: Bounded adaptive replanning. When an EXECUTE-phase agent (@CodeWriter, @Reviewer, @TestKeeper, @TraceabilityChecker) discovers a structural gap that fix-in-place cannot close — the spec is incomplete, an EC was missed, a dependency was not foreseen — invoke this skill before escalating. It produces a bounded plan delta (≤3 new steps) instead of stopping the pipeline. Hard cap: max 2 replan events per feature.
---

# Replan-on-discovery skill

Optional v5.2+ skill. Replaces immediate escalation with a **bounded** plan amendment when a structural gap is discovered mid-EXECUTE.

## v6 note — writes only to plan.md

In v5.2 this skill amended the v5 monolithic `feature.md § Implementation plan`. v6 splits feature.md into `spec.md` (FROZEN at CONFIRM) and `plan.md` (mutable). Replan amends only `plan.md § Implementation plan`. If the structural discovery actually requires changing AC / EC / How-it-works (i.e. the spec is wrong), that is NOT a replan trigger — escalate to PO with proposal "spec amendment via @Analyst", which is a fresh DRAFT cycle.

## Why this exists (and why it is bounded)

In v5.0–v5.1, when `@CodeWriter` hits the 3-cycle review-fix cap, `@Main` escalates to PO. This is correct for fix-in-place issues (the model genuinely cannot solve the problem and PO judgment is needed). But it is the wrong response when the discovery is **structural**:

- An AC was implicitly broader than the spec said.
- An EC was missed during ANALYSIS — only surfaced when code was written.
- Step 4 depends on a class not yet built; Step 2 was supposed to build it but didn't.

In those cases, the right move is to extend the plan by 1–3 steps, not to stop. v5.0 had no mechanism for this — every structural discovery looked the same as a fix-in-place loop and triggered escalation. Result: PO interrupted for plan-shape issues that the agent could amend cleanly.

The hard cap (max 2 replan events per feature, ≤ 3 new steps each) is the **non-negotiable safeguard** against runaway scope. Without it, this skill would let the agent rewrite its own plan indefinitely. Per [Self-Refine 2026 guidance](https://selfrefine.info/) on diminishing returns, two adaptive rounds is the empirical sweet spot — beyond that, complexity dominates.

## When to use

`@Main` invokes this skill from FEATURE step 5.4 (fix loop) or step 5.7–5.8 (post-EXECUTE checks) when **all** of the following are true:

1. The trigger comes from a verdict produced by `@Reviewer`, `@TestKeeper RECONCILE`, `@TraceabilityChecker`, or `@CodeWriter BLOCKED`.
2. The verdict points to a **structural gap** — see the four trigger patterns below.
3. Replan counter for this feature is < 2 (read from `.planning/tasks/<slug>.md`).
4. Fix-in-place attempts have not yet exceeded their cycle cap (otherwise the issue is fix-in-place, not structural).

## When NOT to use

| Situation | Correct action |
|-----------|----------------|
| `@CodeWriter` failed to fix the same Reviewer finding 3 times | Escalate. Fix-in-place issue. |
| Reviewer found a typo / style nit | Continue fix loop. Not structural. |
| TestKeeper EXECUTE returned FAILURES (test red after change) | Continue fix loop. Tests describe expected behaviour; agent needs to honour them. |
| @TraceabilityChecker WEAK_ASSERTION | Info-only. Don't replan. |
| Replan counter already = 2 | Escalate. Hard cap. |
| The "discovery" amounts to "let me also add feature X" | Refuse. Not in scope. Record as tech-debt or new task. |

## Trigger patterns (the four valid invocations)

### Pattern A — Reviewer flags AC-violation root in spec

`@Reviewer` returns CRITICAL on Pass A1 (spec alignment) with reason like *"the spec says X but AC-2 implies Y; current code implements X correctly"*. The code is right; the **spec** is wrong. Fix-in-place would silently change the AC.

### Pattern B — TestKeeper RECONCILE finds Critical EC uncovered

After all planned steps, `@TestKeeper MODE=RECONCILE` reports an EC of severity Critical or High that has no PASS TC AND no `[deferred]` note. The EC was added to `spec.md` after the plan was confirmed (PO edit, or `@Analyst` reflection during a fresh DRAFT cycle) but no plan step covers it. Adding a TC alone is insufficient — implementation work is needed; replan adds the missing step to plan.md without touching spec.md.

### Pattern C — TraceabilityChecker reports ENDPOINT_ORPHAN on a Critical surface

`@TraceabilityChecker` finds a spec endpoint with no handler. The spec genuinely needs the endpoint (per `spec.md § How it works`), but no plan step builds it. This is plan-incomplete, not code-broken.

### Pattern D — CodeWriter returns BLOCKED on missing dependency

`@CodeWriter` returns the BLOCKED block (per its Step 5 atomicity rule) with reason *"depends on a class declared in Step 5 but not yet built"*. The plan order is wrong, or a step is missing.

## Process

```
0. THINK — confirm this is structural, not fix-in-place.
   - Read the trigger verdict in full.
   - Read plan.md § Implementation plan + § Acceptance Criteria + § Edge Cases.
   - Identify: which AC / EC / endpoint is the gap? Is there an existing step
     that should have covered it but didn't?
   - If unclear → escalate (do not invoke this skill).

1. CLASSIFY the discovery. Pick exactly one:
   a) AMEND   — existing step needs additional sub-tasks (1-3 lines added).
   b) INSERT  — entirely new step needed between existing ones (e.g. a
                missing dependency step before a downstream step).
   c) SHIFT   — scope from one step migrates to a new step (e.g. Step 4
                was too big; carve out a Step 4a).
   Anything that doesn't fit a/b/c is out of scope for this skill.

2. CHECK CAPS:
   - Read .planning/tasks/<slug>.md → grep for "REPLAN-" markers.
   - If count >= 2 → STOP, return ESCALATE verdict.
   - If proposed delta > 3 new lines/steps → STOP, return ESCALATE verdict.

3. WRITE the amendment to plan.md § Implementation plan.
   Use a structured marker so future readers (and DoDGate) can audit:

   <!-- REPLAN-N (YYYY-MM-DD): <one-line trigger summary>
        Trigger: <agent>-<verdict>
        Pattern: <A|B|C|D>
        Reason: <2-3 sentences why fix-in-place was insufficient>
   -->
   - [ ] Step Xa (replan): <new step text>

   Where N = 1 or 2 (per cap). The marker is HTML-comment so it doesn't
   render in PO-readable view but is preserved by /kit-update merge.

4. RECORD in task file:
   Append to .planning/tasks/<slug>.md:
   ## <ISO timestamp>
   - REPLAN-<N>: trigger=<agent>:<verdict-id> pattern=<A|B|C|D>
   - Steps added: <list of new step numbers>
   - DONE: replan-<N> recorded
   - NEXT: re-confirm with PO (if auto_approve.feature != true)

5. NOTIFY PO:
   - If auto_approve.feature is true → log "auto-approved replan-<N>"
     and continue EXECUTE from the new step.
   - Else → output the replan block to PO with this format:

   REPLAN-<N> proposed
   Trigger:    <one-line>
   Pattern:    <A|B|C|D>
   New steps:  <count> (cumulative replan count: <N>/2)
   Plan delta: see plan.md § Implementation plan (REPLAN-<N> marker)

   /kit-approve to proceed, or describe the change you want instead.

6. RETURN to @Main: REPLAN_DONE | ESCALATE.
   On REPLAN_DONE → @Main resumes EXECUTE at the new step (or pending
   /kit-approve in auto_approve.feature=false mode).
   On ESCALATE → @Main proceeds with the existing escalation flow.
```

## Hard rules

1. **Max 2 replan events per feature.** Counted by REPLAN-N markers in `plan.md § Implementation plan` and replan entries in the task file. The two are reconciled: if either says ≥2, cap is exhausted.
2. **Max 3 new steps per replan event.** No open-ended additions. If the discovery requires more — that is a sign the original plan was wrong at a level this skill cannot recover; escalate to PO who may decide to split into a new feature.
3. **No silent replan.** Every replan event MUST produce both a `REPLAN-<N>` HTML-comment in `plan.md § Implementation plan` AND a task-file checkpoint entry. If you cannot write either (file lock, write error) — abort, do not continue.
4. **Replan does NOT modify `§ Acceptance Criteria` or `§ Edge Cases`.** Those are `@Analyst`'s domain. If the discovery implies an AC/EC change, escalate so PO + `@Analyst` handle it. Replan only amends `§ Implementation plan`.
5. **Replan does NOT bypass `@DoDGate`.** New steps go through the same EXECUTE loop (CodeWriter → TestKeeper → Reviewer → fix loop). DoDGate sees them like any other step.
6. **Replan within a single turn.** Skill invocation, edit, and notification all happen in one `@Main` turn. No loop over replan invocations within a single trigger.

## Anti-patterns

- **Replan as scope creep.** PO requested rate-limit; mid-EXECUTE the agent decides to also add IP allowlisting. Refuse — that is a separate task, not a replan trigger.
- **Replan to sidestep a Reviewer finding.** Reviewer says CRITICAL on a code smell; agent invokes replan to "skip" the smell by adding a wrapper step. Refuse — fix-in-place is the right response to code-quality findings.
- **Replan that rewrites approved plan.** PO confirmed a 5-step plan. Replan turns it into a 12-step plan. Refuse — replan is a delta, not a rewrite. The 3-step cap enforces this.
- **Replan invoked from `@Analyst` self-reflection.** `@Analyst` has its own Pass 2 reflection within ANALYSIS. Replan-on-discovery is for EXECUTE phase only, post-CONFIRM.

## Interaction with auto_approve

The granular `auto_approve` flag controls whether replan needs PO sign-off:

| `auto_approve.feature` | Replan behaviour |
|---|---|
| `true` | Auto-confirm replan; log to checkpoint and continue EXECUTE immediately. |
| `false` (default) | Pause EXECUTE; print REPLAN proposal block; wait for PO `/kit-approve` or alternative direction. |
| (object missing this key) | Treat as `false`. |

For TECH and BUG pipelines, the corresponding `auto_approve.tech` / `auto_approve.bug.<severity>` keys apply.

## Notes

This skill is invoked from `@Main`'s `<host_dir>/skills/replan-on-discovery/SKILL.md` reading. There is no separate dispatching agent — the skill lives in the orchestrator's turn (like `pre-mortem`).

If the kit is rendered without this skill (delete the directory before install / `/kit-update`), `@Main`'s fall-through is identical to v5.1.0 — escalate to PO at fix-loop cap. Removing this skill is **safe and back-compat**.
