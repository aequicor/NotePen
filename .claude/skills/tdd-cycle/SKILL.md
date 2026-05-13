---
name: "tdd-cycle"
description: "Red-Green-Refactor cycle calibrated to v3 step tiers — write the failing test first, make it pass, then clean. Skipped on pure refactor or doc steps."
---
<skill name="tdd-cycle">

<purpose>
Red-Green-Refactor cycle calibrated to v3 step tiers — write the failing test first, make it pass, then clean. Skipped on pure refactor or doc steps.
</purpose>

<when_to_invoke>
| Stage | Trigger |
|---|---|
| Session 2 Stage 3 step 2 | starting a `standard` or `heavy` step whose Goal asserts new behavior |
| Session 2 Stage 3 step 4 | when `BUILD: red` is from a missing test, not a regression |
| Session 3 step 5 | composing the regression guard inside the [debug-loop](debug-loop) skill |

Skip when: the step is `light` (rename, format, doc), the step is a pure refactor with `no-test-changes: false` but no new behavior, or the project has no test runner declared in `stack.test_command`.
</when_to_invoke>


<procedure>
Three phases, in order. Each phase ends with a commit-grade artifact (failing test, passing test, clean code). Do not collapse them — the value is in the discipline, not the result.

## 1. Red

Write the smallest test that asserts the new behavior. Run it. Confirm it fails for the expected reason (assertion mismatch, not compile error or test-not-found). A test that fails for the wrong reason is not yet red.

Pick the test layer by impact:

| Layer | Use when | Cost |
|---|---|---|
| unit | the behavior lives in one class / module | seconds |
| integration | the behavior spans two or three collaborators | <1 minute |
| end-to-end | the behavior is observable only through the UI / API surface | minutes |

Default to the cheapest layer that still pins the behavior. A unit test on a wrapper around a network call is not pinning anything — push it to integration.

## 2. Green

Write the minimum production code that makes the red test pass. Resist the urge to also fix adjacent issues, refactor neighboring code, or rename for clarity. Those land in step 3 or in a separate step.

If the green code is obviously the wrong shape (duplication, mixed concerns), that's a signal — finish green first, then deal with it in refactor. Cleanups before green is what produces over-engineered "fixes" that don't actually pass the test.

## 3. Refactor

With green in hand, simplify. Concrete moves:
- Extract a helper when the same expression appears twice.
- Collapse a temporary into its single use.
- Rename a variable whose meaning shifted during step 2.
- Push a constant up to the file's top when it's used by more than one function.

Run the test after each move. The cycle's invariant is: tests stay green from now until commit.

## Anti-patterns

- **Test-after.** Writing the test after the implementation passes guarantees the test reflects the implementation, not the spec. The test will pass even when the spec is wrong.
- **Test that doesn't fail first.** If you can't make it red on purpose, you don't know what it asserts.
- **Refactor during green.** Moving code while making a test pass conflates two changes — neither is reviewable.
- **One huge red.** If the failing test spans 200 lines and three concepts, split into three smaller cycles.
</procedure>

<output_format>
Three commit-grade artifacts inside the step's single auto-commit: the new test (red moment captured in commit message body if useful), the production change (green), the small cleanup (refactor). The STEP SUMMARY's Agent-verified `BUILD:` line covers all three.
</output_format>

</skill>
