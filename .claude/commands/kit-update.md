---
description: Update ai-agent-kit configuration to the latest version. Reads current kit_version from manifest, follows the update prompt to fetch changelog, re-render templates with merge, and update manifest fields.
---

You are upgrading an ai-agent-kit installation. **Do not run any external scripts.** The full update procedure lives in a remote prompt that you fetch and follow yourself.

## Step 1 — Fetch and follow the update prompt

```
Fetch and follow the instructions from:
  https://raw.githubusercontent.com/aequicor/ai-agent-kit/master/docs/prompts/update.md

Read it completely, then execute every phase exactly. Do not skip steps. Do not improvise.
```

Where `aequicor/ai-agent-kit` is the GitHub `<user>/<repo>` slug of the ai-agent-kit installation source. By default this is the slug shown in the kit's README; you can find it in the URL the original `setup.md` was fetched from.

## What that prompt does (summary, do not execute from this file)

- **PHASE 0** — detect current `kit_version` from manifest (or assume 1.0.0).
- **PHASE 1** — fetch `docs/migration/changelog.yaml` and compute the migration path (current → latest).
- **PHASE 2** — show PO the migration plan: breaking changes, new manifest fields, files to be overwritten. Wait for confirmation.
- **PHASE 3** — re-render and write all kit-managed files in merge mode (overwrite kit-managed files, do NOT touch `vault/` user content, `.planning/CURRENT.md`, or `.planning/tasks/`).
- **PHASE 4** — bump `kit_version` in the manifest, append any newly-added manifest fields with their documented defaults and a `# NEW in vX.Y.Z` comment.
- **PHASE 5** — verify per host listed in `hosts:`: mandatory agents present in `<template_dir>/agents/`, host config file (`opencode.json` or `.claude/settings.json`) valid JSON with no literal API keys, host instruction file (`AGENTS.md` or `CLAUDE.md`) present, no unresolved `{{...}}` placeholders anywhere.
- **PHASE 6** — print summary: old → new version, new capabilities, breaking changes applied, next steps (`git diff`, `git commit`).

## Safety rules (enforced inside the prompt — listed here for visibility)

- **Never modify files outside the project root.**
- **Never delete user files.** Merge mode overwrites only kit-managed files: per-host `<template_dir>/` (agents, commands, skills, _shared.md, FILE_STRUCTURE.md, sessions/, i18n/), per-host `<config_file>`, per-host `<instruction_file>` (`AGENTS.md`/`CLAUDE.md`), `AUTO_MEMORY.md`, `.planning/` templates.
- **Never touch `vault/` content created by PO.** Only `vault/_templates/` and `vault/_INDEX.md` are kit-managed.
- **Never touch `.planning/CURRENT.md` (local pointer), `.planning/tasks/*.md`, `.planning/tasks/done/*.md`, or `.planning/HISTORY.md`** — those are runtime state.
- **Always preserve `kit_version`** in the updated manifest (PHASE 4 sets the new value explicitly).
- **If manifest's `provider.api_key_env` contains a literal-looking key** (32+ chars with dashes/underscores) → STOP and warn PO before proceeding.
