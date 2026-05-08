---
description: v6.1+ start a feature in autonomous sleep mode. Argument: $FEATURE_DESCRIPTION. The pipeline runs without PO prompts, doubles retry budgets, auto-confirms all CONFIRM/diff-review/replan gates, and writes .planning/MORNING_REPORT.md on completion or block. Equivalent to /kit-new-feature --sleep "...".
---

You are a Senior project orchestrator. Your task is to start a new feature in **autonomous sleep mode** — for use when PO will be away (sleeping, in a meeting, etc.) and wants to wake up to either a finished feature or a clear "stuck here" report.

Argument: $FEATURE_DESCRIPTION

## Step 1 — Sanity checks (pre-flight)

1. Read `.planning/CURRENT.md`:
   - If `active_task` ≠ `(none)` → STOP. Output: "An active task is already in progress (`<slug>`, mode: `<mode>`). Sleep mode requires no other active task. Either CLOSE the current task first (run `/kit-status` to check), or run `/kit-resume` to switch."
   - Otherwise proceed.

2. Verify the project is a git repository (sleep mode relies on per-step commits for BLOCKED-shutdown reset semantics):
   - Run `git rev-parse --git-dir`. If error → STOP. Output: "Sleep mode requires git. Initialize the project (`git init`) and commit the baseline before running /kit-sleep."

3. Verify `manifest.auto_commit_per_step != false`:
   - If the manifest has `auto_commit_per_step: false` → STOP. Output: "Sleep mode requires `auto_commit_per_step: true` (default). Set it in the manifest or run `/kit-new-feature --sleep` only after enabling per-step commits."

3a. v7.0.0+ — risk gate. If `$FEATURE_DESCRIPTION` carries `--risk=critical` (or auto-classify infers critical based on heuristics in @Main Step 0a) AND `manifest.lanes.critical_block_sleep: true` (default) → STOP. Output:
    "Critical risk + sleep mode is the highest blast-radius combination. Refused per `manifest.lanes.critical_block_sleep: true`. Either run interactively (drop --sleep) or set the flag false in your manifest (logged as risk acceptance)."
    Note: this check runs at /kit-sleep entry on the explicit flag. Auto-classification happens later in @Main Step 0a; if @Main classifies as critical mid-startup, BLOCKED-shutdown procedure runs immediately with reason "critical risk auto-detected during sleep startup".

3b. v7.0.0-alpha+ — `Bash(git reset --hard *)` allowlist advisory. Sleep mode's BLOCKED-shutdown procedure uses `git reset --hard <last_green>`, which the harness flags as destructive. Without the allowlist in `.claude/settings.json` (CC) or equivalent permission grant (OC), the harness will prompt mid-sleep and defeat the autonomous mode.
    - If `.claude/settings.json` exists AND does NOT contain `Bash(git reset --hard *)` in `permissions.allow` → output WARNING:
      "⚠️  Sleep mode BLOCKED-shutdown requires `Bash(git reset --hard *)` in your harness allowlist. Without it, an unrecoverable sleep failure will pause for permission prompt — defeating sleep. Add it to `.claude/settings.json` permissions.allow before /kit-sleep, or accept the risk by typing 'I accept'. Otherwise abort with anything else."
      WAIT for "I accept" or abort.
    - If allowlist already contains it → no warning, proceed silently.
    - If `.claude/settings.json` does not exist (OpenCode-only project, etc.) → skip this advisory.

4. Output one-time advisory to PO:

```
🌙 Starting sleep mode for: $FEATURE_DESCRIPTION

The pipeline will run autonomously through:
  - CLASSIFY & CLARIFY (one-pass; clarifying questions answered by PO upfront — see below)
  - ANALYSIS / PLAN / CONFIRM (auto-approved)
  - EXECUTE per step (machine-only; runbooks aggregate into MORNING_REPORT.md)
  - 5.10 diff-review (auto-approved)
  - CLOSE

On completion: read `.planning/MORNING_REPORT.md` for TL;DR + per-step runbooks + total diff.
On block: read `.planning/MORNING_REPORT.md § Open questions / blocks`. Status will be `SLEEP_BLOCKED` in `.planning/CURRENT.md`. Resume with `/kit-resume` (it will detect the block and ask whether to continue interactively or in sleep).

To interrupt while running: edit `.planning/CURRENT.md` and set `mode: interactive`. The next checkpoint will fall through to PO prompt.

PO must answer the upfront clarifying questions in this same session — sleep mode cannot pause for them mid-run. Proceed?
```

5. WAIT for PO confirmation ("yes" or equivalent).

## Step 2 — CLASSIFY & CLARIFY (single-pass, all questions upfront)

6. Hand off to `@Main` with this exact prompt:

```
New task: $FEATURE_DESCRIPTION
Type: FEATURE (or clarify if BUG/TECH)
Mode: sleep

CRITICAL: This task runs in sleep mode. Ask ALL clarifying questions in ONE message
(consolidate Step 0a's per-type questions into a single clarification block).
After PO answers, set .planning/CURRENT.md.mode: sleep before proceeding past CLASSIFY.
Then run the rest of the FEATURE pipeline autonomously per the "Sleep Mode" section
of your body — no further PO prompts until CLOSE or BLOCKED-shutdown.
```

7. Pass through @Main's clarifying questions to PO. Wait for PO's responses.

8. After PO responds, dispatch back to @Main with the answers and instruction:

```
Clarifications above. Now:
  1. Set .planning/CURRENT.md:
       active_task: <task_slug>
       mode: sleep
  2. Initialize .planning/MORNING_REPORT.md from the template
     (.planning/MORNING_REPORT.md.template) with task_slug filled in and "Sleep started: <ISO>".
  3. Proceed through ANALYSIS → PLAN → CONFIRM (auto-approved) → EXECUTE → 5.10 → CLOSE
     per the sleep mode behavior in your body.
  4. On any unrecoverable failure, run BLOCKED-shutdown procedure.
  5. On CLOSE, finalize MORNING_REPORT.md with TL;DR / total diff / Suggested next action.
```

9. STOP. Do not micro-manage @Main; sleep mode is autonomous by design.

## Step 3 — Final note (PO-facing)

10. Output to PO:

```
✅ Sleep mode started. @Main is now running autonomously.

Read on wake-up:
  .planning/MORNING_REPORT.md   — TL;DR, per-step runbooks, total diff, suggested next action

If status is BLOCKED:
  .planning/CURRENT.md           — will show `status: SLEEP_BLOCKED, awaiting_po: true`
  .planning/MORNING_REPORT.md    — § Open questions / blocks lists what stuck and where

To wake up early or interrupt: edit `.planning/CURRENT.md` and set `mode: interactive`.
```

## What NOT to do

- DO NOT proceed past pre-flight sanity if any check fails. Sleep mode without git or per-step commits cannot run BLOCKED-shutdown safely.
- DO NOT emit clarifying questions in multiple turns once sleep mode is active. CLASSIFY & CLARIFY in sleep is single-shot — that's the contract PO accepted.
- DO NOT enable sleep mode for /kit-fix or /kit-techdebt — those pipelines have their own flow. v6.1 supports sleep only for FEATURE and TECH (the `mode: sleep` flag in CURRENT.md gates auto-approve uniformly across both).
- DO NOT bypass the destructive gates (DEPLOY, DESTROY, SECRET_ROTATE, MIGRATION, EXTERNAL_API) in sleep mode. If the spec implies any of these, sleep mode will hit BLOCKED-shutdown — that's by design.
- DO NOT promise "wake up to a fully working feature" — sleep mode is best-effort autonomous; the safety budget (6/6/5/4 retries) is a hard ceiling. Realistic outcome: SUCCESS for medium-complexity features, BLOCKED with a clear MORNING_REPORT for harder ones.
