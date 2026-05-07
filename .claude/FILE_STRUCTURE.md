# File Structure — NotePen Agent Reference

> Central path reference. Before creating any new file — check this table.
> All agents read this file instead of guessing paths.
> ai-agent-kit v5.

---

## Planning (.planning/)

| Path | Owner | Purpose |
|------|-------|---------|
| `.planning/CURRENT.md` | @Main | **Local session pointer (gitignored).** Holds `active_task: <slug>`. Each developer has their own. |
| `.planning/tasks/<slug>.md` | @Main | **Per-task state (committed).** Type, Module, Description, Timeline (DONE/NEXT/BLOCKED). |
| `.planning/tasks/done/<slug>.md` | @Main | Archived task files after CLOSE. |
| `.planning/DECISIONS.md` | @Main, humans | ADR — architectural decisions log (append-only). |

---

## Knowledge Vault (vault/)

> Indexed by KnowledgeOS.
> v5 uses a flat layout — one folder per feature with two or three files.

### Per-feature structure

```
vault/
  _INDEX.md                     — vault map, entry point for agents
  _templates/feature.md         — canonical feature doc skeleton

  features/<module>/<feature>/
    feature.md                  — single design doc: Why / ACs / Edge cases / How it works /
                                  Test plan / Implementation plan / Definition of Done
    test-cases.md               — live state: TC table + Defects log
    retro.md                    — optional: accumulates bug-fix retros

  guidelines/<module>/<topic>.md    — coding patterns / conventions (optional)
  guidelines/libs/<lib>-<version>.md — cached external API docs

  tech-debt/<module>/<slug>.md      — open / in-progress entries
  tech-debt/<module>/done/<slug>.md — archived after /kit-techdebt closes them
```

### Modules

| Module | Gradle task | Source root |
|--------|-------------|-------------|
| `common` | `:app:byCompose:common` | `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/` |
| `shared` | `:shared` | `shared/src/commonMain/kotlin/` |

### System paths

| Path | Owner | Purpose |
|------|-------|---------|
| `vault/_templates/feature.md` | (template) | Canonical feature.md skeleton — copy and fill |
| `vault/features/<module>/<feature>/feature.md` | @Analyst, @Designer, @Main, @DoDGate | The single feature design doc |
| `vault/features/<module>/<feature>/test-cases.md` | @TestKeeper, @BugFixer (Status flips on fix) | Live test state |
| `vault/features/<module>/<feature>/retro.md` | @BugFixer | Accumulating bug retros for this feature |
| `vault/guidelines/libs/<lib>-<version>.md` | @CodeWriter, @BugFixer | External library documentation cache |
| `vault/tech-debt/<module>/<slug>.md` | @CodeWriter, @BugFixer, @Reviewer | Open tech-debt entries |
| `vault/tech-debt/<module>/done/<slug>.md` | @Main (via `/kit-techdebt`) | Closed entries |

---

## Agent Configuration (.claude/)

| Path | Owner | Purpose |
|------|-------|---------|
| `.claude/_shared.md` | human (PO) | Shared context for all agents |
| `.claude/FILE_STRUCTURE.md` | human (PO) | This file — path reference |
| `.claude/agents/[Name].md` | human (PO) | Individual agent prompt |
| `.claude/skills/[name]/SKILL.md` | human (PO) | Skill definition |
| `.claude/settings.json` | human (PO) | Runtime config for Claude Code |

There is no `@PromptEngineer` agent in v5 — edit agent / skill files directly.

---

## Source Code

### Modules

| Module | Gradle task | Source root |
|--------|-------------|-------------|
| `common` | `:app:byCompose:common` | `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/` |
| `shared` | `:shared` | `shared/src/commonMain/kotlin/` |

### Test roots

| Module | Test root |
|--------|----------|
| `common` | `app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/` |
| `shared` | `shared/src/commonTest/kotlin/` |

---

## Build and Verification

| Command | What it does |
|---------|--------------|
| `./gradlew [module]:build` | Full module build |
| `./gradlew compileKotlin` | Quick compile check |
| `./gradlew :[module]:test` | Module tests |
| `./gradlew detekt ktlintCheck` | Lint + code style |
