---
name: "doubt-triage"
description: "Classify every doubt before it lands in Human-required — static doubts go to a fresh-context subagent (or main-agent self-resolve), only runtime-evidence reaches the human."
---
<skill name="doubt-triage">

<purpose>
Classify every doubt before it lands in Human-required — static doubts go to a fresh-context subagent (or main-agent self-resolve), only runtime-evidence reaches the human.
</purpose>

<when_to_invoke>
| Stage | Trigger |
|---|---|
| Session 2 Stage 3 step 6 | before filling the STEP SUMMARY's Human-required section |
| Session 2 Stage 4 backstop | before raising any diff-review concerns to the user |
| Session 3 step 7 | before filling the FIX SUMMARY's `Verify by hand:` section |

Hard rule: every candidate line for `Verify by hand:` and `Uncertain:` passes through this skill. The default if triage is skipped is "I dump everything I'm unsure about on the human" — exactly the anti-fatigue this skill exists to prevent.
</when_to_invoke>


<procedure>
Walk the candidate-doubt list (your own surfaced concerns + `doubt-driven-review` output, if loaded) and classify each. The category line: **if a fresh reader of the diff + the relevant files / docs could answer the question without executing the code, it is static**. If a tool's exit code answers it, it is mechanical. Only if real execution is required is it runtime.

| Doubt shape (examples) | Category |
|---|---|
| "does `openInputStream(uri)?.use { it.readBytes() }` block the main thread?" | static |
| "does the `else` branch correctly emit `onResult(null)`?" | static |
| "is `JFileChooser` modal against the AWT EDT in a Compose-Desktop window?" | static |
| "does `remember(launcher) { ... }` survive recomposition?" | static |
| "is recomposition / lifecycle / thread-safety reasoning correct?" | static |
| "does library X behave per docs in this setup?" | static |
| "does it compile / pass lint / type-check?" | mechanical |
| "does opening a 50 MB PDF on Pixel 5 API 30 cause ANR / dropped frames?" | runtime |
| "does the UX of the picker match design intent?" | runtime |
| "what does Logcat / browser console show during scenario X?" | runtime |
| "step-through debug: at breakpoint X, is variable Y as expected?" | runtime |

## Dispatch — static doubts

Spawn a fresh-context Verifier subagent. Brief format:

```
Read: <files / docs the doubt depends on>
Question: <the doubt, stated as a yes/no claim or specific assertion>
Return exactly one of:
  OK — <one-line confirmation>
  ISSUE — <what is wrong, with path:line>
  NEEDS RUNTIME — <minimal runtime scenario that would resolve it>
```

Wait for the verdict, then apply:

- **`OK`** → resolved. Drop the doubt — do not surface it in Human-required. Optionally log `Verified by subagent (static): <one-line>` inside the Agent-verified section if the doubt was material.
- **`ISSUE — <X>`** → the step has a real defect. Surface it as the first bullet of the SUMMARY's `Uncertain:` section, prefixed `Subagent found issue:`, and append the gate recommendation: `Recommend /kit-fix <hash> "<short desc>" before next.` Do **not** mask the defect as `Verify by hand:` content; that hides it from the gate.
- **`NEEDS RUNTIME — <scenario>`** → mutate the doubt into the runtime scenario the subagent specified, then fold it into `Verify by hand:` (formatted per `verify-by-hand-tiers`).

## Dispatch — mechanical doubts

The Agent-verified `BUILD:` block already reports these. If the doubt is "does this still pass lint" and `lint` is in the step's `verify:` list, the answer is in `BUILD:`. Do not restate it in Human-required.

If the doubt names a check **not** covered by `verify:`, run the tool yourself one-shot before drafting SUMMARY and add the result to `BUILD:` as a `shell:` line. Still does not belong in Human-required.

## Dispatch — runtime-evidence doubts

Only these survive to `Verify by hand:`. Each surviving item must name:

- The exact scenario to execute (device / OS version / browser / input file / dataset).
- The signal the human should look for (ANR, dropped frames, log line, layout shift, error toast).
- The expected vs. acceptable answer.

Then format with the depth rules in `verify-by-hand-tiers`.
</procedure>

<output_format>
This skill produces no artifact of its own. It shapes two existing sections of STEP SUMMARY / FIX SUMMARY:

- `Verify by hand:` — only runtime-evidence items, depth set by `verify-by-hand-tiers`
- `Uncertain:` — only static doubts that the resolution step could not close (rare; if frequent, the role-swap / subagent is being skipped). Subagent-found issues land here with the explicit `Recommend /kit-fix` gate.
</output_format>

</skill>
