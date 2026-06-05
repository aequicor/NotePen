# Checkpoint: editor state/tool settings persistence

Date: 2026-06-05

## Goal

Continue fixing editor state persistence. Original issue: after closing the app or
leaving a document, editor state is not restored correctly; high zoom caused the
page to reopen shifted. Follow-up requirement: tool settings must also be saved.

## Current worktree state

There are uncommitted changes. Do not reset them.

Main changed areas:

- `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DetailsContent.kt`
- `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/EditorPanel.kt`
- `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/session/CaptureSession.kt`
- `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/tabs/TabViewState.kt`
- `app/byCompose/common/src/jvmAndroidMain/kotlin/ru/kyamshanov/notepen/AnnotationRepositoryJvmAndroid.kt`
- `drawing/api/src/commonMain/kotlin/ru/kyamshanov/notepen/annotation/domain/model/AnnotationViewState.kt`
- `rendering/impl/src/*/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfViewerState.*.kt`
- `rendering/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfPagesViewer.kt`
- tests under `app/byCompose/common/src/*Test/...` and `rendering/impl/src/jvmTest/...`
- `vault/kd/common/editor-view-state-restore.md`

## Already implemented

### View position restore

- Added `panXPx` to `AnnotationViewState` and its persisted DTO.
- Added `panXPx` to `TabViewState` and `TabSession.captureSession()`.
- Extended `PdfViewerState.applyInitialState(..., panXPx)` in common expect and JVM/Android actuals.
- `PdfViewerState` saver now includes `pan.x`.
- Added JVM regression test:
  `rendering/impl/src/jvmTest/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfViewerStateRestoreTest.kt`.

### Final flush on close

- `DetailsContent` now final-flushes session autosave in `DisposableEffect`.
- `EditorPanel` now final-flushes `.view` sidecar in `DisposableEffect`, guarded by ready viewer layout:
  `pages.isNotEmpty() && pdfViewerState.basePageWidthPx > 0f`.

### Tool settings work in progress

Current direction in `DetailsContent.kt`:

- Added `currentToolSnapshot()` to capture `toolMode`, `penSettings`, `markerSettings`,
  `eraserSettings`, `markerWidthPinned`.
- Added `checkpointFocusedToolState()` to write the focused document's current tool settings into
  `documentToolStates[filePath]`.
- `saveTab()` now reads tool settings from `documentToolStates[state.filePath] ?: currentToolSnapshot()`
  instead of blindly using the current global tool settings for every tab.
- `saveAllOpenTabs()` checkpoints the focused tool settings before saving all tabs.
- `saveTab()` now passes `highlights` and `notes` too, because annotation bundle save rewrites the
  full JSON and omitting them would wipe saved highlights/notes.
- Added a second `DisposableEffect` in `DetailsContent` that calls `saveAllOpenTabs()` and
  `component.saveLastPageIndex(...)` using `rememberUpdatedState` wrappers.

## Important unresolved risk

Before continuing, inspect `AnnotationRepositoryJvmAndroid.save()` and its `writeAtomically(...)`
implementation for concurrency/locking. I was interrupted while checking whether the new final
save can race with existing background saves.

Risk:

- `onBackWithSave` already launches background `saveAllOpenTabs()`.
- The new `DisposableEffect` also calls `saveAllOpenTabs()` synchronously with `runBlocking(NonCancellable)`.
- If repository writes to the same JSON sidecar concurrently without a per-file lock, two atomic writes
  can race. Last-writer-wins is acceptable only if both saves contain complete, fresh data. It may still
  be better to avoid duplicate saves by making the final dispose save the authoritative path and simplifying
  `onBackWithSave`, or by ensuring repository writes are serialized.

## Verification already run

These passed before/after the current tool-settings change:

- `./gradlew.bat :app:byCompose:common:compileKotlinJvm`
- `./gradlew.bat :app:byCompose:android:compileDebugKotlin`
- `./gradlew.bat :app:byCompose:common:jvmTest --tests "ru.kyamshanov.notepen.AnnotationRepositoryJvmTest"`
- `./gradlew.bat :app:byCompose:common:ktlintCheck`

Earlier, before the follow-up tool-settings edits, these also passed:

- `./gradlew.bat :rendering:impl:jvmTest --tests "ru.kyamshanov.notepen.pdfviewer.PdfViewerStateRestoreTest"`
- `./gradlew.bat :app:byCompose:common:jvmTest --tests "ru.kyamshanov.notepen.AnnotationRepositoryJvmTest" --tests "ru.kyamshanov.notepen.session.SessionSerializationTest"`
- `./gradlew.bat :drawing:api:ktlintCheck :rendering:impl:ktlintCheck :app:byCompose:common:ktlintCheck`
- `git diff --check`

After any further edits, rerun at least:

- `./gradlew.bat :app:byCompose:common:compileKotlinJvm`
- `./gradlew.bat :app:byCompose:android:compileDebugKotlin`
- `./gradlew.bat :app:byCompose:common:jvmTest --tests "ru.kyamshanov.notepen.AnnotationRepositoryJvmTest" --tests "ru.kyamshanov.notepen.session.SessionSerializationTest"`
- `./gradlew.bat :rendering:impl:jvmTest --tests "ru.kyamshanov.notepen.pdfviewer.PdfViewerStateRestoreTest"`
- `./gradlew.bat :app:byCompose:common:ktlintCheck :drawing:api:ktlintCheck :rendering:impl:ktlintCheck`
- `git diff --check`

## Next steps

1. Inspect repository atomic write/concurrency behavior.
2. Decide whether duplicate final saves are safe or simplify shutdown save flow.
3. Consider whether `toolMode` itself should be persisted to disk. Current repository persists
   `pen/marker/eraser` settings, but not the active tool mode. The in-memory `documentToolStates`
   preserves `toolMode` only during the same editor lifetime.
4. Add/adjust tests if adding disk persistence for active tool mode.
5. Re-run checks listed above.

