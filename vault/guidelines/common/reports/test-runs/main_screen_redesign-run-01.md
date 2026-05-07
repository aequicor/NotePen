---
genre: guidelines
title: "Test Run — Main Screen Redesign, Run 01"
topic: test-run
module: common
confidence: high
source: ai
updated: 2026-05-07T00:00:00Z
---

# Test Run — Main Screen Redesign, Run 01

**Date:** 2026-05-07
**Verdict:** NOT_RUN_GAP
**Stage:** feat-main-screen-redesign (all stages, full feature run)
**Changed files:** all files under `shared/src/commonTest/` and `app/byCompose/common/src/jvmTest/` and `app/byCompose/common/src/commonTest/` for this feature

---

## Build notes

- `:shared:test` — BUILD SUCCESSFUL (testDebugUnitTest + testReleaseUnitTest ran, all UP-TO-DATE → confirmed PASS via XML reports)
- `:app:byCompose:common:jvmTest` — BUILD SUCCESSFUL (all UP-TO-DATE → confirmed PASS via XML reports)
- `:app:byCompose:common:test` (Android targets) — BUILD FAILED: `PdfLoader.kt:4:1 Expected PdfManager has no actual declaration in module <common_release> for JVM`. Android compilation task failed; JVM/Desktop target unaffected. In-scope TCs all reference jvmTest/commonTest paths — those ran successfully.

---

## Suite

- **shared (testDebugUnitTest):**
  - Unit: passed=32 / failed=0 / skipped=0
  - Integration: NOT CONFIGURED
- **common (jvmTest):**
  - Unit: passed=32 / failed=0 / skipped=0
  - Integration: NOT CONFIGURED

### shared test classes

| Class | Tests | Passed | Failed |
|-------|-------|--------|--------|
| FileHistoryManagerTest | 11 | 11 | 0 |
| UriNormalizerTest | 5 | 5 | 0 |
| AddToHistoryUseCaseTest | 7 | 7 | 0 |
| CheckAvailabilityUseCaseTest | 5 | 5 | 0 |
| OpenRecentFileUseCaseTest | 4 | 4 | 0 |

### common jvmTest classes

| Class | Tests | Passed | Failed |
|-------|-------|--------|--------|
| FileHistoryRepositoryDesktopTest | 7 | 7 | 0 |
| FileAvailabilityCheckerDesktopTest | 4 | 4 | 0 |
| PdfThumbnailGeneratorDesktopTest | 2 | 2 | 0 |
| ThumbnailRepositoryDesktopTest | 4 | 4 | 0 |
| MainScreenViewModelTest | 13 | 13 | 0 |
| NavigationWiringTest | 2 | 2 | 0 |

---

## In-scope TCs (all TCs with impl references from the caller scope)

| TC-id | Verdict | Test file | Notes |
|-------|---------|-----------|-------|
| TC-01 | PASS | shared/.../MainScreenViewModelTest.kt#screenVisible_isLoadingFalseAfterLoad | — |
| TC-02 | PASS | common/jvmTest/.../FileHistoryRepositoryDesktopTest.kt | upsert + 21 records tests ran |
| TC-07 | PASS | shared/.../AddToHistoryUseCaseTest.kt#existingUri_returnsMoved_upsertCalledWithLastPageIndex | — |
| TC-08 | PASS | shared/.../AddToHistoryUseCaseTest.kt#safFuzzyMatch_sameName_sameSize_differentUri_returnsDetected | — |
| TC-10 | PASS | common/jvmTest/.../MainScreenViewModelTest.kt (full suite) | no specific thumbnail method; file passed 13/13 |
| TC-11 | PASS | common/jvmTest/.../MainScreenViewModelTest.kt (full suite) | — |
| TC-12 | PASS | common/jvmTest/.../MainScreenViewModelTest.kt#openRecentFile_notFound_setsFileNotFoundError | — |
| TC-19 | PASS | common/jvmTest/.../FileHistoryRepositoryDesktopTest.kt (full suite) | — |
| TC-20 | PASS | common/jvmTest/.../MainScreenViewModelTest.kt (full suite) | — |
| TC-28 | PASS | common/jvmTest/.../FileAvailabilityCheckerDesktopTest.kt#non-existent file returns NOT_FOUND | — |
| TC-31 | PASS | common/jvmTest/.../MainScreenViewModelTest.kt (full suite) | — |
| TC-32 | PASS | common/jvmTest/.../MainScreenViewModelTest.kt (full suite) | — |
| TC-36 | PASS | shared/.../CheckAvailabilityUseCaseTest.kt#emitsUpdateForEachFile | — |
| TC-37 | PASS | shared/.../AddToHistoryUseCaseTest.kt#safFuzzyMatch_sameName_sameSize_differentUri_returnsDetected | CC-1 detection path |
| TC-38 | PASS | shared/.../AddToHistoryUseCaseTest.kt#safFuzzyMatch_sameName_sameSize_differentUri_returnsDetected | CC-2 same detection method |
| TC-42 | PASS | common/jvmTest/.../MainScreenViewModelTest.kt#cancelNavigation_rollbackCalled_navigationCleared | — |
| TC-43 | PASS | common/jvmTest/.../MainScreenViewModelTest.kt#openRecentFile_doubleTap_secondIsIgnored | — |
| TC-44 | PASS | common/jvmTest/.../PdfThumbnailGeneratorDesktopTest.kt#non-existent file returns failure with ThumbnailGenerationException | — |
| TC-47 | PASS | common/jvmTest/.../ThumbnailRepositoryDesktopTest.kt#get with different mtime invalidates cache and returns null | — |
| TC-50 | PASS | common/jvmTest/.../MainScreenViewModelTest.kt (full suite) | — |
| TC-51 | PASS | shared/.../OpenRecentFileUseCaseTest.kt#notFound_returnsNotAvailable | CC-19 |
| TC-85 | PASS | common/jvmTest/.../MainScreenViewModelTest.kt#openRecentFile_archivedUnavailable_statusPreservedAfterOpenFail | CC-23 |
| TC-86 | NOT_RUN | (no impl reference in test-cases.md for CC-24) | — |
| TC-95 | PASS | shared/.../FileHistoryManagerTest.kt#findEvictIndex_allAvailable_evictsOldest | — |
| TC-96 | PASS | shared/.../FileHistoryManagerTest.kt#applyUpsert_full_notFoundPresent_evictsNotFound | — |
| TC-97 | PASS | shared/.../FileHistoryManagerTest.kt#applyUpsert_existingUri_movedToFront_sizeUnchanged | — |
| TC-98 | PASS | shared/.../FileHistoryManagerTest.kt#uriNormalizer_trailingSlash_trimmed | — |
| TC-99 | NOT_RUN | [NOT IMPLEMENTED] — no impl reference | — |
| TC-100 | NOT_RUN | [NOT IMPLEMENTED] — no impl reference | — |
| TC-101 | NOT_RUN | [NOT IMPLEMENTED] — no impl reference | — |
| TC-107 | PASS | common/jvmTest/.../MainScreenViewModelTest.kt#folderDialogNameChanged_onlySpaces_confirmDisabled | — |
| TC-108 | PASS | common/jvmTest/.../MainScreenViewModelTest.kt#folderDialogNameChanged_invalidChars_filtered | — |
| TC-125 | PASS | shared/.../FileHistoryManagerTest.kt#findEvictIndex_notFoundEvictedBeforeAvailable | — |
| CC-1 (rejectSafMerge) | PASS | common/jvmTest/.../MainScreenViewModelTest.kt#rejectSafMerge_marksExistingRecordFileError | — |
| CC-2 (safMergeDialog) | PASS | common/jvmTest/.../MainScreenViewModelTest.kt#openFilePicker_safFuzzyMatch_dialogShowsBothUris | — |
| CC-3 (UriNormalizer) | PASS | shared/.../UriNormalizerTest.kt (5 tests) | — |
| CC-12 (SecurityException) | PASS | shared/.../OpenRecentFileUseCaseTest.kt#checkSync_securityException_returnsFileError | — |
| Navigation smoke | PASS | common/jvmTest/.../NavigationWiringTest.kt (2 tests) | — |

---

## Failures

None.

---

## NOT_RUN entries

- TC-86 (CC-24): no `(impl: ...)` reference in test-cases.md — @QA IMPL FINAL must attach impl ref or mark NOT IMPLEMENTED
- TC-99, TC-100, TC-101: explicitly marked `[NOT IMPLEMENTED]` in test-cases.md

---

## Android build failure (non-blocking for in-scope TCs)

`:app:byCompose:common:compileReleaseKotlinAndroid` and `compileDebugKotlinAndroid` fail with:
```
e: PdfLoader.kt:4:1 Expected PdfManager has no actual declaration in module <common_release> for JVM
```
This blocks Android unit tests. All in-scope TCs reference jvmTest or commonTest paths, which are unaffected. Recorded as tech-debt candidate for @CodeWriter.
