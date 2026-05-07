# Claude Code Hooks — NotePen

> ai-agent-kit v5.1.0+

Claude Code hooks are deterministic safety enforcement mechanisms that operate outside the language model, implemented as cross-platform Node.js scripts.

Unlike prompt-based rules that depend on model compliance, hooks are "shell-level" enforcement that fires on lifecycle events the agent cannot bypass.

## The Three Bundled Hooks

### session-start-context.mjs

Fires at `SessionStart`. Injects contextual information (current branch, active task summary) without requiring agent action.

Output: `additionalContext` in JSON — `"branch={name} | last_commit={msg} | active_task={task} | type={type} | next: {step}"`.

### pre-tool-bash-guard.mjs

Fires on `PreToolUse` for `Bash` commands. Enforces three non-negotiable restrictions:

1. **Force-push block:** `git push --force` to main/master/prod is permanently denied.
2. **Credential write block:** Writes to `.env`, `.ssh`, `.aws`, `credentials.json` files are blocked.
3. **Commit check:** Commits staging source files in `src/` without accompanying test-cases updates → asks PO to confirm.

Input: JSON from stdin with `tool_name` and `tool_input.command`.
Output: JSON permission decision (`allow` / `deny` / `ask`) via exit code.

Fails open (allows by default) on errors — misconfiguration never blocks the agent.

### stop-status-reminder.mjs

Fires on `Stop`. Checks for an active task in `.planning/CURRENT.md`. If one exists and is not BLOCKED, prints a reminder to stderr to run `/kit-status` or `/kit-resume`.

Non-blocking — never forces the agent to continue working.

## Design Philosophy

Hooks serve as a "second line of defence." Prompt-level rules remain primary; hooks catch only the ~5% of cases where models slip through those instructions.

All hooks:
- Use only Node.js standard library (no external dependencies).
- Fail gracefully — unexpected errors result in silent no-ops.
- Are cross-platform (Windows + Linux + macOS).
- Execute in under 3 seconds (timeout guard).
