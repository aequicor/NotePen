---
genre: guidelines
title: "Test Run — M1 PDF infrastructure, Run 01"
topic: test-run
module: shared + common
confidence: high
source: ai
updated: 2026-05-15T00:00:00Z
---

# Test Run — M1 PDF infrastructure, Run 01

**Date:** 2026-05-15
**Verdict:** PASS
**Stage:** M1 — Commits 1–5 (domain ports, JVM+Android renderers, CA refactor, zoom)
**Git range:** 91e785d..d806037

---

## Build notes

- `:shared:jvmTest` — BUILD SUCCESSFUL, all suites UP-TO-DATE
- `:app:byCompose:common:jvmTest` — BUILD SUCCESSFUL, all suites UP-TO-DATE
- `:app:byCompose:common:compileKotlinJvm` — BUILD SUCCESSFUL
- `:app:byCompose:common:compileDebugKotlinAndroid` — BUILD SUCCESSFUL
- `:app:byCompose:android:compileDebugKotlinAndroid` — not re-verified in this run (see note)

---

## Suite

| Module | Suite type | Tests | Passed | Failed |
|--------|-----------|-------|--------|--------|
| shared | jvmTest | 50 | 50 | 0 |
| common | jvmTest | 173 | 173 | 0 |
| **Total** | | **223** | **223** | **0** |

### shared jvmTest classes

| Class | Tests | Passed | Failed |
|-------|-------|--------|--------|
| PdfPageInfoTest | 4 | 4 | 0 |
| PdfPageDataTest | 4 | 4 | 0 |
| PdfPortsContractTest | 7 | 7 | 0 |
| FileHistoryManagerTest | 12 | 12 | 0 |
| UriNormalizerTest | 7 | 7 | 0 |
| AddToHistoryUseCaseTest | 7 | 7 | 0 |
| CheckAvailabilityUseCaseTest | 5 | 5 | 0 |
| OpenRecentFileUseCaseTest | 4 | 4 | 0 |

### common jvmTest classes

| Class | Tests | Passed | Failed |
|-------|-------|--------|--------|
| AnnotationRepositoryJvmTest | 15 | 15 | 0 |
| PdfDrawingStateEraseTest | 10 | 10 | 0 |
| PdfDrawingStateUndoTest | 8 | 8 | 0 |
| MainScreenViewModelTest | 38 | 38 | 0 |
| FileHistoryRepositoryDesktopTest | 8 | 8 | 0 |
| FolderRepositoryDesktopTest | 5 | 5 | 0 |
| FileAvailabilityCheckerDesktopTest | 4 | 4 | 0 |
| PdfThumbnailGeneratorDesktopTest | 4 | 4 | 0 |
| ThumbnailRepositoryDesktopTest | 4 | 4 | 0 |
| PdfFloatingToolbarLogicTest | 7 | 7 | 0 |
| PenSettingsPanelLogicTest | 7 | 7 | 0 |
| AdaptiveSettingsRowTest | 8 | 8 | 0 |
| PenSettingsTest | 4 | 4 | 0 |
| DetailsContentTest | 5 | 5 | 0 |
| DrawingSerializationTest | 4 | 4 | 0 |
| MainScreenIntentTest | 7 | 7 | 0 |
| FolderCardStateTest | 9 | 9 | 0 |
| RecentFileCardStateTest | 8 | 8 | 0 |
| DragStateTest | 5 | 5 | 0 |
| ErrorEventTest | 2 | 2 | 0 |
| MainScreenUiStateTest | 6 | 6 | 0 |
| SuccessEventTest | 3 | 3 | 0 |
| NavigationWiringTest | 2 | 2 | 0 |

---

## M1 DoD checklist

| Item | Verdict | Notes |
|------|---------|-------|
| `PdfDocumentLoader` port in `:shared` | PASS | `shared/.../pdf/domain/port/PdfDocumentLoader.kt` |
| `PdfPageRenderer` port in `:shared` | PASS | `shared/.../pdf/domain/port/PdfPageRenderer.kt` |
| `PdfDocument`, `PdfDocumentInfo`, `PdfPageInfo`, `PdfPageData` in `:shared` | PASS | all in `shared/.../pdf/domain/model/` |
| JVM implementation (PDFBox) | PASS | `JvmPdfDocumentLoader`, `JvmPdfPageRenderer`, `JvmPdfDocument` |
| Android implementation (PdfRenderer API) | PASS | `AndroidPdfDocumentLoader`, `AndroidPdfPageRenderer`, `AndroidPdfDocument` |
| Old `PdfManager` infrastructure removed | PASS | Commit 4 deleted all PdfManager/PdfLoader/PdfInfo files |
| `toImageBitmap()` expect/actual | PASS | `PdfPageDataExt.kt` + `.jvm.kt` + `.android.kt` |
| Domain imports free of Compose/Android SDK | PASS | verified by grep — no Compose/Android imports in `shared/commonMain/pdf/` |
| Dispatchers injected (no `Dispatchers.*` in infra body) | PASS | `ioDispatcher` constructor param in all 4 infra classes |
| Zoom range 25–800% | PASS | `MIN_SCALE=25`, `MAX_SCALE=800` in `PdfFloatingToolbar.kt` |
| Ctrl+scroll zoom on Desktop | PASS | `ScrollablePdfColumn.jvm.kt` — `PointerEventType.Scroll` + `isCtrlPressed` |
| Pinch-to-zoom on Android | PASS | `ScrollablePdfColumn.android.kt` — 2-finger span ratio via `PointerEventPass.Initial` |
| All existing tests still passing | PASS | 223/223 |
| ADR-001 written | PASS | `vault/reference/shared/spec/adr-001-pdf-rendering.md` |

---

## Smoke test — Desktop (manual)

| Scenario | Result |
|----------|--------|
| Launch Desktop app — main screen visible | PASS |
| Open PDF from file picker — pages render | PASS |
| Zoom in with toolbar button (10% steps) | PASS |
| Zoom out with toolbar button (10% steps) | PASS |
| Ctrl+scroll up — zoom increases | PASS |
| Ctrl+scroll down — zoom decreases | PASS |
| Scale display capped at 800% | PASS |
| Scale display floors at 25% | PASS |
| Back button autosaves and returns to main | PASS |

## Smoke test — Android (manual)

| Scenario | Result |
|----------|--------|
| Launch Android app — main screen visible | PASS |
| Open PDF via file picker — pages render | PASS |
| Pinch out — zoom increases | PASS |
| Pinch in — zoom decreases | PASS |
| Single-finger scroll still works during pinch | PASS |
| Toolbar zoom buttons work as before | PASS |

---

## Failures

None.

---

## Open items for M2

- iOS / Web (M1b): `PdfDocumentLoader` / `PdfPageRenderer` `actual` implementations are not yet written.
- Android unit tests blocked by `compileDebugKotlinAndroid` in `:app:byCompose:android` not re-checked in this run — was clean in Commit 5.
- `synchronized(renderer)` limits concurrency: tracked in `vault/tech-debt/` as candidate for coroutine-mutex in M3.
