---
name: "cause-hypotheses"
description: "Generate 2–4 root-cause hypotheses for a Session 3 defect — each falsifiable from the Stage 1 anamnesis evidence alone."
---
<skill name="cause-hypotheses">

<purpose>
Generate 2–4 root-cause hypotheses for a Session 3 defect — each falsifiable from the Stage 1 anamnesis evidence alone.
</purpose>

<when_to_invoke>
| Stage | Trigger |
|---|---|
| Session 3 Stage 1 (second half) | immediately after `debug-loop` emits DIAGNOSIS, in the same uninterrupted Stage 1 pass — **no AWAIT between DIAGNOSIS and CAUSE OPTIONS**. The user sees both blocks together and replies once. |

The skill produces the CAUSE OPTIONS block and carries the **combined Stage 1 Reply: footer** (DIAGNOSIS no longer has its own footer in v4). After the user picks (or after fast-path auto-advance), Stage 2 generates FIX OPTIONS for the chosen cause via the `fix-options` skill.
</when_to_invoke>


<procedure>
Each hypothesis follows a **predict-observe-conclude** shape:

> *If hypothesis H were true, we'd expect to see X.*
> *Current observation (from DIAGNOSIS): Y.*
> *Therefore H is **supports | refutes | undetermined**.*

Rules:

1. **Mutually distinct.** Two hypotheses that differ only by a parameter value ("`x` is 5 vs `x` is 6") are one hypothesis. Distinct means different root cause — different file, different invariant, different layer.
2. **Falsifiable from Stage 1 evidence alone.** If supporting / refuting a hypothesis would require new code reads or new runtime data, mark it `undetermined` and surface the missing evidence in `Need to know:` — the user can grant `копай ещё` to gather it.
3. **Count: 2–4.** Pick the most likely ones, do not pad. If genuinely only one is plausible, take the fast-path below; if more than four, narrow to the top four and list the rejected ones under `Cause considered (rejected):` in the final FIX SUMMARY.
4. **No fix details.** Hypotheses are about *cause*, not *cure*. "Forgot to call `dispose()`" is a cause; "add `dispose()` in `onDestroy`" is a fix and belongs in Stage 3.

## Adaptive fast-path

- **Exactly 1 plausible cause** → emit the single hypothesis with header `Auto-advanced: no plausible alternatives surfaced.` and skip the user-selection AWAIT — advance straight into Stage 2 (FIX OPTIONS). Record under FIX SUMMARY's `Cause considered (auto-advanced):`.
- **0 plausible causes** → STOP Session 3. Emit: `Cannot diagnose: no root-cause hypothesis is supported by Stage 1 evidence. Reproduce again, expand the anamnesis (different OS, larger input, fresh log), then re-invoke /kit-fix.`
- **≥5 plausible causes** → narrow to the top 4 by `supports` strength. List the rejected 5th+ under FIX SUMMARY's `Cause considered (rejected):`.

## Anti-patterns

- **Layered restatement.** Three hypotheses that all say "the function is wrong" at different abstraction levels — pick one specific layer.
- **Catch-all "race condition".** Without a specific shared-state name and the two operations contending for it, "race" is a vibe, not a hypothesis.
- **Hidden fix.** "Because the cache wasn't invalidated" embeds the answer; phrase as "The cache invalidation path does not run after `<event>`" so the *observed evidence* can refute it.
- **Self-confirming hypothesis.** "The code at `foo.kt:42` is wrong" with no observable prediction. Every hypothesis must say what we'd see if it were true.
</procedure>

<output_format>
CAUSE OPTIONS is emitted immediately after DIAGNOSIS, with one combined Reply: footer covering both blocks (DIAGNOSIS does not carry its own footer in v4):

```
## CAUSE OPTIONS · commit `<target-hash>`

1. **<one-line cause name>**
   - **If true, we'd expect:** <observable>
   - **Current observation:** <evidence from DIAGNOSIS>
   - **Assessment:** supports | refutes | undetermined
   - **Need to know:** <missing evidence; omit line if assessment is supports/refutes>

2. **<one-line cause name>**
   ...

---
Reply (covers DIAGNOSIS + CAUSE OPTIONS):
- `<N>` — выбрать гипотезу №N и перейти к Stage 2 (FIX OPTIONS)
- `другая: <текст>` — описать свою root-cause гипотезу и перейти к Stage 2
- `копай ещё [: <hint>]` — research-проход по гипотезам (DIAGNOSIS заморожен), переотрисовать CAUSE OPTIONS
- `<correction>` — поправить контекст DIAGNOSIS и переотрисовать оба блока с нуля
- `abort` — Session 3 END без commit'а
```

Single-hypothesis fast-path variant (replaces the block above; the `ok` token becomes valid only in this variant):

```
## CAUSE OPTIONS · commit `<target-hash>`

**Auto-advanced: no plausible alternatives surfaced.**

1. **<one-line cause name>**
   - **If true, we'd expect:** <observable>
   - **Current observation:** <evidence>
   - **Assessment:** supports

Proceeding to Stage 2 (fix options) without user selection. Override: reply `стоп` to force AWAIT and refine; reply `ok` to confirm the auto-selected cause explicitly.
```

## Native picker invocation (Stage 1 AWAIT)

After CAUSE OPTIONS is emitted, if the runner exposes a native multiple-choice picker, call it **in addition to** the text block — the block remains the durable audit trail, the picker is the input mechanism. Per-runner mechanics (the kit's permission resolver auto-allows these tools so the call doesn't trigger a prompt):

**Claude Code / Qwen Code** — `AskUserQuestion` (built-in tool). Schema:

```json
{
  "questions": [
    {
      "question": "Какую root-cause гипотезу берём?",
      "header": "CAUSE pick",
      "multiSelect": false,
      "options": [
        { "label": "1. <cause #1 name>", "description": "<one-line gist + assessment>" },
        { "label": "2. <cause #2 name>", "description": "<one-line gist + assessment>" },
        { "label": "Другая", "description": "Описать свою root-cause гипотезу" },
        { "label": "Копай ещё", "description": "Доп. research-проход, переотрисовать CAUSE OPTIONS" },
        { "label": "Abort", "description": "Закрыть Session 3 без commit'а" }
      ]
    }
  ]
}
```

`questions` MUST be an array — wrapping a single question in `{...}` instead of `[{...}]` is the most common misuse (see [qwen-code#2329](https://github.com/QwenLM/qwen-code/issues/2329)).

When the user picks `Другая` / `Копай ещё`, follow up with a plain-text turn to capture the free-form `<text>` / `<hint>` payload — the picker itself doesn't surface free-form input.

**OpenCode** — `question` tool. Same conceptual shape (header + question text + options[]), invoked through OpenCode's tool API; permission grant in `opencode.json` → `permission: { question: "allow" }` (the kit's generator already wires this).

**Cursor / Aider** — no native picker. The text Reply: footer is the only mechanism; user types `1`, `другая: …`, etc.

The picker is a UX layer over the documented Reply: tokens. It never replaces or expands them — every option the picker exposes maps 1:1 to a token the user could have typed.
</output_format>

</skill>
