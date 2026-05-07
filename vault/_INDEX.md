---
genre: concept
title: Vault Index — NotePen
topic: overview
triggers:
  - "index"
  - "overview"
  - "orientation"
confidence: high
source: human
updated: 2026-05-07T00:00:00Z
---

# NotePen — Knowledge Vault

> **Entry point for AI agents.** Search this vault before any action.
> Structure follows [Diátaxis](https://diataxis.fr/) — each document answers one type of question.

---

## Vault Map

| Genre | Question | Content |
|-------|----------|---------|
| `concepts/` | **Why?** How is it structured? | Architecture, domain model, requirements, plans, ADRs |
| `reference/` | **What exists?** | API specs, DB schema, env vars, test plans |
| `how-to/` | **How to do X?** | Stage files, implementation guides, on-boarding |
| `tutorials/` | **How to learn?** | Getting started, module overviews |
| `guidelines/` | **What rules to follow?** | Conventions, patterns, anti-patterns, lib usage rules |

---

## Modules

| Module | Gradle module | Docs | Responsibility |
|--------|---------------|------|----------------|
| `common` | `:app:byCompose:common` | `vault/common/` | Compose Multiplatform UI: PDF rendering, drawing surface, page management |
| `shared` | `:shared` | `vault/shared/` | Cross-platform business logic and domain models |

### Per-module layout

```
vault/
├── _INDEX.md
├── _templates/                        ← document templates
├── concepts/<module>/                 ← architecture, requirements, plans
│   ├── requirements/<feature>.md
│   └── plans/<feature>-plan.md
├── reference/<module>/                ← specs, test plans, schemas
│   └── spec/<feature>.md
│   └── spec/<feature>-test-plan.md
├── how-to/<module>/                   ← stage files, implementation guides
│   └── plans/<feature>-stage-NN.md
├── tutorials/<module>/                ← getting started, module docs
│   └── documentation/*.md
├── guidelines/<module>/               ← conventions, patterns, anti-patterns
│   ├── <topic>.md
│   └── reports/<bug-name>.md
├── guidelines/libs/                   ← external library API usage rules
│   └── <lib>-<version>.md
└── tech-debt/<module>/                ← deferred non-critical findings (drained by `/kit-techdebt`)
    ├── <slug>.md                      ← open / in-progress
    └── done/<slug>.md                 ← archived after fix
```

---

## Agent Workflow

1. **Search**: `search_docs("topic", genre="guideline")` — find relevant rules
2. **Read**: load linked documents via `[[wikilinks]]`
3. **Execute**: implement by the rules
4. **Write**: `write_guideline(...)` / `update_doc(...)` — record learnings

---

## Quick Links

- Build: `./gradlew`
- Compile: `./gradlew compileKotlin`
- Test: `./gradlew :[module]:test`
- Lint: `./gradlew detekt ktlintCheck`
