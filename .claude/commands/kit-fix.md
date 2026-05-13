---
description: "Run Session 3 (Fix) of the AI-Kit v4 pipeline. Arguments: $ARGUMENTS — the target commit hash followed by a defect description (free-form, or using the structured template emitted by STEP SUMMARY)."
---
# /kit-fix

Run Session 3 (Fix) of the AI-Kit v4 pipeline. Arguments: $ARGUMENTS — the target commit hash followed by a defect description (free-form, or using the structured template emitted by STEP SUMMARY).

<project>NotePen</project>
<stack>kotlin / kotlin-multiplatform, compose-multiplatform</stack>

<communication_language>
Communicate with the user in Russian (ru). All prose — questions, explanations, status updates, summaries, and reasoning addressed to the user — must be in Russian. Keep code, file paths, shell commands, identifiers, manifest keys, error codes, and other technical tokens verbatim in their original form.
</communication_language>




<workflow>
Run Session 3 (Fix) of the AI-Kit v4 pipeline. Arguments: $ARGUMENTS — the target commit hash followed by a defect description (free-form, or using the structured template emitted by STEP SUMMARY).

You are running Session 3 of the AI-Kit v4 pipeline. Session 3 is a **diagnostic, multi-stage** recovery with explicit human gates — not a one-shot patch.

**Args:** $ARGUMENTS

Parse:
- the **first whitespace-separated token** is the target commit hash.
- the rest is the defect description. The rest may be either free-form text **or** the structured template from STEP SUMMARY:

  ```
  Дефект: <name>
  Шаги:
  1) <шаг 1>
  n) <шаг n>
  ФР: <фактический результат>
  ОР: <Ожидаемый результат>
  ```

  When the template is present, treat each field as a separate signal during Stage 1:
  - `Дефект:` → seeds the **Reduce** line of DIAGNOSIS.
  - `Шаги:` → seeds the **Repro** line (collapse numbered steps into one copy-pasteable command or scenario; if the steps cannot be collapsed, surface them verbatim in `Repro` as a multi-line block).
  - `ФР:` / `ОР:` → the observed-vs-expected contrast; feed this into DIAGNOSIS's `Reduce` paragraph and the Stage 1 hypotheses (any cause must explain why ФР ≠ ОР).

  Missing fields are not a hard error — proceed with whatever was provided.

## Pre-checks (run before Stage 1)

1. **Commit-hash exists.** `git cat-file -e <commit-hash> 2>&1` — if it fails, STOP: `Commit <hash> does not exist in this repository.`
2. **Description non-empty.** If the rest of `$ARGUMENTS` after stripping the hash is empty, STOP: `/kit-fix needs a description of the defect after the commit hash. Use the template from STEP SUMMARY: "Дефект: … / Шаги: … / ФР: … / ОР: …".`
3. **Working tree is clean.** `git status --porcelain` — if non-empty, STOP: `Working tree is dirty. Stash or commit other work first; Session 3 needs a clean tree to attribute the new fix-commit cleanly.`
4. **Plan-commit reachable.** `git log --grep="kit: plan for" --format="%H" -n 1 <commit-hash>~` — if empty, STOP: `No "kit: plan for" commit precedes <commit-hash>. /kit-fix only operates on commits made through /kit-do, which lays down a plan-commit upstream. If this is a manual commit, fix it through normal git workflow instead.`

All four pass → enter Stage 1.

## Stage 1 — Анамнез + варианты причины (Anamnesis + Cause options)

**Goal of this stage:** in a single uninterrupted pass, gather defect context **and** propose root-cause hypotheses. No mid-stage AWAIT — the user reviews both blocks together at the end.

1. `git show <commit-hash>` — read the target commit's diff.
2. Read `.aikit/plans/<plan-id>.md` (the plan-commit located in pre-check 4 carries the id in its message).
3. Read related source files needed to understand the defect.
4. Run the `debug-loop` skill: produce **Repro** (one-line copy-pasteable; if the user gave structured `Шаги:`, this is where they go), **Localize** (path:line-range), **Reduce** (one paragraph that explains the ФР vs ОР contrast verbatim when supplied). If the repro cannot be produced or the defect lives upstream of `<commit-hash>`, follow the skill's STOP rules.
5. **Without pausing**, run the `cause-hypotheses` skill: generate 2–4 root-cause hypotheses, each in predict-observe-conclude form, scoped to the evidence just collected. Each hypothesis must be able to explain the ФР ≠ ОР contrast.
6. Emit **DIAGNOSIS** block followed immediately by **CAUSE OPTIONS** block (formats defined in their respective skills). Merge their Reply: footers into one combined footer at the end (see "Combined AWAIT footer" below).
7. **Adaptive fast-path for cause selection:**
   - If exactly 1 hypothesis was plausible → CAUSE OPTIONS header carries `Auto-advanced: no plausible alternatives surfaced.`, **skip the AWAIT**, advance to Stage 2 with that cause selected. User override: replying `стоп` within the next message forces AWAIT and refines.
   - If 0 → STOP per the `cause-hypotheses` skill's rule.
   - If ≥2 → AWAIT.
8. **AWAIT** (unless fast-path skipped it). Use the native interactive picker (`AskUserQuestion` or the runner's equivalent) when available — the cause-pick is a closed list of N options plus a free-form fallback, which fits the picker contract. The free-text input is parsed against the tokens in priority order (first match wins):
   1. `<N>` (number from the cause list) → cause selected; advance to Stage 2
   2. `другая: <text>` → user-supplied cause; advance to Stage 2 with that cause
   3. `ok` (literal) → only valid when CAUSE OPTIONS auto-advanced; confirms the auto-selected cause and proceeds to Stage 2
   4. `копай ещё [: <hint>]` → research pass on hypotheses only (read more code, expand evidence); DIAGNOSIS is frozen; re-emit CAUSE OPTIONS
   5. `abort` (literal) → Session 3 END without commit; working tree is already clean
   6. anything else → treat as `<correction>` on DIAGNOSIS; redo Stage 1 from step 1 with the new constraint; re-emit both blocks

## Stage 2 — Варианты фикса (Fix options)

1. Run the `fix-options` skill: generate 2–3 approaches under the selected cause, distinguishable by Scope / Risk / Test impact / Structural vs workaround axes.
2. Emit **FIX OPTIONS** block (format defined in `fix-options` skill).
3. **Adaptive fast-path:**
   - If exactly 1 viable approach → header carries `Auto-advanced: no viable alternatives surfaced.`, **skip the AWAIT**, advance to Stage 3 with that approach selected. Override: `стоп`.
   - If 0 → STOP per the skill's rule.
   - If ≥2 → AWAIT.
4. **AWAIT** (unless fast-path skipped it). Use the native interactive picker when available — the approach-pick is also a closed list with a free-form fallback. Reply tokens:
   - `<N>` → approach selected; advance to Stage 3
   - `другой: <text>` → user-supplied approach; advance to Stage 3 with that approach
   - `копай ещё [: <hint>]` → research pass (read callers, check test coverage) and re-emit FIX OPTIONS
   - `abort` → Session 3 END without commit

## Stage 3 — Реализация (Implementation)

1. Apply the chosen approach to the working tree. **Do not commit yet.**
2. If the implementation diverges materially from the chosen FIX OPTIONS approach (touches more files / changes test posture / shifts from structural to workaround), surface it in DIFF PREVIEW's Self-check section — do not silently expand.
3. Emit **DIFF PREVIEW** block (format defined in the `summary-format` skill, § DIFF PREVIEW). The block carries `git diff --stat` and the full `git diff` of the worktree against HEAD, plus a Self-check section comparing the diff against the FIX OPTIONS choice.
4. **AWAIT — mandatory, no fast-path, no native picker.** This is the gate v4 protects above all; it stays as a free-form text reply so the user's correction wording becomes part of the audit trail. Reply tokens:
   - `ok` → advance to Stage 4 (commit + verify)
   - `<any correction text>` → continue editing in the same worktree, re-emit DIFF PREVIEW
   - `abort` → `git checkout -- .` to restore the worktree (changes lost), Session 3 END without commit

## Stage 4 — Commit + verify + summary

1. `git add -A && git commit -m "kit: fix <commit-hash> — <slug>"`. The `<slug>` is derived from the user's description (kebab-case, ≤4 words; if the structured template was used, prefer the `Дефект:` value as the source). If the commit fails (pre-commit hook), STOP and surface the error verbatim. Do not retry, do not `--no-verify`.
2. **Run verify.** Resolve the target step's `Verify` field from the plan (or default `[compile, test]`) via the active language profile. Run each command. Capture per-verb result.
3. If verify is red, the fix is not done. Loop back into Stage 3 (the worktree is now empty; re-apply additional changes) **unless** the structural intent was to land a `--keep-red` carry — in that case, document the reason in FIX SUMMARY's `Verify:` explanation field.
4. Emit **FIX SUMMARY** block (format defined in `prompts/Main.md` § Artifacts). Include `Cause considered (auto-advanced):` / `Approach considered (auto-advanced):` lines when those stages took the fast-path.
5. END.

## Combined AWAIT footer (Stage 1)

When DIAGNOSIS and CAUSE OPTIONS are emitted together, drop the individual `Reply:` footers from each block and append **one** combined footer after CAUSE OPTIONS:

```
---
Reply:
- `<N>` — выбрать гипотезу №N и перейти к Stage 2 (FIX OPTIONS)
- `другая: <text>` — описать свою root-cause гипотезу и перейти к Stage 2
- `ok` — (только при Auto-advanced) подтвердить единственную гипотезу и перейти к Stage 2
- `копай ещё [: <hint>]` — research-проход по гипотезам (DIAGNOSIS заморожен), переотрисовать CAUSE OPTIONS
- `<correction>` — поправить контекст DIAGNOSIS и переотрисовать оба блока с нуля
- `abort` — Session 3 END без commit'а
```

## Hard rules

- **Single-step only.** If the requested fix would require more than one conceptual step, STOP at Stage 2: `This fix needs more than one step. The chosen cause spans multiple invariants. Recommend opening a new feature plan with /kit instead.` Do not silently expand scope into Stage 3.
- **Stage 3 AWAIT is mandatory.** It is the one gate that v4 protects above all — auto-`ok` here would defeat the diagnostic flow's purpose. Skipping it is a protocol violation.
- **Use the SUMMARY format exactly.** No narrative substitute. Each block's commit-hash anchor is mandatory — the original Execute session uses it to validate paste-back.
- **NEVER `--no-verify`** on the commit.
- **NEVER modify** the plan file or any commit other than the new fix commit in Stage 4.
- **`abort` at any stage** ends Session 3 cleanly: Stage 1 / 2 abort leaves the tree untouched; Stage 3 abort restores via `git checkout -- .`.

## Sub-step replies during AWAIT

`копай ещё` (Stage 1 / 2 only) triggers an additional research pass:

- Stage 1: read more source files relevant to the localized span, re-derive hypotheses, re-emit CAUSE OPTIONS. **Do not re-emit DIAGNOSIS** — DIAGNOSIS is frozen once Stage 1 first emits it; only a free-form `<correction>` reply rewinds it.
- Stage 2: read callers / tests / sibling files, refine approaches, re-emit FIX OPTIONS.

The user's `копай ещё` reply may carry a hint (`копай ещё: проверь как RotationHandler вызывается из MotionEventDispatcher`). Use the hint to direct the research pass.
</workflow>
