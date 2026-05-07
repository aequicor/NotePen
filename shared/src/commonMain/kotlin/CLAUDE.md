# CLAUDE.md — shared / NotePen

> Nested module-level agent instructions for `shared`.
> This file is closest to the source root — it takes precedence over the root `CLAUDE.md` for module-specific rules.

---

## Module: shared

**Responsibility:** Cross-platform business logic and domain models
**Source root:** `shared/src/commonMain/kotlin/`
**Test root:** `shared/src/commonTest/kotlin/`
**Gradle module:** `:shared`

---

## Module Build & Test Commands

| Command | Purpose |
|---------|---------|
| `./gradlew :shared:build` | Build this module |
| `./gradlew :shared:test` | Run tests |
| `./gradlew :shared:compileKotlin` | Quick compile |

---

## Module-Specific Conventions

(use project-default conventions from root CLAUDE.md)

---

## Module Dependencies

- `shared`: (none specified)

---

## Module Docs

Docs path: `vault/shared/features/`

- One folder per feature: `<feature>/feature.md`, `<feature>/test-cases.md`, optional `<feature>/retro.md`.
- Coding patterns / conventions for this module: `vault/shared/guidelines/<topic>.md` (optional).
- Tech-debt entries: `vault/tech-debt/shared/`.

---
