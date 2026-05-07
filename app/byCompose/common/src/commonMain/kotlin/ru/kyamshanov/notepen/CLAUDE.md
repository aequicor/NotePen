# CLAUDE.md — common / NotePen

> Nested module-level agent instructions for `common`.
> This file is closest to the source root — it takes precedence over the root `CLAUDE.md` for module-specific rules.

---

## Module: common

**Responsibility:** Compose Multiplatform UI: PDF rendering, drawing surface, page management
**Source root:** `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/`
**Test root:** `app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/`
**Gradle module:** `:app:byCompose:common`

---

## Module Build & Test Commands

| Command | Purpose |
|---------|---------|
| `./gradlew :app:byCompose:common:build` | Build this module |
| `./gradlew :app:byCompose:common:test` | Run tests |
| `./gradlew :app:byCompose:common:compileKotlin` | Quick compile |

---

## Module-Specific Conventions

(use project-default conventions from root CLAUDE.md)

---

## Module Dependencies

- `common`: (none specified)

---

## Module Docs

Docs path: `vault/common/features/`

- One folder per feature: `<feature>/feature.md`, `<feature>/test-cases.md`, optional `<feature>/retro.md`.
- Coding patterns / conventions for this module: `vault/common/guidelines/<topic>.md` (optional).
- Tech-debt entries: `vault/tech-debt/common/`.

---
