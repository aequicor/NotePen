# Test Run — Main Screen Redesign, Run 02
**Date:** 2026-05-07 12:10
**Verdict:** ALL_GREEN
**Stage:** full feature (all stages complete)
**Changed files:** shared/src/commonTest, shared/src/jvmMain, app/byCompose/common/src/commonTest, app/byCompose/common/src/jvmTest

---

## Build

| Module | Result |
|--------|--------|
| `:shared` | PASS |
| `:app:byCompose:common` | PASS |

Build warnings (non-fatal):
- `DefaultRootComponent.kt`: opt-in required for `@DelicateDecomposeApi` — compiler warning only, build succeeded.
- `FileHistoryRepositoryDesktopTest.kt`, `FolderRepositoryDesktopTest.kt`, `ThumbnailRepositoryDesktopTest.kt`: deprecated `createTempDir` — compiler warning only, build succeeded.

---

## Suite

| Target | Passed | Failed | Skipped | Runtime |
|--------|--------|--------|---------|---------|
| `:shared:jvmTest` | 35 | 0 | 0 | ~17 s |
| `:app:byCompose:common:jvmTest` | 42 | 0 | 0 | ~7 s |
| **Total** | **77** | **0** | **0** | ~24 s |

Integration: NOT CONFIGURED (no detectable integration test target separate from jvmTest).

---

## In-scope TC mapping

| TC-id | Verdict | Test file | Notes |
|-------|---------|-----------|-------|
| TC-01 | PASS | app/byCompose/common/src/commonTest/.../MainScreenViewModelTest.kt#screenVisible_isLoadingFalseAfterLoad | — |
| TC-02 | PASS | app/byCompose/common/src/jvmTest/.../FileHistoryRepositoryDesktopTest.kt | — |
| TC-07 | PASS | shared/src/commonTest/.../AddToHistoryUseCaseTest.kt#existingUri_returnsMoved_upsertCalledWithLastPageIndex | — |
| TC-08 | PASS | shared/src/commonTest/.../AddToHistoryUseCaseTest.kt#safFuzzyMatch_sameName_sameSize_differentUri_returnsDetected | — |
| TC-10 | PASS | app/byCompose/common/src/commonTest/.../MainScreenViewModelTest.kt | — |
| TC-11 | PASS | app/byCompose/common/src/commonTest/.../MainScreenViewModelTest.kt | — |
| TC-12 | PASS | app/byCompose/common/src/commonTest/.../MainScreenViewModelTest.kt#openRecentFile_notFound_setsFileNotFoundError | — |
| TC-19 | PASS | app/byCompose/common/src/jvmTest/.../FileHistoryRepositoryDesktopTest.kt | — |
| TC-20 | PASS | app/byCompose/common/src/commonTest/.../MainScreenViewModelTest.kt | — |
| TC-28 | PASS | app/byCompose/common/src/jvmTest/.../FileAvailabilityCheckerDesktopTest.kt | — |
| TC-31 | PASS | app/byCompose/common/src/commonTest/.../MainScreenViewModelTest.kt | — |
| TC-32 | PASS | app/byCompose/common/src/commonTest/.../MainScreenViewModelTest.kt | — |
| TC-36 | PASS | shared/src/commonTest/.../CheckAvailabilityUseCaseTest.kt#emitsUpdateForEachFile | — |
| TC-37 | PASS | app/byCompose/common/src/commonTest/.../MainScreenViewModelTest.kt#rejectSafMerge_addsNewUriAsNewEntry | — |
| TC-38 | PASS | app/byCompose/common/src/commonTest/.../MainScreenViewModelTest.kt#openFilePicker_safFuzzyMatch_dialogShowsBothUris | — |
| TC-39 | PASS | app/byCompose/common/src/jvmTest/.../FileHistoryRepositoryDesktopTest.kt#upsert persistsAcrossReinstantiation | — |
| TC-40 | PASS | shared/src/commonTest/.../FileHistoryManagerTest.kt#findEvictIndex_allUnknown_twentyEntries_evictsOldestByOpenedAt | — |
| TC-41 | PASS | shared/src/commonTest/.../UriNormalizerTest.kt#normalize_nfdEncodedPath_androidLikeContentUri_noCrash | — |
| TC-42 | PASS | app/byCompose/common/src/commonTest/.../MainScreenViewModelTest.kt#cancelNavigation_rollbackCalled_navigationCleared | — |
| TC-43 | PASS | app/byCompose/common/src/commonTest/.../MainScreenViewModelTest.kt#openRecentFile_doubleTap_secondIsIgnored | — |
| TC-44 | PASS | shared/src/commonTest/.../FileHistoryManagerTest.kt#findEvictIndex_allAvailable_evictsOldest | — |
| TC-45 | PASS | app/byCompose/common/src/jvmTest/.../FileHistoryRepositoryDesktopTest.kt#upsert_concurrent_noDuplicates | — |
| TC-46 | PASS | app/byCompose/common/src/commonTest/.../MainScreenViewModelTest.kt#thumbnailGeneration_oom_emitsThumbnailStateError | — |
| TC-47 | PASS | shared/src/commonTest/.../OpenRecentFileUseCaseTest.kt#checkSync_securityException_returnsFileError | — |
| TC-50 | PASS | app/byCompose/common/src/commonTest/.../MainScreenViewModelTest.kt#screenVisible_retriggersAvailabilityCheck_unknownBecomesAvailable | — |
| TC-51 | PASS | shared/src/commonTest/.../OpenRecentFileUseCaseTest.kt#notFound_returnsNotAvailable | — |
| TC-85 | PASS | app/byCompose/common/src/commonTest/.../MainScreenViewModelTest.kt#openRecentFile_archivedUnavailable_doesNotOpenEditor | — |
| TC-87 | PASS | app/byCompose/common/src/commonTest/.../MainScreenViewModelTest.kt#folderDialogNameChanged_invalidChars_filtered | — |
| TC-88 | PASS | app/byCompose/common/src/jvmTest/.../FolderRepositoryDesktopTest.kt#delete_folder_removes_associated_links | — |
| TC-89 | PASS | app/byCompose/common/src/jvmTest/.../FolderRepositoryDesktopTest.kt#getAll_includes_empty_folder_with_zero_files | — |
| TC-95 | PASS | shared/src/commonTest/.../FileHistoryManagerTest.kt#findEvictIndex_allAvailable_evictsOldest | — |
| TC-96 | PASS | shared/src/commonTest/.../FileHistoryManagerTest.kt#applyUpsert_full_notFoundPresent_evictsNotFound | — |
| TC-97 | PASS | shared/src/commonTest/.../FileHistoryManagerTest.kt#applyUpsert_existingUri_movedToFront_sizeUnchanged | — |
| TC-98 | PASS | shared/src/commonTest/.../FileHistoryManagerTest.kt#uriNormalizer_trailingSlash_trimmed | — |
| TC-107 | PASS | app/byCompose/common/src/commonTest/.../MainScreenViewModelTest.kt#folderDialogNameChanged_onlySpaces_confirmDisabled | — |
| TC-108 | PASS | app/byCompose/common/src/commonTest/.../MainScreenViewModelTest.kt#folderDialogNameChanged_invalidChars_filtered | — |
| TC-121 | PASS | app/byCompose/common/src/jvmTest/.../PdfThumbnailGeneratorDesktopTest.kt#non-existent file returns failure with ThumbnailGenerationException | — |
| TC-125 | PASS | shared/src/commonTest/.../FileHistoryManagerTest.kt#findEvictIndex_notFoundEvictedBeforeAvailable | — |

---

## Waived TCs (explicitly out of scope)

| TC-id | Status | Reason |
|-------|--------|--------|
| TC-03..TC-35 (acceptance/UI) | WAIVED | Compose UI test infra not configured; dod_waiver 1.1 |
| TC-48 | WAIVED | CC-15 Desktop flush ordering, known limitation; dod_waiver 1.1 |
| TC-49 | WAIVED | CC-17 manual visual test; dod_waiver 1.1 |
| TC-52..TC-84 | WAIVED | Folder acceptance TCs; dod_waiver 1.1 |
| TC-86 | WAIVED | Manual end-to-end, ADR-007 |
| TC-90..TC-94 | WAIVED | Integration TCs; dod_waiver 1.1 |
| TC-99, TC-100, TC-101 | WAIVED | NOT IMPLEMENTED, ADR-007 future work |
| TC-102..TC-106, TC-109..TC-120, TC-122..TC-124 | WAIVED | NOT IMPLEMENTED; dod_waiver 1.1 |

---

## Failures

None.
