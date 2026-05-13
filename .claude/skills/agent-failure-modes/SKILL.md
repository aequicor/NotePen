---
name: "agent-failure-modes"
description: "Catalogue of six diff-level failure modes a v3 step can hide even when BUILD is green — read before approving any `standard` / `heavy` step."
---
<skill name="agent-failure-modes">

<purpose>
Catalogue of six diff-level failure modes a v3 step can hide even when BUILD is green — read before approving any `standard` / `heavy` step.
</purpose>

<when_to_invoke>
| Stage | Trigger |
|---|---|
| Session 2, after STEP SUMMARY | reviewing the diff of a step commit before replying `next` |
| Session 2 Stage 4 backstop  | reviewing the full cumulative `git diff <plan-commit>..HEAD` |
| Session 3, after fix commit | sanity-checking that the fix didn't introduce a new failure mode |
| Any session, on a `light` step | one matching pattern means the step is mistyped — escalate to `standard` |

Tests passing and `BUILD: green` in the Agent-verified section **do not** catch these. Read the diff with this catalogue in mind.
</when_to_invoke>


<procedure>
Six patterns. Skim the diff for each in order. Any hit on a `light` step → escalate the tier. Any hit on `standard` / `heavy` → reject the step with `/kit-fix`, not `next`.

## 1. Tests deleted or weakened

In the diff, look for:
- removed lines like `-@Test`, `-it("...")`, `-test(...)` (or your framework's equivalent)
- an assertion rewritten as a tautology — `assertTrue(true)`, `expect(true).toBe(true)`, `assertEquals(x, x)`
- new `@Ignore` / `xit(...)` / `pytest.mark.skip` / `@Disabled` markers

Tests passing because they were silenced are not tests passing.

## 2. Fabricated imports

A class / function / module name looks plausible but does not exist in the repo or in a declared dependency. Before approving, grep the name — if it does not resolve, the agent invented it.

## 3. Scope creep on a `light` step

Plan said "rename X" but the diff also touches unrelated files, reorders methods, or "fixes" lint warnings nobody asked for. Reject the step and ask for a focused redo.

## 4. Silent dependency or build-config additions

Look for new `implementation("…")`, new entries in `package.json` / `requirements.txt` / `pyproject.toml`, new tasks in build files, new MCP servers in settings. These should never appear on a `light` step. On `standard` / `heavy` they must be explicit in the plan; if not, the step is misclassified.

## 5. Try / catch that swallows errors

A new `try { … } catch (_) { }`, `try: … except: pass`, `catch (e) { }` without re-raise or log is the canonical way an agent "fixes" a flaky test or a hard error path. Ask what should happen on the error.

## 6. Suppression of static checks

New `@Suppress(...)`, `// @ts-ignore`, `# type: ignore`, `// eslint-disable`, `// noinspection ...` — these bypass checks, not pass them. Each one needs a justification in the step's Plan deviations or Uncertain section.
</procedure>

<output_format>
This skill produces no artifact of its own. When invoked it shapes the human-required side of the next STEP SUMMARY or FIX SUMMARY: if any pattern fires, list the matching pattern number and a one-line pointer to the offending line in the Uncertain section of that summary. Example: `Uncertain: pattern #1 — assertEquals(x, x) added at src/Foo.kt:42`.
</output_format>

</skill>
