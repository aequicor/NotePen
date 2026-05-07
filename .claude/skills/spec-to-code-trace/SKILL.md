---
name: spec-to-code-trace
description: Heuristics and conventions for linking spec/requirements/corner-case IDs to test files and source symbols. Read by @TraceabilityChecker (mandatory) and @CodeReviewer (when reviewing spec alignment). Defines tag prefixes, impl-link format, endpoint detection rules, and the "weak assertion" criterion.
---

# Spec-to-Code Trace Skill

Shared rules for building and verifying the chain:

```
AC / CC / spec endpoint  →  TC row in test-cases.md  →  test file path  →  source symbol / route handler
```

`@TraceabilityChecker` reads this skill to construct the trace report. `@CodeReviewer` reads it to evaluate "spec alignment" claims. `@QA` writes test-cases rows in formats this skill recognizes. `@CodeWriter` writes test files whose paths fit the impl-link format.

This is the **single source of truth** for the trace conventions. Do not redefine them in agent bodies — link here instead.

## When to use

- `@TraceabilityChecker` invokes this skill at every run.
- `@CodeReviewer` consults it to verify spec-alignment claims at focus area 3.
- `@QA` follows the tag-prefix convention when adding rows in REQUIREMENTS / IMPLEMENTATION phase.
- `@CodeWriter` follows the impl-link convention when writing test files (so reconciliation in `@QA FINAL` works).

## ID conventions (must be respected by all writers)

| Family | Format | Example | Source document |
|--------|--------|---------|-----------------|
| User Story | `US-N` | `US-3` | `<feature>.md` (requirements) |
| Acceptance Criterion | `AC-N` | `AC-7` | same; nested under a US |
| Corner Case | `CC-N` (with severity tag) | `CC-2` (Critical) | `<feature>-corner-cases.md` |
| Test Case | `TC-NN` | `TC-04` | `<feature>-test-cases.md` |
| Spec endpoint | `<METHOD> <path>` | `POST /orders` | `<feature>.md` (spec) |
| Pre-mortem risk | `R-N` | `R-3` | plan's `## Pre-mortem risks` |

IDs are stable across the lifetime of a feature. Renumbering is forbidden — agents may only **append** new IDs.

## Tag-prefix convention in test-cases.md Description

Every TC row's `Description` cell starts with a tag bracketing its source. This is what makes the AC↔TC and CC↔TC mapping greppable.

| TC source | Prefix | Example |
|-----------|--------|---------|
| Happy path for a User Story | `[US-N]` | `[US-2] submit valid order, expect confirmation` |
| Acceptance Criterion verification | `[AC-N]` | `[AC-3] reject quantity = 0 with explicit message` |
| Corner case (severity in tag) | `[CC-N <Severity>]` | `[CC-1 Critical] payment gateway timeout > 30s` |
| Spec-only impl test (no business AC) | `[spec]` | `[spec] POST /orders rejects payload > 1MB` |
| PO-added scenario (free-form bug) | `[PO-added]` | `[PO-added] login fails when email contains '+'` |
| Pre-mortem risk | `[premortem-R<N>]` | `[premortem-R3] order-creation 504 not observable` |
| Tester-found bug not in spec | `[bug-fix]` | `[bug-fix] crash when uploading 2GB CSV` |

A row whose Description has **no** tag is treated as `[PO-added]` for the purpose of trace.

## Impl-link convention (added by `@QA` IMPLEMENTATION FINAL or `@CodeWriter`)

Once a TC has a real test in code, its Description gets a parenthesized `(impl: <path>)` reference appended:

```
[CC-1 Critical] payment gateway timeout > 30s (impl: src/orders/payment.test.ts)
```

Multiple test files: comma-separated.

```
[AC-3] reject quantity = 0 with explicit message (impl: src/orders/validation.test.ts, src/orders/api.test.ts)
```

`@TraceabilityChecker` parses this string with the regex `\(impl:\s*([^)]+)\)` and verifies every listed file exists. Missing file → `MISSING_IMPL`.

The `(impl: ...)` annotation **must not** appear in any column other than `Description`. The test-cases template owns the Notes column for the manual tester.

## Endpoint → handler heuristic

For each spec endpoint, `@TraceabilityChecker` searches the source tree for a handler match in this order. Stop at the first match.

1. **Decorator / attribute syntax** (most languages support a framework annotation):

   | Framework | Pattern |
   |-----------|---------|
   | Spring (Kotlin/Java) | `@PostMapping("/orders")`, `@RequestMapping(method=POST, value="/orders")` |
   | Ktor | `post("/orders") { ... }` inside a `routing { }` block |
   | Express / Fastify | `app.post('/orders', ...)`, `router.post('/orders', ...)` |
   | NestJS | `@Post('/orders')` |
   | FastAPI | `@app.post("/orders")` |
   | Rails | `post '/orders', to: '...'` |
   | Go (chi/gin/echo) | `r.Post("/orders", ...)`, `e.POST("/orders", ...)` |

2. **Route table / config** — a single file declares route → handler mappings. Search for the literal path string `'/orders'` or `"/orders"`.

3. **Convention** — handler name matches `<Method><Resource>Handler` or `<resource>_<method>` (e.g. `createOrderHandler`, `orders_post`). Last-resort heuristic; flag as `LOW_CONFIDENCE`.

If steps 1–3 all miss → `ENDPOINT_ORPHAN`. Report includes the patterns tried.

## Handler → TC heuristic (reverse direction, used by reviewers)

Given a source handler, find the TCs that exercise it:

1. Scan for test files whose imports include the handler's class / module.
2. For each such file, check whether a TC's `(impl: ...)` reference points to it.
3. The intersection is the set of TCs covering the handler.

A handler with **no covering TC** is a **handler orphan** — the inverse of an endpoint orphan. `@TraceabilityChecker` reports both directions.

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
| `assertThat(list).isNotEmpty()` for a Critical CC | Says nothing about which items are in the list |

`@TraceabilityChecker` flags `WEAK_ASSERTION` only on coverage of **Critical or High** items. Weak tests on Low or Medium do not block.

## TC ↔ AC/CC validation

For every TC with a tag prefix, the referenced ID must exist in the source document:

- `[AC-N]` → AC-N must exist in the requirements file.
- `[CC-N ...]` → CC-N must exist in the corner-case register at the indicated severity.
- `[premortem-R<N>]` → R-N must exist in the plan's `## Pre-mortem risks` table.

Mismatches are `TC_ORPHAN` — the TC references a non-existent item. Reported, blocks DoD.

## Reverse direction (orphan from spec to TC)

For every AC, every Critical CC, every High CC (without `deferred:`), every spec endpoint:

- At least one TC must reference it.

Items with no TC are reverse orphans. Reported as `AC_UNCOVERED` / `CC_UNCOVERED` / `ENDPOINT_UNCOVERED`. Block DoD if Critical or HighCC is uncovered or any AC is uncovered.

## Implementation by `@QA` and `@CodeWriter`

These rules are not optional for writers:

- `@QA` REQUIREMENTS phase generates Description with the correct tag prefix on every row.
- `@QA` IMPLEMENTATION FINAL appends `(impl: ...)` references after `@CodeWriter` finishes a stage.
- `@CodeWriter` writes test files at predictable paths so `@QA` FINAL can locate them. Convention:
  - `<src-path>` ends in `.kt` → corresponding test in `src/test/.../<Name>Test.kt` (mirrored).
  - `<src-path>` ends in `.ts` / `.tsx` → corresponding test in `<same-dir>/<name>.test.ts(x)`.
  - `<src-path>` ends in `.py` → corresponding test in `tests/<mirrored-path>/test_<name>.py`.
- Other languages: follow the project's existing convention; record the convention in the module's guideline file so this skill can reference it.

## Notes

- This skill is **read-only documentation** with no execution. Agents apply the rules in their own steps.
- If new ID families are added (e.g. `RFC-N` for design decisions), update the table here first; agents read this skill, not their bodies.
- The conventions intentionally mirror what is already done by `@QA` (tag prefixes) and the corner-case template (`(impl: ...)` notation). This skill consolidates them so they cannot drift.
