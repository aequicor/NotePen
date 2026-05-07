---
name: look-up
description: Proactively look up external API documentation when working with an unfamiliar library or API. Searches vault first; if not found, researches via context7 or webfetch and caches the result.
---

# Look-Up Skill

Proactive knowledge-gap resolution. Activate when encountering unfamiliar libraries, APIs, outdated knowledge, or uncertain patterns.

## Trigger conditions

- Using a framework or library you haven't used in this session yet.
- Encountering an unknown pattern or annotation.
- Lacking confidence in a specific API method signature.
- Suspecting your training data is outdated for this library version.

## Process

```
1. knowledge-my-app_search_docs "external-apis <lib> <version>"
   → cache hit → read the document, proceed.
   
2. (cache miss) context7_resolve_library_id + context7_get_library_docs
   → success → step 4.
   → rate-limit / not found → step 3.

3. webfetch on canonical library URL.
   → success → step 4.
   → no valid API found → escalate. Do not write code from memory.

4. (MANDATORY after step 2 or 3) knowledge-my-app_write_guideline →
   vault/guidelines/libs/<lib>-<version>.md
   Frontmatter: lib, version, source, date.
   Body: imports used, signatures used, minimal example.
```

## Key principles

- "Look up first, code second" — never assume API behavior from training data alone.
- Trust indexed documents as primary sources over memory.
- Note outdated information and verify current versions.
- Persist manually researched findings so the next agent gets API from vault without network calls.

## What to cache

The guideline file should contain:
- Import statements used in this project
- Method signatures with parameter names and types
- Minimal working example
- Known pitfalls or version-specific behavior

## Skip for

- stdlib and core language runtime (Kotlin stdlib, Java stdlib) — treat as known.
- APIs already used in existing project files (read the existing code instead).
