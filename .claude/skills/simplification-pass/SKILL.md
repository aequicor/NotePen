---
name: "simplification-pass"
description: "Behavior-preserving complexity reduction before commit — collapse, inline, delete — without changing what tests assert."
---
<skill name="simplification-pass">

<purpose>
Behavior-preserving complexity reduction before commit — collapse, inline, delete — without changing what tests assert.
</purpose>

<when_to_invoke>
| Stage | Trigger |
|---|---|
| Session 2 Stage 3 step 3 | after green code is in place, before drafting the STEP SUMMARY |
| Session 2 Stage 3 step 5 | when `BUILD: green` but the diff smells over-built |
| Session 3 step 4 | after the fix passes, before composing the FIX SUMMARY |

The pass is mandatory on `standard` / `heavy` steps and recommended on `light` ones. Skip when: the step is doc-only, or the diff is already <20 lines and has no abstractions.
</when_to_invoke>


<procedure>
Six moves. Apply each by reading the diff once with this checklist in hand. Stop when no more apply; do not invent a seventh just because the list has six.

## 1. Inline single-use helpers

A function called from one place, with no test of its own, is overhead. Inline it. If it had a test, the test moves to the call site.

## 2. Collapse pass-through wrappers

```
foo(x) { return bar(x) }
```
The wrapper exists for no reason — every caller can call `bar` directly. Delete the wrapper, retarget the callers. Exception: an interface-level wrapper that documents intent (`fun render(): String = renderHtml()`) earns its line if `renderHtml` is part of a wider family.

## 3. Delete commented-out code

Git remembers. Code in `//` purgatory does not. If "we might need it again", it's still in `git log`. Delete now.

## 4. Replace conditional branches that reach the same outcome

```
if (x) doA() else doA()       // collapse
if (x) return null else return null  // collapse
return if (x) y else y         // collapse
```
Look for the shape, not the literal text — diffs are full of "if (cfg.flagA && cfg.flagB) … else …" where both branches assemble nearly the same value.

## 5. Replace `Optional<List<T>>` / `null`-or-empty with one shape

If a method returns `null` or an empty list to mean "no results", pick one and stick with it. Callers should not need to remember which the API uses today.

## 6. Move a constant up to its first use

A literal repeated three times across the file becomes a top-level `const`. A literal used once stays inline — extracting it adds a name to scan.

## Anti-patterns this pass prevents

- Extract-method-for-readability that pulls 4 lines into a function named `helper`. The function is the noise, not the inlined code.
- "Add abstraction now, we'll need it later." If the second caller doesn't exist yet, the abstraction is a guess.
- Renaming during simplification. Renames are a separate concern; mixing them in obscures what the pass actually changed.
- Deleting code that's actually load-bearing for a hidden caller (reflection, plugin loader, JNI). Grep first.
</procedure>

<output_format>
The pass leaves the same green test outcome and a smaller diff. No artifact of its own. STEP SUMMARY's `Plan deviations` may briefly cite the move (`Plan deviations: inlined unused helper computeKey() — single caller`) but only when the simplification touches code outside the step's primary file.
</output_format>

</skill>
