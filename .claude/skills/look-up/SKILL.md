---
name: look-up
description: Proactively look up documentation when working with an unfamiliar library, new instruction set, or any topic where knowledge is missing or uncertain. Searches the local index first; if nothing is found, researches the internet and indexes the result for future use.
---

# LOOKUP

Skill for proactive knowledge gap resolution. Use this skill before writing code or giving advice whenever you encounter something unfamiliar or uncertain.

## When to trigger

Activate this skill when **any** of the following is true:

- You are about to use a library, framework, or API you have not used in this project before
- You encounter an instruction, pattern, or convention you are unfamiliar with
- You are unsure about the correct API, version-specific behavior, or best practice
- You lack enough context to confidently complete the task
- You recognize that your training knowledge may be outdated for the topic at hand

**Do not skip this skill** and proceed on assumptions — an incorrect guess wastes more time than a lookup.

## Input

| Field | Required | Description |
|-------|----------|-------------|
| `query` | Yes | The library name, concept, or question — free-form text |
| `module` | No | Target module if known. Omit to search all modules |
| `type` | No | Document type: `documentation`, `guideline`, `specification`, `tutorial`, `reference`, `recipe`. Omit if unsure — default is `documentation`. |

## Algorithm

```
┌─────────────────────────────────────────┐
│  Trigger: knowledge gap detected        │
└──────────────────┬──────────────────────┘
                   │
                   ▼
        Call find_or_create(query)
                   │
          ┌────────┴────────┐
          │                 │
       found            not found /
          │            sampling unavailable
          │                 │
          ▼                 ▼
     Read file(s)    Follow agent instructions
     from paths      returned by find_or_create
          │                 │
          └────────┬────────┘
                   ▼
         Proceed with the task
         using retrieved context
```

### Step 1. Determine document type

| Type | When to use |
|------|-------------|
| `documentation` | General library/framework docs, overviews |
| `guideline` | Team conventions, best practices, code style rules |
| `specification` | Technical specs, requirements, contracts |
| `tutorial` | Step-by-step learning guides |
| `reference` | API reference, configuration keys, CLI flags |
| `recipe` | Code snippets, reusable patterns, how-tos |

If the type is unclear, use `documentation`.

### Step 2. Call find_or_create

Always start with `find_or_create`:

```json
{
  "query": "<topic or library name>",
  "module": "<module name if known>",
  "type": "<document type>"
}
```

### Step 3a. Document found

Read each file from `paths`. Use the content as context. Proceed.

### Step 3b. Document created via sampling

Read the created file. Use the content as context. Proceed.

### Step 3c. Sampling unavailable — follow agent instructions

Execute the instructions:
1. `WebSearch` — find 2–3 authoritative sources
2. `WebFetch` — read each source
3. Fill the template with gathered information
4. Call `add_document` with the path, **type**, and content
5. Read the created file and proceed with the original task

## Principles

- **Look up first, code second.** Never assume API signatures, configuration keys, or behavior.
- **One lookup per unknown.** If multiple unknowns appear in one task, resolve them all before starting implementation.
- **Trust the index.** If a document was found, use it as the primary source.
- **Freshness matters.** If the found document seems outdated (version mismatch, deprecated API), note it and proceed with WebSearch to verify.
- **Index what you learn.** When you research something manually, always persist it via `add_document` so future lookups are instant.

## Tools used

| Tool | MCP Source | Purpose |
|------|-----------|---------|
| `find_or_create` | `knowledge-my-app` | Primary search + auto-create via sampling |
| `add_document` | `knowledge-my-app` | Persist manually researched documents |
| `WebSearch` | `webfetch` tool | Fallback research when sampling is unavailable |
| `WebFetch` | `webfetch` tool | Read source pages during fallback research |

> If `knowledge-my-app` is unavailable — skip lookup, go directly to webfetch using the canonical URL from `_shared.md`.

## Error Handling

| Situation | Action |
|-----------|--------|
| `find_or_create` returns no results and sampling unavailable | Follow fallback: WebSearch → WebFetch → add_document (Step 3c). |
| WebSearch returns irrelevant results | Refine query: add language name, version, or specific API. Try 2 more queries with different keywords. If still irrelevant → report `LOOKUP FAILED: Could not find authoritative docs for [query]. Proceed with caution — flag uncertainty in output.` |
| WebFetch fails (site blocks, timeout, 403) | Skip that source, try the next search result. If all fail → fall back to summarizing what you know and mark it uncertain. |
| `add_document` fails or indexing unavailable | Save the researched content as a local file: `vault/guidelines/[module]/lookup-[topic].md`. Report path. |
| Found document is outdated (references deprecated API) | Note the version mismatch. Research the current version via WebSearch. Update the document if possible, flag it if not. |
