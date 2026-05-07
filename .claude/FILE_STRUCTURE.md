# File Structure — NotePen Agent Reference

> Central path reference. Before creating any new file — check this table.
> All agents read this file instead of guessing paths.
> ai-agent-kit v6 — spec.md (FROZEN at CONFIRM) + plan.md (mutable).

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

> Indexed by [KnowledgeOS](https://github.com/aequicor/KnowledgeOS).
> v6 splits the v5 single feature.md into spec.md (FROZEN at CONFIRM) and plan.md (mutable). v4's Diátaxis subtrees remain gone.

### Per-feature structure

```
vault/
  _INDEX.md                     ← vault map — entry point for agents
  _templates/spec.md            ← canonical spec.md skeleton (frozen-at-CONFIRM portion)
  _templates/plan.md            ← canonical plan.md skeleton (mutable-during-EXECUTE portion)

  features/<module>/<feature>/
    spec.md                     ← FROZEN at CONFIRM — Why / ACs / Edge cases / How it works /
                                  Test plan / UI section
                                  (@Analyst writes core; @Designer appends UI section
                                   BEFORE CONFIRM; nothing edits this after CONFIRM)
    plan.md                     ← MUTABLE — Slice budget / Implementation plan / Replan log /
                                  Step-level diff stats / Diff-review / Definition of Done
                                  (@Analyst writes skeleton; @Main fills steps + diff stats +
                                   diff-review; @DoDGate fills DoD verdict; replan-on-discovery
                                   skill appends REPLAN-N markers)
    test-cases.md               ← live state — TC table + Defects log (@TestKeeper)
    retro.md                    ← optional — accumulates bug-fix retros (@BugFixer, bug-retro skill)

  guidelines/<module>/<topic>.md   ← coding patterns / conventions (optional)
  guidelines/libs/<lib>-<version>.md  ← cached external API docs (@CodeWriter, @BugFixer)

  tech-debt/<module>/<slug>.md     ← open / in-progress entries (@CodeWriter, @BugFixer, @Reviewer)
  tech-debt/<module>/done/<slug>.md  ← archived after `/kit-techdebt` closes them
```

### Modules

| Module | Gradle task | Source root |
|--------|-------------|-------------|
| common | `:app:byCompose:common` | `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/` |
| shared | `:shared` | `shared/src/commonMain/kotlin/` |

### System paths

| Path | Owner | Purpose |
|------|-------|---------|
| `vault/_templates/spec.md` | (template) | Canonical spec.md skeleton — frozen portion |
| `vault/_templates/plan.md` | (template) | Canonical plan.md skeleton — mutable portion |
| `vault/features/<module>/<feature>/spec.md` (FROZEN at CONFIRM) | @Analyst (write), @Designer (UI section before CONFIRM only) | Frozen contract: Why / ACs / ECs / How / Test plan / UI |
| `vault/features/<module>/<feature>/plan.md` | @Analyst (skeleton), @Main (steps, diff stats, diff-review), @DoDGate (DoD verdict), replan-on-discovery (REPLAN markers) | Mutable plan / replan log / DoD verdict |
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
| `.claude/settings.json` | human (PO) | Runtime config for this host (Claude Code) |

There is no `@PromptEngineer` agent in v5 — edit agent / skill files directly.

---

## Source Code

### Modules

| Module | Gradle task | Source root |
|--------|-------------|-------------|
| common | `:app:byCompose:common` | `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/` |
| shared | `:shared` | `shared/src/commonMain/kotlin/` |

### Test roots

| Module | Test root |
|--------|----------|
| `common` | `app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/` |
| `shared` | `shared/src/commonTest/kotlin/` |

---

## Build and Verification

| Command | What it does |
|---------|-------------|
| `./gradlew [module]:build` | Full module build |
| `./gradlew compileKotlin` | Quick compile check |
| `./gradlew :[module]:test` | Module tests |
| `./gradlew detekt ktlintCheck` | Lint + code style |
