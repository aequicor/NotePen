---
id: TD-common-folder-repo-no-history-check
module: common
category: smell
severity: medium
status: open
discovered: 2026-05-07
discovered_by: CodeWriter
task: feat-drag-drop-pdf
---

# TD-common-folder-repo-no-history-check

## Problem

`FolderRepositoryDesktop.addFile` does not throw `FileNotInHistoryException` even though the port interface `FolderRepository.addFile` explicitly declares this as a contract requirement for URIs absent from `FileHistoryRepository` (AC-37). The Desktop implementation only checks `FolderNotFoundException` and `FileDuplicateInFolderException`, silently creating a `FolderFileLink` for a URI that has no corresponding `RecentFile`. This violates the Liskov Substitution Principle: callers depending on the port contract cannot rely on the Desktop implementation honouring it. The check is currently compensated at the ViewModel level via state inspection (drag only starts from visible `RecentFileUiModel` entries), but the repository boundary remains unsecured.

## Location

| File | Lines | Notes |
|------|-------|-------|
| `app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/mainscreen/infrastructure/FolderRepositoryDesktop.kt` | 61–68 | `addFile` — no `FileHistoryRepository` lookup before writing `FolderFileLink` |
| `shared/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/domain/port/FolderRepository.kt` | 43–44 | KDoc declares `@throws FileNotInHistoryException` |

## Deferral rationale

`FolderRepositoryDesktop` currently does not receive `FileHistoryRepository` as a constructor dependency. Adding it requires changing the constructor, the composition-root wiring in `main.kt`, and the test fixture in `FolderRepositoryDesktopTest`. This is a multi-file change that expands scope beyond the DoDGate fix. The ViewModel layer provides effective compensation at runtime; no data corruption or security exposure results from the gap.

## Suggested fix

1. Add `private val historyRepository: FileHistoryRepository` to the `FolderRepositoryDesktop` constructor.
2. In `addFile`, inside `withLockedIO`, call `historyRepository.getAll()` and check whether `uri` is present. If not, throw `FileNotInHistoryException(uri)`.
3. Update `FolderRepositoryDesktopTest` to supply a stub `FileHistoryRepository` and add integration test TC-17 (currently SKIP).
4. Update `main.kt` wiring: pass `historyRepo` to `FolderRepositoryDesktop(...)`.

## References

- Originating task: feat-drag-drop-pdf
- Related feature: `vault/features/common/drag-drop-pdf/feature.md`
- TC-17 skipped: `vault/features/common/drag-drop-pdf/test-cases.md`
- Port contract: `shared/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/domain/port/FolderRepository.kt` § `addFile`
