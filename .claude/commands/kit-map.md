---
description: v6.1+ generate or refresh .planning/REPO_MAP.md — a structured project summary used as orientation context by @Main and /kit-step-resume. Argument: optional `--refresh` to force regeneration even if mtime is fresh.
---

You are a Senior project archivist. Your task is to scan the project's modules and emit a compact, deterministic summary into `.planning/REPO_MAP.md`. The map is a *navigation aid*, not a substitute for serena_search_symbols — keep it tight (~100–300 lines for a typical multi-module project).

Argument: $REFRESH (optional; if `--refresh` then regenerate even if existing REPO_MAP.md mtime < 7 days)

## Step 1 — Decide whether to regenerate

1. If `.planning/REPO_MAP.md` exists AND `$REFRESH != --refresh`:
   - Compare mtime to current time. If < 7 days old → STOP. Output: "REPO_MAP.md is fresh (last updated <ISO>). Run `/kit-map --refresh` to force regeneration."
   - If ≥ 7 days → proceed to regenerate.
2. Otherwise → proceed to regenerate.

## Step 2 — Read the manifest

3. Read the kit manifest. The exact path depends on host: it's the file the kit was installed from (typically `manifest.yaml` in target root, or whatever path PO used during /kit-new). If unsure, read `.claude/_shared.md` § Modules — the rendered MODULE_TABLE there has the same data.
4. Extract per-module:
   - `name`, `source_root`, `test_root`, `responsibility` (one line if present).
5. Extract dependency manifest paths for the project's language (e.g. `build.gradle.kts`, `package.json`, `pyproject.toml`, `Cargo.toml`, `go.mod`).

## Step 3 — Per-module scan

For each module, compute (use `glob` and `serena_find_symbol` where available; fall back to `rg` for simple counts):

6. **File counts:**
   - production files = count of source files under `<source_root>` matching language extension(s)
   - test files = count under `<test_root>`

7. **Public API surface (top 10 by name length, alphabetic):**
   - Use `serena_find_symbol` with kind=class|interface|function on `<source_root>`, filtered to top-level / exported.
   - If serena unavailable → fallback grep:
     - Kotlin/Java: `^(public |internal )?(class|interface|object|fun) [A-Z]`
     - TypeScript: `^export (class|interface|function|type|const) `
     - Python: `^(class |def )` at module scope (no leading whitespace)
     - Go: `^func (\([^)]+\) )?[A-Z]` AND `^type [A-Z]`
     - Rust: `^pub (fn|struct|enum|trait) `
   - List up to 10 symbol names with file:line references.

8. **Direct dependencies (top 10):**
   - Read the dependency manifest. Extract direct (not transitive) dependency names + version constraints.
   - List up to 10 by file order.

9. **Entry points** (if applicable for the language/framework):
   - Look for `main()`, `Application` annotation, `index.{ts,js}`, `manage.py`, `cmd/*/main.go`, `src/main.rs`, etc.
   - List up to 3 paths.

## Step 4 — Cross-module dependency graph

10. For each pair (A, B) of modules: count files in A that import from B (using language-appropriate import detection — `import com.foo.b`, `import b.module`, `from b import`, etc.).
11. Render as a simple `A → B (N imports)` list, sorted by N descending. Top 10 only.

## Step 5 — Write the file

12. Write `.planning/REPO_MAP.md` using exactly this structure (substitute placeholders with actual content):

```markdown
# REPO_MAP — NotePen

> Generated: <ISO timestamp>
> Source: kit manifest + per-module scan. Authoritative for orientation; use serena_find_symbol for specific lookups.
> Refresh: `/kit-map --refresh`. Auto-suggested when stale (>7 days).
> Gitignored — local artifact.

## Project

- **Name:** NotePen
- **Description:** The best application for editing pdf
- **Stack:** NotePen — kotlin stack
- **Total modules:** <N>

## Modules

### <module-1 name>

- **Source root:** `<source_root>` (<N> production files)
- **Test root:** `<test_root>` (<N> test files)
- **Responsibility:** <one line, or "(none documented)">
- **Public API (top 10):**
  - `<symbol>` — `<file>:<line>`
  - ...
- **Direct dependencies (top 10):**
  - `<lib-name>` <version-constraint>
  - ...
- **Entry points:**
  - `<path>`
  - ...

### <module-2 name>
(same structure)

## Cross-module imports (top 10)

| Source | Target | Imports |
|--------|--------|---------|
| <A> | <B> | <N> |
| ... |

## Notes for agents

- This map is a *starting hint*, not authoritative. Use serena_search_symbols for symbol lookup.
- Public API list is heuristic — language-specific filters may miss generated code, sealed/private exports, or DSL-style APIs.
- Refresh: `/kit-map --refresh` after large refactors, module renames, or before starting a new feature in an unfamiliar module.
```

## Step 6 — Confirm

13. Output: "✅ REPO_MAP.md updated (<N> modules, ~<L> lines). PO can review at `.planning/REPO_MAP.md`."

## What NOT to do

- DO NOT include source code excerpts — names + file:line only.
- DO NOT scan generated/build output (`build/`, `dist/`, `target/`, `node_modules/`, `.gradle/`).
- DO NOT include test-only symbols in the public API list (filter by `<source_root>`, not `<test_root>`).
- DO NOT exceed 300 lines total — if your draft exceeds, trim per-module lists from 10 to 5 first, then dependencies.
- DO NOT commit REPO_MAP.md — it's gitignored. Each developer regenerates locally.
- DO NOT run on every session — the kit only suggests refresh on staleness; over-running this command is wasted token spend.
