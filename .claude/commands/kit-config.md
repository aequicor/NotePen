---
description: Reconfigure an installed kit using a plain-language description. Edits the manifest in place (provider, models, MCP toggles, language, modules, project metadata, formatter, lsp, ui, code-quality patterns) and re-renders only the kit-managed files affected by the change. Does NOT bump kit_version (use /kit-update for that) and does NOT add profiles (use /kit-extend for that). Usage `/kit-config <plain-language description>` or `/kit-config` for interactive.
---

You are reconfiguring an installed ai-agent-kit. **Do not run any external scripts.** The full procedure lives in a remote prompt that you fetch and follow yourself.

## Step 1 — Fetch and follow the config prompt

```
Fetch and follow the instructions from:
  https://raw.githubusercontent.com/aequicor/ai-agent-kit/master/docs/prompts/config.md

Read it completely, then execute every phase exactly. Do not skip steps. Do not improvise.
Pass the PO's free-form request as the `PO_REQUEST` constant. If the user invoked `/kit-config` with no arguments, set `PO_REQUEST` to the empty string and run the interactive picker (PHASE 0 of the prompt).
```

Where `aequicor/ai-agent-kit` is the GitHub `<user>/<repo>` slug of the ai-agent-kit installation source. By default this is the slug shown in the kit's README; you can find it in the URL the original `setup.md` was fetched from.

The PO calls this command as `/kit-config <plain-language description>`. Examples:

```
/kit-config switch the reviewer model to claude-opus-4-7
/kit-config выключи MCP serena, он больше не нужен
/kit-config поменяй провайдера на ollama-cloud, ключ в OLLAMA_KEY
/kit-config add a forbidden pattern: no var declarations in Kotlin
/kit-config rename module server to backend
/kit-config                                # interactive — picks a section, asks what to change
```

## What that prompt does (summary, do not execute from this file)

- **PHASE 0** — find manifest, parse PO's plain-language intent (or run interactive picker if empty).
- **PHASE 1** — translate intent into a list of `<field path> : <old> → <new>` edits. Ask one clarifying question if the request is ambiguous. Refuse and redirect for `kit_version` (→ `/kit-update`), `hosts` and `stack.profiles[]` (→ `/kit-extend`).
- **PHASE 2** — schema-validate every edit; security-scan every `*api_key_env` change for literal-key patterns.
- **PHASE 3** — classify blast radius (LOW / MEDIUM / HIGH) and compute the union of files that need re-rendering. Ground truth for the field → file mapping is `docs/prompts/setup.md` PHASE 4.
- **PHASE 4** — show PO the manifest diff + file list + HIGH-blast warnings; wait for `/kit-approve` (or dispatch `@AutoApprover` if `AUTO_APPROVE=true`).
- **PHASE 5** — write the manifest, re-render only the affected files in merge mode (same skip-list as `/kit-update`).
- **PHASE 6** — re-validate manifest + every host config (`opencode.json` / `.claude/settings.json`) is valid JSON with no literal keys; no unresolved `{{...}}` placeholders.
- **PHASE 7** — print summary: changes applied, files touched, env-var advice if `provider.api_key_env` changed, recommended next steps (`git diff`, `git commit`).

## Safety rules (enforced inside the prompt — listed here for visibility)

- **Never change `kit_version`** — that is the exclusive job of `/kit-update`.
- **Never change `hosts` or `stack.profiles[]`** — use `/kit-extend` (or reinstall via `setup.md`).
- **Manifest write needs PO confirmation.** A unified diff is shown before the manifest is overwritten.
- **HIGH-blast changes need explicit warnings.** `vault_path` rename, `modules[]` rename/remove, and `project.name` change cascade widely; the prompt warns PO about runtime-state and vault-content migration steps that the kit cannot perform automatically.
- **Never modify files outside the project root.**
- **Never touch `vault/concepts/**`, `vault/reference/**`, `vault/how-to/**`, `vault/guidelines/**`, `vault/tech-debt/**`** — PO content.
- **Never touch `.planning/CURRENT.md`, `.planning/tasks/*.md`, `.planning/tasks/done/*.md`, `.planning/HISTORY.md`, `.planning/DECISIONS.md`, `.planning/bugs/*.md`** — runtime state.
- **If any `*api_key_env` value** matches `sk-`, `ghp_`, `glpat-`, `AKIA*`, `xox[bp]-`, or 32+ chars high-entropy → STOP immediately and warn PO. The field stores the env-var **name**, never the literal key.
- **Never auto-rollback.** If post-write validation fails, the prompt surfaces the failure with file paths and lets PO decide.
