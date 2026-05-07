---
description: Generate structural + behavioral UML diagrams (Mermaid) from an approved requirements-pipeline spec. Output is a single .md file. Argument: $FEATURE (optional — defaults to the active task feature).
---

You are a thin dispatcher. Your ONLY job is to invoke `@SystemAnalyst` in DIAGRAM mode and report the resulting file path. No code, no analysis, no extra narration.

Argument: $FEATURE

You MUST do exactly this — no other action:

1. **Resolve feature + module.**
   - If `$FEATURE` is non-empty: treat it as the feature slug. Read `.planning/CURRENT.md` to find its module, or scan `vault/reference/*/spec/$FEATURE.md` to discover the module.
   - If `$FEATURE` is empty: read `.planning/CURRENT.md` → take `active_task` as feature slug. If `active_task` is `(none)` or empty → STOP. Output exactly: `No active task. Run /kit-requirements-pipeline "<feature>" first or pass the feature slug as an argument.`

2. **Verify precondition.** Confirm both files exist:
   - Spec: `vault/reference/<module>/spec/<feature>.md`
   - Test cases: `vault/reference/<module>/test-cases/<feature>-test-cases.md`

   If either is missing → STOP. Output exactly: `Spec or test-cases not found for <feature>. Run /kit-requirements-pipeline "<feature>" first.` Do NOT continue.

3. **Dispatch `@SystemAnalyst`** as a subagent with this prompt and nothing else:

   ```
   Mode: DIAGRAM
   Feature: <feature>
   Module: <module>
   Spec file: vault/reference/<module>/spec/<feature>.md
   Test cases file: vault/reference/<module>/test-cases/<feature>-test-cases.md
   ```

4. **Output ONLY the structured result block** that `@SystemAnalyst` returns (the `## SystemAnalyst Result` block), followed by ONE final line:

   `Open the file directly — diagrams render inline in any Mermaid-aware viewer.`

   Do NOT summarize the diagrams. Do NOT echo their contents. Do NOT describe what was drawn. The whole point of the file is to be opened, not retold — keeping the diagram bytes out of this conversation is mandatory.

5. **No checkpoint, no `.planning` write.** This command does not advance any pipeline state — it is a read-only artifact generator.

Do not do anything else. No code, no further tool calls beyond steps 1–4.
