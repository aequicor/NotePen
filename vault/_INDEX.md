---
genre: reference
title: Vault Index — NotePen
topic: navigation
confidence: high
source: kit
updated: 2026-05-07T00:00:00Z
---

# Vault Index — NotePen

> Entry point for AI agents. Search here before taking action.
> ai-agent-kit v5 — flat layout.

## How to use

1. Search `knowledge-my-app_search_docs` for the topic you need.
2. Open linked files for full context.
3. Implement according to guidelines.
4. Record learnings back to vault.

## Structure (v5)

```
vault/
  features/<module>/<feature>/
    feature.md        — design doc: Why / ACs / ECs / How it works / Test plan / Implementation plan
    test-cases.md     — live state: TC table + Defects log
    retro.md          — optional: bug-fix retrospectives

  guidelines/<module>/<topic>.md     — coding patterns
  guidelines/libs/<lib>-<version>.md — cached external API docs

  tech-debt/<module>/<slug>.md       — open deferred items
  tech-debt/<module>/done/<slug>.md  — closed items

  _templates/feature.md    — feature doc skeleton
  _templates/test-cases.md — test cases skeleton
  _templates/retro.md      — retro skeleton
  _templates/tech-debt.md  — tech-debt entry skeleton
```

## Modules

| Module | Source root |
|--------|-------------|
| `common` | `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/` |
| `shared` | `shared/src/commonMain/kotlin/` |

## Build commands

| Command | Purpose |
|---------|---------|
| `./gradlew :app:byCompose:common:build` | Build common module |
| `./gradlew :shared:build` | Build shared module |
| `./gradlew detekt ktlintCheck` | Lint + code style |

## Quick links

- Feature template: `vault/_templates/feature.md`
- Test cases template: `vault/_templates/test-cases.md`
- Agent config: `.claude/_shared.md`
- File structure: `.claude/FILE_STRUCTURE.md`
