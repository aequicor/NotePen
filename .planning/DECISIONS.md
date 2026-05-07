# DECISIONS.md — NotePen

> Architectural Decision Records (ADR). Each decision is numbered and dated.
> Reference this file before making architectural choices.

---

## Template

```markdown
## ADR-NNN: <title>

**Status:** Proposed | Accepted | Deprecated | Superseded by ADR-NNN
**Date:** YYYY-MM-DD

### Context
Why this decision was needed.

### Decision
What was decided.

### Alternatives considered
What else was considered and why rejected.

### Consequences
Trade-offs and implications.
```

---

## ADR-001: Use DECISIONS log

**Status:** Accepted
**Date:** (fill in project start date)

### Context
Architectural decisions made without documentation are rediscovered expensively.
Agents and team members need a reliable source of "why we did it this way".

### Decision
All architectural decisions — library choices, patterns, key trade-offs — are documented here in ADR format.
Agents must check this file at startup and before making architectural recommendations.
Any TODO/FIXME left in code must have a corresponding entry here.

### Alternatives considered
- Inline code comments: too scattered, not searchable, lost in refactors.
- External wiki: not co-located with code, agents can't read it directly.

### Consequences
+: Decisions survive team changes and session resets.
+: Agents can avoid re-litigating settled decisions.
-: Requires discipline to actually write entries.

---

## ADR-002: Port placement — ThumbnailRepository, PdfThumbnailGenerator, FileAvailabilityChecker in :shared

**Date:** 2026-05-07
**Status:** Approved

### Context
Spec says ThumbnailRepository, PdfThumbnailGenerator, FileAvailabilityChecker are declared in :common. However :shared use cases (CheckAvailabilityUseCase, OpenRecentFileUseCase) need FileAvailabilityChecker, and :shared doesn't depend on :common.

### Decision
All three ports remain in :shared (alongside FileHistoryRepository and FolderRepository) for consistent dependency direction. :common implements the actual classes.

### Alternatives considered
Moving ports to :common would require :shared to depend on :common — inverting the dependency chain and creating a circular dependency.

### Consequences
+: ThumbnailRepository and PdfThumbnailGenerator are accessible to both ViewModel (:common) and any future use cases in :shared.
+: No circular dependencies.
-: Port declarations are in :shared, not :common as originally noted in spec.

---

## ADR-003: Dispatchers.IO в commonMain для OpenRecentFileUseCase

**Date:** 2026-05-07
**Status:** Approved (с оговоркой)

### Context
OpenRecentFileUseCase вызывает checkSync (блокирующий IO) через withContext. Dispatchers.IO технически JVM/Android-specific, но проект таргетит только JVM и Android (no native/JS targets).

### Decision
Dispatcher инжектируется как конструкторный параметр (ioDispatcher: CoroutineDispatcher = Dispatchers.IO). Это позволяет подставить UnconfinedTestDispatcher в тестах и остаётся совместимым с commonMain без реального платформо-специфичного кода в src.

### Alternatives considered
Hardcoded Dispatchers.IO: не тестируемо; expect/actual для dispatcher — избыточно для текущего таргета.

### Consequences
+: Тесты могут подставить UnconfinedTestDispatcher().
+: В продакшне по умолчанию используется Dispatchers.IO.
-: Конструктор имеет параметр по умолчанию, который технически упоминает IO-специфичный тип.

---

## ADR-005 — FolderCard onClick deferred

Folder navigation (open folder content screen) is not part of the main screen redesign scope.
`FolderCard.onClick` is a no-op in Stage 04; a dedicated folder-view feature will wire it.

---

## ADR-004: Broad Exception catch в CheckAvailabilityUseCase и OpenRecentFileUseCase

**Date:** 2026-05-07
**Status:** Approved

### Context
FileAvailabilityChecker.check() и checkSync() могут бросать платформо-специфичные исключения (IOException, SecurityException, ContentProviderException и др.) без их перечисления в контракте порта.

### Decision
Используем catch(e: Exception) с явным комментарием и логированием категории исключения (не URI, не данные пользователя). Это защитный boundary: crashed availability check должен порождать FILE_ERROR, а не крашить UI.

### Alternatives considered
Перечислить все исключения: невозможно в commonMain — платформо-специфичные типы недоступны.

### Consequences
+: UI не падает при неожиданных платформенных исключениях.
+: Логируем класс исключения для диагностики без утечки PII.
-: Потенциально скрывает неожиданные исключения — митигируется логированием.

---

## ADR-007 — ARCHIVED_UNAVAILABLE transition on eviction deferred

CC-24 requires transitioning evicted files to ARCHIVED_UNAVAILABLE in folder links.
Current architecture does not expose the evictedUri from FileHistoryRepository.upsert to callers.
The cross-session transition (eviction → archive) is handled by design: if a file appears in folder links
but not in history, it is treated as archived in the folder view. Real-time in-session transition
deferred to a future FolderView feature that will implement the archive concept fully.

---

## ADR-006 — FilePicker MainActivity wiring deferred

**Date:** 2026-05-07
**Status:** Accepted

### Context
`onOpenFilePicker` callback in `MainScreenComponent` triggers system file picker launch.
On Android, `ActivityResultLauncher` (via `rememberLauncherForActivityResult`) must be created inside a `@Composable` context. `MainActivity.onCreate` is not Composable, so wiring requires a dedicated Composable wrapper or passing the launcher reference before component creation.
On Desktop, `FilePicker.jvm.kt` uses `java.awt.FileDialog` which is triggered directly from `MainContent` in response to `NavigationTarget.FilePicker` state — the `onOpenFilePicker = {}` stub is intentionally empty because Desktop's file-picker flow is handled inside the Compose UI tree.

### Decision
`onOpenFilePicker = {}` remains a no-op stub in `MainActivity` and `main.kt` for Stage 07. Full Android wiring (ActivityResultLauncher setup in RootContent or a dedicated wrapper Composable) is deferred to a dedicated file-picker integration task.

### Alternatives considered
- Wire in `setContent` block: requires passing mutable launcher ref across the boundary — fragile.
- Wire in `RootContent`: cleanest approach, needs `rememberLauncherForActivityResult` which requires `androidx.activity.compose` in `:common`. Deferred pending dependency audit.

### Consequences
-: Android file picker does not open from the main screen until the integration task is done.
+: No broken abstraction or unsafe casts introduced as a workaround.
