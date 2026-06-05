# Completed checkpoint: editor state/tool settings persistence

Date: 2026-06-05

## Goal

Editor state persistence is completed for the checkpoint scope. Original issue:
after closing the app or leaving a document, editor state was not restored
correctly; high zoom caused the page to reopen shifted. Follow-up requirement:
tool settings, including the active tool, must also be saved.

## Main changed areas

- `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DetailsContent.kt`
- `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/EditorPanel.kt`
- `app/byCompose/common/src/jvmAndroidMain/kotlin/ru/kyamshanov/notepen/AnnotationRepositoryJvmAndroid.kt`
- `app/byCompose/common/src/jvmTest/kotlin/ru/kyamshanov/notepen/AnnotationRepositoryJvmTest.kt`
- `drawing/api/src/commonMain/kotlin/ru/kyamshanov/notepen/annotation/domain/model/AnnotationBundle.kt`
- `drawing/api/src/commonMain/kotlin/ru/kyamshanov/notepen/annotation/domain/model/AnnotationViewState.kt`
- `drawing/api/src/commonMain/kotlin/ru/kyamshanov/notepen/annotation/domain/port/AnnotationRepository.kt`
- `sync/src/commonMain/kotlin/ru/kyamshanov/notepen/sync/domain/HostAnnotationProjection.kt`
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

### Tool settings

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
- Added `AnnotationBundle.toolMode` and `AnnotationDataDto.toolMode`; legacy JSON defaults to `NONE`.
- `EditorPanel` restores `toolMode` together with pen/marker/eraser settings.
- `EditorPanel` autosave now uses `preserveToolSettings = true`, so panel-level content saves do not
  overwrite per-document tool settings owned by `DetailsContent`.

### Concurrency

- `AnnotationRepositoryJvmAndroid` serializes writes per annotation sidecar with a `Mutex`.
- The same lock covers heavy annotation save and `.view` save, preventing races around the shared `.tmp`
  file and around read-preserve-write of `.view` state.

## Verification

- `./gradlew :app:byCompose:common:compileKotlinJvm`
- `./gradlew :app:byCompose:android:compileDebugKotlin`
- `./gradlew :app:byCompose:common:jvmTest --tests "ru.kyamshanov.notepen.AnnotationRepositoryJvmTest" --tests "ru.kyamshanov.notepen.session.SessionSerializationTest"`
- `./gradlew :rendering:impl:jvmTest --tests "ru.kyamshanov.notepen.pdfviewer.PdfViewerStateRestoreTest"`
- `./gradlew :sync:jvmTest --tests "ru.kyamshanov.notepen.sync.domain.HostAnnotationProjectionNoteSnapshotTest"`
- `./gradlew :app:byCompose:common:ktlintCheck :drawing:api:ktlintCheck :rendering:impl:ktlintCheck :sync:ktlintCheck`
- `git diff --check`
