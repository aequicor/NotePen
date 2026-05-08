---
name: gate-telemetry
description: v6.3+ append per-gate telemetry rows to evals/runs/<kit_version>/gates.csv. Used by @Main and subagents at every gate verdict (slice-cap, runnable-slice, token-budget, ground-truth, runbook, ground-truth attach, diff-review, DoD, trace, review, build, defect-origin). Opt-in by directory presence — if evals/runs/ does not exist, skill is a no-op. Together with defects.csv (P19, v6.2+) gives the kit a self-cleaning gate-evaluation mechanism: gates with signal_ratio < threshold over evaluation_window_tasks become candidates for deprecation.
---

# Gate-telemetry skill

Optional v6.3+ skill. Appends one row to `evals/runs/<kit_version>/gates.csv` per gate verdict. Read on aggregation by the `eval-collector` skill at task CLOSE.

## Why exists

Every release of the kit has added gates (P1…P19 across v6.0–v6.2). None of those releases included a mechanism to **measure** whether each gate earned its token cost. Without data, "let's also add Pass D" is intuition; with data, it's a decision. The kit's exit-criterion for adding a new gate (per `PIPELINE_V7_PROPOSAL.md` § 4 П5) is: gate must reach signal_ratio ≥ threshold within evaluation_window_tasks or be removed.

This skill is the **write side** of that mechanism. The read side is `eval-collector` § "v6.3+ gates.csv aggregation".

## When invoked

By @Main at every gate verdict. By subagents that own a gate (e.g. @Verifier MODE=REVIEW when a Pass produces CRITICAL/HIGH; @Verifier at EXECUTE green/red; @Verifier MODE=DOD at PASS/BLOCK). Each invocation is one row.

## Opt-in

Directory presence: `evals/runs/<kit_version>/`. Same convention as `eval-collector` and `/kit-defect`'s defects.csv writer. If absent → no-op, no warning.

## CSV schema

```
timestamp,task_slug,step,gate,verdict,blocked_close,false_positive,lane,reason
```

| Column | Type | Notes |
|---|---|---|
| `timestamp` | ISO-8601 UTC | When the gate fired |
| `task_slug` | string | From `.planning/CURRENT.md.active_task` |
| `step` | integer | `current_step_idx` from task file; 0 for pre-EXECUTE gates (slice-cap, runnable-slice at 3a) |
| `gate` | enum | See § Gate enumeration below |
| `verdict` | enum | `pass` \| `block` \| `warn` \| `info` |
| `blocked_close` | bool | true iff this verdict prevented progression to CLOSE; false iff it was logged but pipeline continued |
| `false_positive` | bool | filled by `eval-collector` aggregation, NOT at write time. Default `unknown` at write; updated when defects.csv shows the same step had defect_origin matching this gate's class |
| `lane` | enum | `trivial` \| `standard` \| `critical` (v7+); v6.3 always writes `standard` |
| `reason` | string | ≤120 chars; specific issue (e.g. `OVERFLOW_FILES`, `missing-runbook-section: How to verify`) |

## Gate enumeration

The complete set v6.3 logs (one row per fire):

| Gate id | Owner | Fires at | Verdict semantics |
|---|---|---|---|
| `slice-cap` | @Main | 3a, 5.1 | `block` on overflow steps/files; `pass` if under cap |
| `runnable-slice` | @Main | 3a | `block` on missing `Runnable:` line; `pass` otherwise |
| `token-budget` | @Main | 5.1 | `block` on OVERFLOW_TOKENS; `pass` if under cap or trim succeeded; `warn` if trim was required |
| `build` | @Verifier MODE=EXECUTE | 5.3 | `pass` ALL_GREEN; `block` BUILD_FAIL/FAILURES; `info` NOT_RUN_GAP |
| `review-correctness` | @Verifier MODE=REVIEW Pass A | 5.4 | `pass` CLEAN; `block` CRITICAL_OR_HIGH_FOUND |
| `review-scope` | @Verifier MODE=REVIEW Pass D | 5.4 | `pass` CLEAN; `block` HIGH out-of-module; `warn` MEDIUM out-of-step |
| `review-bypass` | @Verifier MODE=REVIEW Pass C | 5.4 | `pass` CLEAN; `block` CRITICAL `--no-verify`/`@SuppressWarnings` etc. |
| `review-runbook` | @Verifier MODE=REVIEW Pass E | 5.4 | `pass` CLEAN; `warn` runbook quality issues |
| `review-adversarial` | @Verifier MODE=REVIEW Pass A* | 5.4 (Critical-EC only) | `pass` CLEAN; `block` what-is-missing finding |
| `unchanged-call-sites` | @Main | 5.4a | `info` always (advisory; never blocks) |
| `runbook-complete` | @Main | 5.6 | `pass` 4 sections present; `block` missing section |
| `ground-truth` | @Main | 5.6 | `pass` artefact attached; `block` missing; `warn` waived; `info` excluded |
| `reconcile` | @Verifier MODE=RECONCILE | 5.7 | `pass` all TCs covered; `block` Critical/High EC uncovered |
| `traceability` | @Verifier MODE=TRACE | 5.8 | `pass` no orphans; `block` orphan AC/EC/endpoint |
| `dod` | @Verifier MODE=DOD | 5.9 | `pass` all 7 checks; `block` ≥1 check fails |
| `diff-review` | @Main | 5.10 | `pass` PO `/kit-approve` (or auto-approved); `block` `/kit-revert <file>` or `/kit-rework` |
| `mutation-sample` (v7.1.0+) | @Verifier MODE=MUTATION-SAMPLE | 5.6 (auto for backend) or `/kit-mutate` ad-hoc | `pass` killed≥THRESHOLD; `block` killed<THRESHOLD; `info` skipped (trivial lane / no production code in CHANGED_FILES). `reason` carries `<killed>/<total> killed; threshold <T>; tool <name>`. |

PO commands also generate rows (read-only events for cross-reference):

| Gate id | Owner | Fires when | Verdict |
|---|---|---|---|
| `defect-origin` | /kit-defect | PO reports defect at 5.6 | `info`; `reason` is the `--origin=<value>` |
| `revert-step` | /kit-revert-step | PO reverts step | `info` |
| `ground-truth-waiver` | /kit-approve --no-ground-truth | PO overrides 5.6 ground-truth | `warn` |

## Process

```
0. CHECK opt-in. Does `evals/runs/<kit_version>/` exist?
   - No → STOP, no-op.
   - Yes → continue.

1. RESOLVE inputs (passed by caller):
   - task_slug
   - step_idx (default 0 for pre-EXECUTE)
   - gate id (from § Gate enumeration)
   - verdict ∈ {pass, block, warn, info}
   - blocked_close: true iff this verdict prevented CLOSE
   - lane (from task file; v6.3 always 'standard')
   - reason (≤120 chars; `(none)` if no specific reason)

2. CHECK manifest.telemetry.gates_log_enabled (default true).
   If false → STOP, no-op.

3. FORMAT row:
   <ISO timestamp>,<task_slug>,<step_idx>,<gate>,<verdict>,<blocked_close>,unknown,<lane>,"<reason escaped for CSV>"

   - Quote `reason` if it contains commas; escape internal quotes by doubling.
   - `false_positive` always `unknown` at write time.

4. APPEND to `evals/runs/<kit_version>/gates.csv`.
   - If file does not exist → create with header line first:
     `timestamp,task_slug,step,gate,verdict,blocked_close,false_positive,lane,reason`
   - Use append mode; one row per call.

5. Return silently to caller. No output.
```

## Aggregation (read by eval-collector at CLOSE)

`eval-collector` (`kit/_shared/skills/eval-collector/SKILL.md.template`) reads `gates.csv` at task CLOSE and computes for each gate:

```
total_fires        = count of rows where gate == <id>
blocking_fires     = count where blocked_close == true
signal_ratio       = blocking_fires / total_fires (per gate, this task)
unique_classes_caught = (read from defects.csv: defects with origin matching
                        this gate's class that were NOT blocked here pre-CLOSE)
```

`unique_classes_caught` requires cross-reference between `gates.csv` and `defects.csv`:

- For each defect in `defects.csv` for this task: if `defect.origin` maps to a gate (e.g. `origin=ui` → gate `ground-truth`), AND that gate logged a `pass` for the same step BEFORE the defect was reported → the gate **missed** this defect. Increment that gate's `false_negatives` counter.
- Conversely: if the gate logged `block` and PO did NOT subsequently report a defect for that step → the gate **caught** something genuine. Increment `true_positives`.
- A gate's effective signal = `true_positives / (true_positives + false_negatives + false_positives)`.

False positive detection is best-effort in v6.3 — it requires PO to confirm at /kit-defect time which gate fired wrongly. Add the optional flag `--false-positive=<gate_id>` to /kit-defect for cases where PO can attribute (v6.3 partial; full attribution is v7+).

## Aggregation cadence

- **Per-task at CLOSE**: eval-collector writes signal_ratio per gate into `evals/runs/<kit_version>/<task_slug>.md` § "Gate signals (v6.3+)".
- **Cross-task at /kit-status**: `/kit-status` reads gates.csv and SUMMARY.md, reports rolling signal_ratio over last `manifest.telemetry.evaluation_window_tasks` (default 30) tasks. Highlights gates with signal_ratio < `manifest.telemetry.signal_ratio_threshold` (default 0.05) as deprecation candidates.

## Deprecation candidate criterion

Per `PIPELINE_V7_PROPOSAL.md` § 8.2 exit criterion:

```
IF rolling_signal_ratio(gate) < threshold over evaluation_window_tasks
   AND no defect_origin in window matches this gate's class:
   → flag gate as deprecation_candidate in /kit-status output
   → next minor release should remove the gate
```

The skill itself does NOT auto-deprecate. It surfaces the data; PO + maintainer decide. v7+ moves toward automatic removal once tooling matures.

## Hard rules

1. **Append-only.** Never rewrite gates.csv rows; corrections go via additional rows. Audit data is immutable.
2. **No external network.** Local filesystem only.
3. **Idempotent on retry.** If the same gate fires twice for the same (task_slug, step, gate) due to a fix loop, append BOTH rows — the retry information is signal.
4. **Honest unknowns.** `false_positive: unknown` at write time. The aggregation step (eval-collector) updates it. Never guess at write time.
5. **CSV-quote correctly.** `reason` may contain commas, quotes, newlines (last is rare). Always quote the field with double-quotes; escape internal quotes by doubling. No CSV libraries available in @Main runtime — agents follow this rule manually.
6. **Schema versioning.** If a future kit version changes column count → add columns at the END only; never reorder. Existing rows must remain parseable. Add a header row update only on file creation; do not migrate existing files mid-flight.

## Cross-host compatibility

Host-agnostic. Runs in @Main's turn (or subagent's turn) on both OpenCode and Claude Code. Pure prompt + filesystem operations.

## Notes for kit maintainers

- This skill replaces ad-hoc telemetry sprinkled across @Main's body. Each gate now invokes the skill with structured inputs; @Main's body documents `which gates fire where` (already there in this skill's § Gate enumeration table).
- When adding a new gate (v6.4+): add a row to § Gate enumeration here, log it from the gate's owner agent, and explicitly state the deprecation criterion in the changelog entry.
- gates.csv is gitignored by template (audit data, may be sensitive). PO commits selectively if they want eval evidence in repo.
