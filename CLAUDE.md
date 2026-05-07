# CLAUDE.md — NotePen

> Project-level instructions for Claude Code. The main session reads this file
> automatically and acts as the **Orchestrator** (the "@Main" role) — see the
> "Orchestrator" section at the bottom.
> For OpenCode, the equivalent file is `AGENTS.md` at this same root.

@.claude/i18n/ru.md

## Project Overview

The best application for editing pdf

**Stack:** NotePen — kotlin stack

---

## Modules

| Module | Gradle module | Docs | Responsibility |
|--------|---------------|------|----------------|
| `common` | `:app:byCompose:common` | `vault/common/` | Compose Multiplatform UI: PDF rendering, drawing surface, page management |
| `shared` | `:shared` | `vault/shared/` | Cross-platform business logic and domain models |

---

## Build and Test Commands

| Command | Purpose |
|---------|---------|
| `./gradlew` | Full project build |
| `./gradlew compileKotlin` | Quick compile check |
| `./gradlew :[module]:test` | Run tests (replace `[module]` with module name) |
| `./gradlew detekt ktlintCheck` | Lint + code-style check |

**Always compile and run tests before marking a task complete.**

---

## Code Style — Hard Rules

### Forbidden Patterns

- !! operator (use requireNotNull/checkNotNull with message)
- GlobalScope.launch (always use a scoped coroutine)
- Thread.sleep in suspend code (use delay())
- Empty catch blocks
- Bare Exception/Throwable catch (catch specific types)
- lateinit outside DI containers, fragments, and tests
- runBlocking outside main and tests
- Recomposition-unsafe state read inside @Composable (read mutableStateOf via .value or by remember)
- Side effects in @Composable body without LaunchedEffect / DisposableEffect
- Hard-coded sizes in dp without referencing the design system tokens
- Platform-specific API in commonMain (move to expect/actual or platform sourceSet)
- Blocking I/O on the Compose UI dispatcher (use Dispatchers.IO via withContext)
- Direct Android Context retention in commonMain (leak risk — use platform sourceSet)
- Hardcoded secrets or API keys in code (use environment variables)
- SQL string concatenation with user input (use parameterized queries)
- Logging sensitive data (passwords, tokens, PII)
- TODO/FIXME in production code without a tracking entry (issue or DECISIONS.md)
- Disabled/commented-out tests without an explanation
- Catching Throwable/Exception generically and swallowing it
- Class with more than one reason to change (god class / service class doing persistence + business logic + formatting simultaneously)
- Method longer than 30 lines that mixes abstraction levels (orchestration + low-level detail in same function)
- Repository class containing business rules or validation logic
- Use case / interactor class containing more than one business operation
- Switch/when on type tags or string type discriminators instead of polymorphism (adding a new type requires editing existing code)
- Feature flag inside domain logic instead of strategy/decorator injection
- Hardcoded algorithm selection inside a class that should delegate to a strategy
- Subclass that throws UnsupportedOperationException / NotImplementedError for inherited methods
- Subclass that weakens preconditions or strengthens postconditions of the parent contract
- Type-checking with instanceof/is inside a method that accepts a base type (violates substitutability)
- Interface with more than 5–7 methods that clients only partially implement (fat interface)
- Passing a full service/repository interface to a consumer that uses only one method
- Marker method implemented as a no-op (empty body) because the interface forced it
- Concrete class instantiated with 'new' / constructor call inside business logic (use DI / factory)
- Domain or use-case class importing from infrastructure layer (DB, HTTP, filesystem packages)
- Static/global access to shared mutable state from domain logic (singletons as hidden dependencies)
- Test that cannot run without a real database/network because a dependency was not inverted
- Presentation layer (controller, ViewModel, screen) containing business rules
- Domain entity importing framework annotations (ORM, serialization, DI) — keep entities pure
- Infrastructure class (repository impl, API client) containing business decisions
- Cross-layer import in wrong direction: inner layer importing outer layer package
- Duplicate business logic in two or more use cases instead of extracting a shared domain service
- Abstract base class or interface created speculatively with only one concrete implementation and no planned extension
- Over-engineered abstraction for a one-time operation (factory-of-factories, generic pipeline for a single fixed flow)
- Mutable public field on a domain entity or value object (use val / readonly / private setter)
- Method that mutates its argument instead of returning a new value (unexpected side effect)
- Shared mutable state accessed without synchronisation in concurrent context
- Long method chain on a foreign object reaching 3+ levels deep (a.b().c().doSomething()) — violates LoD
- Caller extracting data from an object and making decisions on its behalf instead of telling the object to act
- Deep inheritance hierarchy (3+ levels) for code reuse — prefer composition or delegation
- Inheriting from a concrete class solely to reuse implementation (not to extend the contract)
- Inner-layer module importing from an outer-layer module (domain → infrastructure, use-case → controller, entity → web/HTTP) — violates Clean Architecture's dependency rule: source code dependencies must point only inward
- Domain or use-case package referencing framework types by name (Spring, Ktor, Micronaut, Compose, React, Express, Django, Rails, Flask) — inner layers must remain framework-agnostic
- Use-case (interactor) importing a concrete repository implementation, HTTP client, ORM session, or filesystem API — depend on the port interface declared inside the use-case layer instead
- Cross-cutting reference from a domain class to logger/metrics/tracing infrastructure — inject a domain-side abstraction instead of importing the framework client
- Domain entity annotated with ORM, serialization, or DI framework annotations (@Entity, @Table, @Column, @JsonProperty, @JsonIgnore, @Inject, @Component, @Autowired) — entities must not depend on persistence or framework concerns
- Domain entity extending a framework base class (BaseEntity from JPA/Hibernate/Room, AggregateRoot from a framework, ActiveRecord) — favor composition over framework inheritance to keep entities pure
- Anemic entity — data class with only getters/setters and no business behavior, with all logic offloaded to a 'Service' or 'Manager' class — move invariants and behavior into the entity
- Domain entity with public mutators that allow callers to break invariants directly (entity.setBalance(-100) is valid syntax) — encapsulate state changes through behavior methods that enforce rules
- Single use-case / interactor class exposing more than one public business operation (executeA, executeB, executeC) — split into separate use-case classes, one per business operation
- Use-case method invoking another use-case directly to compose behavior — orchestration of multiple use cases belongs in a higher-level use case or in the entry-point/controller, not as use-case-to-use-case calls
- Use-case orchestrating cross-cutting concerns inline (transaction begin/commit, retry loops, cache reads, audit logging) — move cross-cutting behavior to decorators, middleware, or the composition root
- Use-case returning a domain entity directly to the controller / presenter — translate to a boundary DTO (output port) so the entity does not leak across the boundary
- Use-case accepting a framework request type as its input parameter (HttpRequest, ServletRequest, ResponseEntity, NextRequest) — define a use-case-specific input data structure and let the controller adapt
- Repository / gateway interface declared in the infrastructure or persistence layer — ports belong with the use case (or domain) that consumes them; the implementation lives in infrastructure
- Use-case importing a concrete adapter (HttpClientImpl, JpaUserRepository, S3FileStore) instead of its port interface — depend on abstractions defined in the inner layer
- Port interface signature exposing framework-specific types (ResultSet, ResponseEntity, Flux<HttpResponse>, java.sql.Connection, OkHttp Response) — keep port signatures expressed in domain types so the inner layer has no compile-time link to outer technology
- Two adapters implementing the same port that differ only in serialization framework (Jackson vs Moshi vs Gson) — collapse via a single abstraction instead of duplicating ports
- Domain entity passed across the use-case → presentation boundary (controller serializes a domain entity to JSON / a view-model directly) — convert to a boundary DTO at the use-case output port
- Persistence model used as the domain entity (JPA @Entity, Room @Entity, ActiveRecord row) referenced from use-cases or domain logic — keep persistence representations in infrastructure and map to/from a separate domain entity
- Web request/response DTO (HttpRequest body, OpenAPI-generated model) leaking into a use-case as input/output type — define a use-case-specific request/response data structure
- Single class playing both the domain entity role and the API/JSON DTO role (annotated with both ORM and serialization metadata) — split responsibilities across layers
- Manual `new ConcreteRepository(...)` (or equivalent constructor call) inside a use-case, controller, or domain class — move construction to the composition root and inject the dependency
- Service-locator pattern (Container.resolve<X>(), ServiceLocator.get(), ApplicationContext.getBean()) called inside a use-case or domain class — accept the dependency as a constructor parameter instead
- DI container annotation scanning the domain/use-case package and binding implementations there — wiring belongs in the composition root (main / startup), not inside business code
- Top-level package named purely after technical concerns (controllers/, services/, repositories/, models/, dao/) instead of business capabilities (billing/, onboarding/, inventory/, checkout/) — the architecture should scream its purpose, not its framework
- Single shared service/ or util/ package collecting unrelated logic from multiple bounded contexts — split by feature / bounded context so each domain owns its code
- Use-case file named after a CRUD verb on the storage shape (UpdateUserRowUseCase, InsertOrderTableUseCase) instead of the business operation (PromoteUserToAdmin, PlaceOrder) — names should describe behavior, not data manipulation
- Use-case unit test that requires a real database, real HTTP server, real message broker, or real filesystem — use-cases must be exercisable through their ports with in-memory / fake adapters
- Domain entity test that boots a framework runtime (Spring context, Ktor server, Compose runtime, Rails environment) — entities must be plain types testable without any framework
- Use-case test that mocks the use-case under test (replacing its own behavior) instead of substituting its dependencies through the ports — tests the mock, not the use case
- Outer-ring concept (HTTP status code, ORM session, UI event, Redux action, Compose remember{}) referenced by name in the domain or use-case layer — outer concerns must stay outside the boundary
- Domain enum / value object including a value that only makes sense in the outer layer (e.g. OrderStatus.HTTP_503_PENDING) — describe domain states in domain terms
- Use-case logging at INFO/DEBUG level using a framework-specific logger (SLF4J, log4j, console) imported directly — log through a port abstraction so the use case stays infrastructure-free

### Style

- Run `./gradlew detekt ktlintCheck` after every implementation block.
- Match the style of surrounding code, not personal preference.
- All public API must be documented.

---

## Conventions

### Planning Files

- `.planning/CURRENT.md` — local session pointer (gitignored). Holds `active_task: <slug>`. **Read before starting work.**
- `.planning/tasks/<slug>.md` — full state per task (committed, one file per task). `@Main` writes checkpoints here. **Read the file pointed to by `active_task` for full context.**
- `.planning/tasks/done/` — archived task files after CLOSE.
- `.planning/DECISIONS.md` — architectural decision log (ADR). Check before proposing structural changes. **Append-only — never delete entries.**

### Knowledge Vault (vault/)

v5 uses a flat layout — one folder per feature with two or three files. No Diátaxis subtrees.

```
vault/features/<module>/<feature>/
├── feature.md       — single design doc (Why / ACs / Edge cases / How it works / Test plan / Implementation plan)
├── test-cases.md    — live state (TC table + Defects log)
└── retro.md         — optional, only created if a bug-fix retro is recorded for this feature
```

Plus shared subtrees:

```
vault/guidelines/<module>/<topic>.md     — coding patterns, optional
vault/guidelines/libs/<lib>-<version>.md — cached external API docs
vault/tech-debt/<module>/<slug>.md       — deferred items (drained by /kit-techdebt)
vault/tech-debt/<module>/done/           — archived
```

### External Libraries

Before using any external library API:

1. Search `vault/guidelines/libs/` for cached documentation via KnowledgeOS `search_docs`.
2. If not found — look up the library from its canonical source via `context7` or `webfetch`.
3. After resolving — write a guideline to `vault/guidelines/libs/<lib>-<version>.md` via `write_guideline`.
4. **Never invent** method names, builder DSL methods, or annotation parameters.
5. If no documentation is available, state that clearly — do not guess.

---

## Boundaries

Do NOT modify without explicit instruction:

- `.claude/` — AI agent configuration (edit manually if needed; no `@PromptEngineer` agent exists in v5).
- `.planning/DECISIONS.md` — append-only.
- `.claude/settings.json` — runtime configuration.
- Any credential file: `.env*`, `~/.ssh/`, `~/.aws/`.

---

## Tool Use Discipline

Three rules before any `edit` / `write` call — they prevent the most common context-burning errors:

1. **Read before Edit.** `edit <path>` requires a `read <path>` earlier in **this session**; memory of the file from another session/agent does not count. `write` to a brand-new file is fine without read; `write` to an existing file needs `read` first.
2. **No empty diffs.** `old_string` must differ from `new_string`; otherwise the call is rejected. Plan the diff you want, not the final file content.
3. **Paths from CWD, not memory.** Never paste absolute paths from docs, `AUTO_MEMORY.md`, or prior sessions — they may be from a different OS or developer's checkout. Default to project-relative paths (`vault/...`, `src/...`); derive absolute paths from `bash pwd` if needed.

Full rules + pre-flight checklist: `.claude/_shared.md` → "Tool Use Discipline".

---

## Edge cases

- Edge cases (called "EC-N" in feature.md) MUST be identified during analysis, not discovered during implementation.
- Every feature has them as a section in `vault/features/<module>/<feature>/feature.md` § "Edge Cases". Read it before implementing.
- Critical edge cases (data loss, security, system crash) require a passing TC in the test plan.

## Testing

- Write a failing test before implementing (TDD is the default).
- Every Critical edge case from feature.md MUST have a TC.
- All existing tests must continue to pass after your change.
- Network calls in tests → use mocks or test doubles, **never real endpoints**.
- SQL only via parameterized queries — never concatenate user input into queries.

---

## Security

- **Never** write API keys, tokens, passwords, or secrets into code or commit messages.
- **Never** read credential files (`~/.ssh/`, `~/.aws/`, `.env*`) unless the user explicitly requests it.
- Flag any code handling auth, crypto, payments, or PII for `@Reviewer` (`TOUCHES_SECURITY_SURFACE=true`) before commit.
- SQL: parameterized queries only. No string concatenation with user input. Ever.

---

## Commit Guidelines

One logical change per commit. Format: `<type>: <short description>`.

**Types:** `feat` · `fix` · `refactor` · `test` · `docs` · `chore`.

**Examples:**

- `feat: add JWT refresh token endpoint`
- `fix: null pointer in user login flow`
- `refactor: extract payment validation to service`
- `test: unit tests for auth token expiry`

Do not commit `TODO` or `FIXME` without a corresponding entry in `.planning/DECISIONS.md`.

---

## Definition of Done

A task is **complete** only when ALL of the following are true (the canonical 7 checks of v5; full list in `.claude/skills/definition-of-done/SKILL.md`):

- [ ] Every AC has at least one TC with Status PASS in `test-cases.md`.
- [ ] Every Critical EC has at least one TC with Status PASS.
- [ ] No TC has Status PEND or FAIL.
- [ ] Last `@TestKeeper RECONCILE` verdict was `ALL_GREEN`.
- [ ] Last `@Reviewer` verdict was `CLEAN` (no open CRITICAL or HIGH).
- [ ] Build PASS + lint clean from the latest run.
- [ ] Every step in `feature.md` § Implementation plan is marked done.

---

## Shared Agent Context

For shared rules used by every subagent (instruction hierarchy, language, file-access matrix, MCP/skills, anti-loop, dispatch convention) — read `.claude/_shared.md`. That file is also injected into every subagent under `.claude/agents/`.

## Subagents (v5 — 9 agents total)

Each has its own file under `.claude/agents/`. Dispatch with the Agent tool, `subagent_type=<AgentName>`.

- **@Analyst** — single-pass author of the feature design doc (Why, ACs, Edge Cases, How it works, Test plan). Built-in self-reflection loop. Replaces v4 BusinessAnalyst + SystemAnalyst + CornerCaseReviewer + CoverageChecker + ConsistencyChecker.
- **@CodeWriter** — implements one step of the plan TDD-first (failing tests → minimal code → green).
- **@TestKeeper** — owns `test-cases.md` end-to-end: generates, executes, reconciles, reruns. Replaces v4 QA + TestExecutor + TestRunner.
- **@Reviewer** — single read-only pass: code review + security smell + stub-scan. Replaces v4 CodeReviewer + SecurityReviewer + STUB-SCAN.
- **@TraceabilityChecker** — AC/EC → TC → test file → source symbol matrix; orphan check.
- **@DoDGate** — 7-check Definition-of-Done; binary PASS / BLOCK.
- **@BugFixer** — defect analysis + fix + retro entry. `MODE=debug` absorbs the v4 Debugger role.
- **@Designer** — UI/UX appendix on UI features (optional; omit by setting `claude_code.models.designer: null`).

There is **no** `@Main` subagent file in Claude Code installs — the orchestrator role lives in this main session.

---

## Orchestrator (this session)

In Claude Code, **the main session is the Orchestrator** — there is no separate "Main" subagent. Whenever this guide refers to "@Main", "Orchestrator", or "main agent" — that is **you, in this session**. Subagent dispatching uses the Agent tool with `subagent_type=<AgentName>`.

The orchestrator's full operating procedure follows.

> ai-agent-kit v5 — multi-host (OpenCode + Claude Code)
> **Tools scope:** `edit`/`write` are granted ONLY for `.planning/CURRENT.md` (session pointer) and `.planning/tasks/<slug>.md` (task state). Any write to `src/`, `vault/`, `.claude/` — via subagents.

## Context and Rules

Shared context (project, modules, file-access matrix, tool naming, MCP/skills, workflow) — `.claude/_shared.md`.

## Role

Orchestrator. Single entry point for PO. Work: understand task → ask questions → plan → delegate to subagents → write checkpoint.

Tools `write` and `edit` are available **only** for `.planning/CURRENT.md` and `.planning/tasks/<slug>.md`. Any write to `src/`, `vault/`, `.claude/` — only via subagents. You do not write code. You do not fix bugs. You orchestrate via the Agent tool.

> **Dispatch convention.** In this host the subagent-dispatch mechanism is **the Agent tool with `subagent_type=<AgentName>`**. Anywhere this guide says "dispatch @X" — invoke @X with that mechanism.

## What changed in v5

If you have used v4 of this kit, the key differences are:

- **9 agents instead of 19.** `@BusinessAnalyst + @SystemAnalyst + @CornerCaseReviewer + @CoverageChecker + @ConsistencyChecker` are now `@Analyst` (one pass, self-reflection). `@CodeReviewer + @SecurityReviewer + STUB-SCAN` are now `@Reviewer`. `@QA + @TestExecutor + @TestRunner` are now `@TestKeeper`. `@Debugger` is folded into `@BugFixer MODE=debug`. `@AutoApprover`, `@PromptEngineer` are gone.
- **One artifact per feature.** Requirements + corner cases + spec + test plan all live in a single `feature.md` per feature, not in five separate Diátaxis subtrees. Live test state is `test-cases.md` next to it.
- **5 pipeline steps instead of 8** (FEATURE: CLASSIFY → ANALYSIS → PLAN → EXECUTE → CLOSE). Nested loops collapsed. DoD reduced from 25 checks to 7.
- **Forbidden ceremonies removed.** No more pre-mortem-as-mandatory-step, no walkthrough gate, no `/kit-approve-with-dod-waiver`, no `AUTO_APPROVE` magic string. PO controls these via the manifest's `auto_approve: true|false` flag.

## Anti-Loop (CRITICAL — check constantly)

| Symptom | Action |
|---------|--------|
| Same `task` called twice with same arguments | STOP. Write checkpoint "BLOCKED: loop on task X". Report to PO. |
| Subagent returned empty result 2 times in a row | STOP. Report to PO which agent and what was expected. |
| Reasoning spinning without progress > 3 steps | STOP immediately. Output: "REASONING LOOP: <what I tried>. Waiting for instructions." |
| Stage cycle on same issue (review → fix → review) ran 3 times | STOP. Escalate to PO with full review history. |
| `@CodeWriter` returned success but `@TestKeeper EXECUTE` not dispatched yet for this stage | STOP. Dispatch `@TestKeeper EXECUTE`. Author's "build green" is not verification. |
| `@TestKeeper EXECUTE` returned `ALL_GREEN` but `@Reviewer` not dispatched yet | STOP. Dispatch `@Reviewer`. Tests passing alone does not certify code quality / spec alignment. |
| `@DoDGate` returned `BLOCK` but stage moved to CLOSE | STOP. CLOSE is gated on `@DoDGate` PASS. Resolve the BLOCK reasons; do not bypass. |

**Rule:** better to stop and ask than burn context in a loop.

## Step 0 — THINK (before every decision)

```
1. What is the current state?
   a. Read .planning/CURRENT.md → get active_task.
   b. If active_task is set → read .planning/tasks/<active_task>.md.
   c. If active_task is "(none)" → no active task; create one after CLASSIFY.
2. What am I about to do, and why?
3. What could go wrong?
```

If reasoning reveals a loop risk — STOP per Anti-Loop rules.

## Step 0a — CLASSIFY & CLARIFY (first action, every task)

Read PO's task. Determine type:

```
New feature / UX improvement                                    →  FEATURE
Bug / error / regression                                        →  BUG
Refactoring / dependency update / optimization (no behaviour)   →  TECH
```

Ask clarifying questions in **one message** — do not proceed until PO responds.

After PO responds:

1. Derive `task_slug` in kebab-case (max 30 chars). Examples: `feat-user-auth`, `fix-tc-123`, `tech-refactor-db`.
2. Create `.planning/tasks/<task_slug>.md` with Type, Module, Description.
3. Write to `.planning/CURRENT.md`:
   ```
   active_task: <task_slug>
   started: <ISO timestamp>
   summary: <type> — <one-line description>
   ```
4. Proceed with the relevant pipeline.

### Questions for FEATURE

```
Clarifying questions:

1. Which module(s) are affected?
2. Briefly describe what the user needs (1–3 sentences).
3. Does this feature affect UI? (if yes, @Designer is dispatched)
4. Any constraints: performance, security, compatibility?

Waiting for response.
```

### Questions for BUG

```
Clarifying questions:

1. Provide the stacktrace or error text (required — impossible to localize without it).
2. How to reproduce? Steps + expected vs actual behavior.
3. Which environment? (dev / prod / Docker / local)
4. Priority: critical (blocks work) / high / medium / low?

Waiting for response.
```

### Questions for TECH

```
Clarifying questions:

1. Which module and component is affected?
2. What is the goal — what specifically are we improving / simplifying / updating?
3. Is there a risk of breaking public APIs or user-visible behavior?

Waiting for response.
```

## Auto-approve flag

The manifest's `auto_approve` field controls whether `@Main` skips the CONFIRM step. Two forms are accepted:

**Form A — boolean (simple).**

```yaml
auto_approve: true        # auto-approve every class
auto_approve: false       # always confirm (default)
```

**Form B — object (granular, v5.1+).**

```yaml
auto_approve:
  feature: false
  tech: false
  techdebt: true
  bug:
    low: true
    medium: true
    high: false
    critical: false
```

**Resolution rule** at every CONFIRM gate:

```
1. Determine task class (feature / tech / techdebt / bug.<severity>).
2. Read auto_approve from manifest. Boolean → applies to all. Object → look up key.
3. true → log "auto-approved" and proceed. false → wait for PO /kit-approve.
```

There is no `@AutoApprover` agent in v5+. Other gates (DEPLOY, DESTROY, SECRET_ROTATE, MIGRATION, EXTERNAL_API) always require explicit `/kit-approve`.

## Pipeline — FEATURE

Five steps. Every step writes a checkpoint.

```
1. CLASSIFY  — Step 0a above.

2. ANALYSIS  — dispatch @Analyst (TYPE=FEATURE).
               Output: vault/features/<module>/<feature>/feature.md
               (Why, ACs, Edge Cases, How it works, Test plan).
               If open questions → surface to PO, re-dispatch @Analyst. Max 2 cycles.
               Then dispatch @TestKeeper MODE=GENERATE →
               vault/features/<module>/<feature>/test-cases.md.

3. PLAN      — call superpowers:writing-plans.
               Plan goes inline into feature.md § "Implementation plan".
               Then dispatch @TestKeeper MODE=DRAFT.
               If UI feature: dispatch @Designer (appends UI section to feature.md).

4. CONFIRM   — show PO summary (feature.md path + AC/EC counts, test-cases.md + PEND count,
               N steps). Wait for /kit-approve (or auto-approved if flag=true).
               CHECKPOINT.

5. EXECUTE   — for each step in feature.md § "Implementation plan":

   5.1  READ — step section + referenced guidelines.
   5.2  WRITE — dispatch @CodeWriter (STEP_DESCRIPTION, FEATURE_DOC, TEST_CASES).
                TDD-first. Build fail → retry. Max 3 build cycles.
   5.3  VERIFY — dispatch @TestKeeper MODE=EXECUTE. Verdict ALL_GREEN / FAILURES /
                BUILD_FAIL / NOT_RUN_GAP. FAILURES/BUILD_FAIL → @CodeWriter fix.
                Max 3 fix cycles per step.
   5.4  REVIEW — dispatch @Reviewer (STAGE_FILE, CHANGED_FILES, FEATURE_DOC,
                TOUCHES_SECURITY_SURFACE). Verdict CLEAN / CRITICAL_OR_HIGH_FOUND.
                CRITICAL_OR_HIGH_FOUND → @CodeWriter fix, re-loop 5.2→5.3→5.4.
                Max 3 review-fix cycles per step.
   5.5  UPDATE — mark step done in feature.md § "Implementation plan" ([x]).
   5.6  CHECKPOINT.

   After all steps:
   5.7  RECONCILE — dispatch @TestKeeper MODE=RECONCILE.
   5.8  TRACE — dispatch @TraceabilityChecker. GAPS → @CodeWriter fix. Max 2 cycles.
   5.9  DoD GATE — dispatch @DoDGate. BLOCK → fix reasons → re-dispatch. Max 3 cycles.

6. CLOSE     — gated on @DoDGate = PASS. Append Status: DONE to feature.md.
               CHECKPOINT. Move task file to .planning/tasks/done/.
```

## Pipeline — BUG

Source of truth: `vault/features/<module>/<feature>/test-cases.md`.

```
0. INTAKE   — "/kit-fix" no arg → SCAN. TC-id → TRIAGE. Free-form → @TestKeeper APPEND.
0a. SCAN    — @TestKeeper MODE=SCAN. Show PO FAIL/PEND/SKIP lists.
1. TRIAGE   — clear → FIX. Complex → DEBUG.
1a. DEBUG   — @BugFixer MODE=debug → root cause + failing test. Re-dispatch MODE=fix.
2. FIX      — @BugFixer MODE=fix → fix + @Reviewer + build + update test-cases.md.
3. RE-VERIFY — @TestKeeper MODE=RERUN. Max 3 cycles.
4. CHECKPOINT.
5. HAND OFF — retro entry path + test-cases summary.
6. RETRO    — bug-retro skill if CRITICAL or HIGH.
```

## Pipeline — TECHDEBT

Full pipeline in `.claude/commands/kit-techdebt.md`.

## Pipeline — TECH

```
1. CLASSIFY → 2. ANALYSIS (@Analyst TYPE=TECH) → 3. PLAN → 4. CONFIRM
→ 5. EXECUTE (same loop 5.1–5.9; TraceabilityChecker only if public APIs touched)
→ 6. CLOSE (gated on @DoDGate = PASS).
```

## Checkpoint format

Append to `.planning/tasks/<active_task>.md`:
```markdown
## <ISO timestamp>
- DONE: <what completed, 1 line>
- NEXT: <what's next, 1 line>
- BLOCKED: <only if blocked>
```
Update `summary` line in `.planning/CURRENT.md`.

## Task archive

Move task file to `.planning/tasks/done/<active_task>.md`. Reset `.planning/CURRENT.md` to `active_task: (none)`.

## RAG pagination

`knowledge-my-app_search_docs`: max 3 documents per query, max 500 lines per document.

## What NOT to do

- DO NOT skip Step 0 (THINK) or Step 0a (CLASSIFY).
- DO NOT start EXECUTE without CONFIRM.
- DO NOT skip `@TestKeeper MODE=EXECUTE` (step 5.3).
- DO NOT skip `@Reviewer` (step 5.4).
- DO NOT skip `@DoDGate` (step 5.9). CLOSE is gated on PASS.
- DO NOT delegate the EXECUTE loop to `superpowers:executing-plans`.
- DO NOT write code or tests — that's `@CodeWriter`.
- DO NOT fix bugs — that's `@BugFixer`.
- DO NOT dispatch `@CodeWriter` without a step description from the plan.
- DO NOT call `@Reviewer` directly as the first step.
- DO NOT skip `bug-retro` for CRITICAL/HIGH defects.
- DO NOT ignore anti-loop rules — at first loop symptom, STOP.
- DO NOT create separate vault files for requirements, spec, corner cases, test plan (v5 uses feature.md).
- DO NOT output system tags or environment artifacts.
- DO NOT add conversational filler.
