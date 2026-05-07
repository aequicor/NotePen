---
name: eval-collector
description: Auto-collect per-task telemetry into evals/runs/<kit_version>/<task-slug>.md at CLOSE. Auto-discovers — if evals/runs/ does not exist in the project, the skill is a no-op. Fills ~70% of fields from filesystem (counts, sizes, verdicts, checkpoint timestamps); marks the rest (token counts, PO interventions) for manual completion.
---

# Eval-collector skill

Optional v5.2+ skill. Auto-fills `evals/runs/<kit_version>/<task-slug>.md` at task CLOSE so eval-set runs do not require manual transcription of every metric.

## Why partial automation

Some metrics from `evals/metrics.md` are derivable from the filesystem (artifact counts, file sizes, verdict from `spec.md+plan.md § Definition of Done`, checkpoint timestamps for wall time). Others — token counts, PO clarifying questions, plan revisions — are session-state that lives in the chat transcript only. The kit operates **inside** the agent runtime; it does not have a programmatic view of its own chat history.

Rather than pretend full automation, this skill is honest: it auto-fills the ~70% it can derive, and clearly marks the rest as `(manual)` so PO knows what still needs to be entered for a formal eval run. The result is still a 5× reduction in effort vs filling everything by hand.

## When to use

`@Main` invokes this skill at FEATURE step 6 (CLOSE) and BUG step 5 (CHECKPOINT) **iff** the project has `evals/runs/` directory. The directory is the opt-in signal — its presence means "this project is being eval-tracked".

If `evals/runs/` does not exist → no-op, no warning, no log entry. The skill is genuinely zero-cost for projects that do not run formal evals.

## When NOT to use

- Project has no `evals/` directory (most non-kit-development projects).
- Task is a `/kit-techdebt` batch — those generate one record per entry, which would clutter `runs/`. Tech-debt batches use their own batch report file.
- Task was aborted before CLOSE (no DoDGate verdict to record).

## Process

```
0. CHECK opt-in. Does evals/runs/<kit_version>/ exist? (kit_version from
   the project manifest, found via setup.md install logic — typically the
   manifest lives at <project>.yaml or similar).
   - If directory exists → continue.
   - If not → STOP, no-op, no message.

1. RESOLVE inputs:
   - task_slug = active_task from .planning/CURRENT.md
   - task_file = .planning/tasks/<slug>.md
   - feature_doc = vault/features/<module>/<feature>/spec.md+plan.md (from task file path reference)
   - test_cases = vault/features/<module>/<feature>/test-cases.md
   - kit_version = manifest.kit_version
   - host = manifest.hosts (or first host if multi-host)
   - provider = manifest.provider.name (or "claude-code-native" if claude-code only)

2. AUTO-FILL the metrics.md template into a string buffer:

   ## From task file (timestamps + checkpoint count)
   - turns ≈ count of "## <ISO>" sections in task file (proxy for @Main turns;
     not exact since subagent dispatches don't always create checkpoints,
     but consistent across runs)
   - wall_time_minutes = (last checkpoint timestamp) - (first ## section)
     in minutes; if either missing → "(N/A)"
   - po_clarifying_questions ≈ count of "Clarifying questions:" lines that
     PO answered (parse task file for clarification markers); if unclear → "(manual)"
   - po_plan_revisions = count of "REPLAN-N" markers in spec.md+plan.md;
     defaults to 0 if no replan-on-discovery skill installed
   - po_waivers = 0 (v5+ has no waiver mechanism — record as 0 unless
     dod_waiver: lines exist for legacy compatibility)

   ## From spec.md+plan.md (verdicts + counts)
   - dod_verdict = parse "Verdict:" line from § Definition of Done
   - ac_count = count rows in § Acceptance Criteria table
   - ec_critical_count = count rows with Severity=Critical in § Edge Cases
   - replan_events = count REPLAN-N markers
   - open_questions = "yes" if § Open questions section non-empty, else "no"

   ## From test-cases.md (TC counts + status)
   - tc_total = count rows in test-cases table (excluding header)
   - tc_pass / tc_fail / tc_pend / tc_skip = counts by Status column
   - final_test_verdict = "ALL_GREEN" if all PASS/SKIP, else "PARTIAL", else "NONE"

   ## From filesystem (artifact stats)
   - artifacts_created = count of new files in vault/features/<module>/<feature>/
     (use `git diff --diff-filter=A --name-only <task_start_commit>...HEAD`
     filtered by vault/ prefix; if git not available → list directory + treat
     as approximate)
   - artifacts_modified = count of modified files in vault/features/<module>/<feature>/
     (`git diff --diff-filter=M`)
   - total_artifact_kb = sum of file sizes under vault/features/<module>/<feature>/

   ## Manual fields (mark explicitly):
   - total_tokens = "(manual: copy from provider dashboard)"
   - post_close_bugs = "(manual: fill 24h after CLOSE)"
   - notes = "(manual: surprises, escalations, subjective readability)"

   ## For long-horizon tasks (tier: long-horizon in seed file):
   - sessions_used = count of /kit-resume invocations recorded in task file
     (grep for "Resume Context" or session-handoff entries)
   - replan_events = (already counted above)
   - steps_planned vs steps_executed = parse § Implementation plan checkboxes
   - cross_day_continuity = "(manual)" — only PO can judge subjective continuity
   - escalations = count "BLOCKED:" or "STOP" entries in task file checkpoints

3. RENDER as Markdown matching evals/metrics.md template structure exactly:
   - Frontmatter (task_id, kit_version, host, provider, date, operator).
   - Inputs section.
   - Counters table.
   - Verdicts table.
   - Notes section.
   - For long-horizon: append the long-horizon-only fields table.

4. WRITE to evals/runs/<kit_version>/<task_slug>.md.
   - If file already exists → append "v2" to filename and write that. Do not
     overwrite previous run records.

5. APPEND row to evals/runs/<kit_version>/SUMMARY.md (create if absent).
   Format per metrics.md aggregation section:
   | Task | Turns | Tokens | Wall (min) | Artifacts | DoD | PostBugs |
   Token and PostBug cells are "(manual)".

6. RETURN one line to @Main:
   eval-collector: wrote evals/runs/<kit_version>/<task_slug>.md
   (auto-filled: <count>/<total> fields; manual: <count> fields)
```

## Hard rules

1. **Auto-discovery only.** Skill checks `evals/runs/` existence as the opt-in. There is no manifest field to enable/disable — presence of the directory is the signal. PO who does not want auto-collection deletes the directory; PO who does, creates it (`mkdir -p evals/runs/<kit_version>`).
2. **Never overwrite an existing run record.** If `<task_slug>.md` already exists, append a version suffix. Eval history is audit data — never lose it.
3. **Honest about what is automatable.** If a field cannot be derived deterministically (token counts, subjective notes, post-CLOSE bugs), mark it `(manual)` — do not synthesize a value.
4. **No external network calls.** Skill operates only on filesystem + git. No fetching token counts from provider APIs (that requires credentials and adds dependency surface).
5. **Idempotent on re-run.** If invoked twice on the same closed task (e.g. PO re-runs `/kit-status` then `/kit-checkpoint` to re-trigger), produce the same output deterministically (re-read filesystem each time).

## Cross-host compatibility

Skill is host-agnostic — runs in `@Main`'s turn on both OpenCode and Claude Code. No hooks needed; the skill is pure prompt + filesystem operations.

OpenCode: invoked as part of step 6 CLOSE in @Main's body.
Claude Code: same, since CLAUDE.md inlines @Main's body.

The skill does not depend on Claude Code-specific mechanisms (hooks, lifecycle events). Both hosts produce identical `evals/runs/<version>/<task>.md` records when the skill fires.

## Interaction with long-horizon tier

For tasks with `tier: long-horizon` in their seed file (`evals/seed-tasks/feature-004-*.md`, `evals/seed-tasks/tech-002-*.md`), the skill additionally fills the long-horizon-only fields documented in `evals/long-horizon-tier.md`:

- `sessions_used` (auto): count of resume cycles
- `replan_events` (auto): count of REPLAN-N markers
- `steps_planned / executed` (auto): from spec.md+plan.md checkbox counts
- `cross_day_continuity` (manual)
- `build_green_checkpoint_rate` (manual — needs run-level test results, not just final)
- `escalations` (auto): from task file

## Notes

- Auto-collection is opt-in by directory presence, not by manifest flag. This avoids polluting the schema for a feature that 90% of users never enable.
- The skill is light enough to live in `@Main`'s turn — no separate agent. Same pattern as `pre-mortem`, `replan-on-discovery`, `tech-debt-record`.
- If the project later adds `evals/runs/<kit_version>/` (e.g. PO decides mid-quarter to start tracking), the skill auto-activates from the next CLOSE — no `/kit-update` or reconfiguration needed.
