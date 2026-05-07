---
description: Add an extra profile to this installed kit by URL. Usage `/kit-extend <url-to-profile.yaml>`. Fetches the profile, validates against profile.schema.json, merges into the manifest (with PO confirmation), and re-renders kit-managed files. Supports both `github.com/.../blob/...` and `raw.githubusercontent.com/...` URLs, including profiles hosted outside this kit's repo.
---

You are extending an installed ai-agent-kit with one additional profile. **Do not run any external scripts.** The full procedure lives in a remote prompt that you fetch and follow yourself.

## Step 1 — Fetch and follow the extend prompt

```
Fetch and follow the instructions from:
  https://raw.githubusercontent.com/aequicor/ai-agent-kit/master/docs/prompts/extend.md

Read it completely, then execute every phase exactly. Do not skip steps. Do not improvise.
Pass the URL the PO supplied as the `PROFILE_URL` constant.
```

Where `aequicor/ai-agent-kit` is the GitHub `<user>/<repo>` slug of the ai-agent-kit installation source.

The PO calls this command as `/kit-extend <url>`. The single argument is `PROFILE_URL`. If the argument is missing, stop and tell PO:

> Pass a profile URL, for example:
> `/kit-extend https://github.com/aequicor/ai-agent-kit/blob/master/profiles/capability/solid.yaml`

## What that prompt does (summary, do not execute from this file)

- **PHASE 0** — find the project's manifest, sanity-check `stack.profiles`.
- **PHASE 1** — normalise `PROFILE_URL` (`github.com/.../blob/...` → `raw.githubusercontent.com/...`); detect whether it points at this kit's repo (`IS_OFFICIAL`) or an external repo.
- **PHASE 2** — fetch the YAML, validate the front-matter and the full body against `kit/profile.schema.json`. Cross-check `_profile_axis` against the URL path when the URL is under `profiles/<axis>/`.
- **PHASE 3** — if the URL is external, print the parsed profile and ask PO for explicit confirmation before adding.
- **PHASE 4** — duplicate check (no-op + message), then cardinality check (`language` / `provider` are exactly 1; `host` is 1..N — extending with a second `host` profile adds it; replacing an existing `language`/`provider` asks for confirmation).
- **PHASE 5** — deep-merge the profile into the manifest (lists concat+dedupe; scalars only overwritten on confirmed `language`/`provider` replacement and only for fields the axis owns). Append the name to `stack.profiles[]`. For external profiles, record the URL in `stack.external_profiles[<name>] = <url>` so `/kit-update` can refetch later. Show a manifest diff and wait for PO confirmation before writing.
- **PHASE 6** — re-render every kit-managed file in merge mode (same skip-list as `/kit-update`: never touch `vault/concepts/**`, `vault/reference/**`, `.planning/CURRENT.md`, `.planning/tasks/`, etc.).
- **PHASE 7** — re-validate the manifest, check every host's config file (`opencode.json` / `.claude/settings.json`) is valid JSON with no literal API keys, scan for unresolved `{{...}}` placeholders.
- **PHASE 8** — print summary (profile added / replaced, source, files touched, next steps `git diff` + commit).

## Safety rules (enforced inside the prompt — listed here for visibility)

- **External profiles need PO confirmation.** A profile fetched from outside `aequicor/ai-agent-kit` cannot be added silently.
- **Cardinality replacement needs PO confirmation.** Adding a `language` or `provider` profile when one is already present asks "Replace `<old>` with `<new>`?" and stops on no.
- **Manifest write needs PO confirmation.** A unified diff is shown before the manifest is overwritten.
- **Never modify files outside the project root.**
- **Never touch `vault/` content created by PO** — only `vault/_templates/` and `vault/_INDEX.md` are kit-managed.
- **Never touch `.planning/CURRENT.md`, `.planning/tasks/*.md`, `.planning/tasks/done/*.md`, `.planning/HISTORY.md`** — runtime state.
- **If any host's config file** (`opencode.json` / `.claude/settings.json`) **ends up with a literal API key** (matches `sk-`, `ghp_`, `glpat-`, `AKIA*`, `xox[bp]-`, or 32+ chars high-entropy) → STOP immediately, warn PO.
