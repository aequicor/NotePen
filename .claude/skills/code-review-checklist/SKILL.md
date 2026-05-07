---
name: code-review-checklist
description: Pre-commit code review checklist filled by @CodeReviewer after each CodeWriter cycle. Use ONLY when reviewing code changes before commit — not for design reviews, not for requirements analysis.
---

# Code Review Checklist

@CodeReviewer fills this checklist after each @CodeWriter cycle. Walk through every item. Mark each `[ ]` as `[x]` when verified. If any item fails — describe the issue, reference the file:line, and return to @CodeWriter.

## Invocation

Called by @CodeReviewer automatically after each @CodeWriter stage completion. No manual trigger needed.

## Input

| Field | Required | Description |
|-------|----------|-------------|
| `stage_file` | Yes | Path to the stage file @CodeWriter implemented |
| `files_changed` | Yes | List of changed files (diff summary) |
| `corner_cases` | No | Path to corner case register (if available) |

## Functionality

- [ ] All acceptance criteria from the spec are addressed
- [ ] Edge cases are handled (null, empty, boundary values)
- [ ] Corner case register reviewed — every Critical item has a test
- [ ] Corner case register reviewed — every High item has an explicit decision (test or defer)
- [ ] Error states are handled with appropriate messages
- [ ] No regressions — existing functionality continues to work

## Code Quality

- [ ] Functions are small and single-purpose
- [ ] No magic numbers — use named constants
- [ ] Meaningful variable and function names
- [ ] No dead code, commented-out blocks, or unused imports
- [ ] Consistent formatting (auto-formatter applied)
- [ ] No patterns from forbidden list: `- !! operator (use requireNotNull/checkNotNull with message)
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
- Use-case logging at INFO/DEBUG level using a framework-specific logger (SLF4J, log4j, console) imported directly — log through a port abstraction so the use case stays infrastructure-free`

## Stub & Deferral Check (CRITICAL — blocking)

Regex scan over every changeset file: `TODO|FIXME|XXX|HACK|stage [0-9]|later|TBD`.

- [ ] No unpaired `TODO`, `FIXME`, `XXX`, `HACK`, "stage N", "later", or "TBD" in production code added/modified by this stage
- [ ] Every such marker (if any) is paired with a `.planning/DECISIONS.md` reference, an external tracker ID (`#123`, `JIRA-456`, `TC-NN`), or an explicit deferral entry already in the stage file
- [ ] No method body for an AC the stage was supposed to deliver is comment-only (`{ // TODO: implement… }`)
- [ ] No method body is silently noop (`fun foo() { /* ... */ }`) where the spec required behavior

Any unchecked box → CRITICAL issue, return to @CodeWriter (or escalate to @Main as BLOCKED). This rule fires on **in-changeset hits only** — pre-existing markers in untouched files belong to tech-debt, not the review.

## Architecture

- [ ] No circular dependencies between modules
- [ ] Correct layering: domain → service → controller/presentation
- [ ] Dependency injection used where appropriate
- [ ] Interfaces defined at module boundaries

## Testing

- [ ] Unit tests cover happy path + error cases
- [ ] Integration tests cover API contracts
- [ ] Tests are deterministic (no Thread.sleep, real network calls)
- [ ] All tests pass: `./gradlew :[module]:test`

## Security

- [ ] Input validation on all external inputs
- [ ] SQL via parameterized queries only
- [ ] No tokens, passwords, or PII in logs
- [ ] Authentication/authorization checks not weakened
- [ ] No sensitive data in error responses

## Performance

- [ ] No N+1 queries in loops
- [ ] Appropriate caching strategy
- [ ] Resources closed properly (connections, files, streams)
- [ ] No blocking calls on UI/main threads

## Documentation

- [ ] Public API documented
- [ ] Complex logic has inline comments explaining WHY, not WHAT
- [ ] Spec/requirements match implementation
- [ ] Any new guidelines saved to `vault/guidelines/[module]/`

## Output

Return a verdict:

```
VERDICT: [PASS | CRITICAL | HIGH | MEDIUM]
FILES_REVIEWED: [list]
ISSUES:
- [severity] [file:line] [description] → [fix suggestion]

CHANGES_SUMMARY: [1-2 sentences]
```

**PASS** → proceed to next stage.  
**CRITICAL/HIGH** → return to @CodeWriter with issues.  
**MEDIUM** → document and proceed (fix later).

## Error Handling

- If stage file is missing or empty → report `VERDICT: CRITICAL — stage file not found at [path]`. Do not proceed.
- If diff is empty (no code changes) → report `VERDICT: CRITICAL — @CodeWriter produced no changes for [stage]`. Do not proceed.
- If corner case register references a Critical item with no corresponding test → flag as HIGH issue, do not auto-approve.