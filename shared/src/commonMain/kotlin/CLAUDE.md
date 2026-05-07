# AGENTS.md — shared / NotePen

> Nested module-level agent instructions for `shared`.
> This file is closest to `shared/src/commonMain/kotlin/` — it takes precedence over the root `AGENTS.md`.

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

Docs path: `vault/shared/`
- `requirements/` — business requirements
- `spec/` — technical specifications
- `guidelines/` — patterns and rules
- `plans/` — implementation plans
- `reports/` — bug fix reports
