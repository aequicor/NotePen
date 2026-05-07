# Claude Code hooks (ai-agent-kit)

Deterministic enforcement layer that runs **outside the LLM**, written in cross-platform Node.js (`.mjs`). Added in kit v5.1.0.

> Hooks are a Claude Code feature only. The OpenCode equivalent — bash command allow/ask/deny patterns — lives in `opencode.json`. Both reach the same level of guardrail; only the mechanism differs.

## Why hooks vs prompt rules

Prompt rules ("DO NOT skip step X") rely on the model honouring them. Hooks are shell-level: they fire on lifecycle events that the agent cannot opt out of. They are the right tool for **non-negotiable** safety gates.

The kit ships three hooks, each addressing one v5.0 gap that prompt-only enforcement could not close.

## Bundled hooks

| File | Event | What it does |
|---|---|---|
| `session-start-context.mjs` | `SessionStart` | Emits `additionalContext` with current branch, last commit, and active task summary. The agent sees this at session start without needing to dispatch `/kit-status`. |
| `pre-tool-bash-guard.mjs` | `PreToolUse` (matcher: `Bash`) | (a) Hard-denies `git push --force` to `main/master/prod` regardless of permission table; (b) Hard-denies writes to credential files (`.env*`, `~/.ssh`, `~/.aws`); (c) Pre-commit sync check — if `src/**` is staged without any test, `test-cases.md`, `spec.md`, or `plan.md` update (v6 — used to be `feature.md`), asks PO before proceeding. |
| `stop-status-reminder.mjs` | `Stop` | Non-blocking. If `.planning/CURRENT.md` shows an active task, writes a one-line reminder to stderr so PO doesn't accidentally close mid-task. |

## How they're wired

The kit's `settings.json.template` (rendered into `<target>/.claude/settings.json` at install time) includes a `"hooks"` block:

```json
"hooks": {
  "SessionStart": [
    { "hooks": [ { "type": "command", "command": "node .claude/hooks/session-start-context.mjs" } ] }
  ],
  "PreToolUse": [
    { "matcher": "Bash", "hooks": [ { "type": "command", "command": "node .claude/hooks/pre-tool-bash-guard.mjs" } ] }
  ],
  "Stop": [
    { "hooks": [ { "type": "command", "command": "node .claude/hooks/stop-status-reminder.mjs" } ] }
  ]
}
```

Claude Code requires Node.js on every platform, so `node .claude/hooks/<name>.mjs` runs identically on Windows, macOS, and Linux. No bash / PowerShell wrappers needed.

## Disabling a hook

Three options, in increasing severity:

1. **Per-session:** rename the hook file (e.g. `mv pre-tool-bash-guard.mjs pre-tool-bash-guard.mjs.disabled`). The `command` in `settings.json` will fail silently and Claude Code falls back to the permission table.
2. **Per-project:** delete the matching block from `.claude/settings.json`.
3. **Permanently in the kit:** delete the file from `kit/.claude/hooks/` and remove the wiring from `kit/.claude/settings.json.template`. Then re-render the project via `/kit-update`.

## Customizing

The hooks are self-contained and small (≤ 100 lines each). Edit them in place — they have no external dependencies beyond Node.js stdlib (`node:fs`, `node:path`, `node:child_process`).

If you add a hook, follow the pattern:

```javascript
#!/usr/bin/env node
// One-line description of what this hook does and on which event it fires.

import fs from "node:fs";
import path from "node:path";

function emit(/* ... */) {
  process.stdout.write(JSON.stringify({
    hookSpecificOutput: {
      hookEventName: "<EventName>",
      // ... event-specific fields
    }
  }));
}

// ... logic, fail-soft (default: exit 0 = allow) ...
process.exit(0);
```

Read the [Claude Code hooks reference](https://code.claude.com/docs/en/hooks-guide) for the full event catalogue and per-event JSON schemas.

## Failure mode

All three hooks are **fail-soft**: any unexpected error (file missing, git not in PATH, malformed stdin) results in `exit 0` with no JSON output. Claude Code interprets that as "no decision" and falls through to the permission table — which is the v5.0 behaviour. **A broken hook never blocks the agent**, only ever adds extra checks on top of the existing path.

This matters: if you ship the kit to a teammate whose machine is misconfigured, your codebase doesn't seize up — the hook silently no-ops and the rest of the kit keeps working as in v5.0.

## What hooks do NOT replace

The prompt-level Anti-Loop rules in `@Main` and the agent bodies are still the primary control plane. Hooks are a **second line of defence**:

- The model still has to choose to dispatch `@Reviewer` after `@CodeWriter` (prompt rule).
- The model still has to mark steps in `plan.md § Implementation plan` (prompt rule; v6 — used to be feature.md).
- Hooks only catch what slips through: pushing to main, committing without tests, leaving an active task on session close.

Don't delete the prompt rules and rely on hooks alone — the prompt rules cover ~95 % of cases; hooks the remaining ~5 % that the model doesn't honour.
