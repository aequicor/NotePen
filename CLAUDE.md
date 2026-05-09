# NotePen — kit constitution

## routing

# Routing table

For this kind of task, look here first:

| Task | Where |
|---|---|
| Unsure which command fits, or want a clean prompt before starting | `/kit-prepare "<rough idea>"` — interview-driven; emits a ready-to-paste block + recommends the right `/kit-*` |
| Add a new feature | `/kit-new-feature` → spec at `vault/specs/features/<module>/<feature>/spec.md` |
| Fix a bug | `/kit-fix` → scan `vault/specs/features/*/test-cases.md` for FAIL rows |
| Refactor / cleanup | `/kit-techdebt` → check `vault/specs/tech-debt/<module>/` first |
| Architecture decision | search `.planning/DECISIONS.md` |
| Subsystem behaviour | `vault/specs/subsystems/<name>.md` |
| Resume interrupted work | `/kit-resume` (full task context) or `/kit-step-resume` (focused per-step bundle after /clear) |
| Refresh project map | `/kit-map --refresh` writes `.planning/REPO_MAP.md` |
| Run autonomously | `/kit-sleep "<feature>"` — see MORNING_REPORT.md on wake-up |

## At 5.6 CHECKPOINT (per step)

After @CodeWriter + @Verifier MODE=EXECUTE/REVIEW pass on a step, the user has a 3-way fork:

- `/kit-approve` — proceed to next step (or CLOSE after last step).
- `/kit-defect <description> --origin=<value>` — re-open this step with a user-found defect.
- `/kit-revert-step` — undo this step entirely (non-destructive `git revert`).

Ground-truth artefact may be required at this gate. Attach via `/kit-attach <path>` or override with `/kit-approve --no-ground-truth`.

## At 5.10 DIFF-REVIEW (before CLOSE)

- `/kit-approve` — proceed to CLOSE.
- `/kit-revert <file>` — revert one file and re-run the affected step.
- `/kit-rework <reason>` — re-open EXECUTE with new direction.

## Other commands

- `/kit-status` — open tasks + rolling gate signal_ratio (deprecation candidates highlighted).
- `/kit-lint` — run project linter, propose targeted fixes.
- `/kit-review <scope>` — read-only review of staged/unstaged/file diff.
- `/kit-mutate` — run mutation-sample ad-hoc on the current step's CHANGED_FILES.
- `/kit-config "<plain-language change>"` — edit the manifest in place + re-render.
- `/kit-extend <url-or-path>` — register a new dialect / adapter / skill / agent package.
- `/kit-update` — re-run `kit-setup generate` against the current manifest.
- `/kit-uninstall` — remove all kit-managed files (with confirmation).

If a question can be answered by reading ONE file, name the file and stop.
If it needs multiple files, name them in priority order.
Do not dump file contents into the conversation; reference paths.

## conventions

# Conventions (always-loaded)

## Code

- Naming: kebab-case file names, camelCase identifiers, PascalCase types.
- Error handling: never silent-swallow; either rethrow with context or log+continue with explicit reason.
- No `console.log` in production code — use the project logger.
- Tests live alongside code: `foo.ts` + `foo.test.ts` in the same directory.

## Documentation

- Every exported function has a JSDoc block.
- Every module has a `README.md` summarising its public API.

## Git

- Commit messages: `<type>: <slug> — <one-line summary>` where `<type>` ∈ {feat, fix, refactor, test, docs, chore}.
- One commit per CodeWriter step (`policies.auto_commit_per_step: true`).

## retrieval_hooks

# Retrieval hooks (cold-tier access)

When a session needs deeper context than the constitution provides, use:

- `{{KNOWLEDGE.read("specs/subsystems/<name>")}}` — fetch a subsystem spec.
- `{{KNOWLEDGE.search("query")}}` — semantic search across `knowledge.specs`.
- `{{KNOWLEDGE.list("specs/features/<module>/")}}` — enumerate feature specs.

Do NOT load specs by default — only fetch what the current task needs. Each
fetched spec costs context budget. The slice-cap `max_tokens_per_step` from
the manifest applies to the assembled bundle.

## orchestration

# Orchestration protocols

## Hand-off contract

When agent A invokes agent B, A passes:
- The task slice (one step from plan.md, NOT the whole plan).
- The relevant spec section (NOT the whole spec).
- A's runbook output (if A produced one).

B reads only what it was given. If B needs more, B asks via `{{KNOWLEDGE.read(...)}}`.

## Risk-based lanes

Every task is classified at intake as `risk: trivial | standard | critical`.

- **Trivial** — ≤1 file, ≤30 lines, no new public symbols. Short pipeline: @CodeWriter → @Verifier MODE=REVIEW (Pass A only) → ground-truth → commit. No @Architect, no DoD, no Trace, no 5.10 diff-review.
- **Standard** — full pipeline.
- **Critical** — standard + adversarial 2nd pass on every step + mutation-sample backend artefact (≥3 mutants killed) + sleep mode forbidden + diff-review never auto-approved.

## Gates

- **auto** — proceed without user input. Renderer warns if `policies.auto_approve.<class>: false` overrides this.
- **approve** — pause; wait for user `/kit-approve` or `/kit-defect <reason>`.
- **diff-review** — pause; show the diff of all step commits, wait for user.
- **ground-truth** — pause; user attaches one of (screenshot | contract-test pass | command-output diff | mutation-sample pass | refactor diff-stat). Auto-invoked for backend via @Verifier MODE=MUTATION-SAMPLE.

## Failure handling

- `on_fail: retry` — invoke same agent again with the failure as input. Bound by `max_retries`. Sleep mode doubles the budget.
- `on_fail: rollback` — revert to last green commit, mark the task BLOCKED.
- `on_fail: abort` — stop the workflow, leave artifacts in place.
- `on_fail: next` — proceed to next step (rare; only for advisory checks).

## Replan-on-discovery

When @Verifier or @CodeWriter discovers a structural gap (spec wrong, EC missed, dependency unforeseen) mid-EXECUTE, @Main may invoke the `replan-on-discovery` skill instead of escalating. Hard cap: max 2 replan events per feature, ≤ 3 new steps per event. Replan never modifies spec.md (frozen at CONFIRM).

## Sleep mode

Per-task autonomous mode (`mode: sleep` in `.planning/CURRENT.md`). Auto-approves all CONFIRM/diff-review/replan gates, doubles retry budgets, downgrades runbook BLOCK to WARNING, on unrecoverable failure runs BLOCKED-shutdown (writes `.planning/MORNING_REPORT.md`). Refused for critical-lane tasks.

## Telemetry

Every gate verdict appends a row to `evals/runs/<kit_version>/gates.csv` (opt-in by directory presence) via the `gate-telemetry` skill. At task CLOSE, `eval-collector` aggregates per-task signal_ratio. `/kit-status` shows rolling cross-task ratios; gates with signal_ratio < threshold AND zero defect_origin matches are flagged as deprecation candidates.

User-reported defects via `/kit-defect <desc> --origin=<value>` append to `evals/runs/<kit_version>/defects.csv` for cross-reference. Origin values: spec | code | review | test | ui | trace | scope | unknown.

## forbidden_patterns

- Hardcoded secrets / API keys в коде (используйте переменные окружения)
- SQL string concatenation с user input (используйте parameterized queries)
- Логирование чувствительных данных (passwords, tokens, PII)
- TODO/FIXME в production-коде без tracking-записи (issue или DECISIONS.md)
- Disabled / закомментированные тесты без объяснения
- Catch Throwable/Exception generically с молчаливым проглатыванием
- @SuppressWarnings или @Suppress без issue-id или ссылки на DECISIONS.md в том же комментарии
- // @ts-ignore или // @ts-expect-error без issue-id (используйте rule-specific форму)
- # noqa или # type: ignore без rule-кода И одно-строчного reason
- // eslint-disable (file-level) — используйте line-level форму с rule-name и reason
- git commit --no-verify в скриптах, хуках или Makefile-целях
- // @SuppressLint, // ktlint-disable, // detekt:suppress без issue-id
- Оператор !! (используйте requireNotNull/checkNotNull с message)
- GlobalScope.launch (всегда используйте scoped coroutine)
- Thread.sleep в suspend-коде (используйте delay())
- Пустой catch-блок
- Bare Exception/Throwable catch (ловите конкретные типы)
- lateinit вне DI-контейнеров, фрагментов и тестов
- runBlocking вне main и тестов
- Recomposition-unsafe state read внутри @Composable (читайте mutableStateOf через .value или by remember)
- Side-effect в @Composable теле без LaunchedEffect / DisposableEffect
- Захардкоженные размеры в dp без ссылки на токены дизайн-системы
- Platform-specific API в commonMain (выносите в expect/actual или платформенный sourceSet)
- Blocking I/O на Compose UI dispatcher (используйте Dispatchers.IO через withContext)
- Хранение Android Context в commonMain (риск утечки — используйте платформенный sourceSet)
- Класс с >1 причиной для изменения (god class, делает persistence + business logic + formatting одновременно)
- Метод длиннее 30 строк, смешивающий уровни абстракции (orchestration + low-level detail)
- Repository класс, содержащий business rules или validation логику
- Use case / interactor с >1 публичной business-операции
- switch/when по type tags или string-дискриминаторам вместо полиморфизма
- Feature flag внутри domain logic вместо инъекции strategy/decorator
- Захардкоженный выбор алгоритма внутри класса, который должен делегировать strategy
- Subclass, бросающий UnsupportedOperationException / NotImplementedError для унаследованных методов
- Subclass, ослабляющий preconditions или усиливающий postconditions родительского контракта
- Type-checking через instanceof/is внутри метода, принимающего базовый тип
- Интерфейс с >5–7 методами, которые клиенты реализуют лишь частично (fat interface)
- Передача full service/repository интерфейса в потребителя, использующего лишь 1 метод
- Marker-метод, реализованный как no-op, потому что интерфейс forced его
- Concrete класс инстанцируется через 'new' / constructor внутри business logic (используйте DI / factory)
- Domain или use-case импортирует из infrastructure layer (DB, HTTP, filesystem)
- Static / global доступ к shared mutable state из domain logic (singletons как hidden dependencies)
- Тест, который не запускается без реальной БД/сети, потому что зависимость не была инвертирована
- Дублирование business-логики в 2+ use cases вместо извлечения shared domain service
- Abstract base class или интерфейс созданы спекулятивно с 1 реализацией без планируемого расширения
- Over-engineered абстракция для одноразовой операции (factory-of-factories для фиксированного flow)
- Mutable публичное поле на domain entity или value object (используйте val / readonly)
- Метод, мутирующий свой аргумент вместо возврата нового значения (неожиданный side-effect)
- Shared mutable state, доступ без синхронизации в concurrent контексте
- Длинная цепочка a.b().c().doSomething() ≥3 уровней — нарушает LoD
- Caller извлекает данные из объекта и принимает решения вместо того, чтобы попросить объект действовать
- Глубокая иерархия наследования (3+ уровня) ради переиспользования — предпочитайте композицию или делегирование
- Наследование от concrete класса исключительно ради переиспользования реализации
- Inner-layer модуль импортирует из outer-layer модуля (domain → infrastructure, use-case → controller, entity → web/HTTP) — нарушает dependency rule
- Domain или use-case package ссылается на framework-типы по имени (Spring, Ktor, Compose, React, Express, Django, Rails, Flask) — inner layers framework-agnostic
- Use-case (interactor) импортирует concrete repository implementation, HTTP client, ORM session или filesystem API — зависьте от port-интерфейса
- Cross-cutting обращение из domain класса к logger/metrics/tracing — инжектьте domain-side абстракцию
- Domain entity с ORM/serialization/DI аннотациями (@Entity, @Table, @Column, @JsonProperty, @Inject, @Component) — entities не должны зависеть от persistence/framework
- Domain entity наследуется от framework base class (BaseEntity from JPA/Hibernate/Room, AggregateRoot, ActiveRecord) — предпочитайте композицию
- Анемичная entity — data class только с getters/setters и без поведения, вся логика вынесена в Service/Manager
- Domain entity с public-мутаторами, позволяющими нарушить инвариант извне (entity.setBalance(-100)) — инкапсулируйте через behavior-методы
- Use-case с >1 публичной business-операцией (executeA, executeB, executeC) — разбейте на отдельные классы
- Use-case вызывает другой use-case напрямую — orchestrating use cases живёт уровнем выше
- Use-case orchestrates cross-cutting concerns inline (transaction, retry, cache, audit) — выносите в декораторы / middleware / композиционный root
- Use-case возвращает domain entity напрямую в controller / presenter — переводите через output port DTO
- Use-case принимает framework request тип на вход (HttpRequest, ResponseEntity, NextRequest) — определите свой input data structure
- Repository / gateway интерфейс объявлен в infrastructure / persistence layer — порты живут с use-case (или domain), реализация — в infrastructure
- Use-case импортирует concrete adapter (HttpClientImpl, JpaUserRepository, S3FileStore) вместо port-интерфейса
- Port-интерфейс exposes framework-specific типы (ResultSet, ResponseEntity, OkHttp Response, java.sql.Connection) — порты в domain-типах
- Два adapter-а с одним портом, отличающиеся только serialization framework — collapse через единую абстракцию
- Domain entity пересекает границу use-case → presentation (controller сериализует entity напрямую) — конвертируйте в boundary DTO
- Persistence model используется как domain entity (JPA @Entity, Room @Entity) и виден из use-cases — храните persistence отдельно и маппите
- Web request/response DTO (HttpRequest body, OpenAPI-generated model) утекает в use-case — определите свой request/response shape
- Один класс играет и роль entity, и API/JSON DTO (ORM + serialization metadata) — split по слоям
- Ручной new ConcreteRepository(...) внутри use-case, controller или domain — стройте в composition root, инжектьте
- Service-locator (Container.resolve, ServiceLocator.get, ApplicationContext.getBean) внутри use-case или domain
- DI annotation-сканирование domain/use-case package и binding implementations там — wiring живёт в startup, не в business code
- Top-level package по техническим concerns (controllers/, services/, repositories/, models/, dao/) вместо business capabilities (billing/, onboarding/, inventory/)
- Один shared service/ или util/ для unrelated logic из разных bounded contexts — split по features
- Use-case по CRUD-глаголу на storage-форму (UpdateUserRowUseCase, InsertOrderTableUseCase) вместо business-операции (PromoteUserToAdmin, PlaceOrder)
- Use-case unit test требует реальную БД, HTTP server, message broker или filesystem — use-cases тестируются через порты с in-memory adapters
- Domain entity test boots framework runtime (Spring context, Ktor server, Compose runtime, Rails environment) — entities testable plain
- Use-case test mocks the use-case under test вместо подмены зависимостей через порты

## typescript-strict

TypeScript strictness rules for this repo.

- `strict: true` in `tsconfig.json` is non-negotiable.
- No `any` without a one-line justification comment immediately above.
- No `as` casts without a one-line justification comment.
- Prefer `unknown` over `any` when the type is genuinely unknown at boundary.
- Use `satisfies` over `as` for literal type narrowing.
- All `// @ts-ignore` and `// @ts-expect-error` must reference an issue id.
