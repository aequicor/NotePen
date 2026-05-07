---
description: Remove all ai-agent-kit files from the current project. Reads the manifest, confirms with PO, then deletes every host directory listed in `hosts:` (.opencode/, .claude/), `.planning/`, kit-managed root files, nested AGENTS.md per module, and optionally the vault directory.
---

You are uninstalling ai-agent-kit from this project. **Do not run any external scripts.** The full uninstall procedure lives in a remote prompt that you fetch and follow yourself.

## Step 1 — Fetch and follow the uninstall prompt

```
Fetch and follow the instructions from:
  https://raw.githubusercontent.com/aequicor/ai-agent-kit/master/docs/prompts/uninstall.md

Read it completely, then execute every phase exactly. Do not skip steps. Do not improvise.
```

Where `aequicor/ai-agent-kit` is the GitHub `<user>/<repo>` slug of the ai-agent-kit installation source. You can find it in the host config file (`opencode.json` for OpenCode, `.claude/settings.json` for Claude Code) or in the URL the original `setup.md` was fetched from.

## What that prompt does (summary, do not execute from this file)

- **PHASE 0** — locate the manifest file (`*.yaml` / `*.yml` with `kit_version:`) in the project root; extract `vault_path`, module `source_root` list, and `hosts`.
- **PHASE 1** — build the full inventory: per-host directories (`.opencode/`, `.claude/`), per-host config files (`opencode.json`, `.claude/settings.json`), per-host instruction files (`AGENTS.md`, `CLAUDE.md`), planning files, root files, nested AGENTS.md/CLAUDE.md per module.
- **PHASE 2** — show PO the complete list and ask for confirmation (`YES K` to keep vault, `YES D` to delete vault). Stop on anything else.
- **PHASE 3** — delete: every host's `<template_dir>/` (one per entry in `hosts:`), `.planning/`, `<vault_path>/_templates/`, every host's `<config_file>` and `<instruction_file>`, `AUTO_MEMORY.md`, `<vault_path>/_INDEX.md`, the manifest YAML, and nested instruction files per module (only if they contain kit markers). Optionally delete the vault root if PO chose DELETE.
- **PHASE 4** — verify that all targeted paths are gone; report any that could not be deleted.
- **PHASE 5** — print summary: what was deleted, what was skipped, vault status.

## Safety rules (enforced inside the prompt — listed here for visibility)

- **Never delete files outside the project root.**
- **Never delete anything not explicitly listed by the prompt.**
- **Never delete the vault root without explicit PO confirmation.**
- **Never delete a nested AGENTS.md or CLAUDE.md that does not contain kit markers** — it may be hand-written.
- **Stop immediately if PO does not confirm** in PHASE 2.
