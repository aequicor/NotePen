# File Structure — NotePen Agent Reference

> Central path reference. Before creating any new file — check this table.
> All agents read this file instead of guessing paths.

---

## Planning (.planning/)

| Path | Owner | Purpose |
|------|-------|---------|
| `.planning/CURRENT.md` | @Main | **Local session pointer (gitignored).** Holds `active_task: <slug>`. Each developer has their own. |
| `.planning/tasks/<slug>.md` | @Main | **Per-task state (committed).** Type, Module, Description, Timeline (DONE/NEXT/BLOCKED). One file per active task — no merge conflicts between team members. |
| `.planning/tasks/done/<slug>.md` | @Main | Archived task files after CLOSE. |
| `.planning/DECISIONS.md` | @Main, humans | ADR — architectural decisions |
| `.planning/bugs/BUG-NNN.md` | @Debugger | Bug investigation report (root cause + failing test) |

---

## Knowledge Vault (vault/)

> Indexed by [KnowledgeOS](https://github.com/aequicor/KnowledgeOS).
> Structure follows [Diátaxis](https://diataxis.fr/).

### Per-module structure

```
vault/
  _INDEX.md                          ← vault map — entry point for agents
  _templates/                        ← document templates (bug-report, spec, test-plan, requirements)

  concepts/<module>/                 ← WHY and HOW it's structured (@Main)
    requirements/<feature>.md
    plans/<feature>-plan.md
    plans/<feature>-corner-cases.md   ← corner case register (@Main, corner-case-refinement)

   reference/<module>/                ← WHAT EXISTS — specs, schemas, test plans, test cases
     spec/<feature>.md
     spec/<feature>-test-plan.md      ← Draft/Final (@QA)
     spec/<feature>-trace.md          ← traceability matrix AC/CC/endpoint → TC → test → source (@TraceabilityChecker)
     spec/<feature>-dod.md            ← Definition-of-Done verdict report (@DoDGate)
     test-cases/<feature>-test-cases.md ← Test cases with defect tracking (@TestRunner)

  how-to/<module>/                   ← HOW TO implement
    plans/<feature>-stage-NN.md      ← stage files (@Main → @CodeWriter)

  tutorials/<module>/                ← HOW TO LEARN
    documentation/*.md

  guidelines/<module>/               ← RULES — accumulated by agents
    <topic>.md
    reports/<bug-name>.md            ← bug fix reports (@BugFixer)
    reports/test-runs/<ISO>-stage-NN.md ← independent test-run reports (@TestExecutor)

  guidelines/libs/                   ← external library API documentation cache
    <lib>-<version>.md               ← (@CodeWriter, @BugFixer)

  tech-debt/<module>/                ← deferred non-critical findings (@CodeWriter, @BugFixer, @CodeReviewer, @SecurityReviewer, @TestExecutor)
    <slug>.md                        ← open / in-progress entries
    done/<slug>.md                   ← archived after `/kit-techdebt` closes them
```

### Modules

| Module | Gradle task | Source root |
|--------|-------------|-------------|
| common | `:app:byCompose:common` | `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/` |
| shared | `:shared` | `shared/src/commonMain/kotlin/` |

### System paths

| Path | Owner | Purpose |
|------|-------|---------|
| `vault/guidelines/libs/<lib>-<version>.md` | @CodeWriter, @BugFixer | External library documentation cache |
| `vault/_templates/test-plan.md` | (template) | Template for @QA |
| `vault/_templates/test-cases.md` | (template) | Template for @TestRunner |
| `vault/_templates/bug-report.md` | (template) | Template for @BugFixer |
| `vault/_templates/spec.md` | (template) | Template for @Main |
| `vault/_templates/requirements.md` | (template) | Template for @Main |
| `vault/_templates/tech-debt.md` | (template) | Template for `tech-debt-record` skill |
| `vault/tech-debt/<module>/<slug>.md` | @CodeWriter, @BugFixer, @CodeReviewer, @SecurityReviewer, @TestExecutor | Open tech-debt entries (status: open\|in-progress) |
| `vault/tech-debt/<module>/done/<slug>.md` | @Main (via `/kit-techdebt`) | Closed entries (status: fixed\|wont-fix) |
| `vault/reference/<module>/spec/<feature>-trace.md` | @TraceabilityChecker | Traceability matrix per feature (AC/CC/endpoint → TC → test file → source) |
| `vault/reference/<module>/spec/<feature>-dod.md` | @DoDGate | Definition-of-Done verdict report — last gate before CLOSE |
| `vault/guidelines/<module>/reports/test-runs/<ISO>-stage-NN.md` | @TestExecutor | Independent run report per stage (build + unit + integration + TC mapping) |

---

## Agent Configuration (.claude/)

| Path | Owner | Purpose |
|------|-------|---------|
| `.claude/_shared.md` | @PromptEngineer | Shared context for all agents |
| `.claude/FILE_STRUCTURE.md` | @PromptEngineer | This file — path reference |
| `.claude/agents/[Name].md` | @PromptEngineer | Individual agent prompt |
| `.claude/skills/[name]/SKILL.md` | @PromptEngineer | Skill definition |
| `.claude/settings.json` | human (PO) | Runtime config for this host (Claude Code) |

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
