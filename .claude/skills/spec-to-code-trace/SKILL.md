---
name: spec-to-code-trace
description: Heuristics and conventions for linking AC / EC / spec endpoint IDs to test files and source symbols. Read by @TraceabilityChecker (mandatory) and @Reviewer (when reviewing spec alignment). Defines tag conventions, impl-link format, endpoint detection rules, and the "weak assertion" criterion.
---

# Spec-to-Code Trace Skill

Shared rules for building and verifying the chain:

```
AC / EC / spec endpoint  →  TC row in test-cases.md  →  test file path  →  source symbol / route handler
```

`@TraceabilityChecker` reads this skill to construct its matrix. `@Reviewer` reads it to evaluate "spec alignment" claims (Pass A1). `@TestKeeper` writes test-cases rows in formats this skill recognizes. `@CodeWriter` writes test files whose paths fit the impl-link format.

This is the **single source of truth** for the trace conventions.

## When to use

- `@TraceabilityChecker` invokes this skill at every run.
- `@Reviewer` consults it to verify spec-alignment claims at Pass A1.
- `@TestKeeper` follows the conventions when generating test-cases.md.
- `@CodeWriter` follows the impl-link convention when writing test files.

## ID conventions

| Family | Format | Example | Source document |
|--------|--------|---------|-----------------|
| Acceptance Criterion | `AC-N` | `AC-7` | `feature.md` § Acceptance Criteria |
| Edge Case | `EC-N` | `EC-2` | `feature.md` § Edge Cases |
| Test Case | `TC-N` | `TC-04` | `test-cases.md` |
| Spec endpoint | `<METHOD> <path>` | `POST /orders` | `feature.md` § How it works |

IDs are stable across the lifetime of a feature. Renumbering is forbidden — only **append** new IDs.

## Verifies cell convention in test-cases.md

| TC source | Verifies | Example |
|-----------|----------|---------|
| Acceptance Criterion verification | `AC-N` | `AC-3` |
| Edge case verification | `EC-N` | `EC-1` |
| Multiple ids | comma-separated | `AC-3, EC-1` |
| Spec-only impl test (no business AC) | `[spec]` | `[spec]` |
| PO-added scenario (free-form bug) | `[PO-added]` | `[PO-added]` |
| Tester-found bug not in spec | `[bug-fix]` | `[bug-fix]` |

A row with **no** Verifies cell is treated as `[PO-added]` for the purpose of trace.

## Impl-link convention (added by `@TestKeeper RECONCILE` or `@CodeWriter`)

Once a TC has a real test in code, its `Test impl` cell gets a path reference:

```
| TC-1 | PASS | unit | [AC-3] reject quantity = 0 ... | AC-3 | tests/orders/validation_test.kt:42 | |
```

Multiple test files: comma-separated.

`@TraceabilityChecker` parses the `Test impl` cell and verifies every listed file exists. Missing file → `MISSING_IMPL`.

The impl path **belongs in the `Test impl` column**. Do not write it inside the `Description` cell.

## Endpoint → handler heuristic

For each spec endpoint, `@TraceabilityChecker` searches the source tree for a handler match in this order:

1. **Decorator / attribute syntax** (Ktor, Spring, etc.):
   - Ktor: `post("/orders") { ... }` inside a `routing { }` block
   - Spring: `@PostMapping("/orders")`

2. **Route table / config** — search for the literal path string `'/orders'` or `"/orders"`.

3. **Convention** — handler name matches `<Method><Resource>Handler` (e.g. `createOrderHandler`). Last-resort heuristic; flag as `LOW_CONFIDENCE`.

If steps 1–3 all miss → `ENDPOINT_ORPHAN`.

## Weak-assertion criterion

A test counts as a "real assertion" if its body contains at least one of:

| Category | Examples |
|----------|----------|
| Equality / comparison | `assertEquals(expected, actual)`, `result shouldBe expected` |
| Side-effect verification | `verify(repo).save(...)` |
| Status / contract assertion | `expect(response.status).toBe(201)` |
| State machine assertion | `assertEquals(OrderStatus.PAID, order.status)` |
| Exception-shape assertion | `assertThrows<InvalidQuantityException> { ... }` (specific type) |

A test with **only** these signals is `WEAK_ASSERTION`:

| Signal | Why weak |
|--------|----------|
| `assertNotNull(result)` alone | Confirms the function returned, says nothing about correctness |
| `assertTrue(success)` tautology | No separate observation |
| `// No exception thrown — pass` | Only confirms no crash |

`@TraceabilityChecker` flags `WEAK_ASSERTION` only on coverage of **Critical or High** items. In v5 these are **info-only** — they don't block `@DoDGate`.

## TC ↔ AC/EC validation

For every TC with a Verifies cell containing an id:
- `AC-N` → AC-N must exist in `feature.md` § Acceptance Criteria.
- `EC-N` → EC-N must exist in `feature.md` § Edge Cases.

Mismatches are `TC_ORPHAN`.

## Reverse direction (orphan from spec to TC)

For every AC, every Critical EC, every High EC (without `[deferred]`):
- At least one TC must reference it.

Items with no TC are reverse orphans: `AC_UNCOVERED` / `EC_UNCOVERED`. Block DoD if Critical EC or any AC is uncovered.

## Implementation by `@TestKeeper` and `@CodeWriter`

- `@TestKeeper MODE=GENERATE` populates the `Verifies` cell on every row.
- `@TestKeeper MODE=RECONCILE` fills the `Test impl` cell after `@CodeWriter` finishes a step.
- `@CodeWriter` writes test files at predictable paths:
  - `<src-path>` ends in `.kt` → corresponding test in mirrored `src/test/.../...Test.kt`.
  - Other languages: follow the project's existing convention.

## Notes

This skill is **read-only documentation** with no execution. Agents apply the rules in their own steps.

v4 used a `(impl: <path>)` notation inside the Description cell of test-cases.md. v5 has a separate `Test impl` column — that is what `@TraceabilityChecker` parses.
