---
name: mutation-sample
description: v7.1.0+ generate small mutants of the step's CHANGED_FILES and verify the test suite catches them. Provides the "ground truth" backend artefact at 5.6 CHECKPOINT (≥1 mutant killed for standard backend, ≥3 for critical). Closes the v6.x→v7.0 gap where Critical-EC backend ground-truth required PO to attach a mutation-sample report manually. Auto-detects language tooling from manifest.stack.language and falls back to AI-driven mutation when no native tool is available.
---

# Mutation-sample skill

v7.1.0+ skill. Replaces "PO attaches mutation-sample report manually" with auto-generation. Backed by Meta's Automated Compliance Hardening (ACH) approach: code coverage is weakly correlated with bug detection; mutation testing measures "do the tests actually catch defects" by injecting small faults and running the suite.

## Why exists

v6.2 (P18) added the ground-truth gate at 5.6 CHECKPOINT. For backend changes (no UI surface, no command output, no contract test), the type is `mutation-sample-pass`: ≥1 mutant killed for standard backend, ≥3 for critical (`manifest.lanes.critical_require_mutation_sample`).

Until v7.1.0, the gate was satisfied by PO attaching a mutation-sample report (e.g. PIT XML, Stryker JSON) via `/kit-attach`. This put the burden on PO and made the "critical-lane backend ground-truth" practically optional in many projects.

v7.1.0 wraps mutation generation inside @Verifier MODE=MUTATION-SAMPLE. PO no longer has to know what tooling to run; the skill picks the right one per language.

## When invoked

Three call sites:

1. **@Verifier MODE=MUTATION-SAMPLE** — explicit invocation by @Main at 5.6 CHECKPOINT when REQUIRED_TYPE = `mutation-sample-pass` AND no `/kit-attach`-supplied artefact exists yet.
2. **Manual PO** — `/kit-mutate` command (thin wrapper, also v7.1.0+) for ad-hoc invocation outside the pipeline.
3. **Critical-lane override** — when `manifest.lanes.critical_require_mutation_sample: true` AND step.risk == critical, the skill is mandatory regardless of REQUIRED_TYPE inferred from step nature.

## Inputs (from caller)

```
CHANGED_FILES: <list of source files modified by @CodeWriter>
   (test files excluded; mutation runs on production code only)
LANGUAGE: <from manifest.stack.language>
TEST_COMMAND: <project's test command for the step's module>
THRESHOLD: <int — ≥1 (standard) | ≥3 (critical)>
LANE: <trivial | standard | critical>
TIMEOUT_SECONDS: <default 300; per-mutant test run cap>
MAX_MUTANTS: <default 10; cap on generation to keep cost bounded>
```

## Tooling detection

Per language, the skill picks the first available tool. Detection runs `which <tool>` (or platform equivalent) to verify install:

| Language | Tool | Detection | Output format |
|---|---|---|---|
| `kotlin` / `java` | [PIT](https://pitest.org/) | `pitest-cli` or Gradle plugin | XML (parses `mutationsKilled` / `mutationsTotal`) |
| `typescript` / `javascript` | [Stryker](https://stryker-mutator.io/) | `stryker` in node_modules or global | JSON (`metrics.killed` / `metrics.totalUndetected`) |
| `python` | [mutmut](https://github.com/boxed/mutmut) | `mutmut` in PATH | Text (parses `<n>/<m> mutants killed`) |
| `go` | [Gremlins](https://gremlins.dev/) | `gremlins` in PATH | JSON |
| `rust` | [cargo-mutants](https://github.com/sourcefrog/cargo-mutants) | `cargo mutants` works | Text |
| `generic` / unknown | AI-driven (fallback) | always available | structured agent output |

If the configured tool is NOT installed:
- Surface to PO once: "mutation-sample requires `<tool>` for language `<lang>`. Install it (`<install-cmd>`) or fall back to AI-driven mutation (lower fidelity but no install)."
- PO chooses: install + retry, or `--fallback-ai` flag to proceed with AI mutation.

## AI-driven mutation (fallback)

When no native tool is available or PO opts in:

1. Read each file in CHANGED_FILES. Identify candidate mutation points:
   - Boundary conditions: `>` ↔ `>=`, `<` ↔ `<=`, `==` ↔ `!=`
   - Negation: remove or insert `!` / `not`
   - Constants: `0` → `1`, `true` → `false`
   - Method calls: skip a side-effect call (replace with `Unit` / `null` / `pass`)
   - Conditional branches: invert `if` / `else if` body
2. Generate up to MAX_MUTANTS candidates (prefer points in lines added/modified by this step's diff over lines untouched).
3. For each mutant: apply the change in-memory (write to temp file or via patch), run TEST_COMMAND with TIMEOUT_SECONDS cap, record killed (test failed → mutant detected) or survived (test passed → mutant slipped through).
4. Restore original file before next mutant (avoid cumulative changes).

AI-driven mutation is intentionally conservative — its goal is detecting "tests don't actually exercise this branch", not exhaustive mutation testing. A native tool gives stronger signal; the AI fallback covers projects where install isn't feasible.

## Process

```
0. CHECK manifest.lanes.critical_require_mutation_sample (default true) and
   incoming THRESHOLD. If THRESHOLD == 0 (skill mis-invoked) → STOP, return
   PASS (skipped: threshold zero).

1. DETECT tool. If LANGUAGE has a native tool installed → use it.
   Else if `--fallback-ai` flag set or no native option → use AI-driven path.
   Else → STOP, return BLOCK with install instructions for native tool.

2. SCOPE mutation. Filter CHANGED_FILES:
   - Remove test files (`*Test.kt`, `*.test.ts`, `tests/**`, `test_*.py`, etc.).
   - Remove generated/build outputs (`build/`, `dist/`, `target/`, `node_modules/`).
   - If 0 files remain after filter → STOP, return PASS (skipped: no production
     code in CHANGED_FILES; e.g. test-only or docs-only step).

3. GENERATE mutants per tool's strategy (or AI heuristics above). Cap at
   MAX_MUTANTS. Prefer mutants in files with lines added/modified by this
   step's diff (`git diff <step.parent_sha>..HEAD -- <file>`).

4. RUN test suite per mutant with TIMEOUT_SECONDS cap.
   - killed: test failed (mutation detected, tests are sensitive to this code).
   - survived: test passed (mutation slipped through, tests miss this branch).
   - timeout: test ran longer than cap (count as survived; record reason).

5. AGGREGATE:
     killed = count of killed mutants
     survived = count of survived (incl. timeouts)
     score = killed / (killed + survived) if denominator > 0 else N/A

6. VERDICT:
   - killed >= THRESHOLD → PASS.
   - killed < THRESHOLD → BLOCK with the per-mutant report (which mutants
     survived and where).

7. WRITE artefact to `.planning/artifacts/<task_slug>/step-<N>/mutation-sample.md`
   (gitignored by default; PO commits explicitly if they want eval evidence).
   Format:
     ```
     # Mutation-sample report — step <N>
     Tool: <native-tool-name | ai-driven>
     Language: <lang>
     Generated: <ISO>

     | # | File | Line | Mutation | Verdict | Test runtime |
     |---|------|------|----------|---------|--------------|
     | 1 | UserService.kt | 42 | `>` → `>=` | killed | 12s |
     | 2 | UserService.kt | 67 | remove `!` | survived | 8s |
     ...

     **Killed: <n> / Survived: <m>**
     **Score: <killed/(killed+survived)>**
     **Verdict: PASS | BLOCK (threshold: <T>)**
     ```

8. UPDATE step_commits[N].ground_truth = {
     type: "mutation-sample-pass",
     path: ".planning/artifacts/<task_slug>/step-<N>/mutation-sample.md",
     summary: "<killed>/<total> mutants killed (threshold ≥<T>)",
     waived: false,
     attached_at: <ISO>
   }.

9. RETURN to caller:
   ```
   MUTATION-SAMPLE RESULT
   verdict: PASS | BLOCK
   killed: <n>
   survived: <m>
   threshold: <T>
   tool: <name>
   report: <path>
   ```
```

## Telemetry

Logs to gates.csv via gate-telemetry skill:
```
gate: mutation-sample
verdict: pass | block
reason: <killed>/<total> killed; threshold <T>; tool <name>
```

`gate-telemetry` § Gate enumeration table extended at v7.1.0 to register `mutation-sample` as a v7.1+ gate.

## Cost considerations

Mutation testing is expensive — each mutant runs the full test suite.

| Lane | THRESHOLD | MAX_MUTANTS | Typical cost (5-min test suite) |
|---|---|---|---|
| trivial | (skipped — no backend artefact required) | — | 0 |
| standard backend | 1 | 5 | up to 25 min |
| critical backend | 3 | 10 | up to 50 min |

For projects with slow test suites, PO can:
- Lower MAX_MUTANTS (manifest field `mutation_sample.max_mutants`).
- Limit scope: target only the step's smallest file (`mutation_sample.scope: smallest_file_only`).
- Cache mutants across re-runs (skill writes `.planning/artifacts/<task>/step-<N>/mutants.cache.json` keyed by file sha; on re-invocation, only re-tests survivors).

## Failure modes

- **Tool times out on every mutant** → BLOCK with reason "tool timeout; suite likely too slow". PO action: increase TIMEOUT_SECONDS or reduce scope.
- **Tool returns no mutants** (e.g. step changed only whitespace) → PASS with note "no mutation candidates in CHANGED_FILES" (legitimate; not a quality signal).
- **Tool crashes mid-run** → BLOCK with stderr captured. PO investigates; not a kit bug.
- **AI fallback generates non-compiling mutant** (syntax error) → discard mutant, retry generation up to 3 times before giving up on that point.

## Hard rules

1. **Mutate production code only.** Test files, generated code, lock files, docs → excluded from mutation.
2. **Restore between mutants.** Each mutant runs in isolation; no cumulative changes to working tree. Use temp files or `git stash`/`git stash pop` if the tool requires on-disk mutation.
3. **Cap on cost.** MAX_MUTANTS hard cap (default 10). Never generate more without PO opt-in via manifest.
4. **Honest about fallback.** When using AI-driven mutation, label it explicitly in the report ("Tool: ai-driven"). Native tool > AI fallback for confidence.
5. **No mutation in trivial lane.** Trivial-lane backend ground-truth (if any) uses simpler artefacts — diff-stat + smoke test. Mutation testing is for standard/critical only.
6. **Idempotent re-run.** Re-invocation on the same step's CHANGED_FILES with the same THRESHOLD produces deterministic results (cache survivors only; do NOT re-run killed mutants).
7. **Never skip silently.** If mutation cannot run for any reason, return BLOCK with explicit reason. The 5.6 gate then forces PO to either fix the cause or `/kit-approve --no-ground-truth` (logged as technical debt).

## Cross-host compatibility

Host-agnostic. Skill runs in @Verifier's turn (subagent in OpenCode and Claude Code). Native tool detection uses platform-appropriate checks (`which` on POSIX, `Get-Command` on Windows).

## Notes for kit maintainers

- Each new language adds a row to the Tooling-detection table above + a tested invocation snippet in the prose.
- Tool output formats drift across versions. The skill describes which fields to parse; an integration test in `evals/` would validate the parsing per tool version. v7.1.0 ships without that integration test — it's a known gap.
- AI-driven mutation is the floor, not the ceiling. The skill prefers native tools because they are auditable, deterministic, and produce machine-parseable reports. Track per-project signal_ratio: if AI-fallback mutation never blocks (signal_ratio = 0), suggest PO install the native tool.
