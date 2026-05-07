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

This is the **single source of truth** for the trace conventions. Do not redefine them in agent bodies — link here.

## When to use

- `@TraceabilityChecker` invokes this skill at every run.
- `@Reviewer` consults it to verify spec-alignment claims at Pass A1.
- `@TestKeeper` follows the conventions when generating test-cases.md (`MODE=GENERATE` and `MODE=DRAFT`).
- `@CodeWriter` follows the impl-link convention when writing test files (so reconciliation by `@TestKeeper RECONCILE` works).

## ID conventions (must be respected by all writers)

| Family | Format | Example | Source document |
|--------|--------|---------|-----------------|
| Acceptance Criterion | `AC-N` | `AC-7` | `spec.md` § Acceptance Criteria |
| Edge Case | `EC-N` | `EC-2` | `spec.md` § Edge Cases (severity recorded inline in the table) |
| Test Case | `TC-N` | `TC-04` | `test-cases.md` |
| Spec endpoint | `<METHOD> <path>` | `POST /orders` | `spec.md` § How it works |

IDs are stable across the lifetime of a feature. Renumbering is forbidden — only **append** new IDs.

## Verifies cell convention in test-cases.md

Every TC row has a `Verifies` cell listing which AC/EC ids it covers. This is what makes the AC↔TC and EC↔TC mapping greppable.

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

```
| TC-2 | PASS | unit | ... | AC-4 | tests/orders/validation_test.kt:88, tests/orders/api_test.kt:14 | |
```

`@TraceabilityChecker` parses the `Test impl` cell and verifies every listed file exists. Missing file → `MISSING_IMPL`.

The impl path **belongs in the `Test impl` column**. Do not write it inside the `Description` cell (v4 used `(impl: ...)` notation inside Description; v5 separates the columns).

## Endpoint → handler heuristic

For each spec endpoint, `@TraceabilityChecker` searches the source tree for a handler match in this order. Stop at the first match.

1. **Decorator / attribute syntax**:

   | Framework | Pattern |
   |-----------|---------|
   | Spring (Kotlin/Java) | `@PostMapping("/orders")`, `@RequestMapping(method=POST, value="/orders")` |
   | Ktor | `post("/orders") { ... }` inside a `routing { }` block |
   | Express / Fastify | `app.post('/orders', ...)`, `router.post('/orders', ...)` |
   | NestJS | `@Post('/orders')` |
   | FastAPI | `@app.post("/orders")` |
   | Rails | `post '/orders', to: '...'` |
   | Go (chi/gin/echo) | `r.Post("/orders", ...)`, `e.POST("/orders", ...)` |

2. **Route table / config** — single file declares route → handler mappings. Search for the literal path string `'/orders'` or `"/orders"`.

3. **Convention** — handler name matches `<Method><Resource>Handler` or `<resource>_<method>` (e.g. `createOrderHandler`, `orders_post`). Last-resort heuristic; flag as `LOW_CONFIDENCE`.

If steps 1–3 all miss → `ENDPOINT_ORPHAN`. Report includes the patterns tried.

## Handler → TC heuristic (reverse direction)

Given a source handler, find the TCs that exercise it:

1. Scan for test files whose imports include the handler's class / module.
2. For each such file, check whether a TC's `Test impl` cell points to it.
3. The intersection is the set of TCs covering the handler.

A handler with **no covering TC** is a **handler orphan**. `@TraceabilityChecker` reports both directions.

## Weak-assertion criterion

A test counts as a "real assertion" if its body contains at least one of:

| Category | Examples |
|----------|----------|
| Equality / comparison against an expected value | `assertEquals(expected, actual)`, `expect(x).toBe(y)`, `assert x == y`, `result shouldBe expected` |
| Verification of side-effect / call | `verify(repo).save(...)`, `expect(spy).toHaveBeenCalledWith(...)`, `mock.assert_called_once_with(...)` |
| Status / contract assertion | `expect(response.status).toBe(201)`, `assertThat(headers["X-Token"]).isNotEmpty()` |
| State machine assertion | `assertEquals(OrderStatus.PAID, order.status)` |
| Exception-shape assertion | `assertThrows<InvalidQuantityException> { ... }` (the type must be specific, not `Exception` or `Throwable`) |

A test with **only** these signals is `WEAK_ASSERTION`:

| Signal | Why weak |
|--------|----------|
| `assertNotNull(result)` alone | Confirms the function returned, says nothing about correctness |
| `assertTrue(success)` where `success` is a boolean returned from the SUT, with no separate observation | Tautology |
| `// No exception thrown — pass` | Confirms only that the path didn't blow up |
| `expect(result).toBeDefined()` alone | Same as `assertNotNull` |
| `assertThat(list).isNotEmpty()` for a Critical EC | Says nothing about which items are in the list |

`@TraceabilityChecker` flags `WEAK_ASSERTION` only on coverage of **Critical or High** items. In v5 these are **info-only** — they don't block `@DoDGate`.

## TC ↔ AC/EC validation

For every TC with a Verifies cell containing an id:

- `AC-N` → AC-N must exist in `spec.md` § Acceptance Criteria.
- `EC-N` → EC-N must exist in `spec.md` § Edge Cases.

Mismatches are `TC_ORPHAN` — the TC references a non-existent item. Reported, blocks DoD.

## Reverse direction (orphan from spec to TC)

For every AC, every Critical EC, every High EC (without `[deferred]`), every spec endpoint:

- At least one TC must reference it.

Items with no TC are reverse orphans. Reported as `AC_UNCOVERED` / `EC_UNCOVERED` / `ENDPOINT_UNCOVERED`. Block DoD if Critical EC or any AC is uncovered.

## Implementation by `@TestKeeper` and `@CodeWriter`

These rules are not optional for writers:

- `@TestKeeper MODE=GENERATE` populates the `Verifies` cell on every row, mirroring `spec.md § Test plan`.
- `@TestKeeper MODE=RECONCILE` fills the `Test impl` cell after `@CodeWriter` finishes a step.
- `@CodeWriter` writes test files at predictable paths so `@TestKeeper RECONCILE` can locate them. Convention:
  - `<src-path>` ends in `.kt` → corresponding test in `src/test/.../<Name>Test.kt` (mirrored).
  - `<src-path>` ends in `.ts` / `.tsx` → corresponding test in `<same-dir>/<name>.test.ts(x)`.
  - `<src-path>` ends in `.py` → corresponding test in `tests/<mirrored-path>/test_<name>.py`.
- Other languages: follow the project's existing convention; record the convention in the module's guideline file so this skill can reference it.

## Notes

- This skill is **read-only documentation** with no execution. Agents apply the rules in their own steps.
- If new ID families are added (e.g. `RFC-N` for design decisions), update the table here first; agents read this skill, not their bodies.
- v4 used a `(impl: <path>)` notation inside the Description cell of test-cases.md. v5 has a separate `Test impl` column for cleanliness — that is what `@TraceabilityChecker` parses.
