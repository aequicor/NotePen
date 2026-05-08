---
description: v6.2+ attach a ground-truth artefact to the current step at 5.6 CHECKPOINT. Argument: $ARTEFACT_PATH (path to screenshot, command-output capture, contract-test log, or mutation-sample report). Records {type, path, summary} into step_commits[N].ground_truth and unblocks the 5.6 ground-truth gate.
---

You are a Senior project manager processing PO's ground-truth artefact attachment for the current EXECUTE step. The 5.6 CHECKPOINT ground-truth gate (P18, v6.2+) blocks `/kit-approve` until either an artefact is attached via this command or PO explicitly overrides via `/kit-approve --no-ground-truth`.

Argument: $ARTEFACT_PATH (mandatory; absolute or project-relative path to the artefact file).

## Step 1 — Resolve target step

1. Read `.planning/CURRENT.md`:
   - If `active_task` is `(none)` → STOP. Output: "No active task."
   - If `mode: sleep` → STOP. Output: "Sleep mode auto-waives the ground-truth gate (logged in MORNING_REPORT). /kit-attach is for interactive 5.6 CHECKPOINT only."

2. Read `.planning/tasks/<active_task>.md`:
   - If `current_step_idx` is missing or `0` → STOP. Output: "No active step. /kit-attach runs at 5.6 CHECKPOINT after a step has machine-greened."
   - Note `current_step_idx` (= the step that just machine-greened — the target).

3. Confirm `$ARTEFACT_PATH` is non-empty. If empty → STOP. Output: "Path required. Usage: `/kit-attach <path-to-artefact>`."

## Step 2 — Validate the file

4. Resolve `$ARTEFACT_PATH` against project root. If relative, prefix project root.

5. Check the path:
   - File does not exist → STOP. Output: "File not found: <resolved path>. Check the path and re-run."
   - File is empty (0 bytes) → STOP. Output: "File is empty: <resolved path>. Empty files do not constitute a ground-truth artefact."
   - File exists outside project root AND outside `.planning/artifacts/` → STOP. Output: "Artefact must live inside the project (or under .planning/artifacts/). Move/copy first, then /kit-attach."

6. Determine artefact `type` from extension and content:
   - `.png` `.jpg` `.jpeg` `.webp` `.gif` → `screenshot` (covers ui REQUIRED_TYPE)
   - `.txt` `.log` `.out` AND filename contains "command" or "cli" → `cli-output`
   - `.txt` `.log` AND filename contains "contract" or "api" → `api-contract`
   - `.txt` `.log` `.json` AND filename contains "mutation" → `mutation-sample`
   - `.md` containing the literal "Mutation score:" or "Mutants killed:" → `mutation-sample`
   - Anything else → `manual` (PO is responsible for the artefact's relevance to REQUIRED_TYPE; logged as such).

7. Compute `summary` (≤ 120 chars):
   - For `screenshot`: "Screenshot, <width>×<height>, <size_kb>KB" (use ImageMagick `identify` if available; fallback "Screenshot, <size_kb>KB").
   - For `cli-output`: first non-empty line of the file, truncated to 120 chars.
   - For `api-contract` / `mutation-sample`: first line containing "PASS" / "score" / "Mutants killed" if present, else first non-empty line.
   - For `manual`: first non-empty line (raw).

## Step 3 — Record into task file

8. In `.planning/tasks/<active_task>.md.step_commits[<current_step_idx>]`:
   - Set `ground_truth: {type: <type>, path: <project-relative path>, summary: <summary>, waived: false, attached_at: <ISO timestamp>}`.

9. Read step's `ground_truth_required_type` (from plan.md step block, or inferred per @Main 5.6 @. gate logic). If detected REQUIRED_TYPE differs from attached type:
   - Warn PO but accept. Output line: "⚠️  Detected REQUIRED_TYPE for step <N> = <required>; attached type = <attached>. PO is responsible — proceeding."

## Step 4 — Hand off to @Main

10. Output to PO:

```
📎 Ground-truth artefact attached to step <current_step_idx>.
   Type: <type>
   Path: <relative path>
   Summary: <summary>
   <warning line, if mismatch>

5.6 CHECKPOINT ground-truth gate: ✅ unblocked.

Continue:
  /kit-approve              — proceed to step <N+1>
  /kit-defect <description> — found a defect during manual check
  /kit-revert-step          — undo step <N>
```

11. The next `/kit-approve` (without `--no-ground-truth`) will succeed immediately — the gate is now satisfied.

## What NOT to do

- DO NOT auto-classify the artefact's relevance to REQUIRED_TYPE — verifying that "this screenshot actually shows the right UI surface" is PO's job. We record the type and the mismatch warning; we do not block on mismatch.
- DO NOT modify the artefact file. Read-only operation; the file is PO-supplied.
- DO NOT commit the artefact to git automatically. Artefacts under `.planning/artifacts/` are gitignored by template (potentially heavy binaries). PO commits explicitly if they want to ship the artefact with the feature.
- DO NOT silently overwrite an existing ground_truth entry on the same step. If `step_commits[<N>].ground_truth` is already non-empty (and not waived), output: "Step <N> already has a ground-truth artefact attached: <summary>. Use /kit-attach --replace <path> to override (logs the previous one in notes)."
- DO NOT validate file content semantically beyond the type-detection above. If PO uploads a meme as a "screenshot", that's their accountability — the gate's purpose is to enforce a non-AI artefact, not to be a second AI-validator.
