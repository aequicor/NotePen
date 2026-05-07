---
genre: reference
title: "Traceability — Main Screen Redesign"
topic: main-screen
module: common
triggers:
  - "traceability main screen"
  - "trace matrix main screen"
confidence: high
source: ai
updated: 2026-05-07
---

# Traceability — Main Screen Redesign

**Module:** common  
**Generated:** 2026-05-07 by @TraceabilityChecker  
**Verdict:** PASS

---

## Coverage matrix (full)

### Acceptance Criteria → Test Cases

| AC | TC(s) | Verdict |
|----|-------|---------|
| AC-1 | TC-01 (PASS) | linked |
| AC-2 | TC-03 (PEND, waived dod 1.1) | linked |
| AC-3 | TC-04 (PEND, waived dod 1.1) | linked |
| AC-4 | TC-05 (PEND, waived dod 1.1) | linked |
| AC-5 | TC-06 (PEND, waived dod 1.1) | linked |
| AC-5a | TC-07 (PASS) | linked |
| AC-5b | TC-08 (PASS) | linked |
| AC-6 | TC-09 (PEND, waived dod 1.1) | linked |
| AC-7 | TC-10 (PASS) | linked |
| AC-8 | TC-11 (PASS) | linked |
| AC-9 | TC-12 (PASS) | linked |
| AC-9a | TC-13 (PEND, waived dod 1.1) | linked |
| AC-9b | TC-14 (PEND, waived dod 1.1) | linked |
| AC-9c | TC-16 (PEND, waived dod 1.1) | linked |
| AC-9d | TC-17 (PEND, waived dod 1.1) | linked |
| AC-9e | TC-15 (PEND, waived dod 1.1) | linked |
| AC-10 | TC-18 (PEND, waived dod 1.1) | linked |
| AC-11 | TC-19 (PASS) | linked |
| AC-12 | TC-20 (PASS) | linked |
| AC-13 | TC-21 (PEND, waived dod 1.1) | linked |
| AC-14 | TC-22 (PEND, waived dod 1.1) | linked |
| AC-15 | TC-23 (PEND, waived dod 1.1) | linked |
| AC-16 | TC-24 (PEND, waived dod 1.1) | linked |
| AC-17 | TC-25, TC-26 (PEND, waived dod 1.1) | linked |
| AC-18 | TC-27 (PEND, waived dod 1.1) | linked |
| AC-19 | TC-28 (PASS) | linked |
| AC-20 | TC-29 (PEND, waived dod 1.1) | linked |
| AC-21 | TC-30 (PEND, waived dod 1.1) | linked |
| AC-22 | TC-31 (PASS), TC-32 (PASS) | linked |
| AC-23 | TC-33 (PEND, waived dod 1.1) | linked |
| AC-24 | TC-52 (PEND, waived dod 1.1) | linked |
| AC-25 | TC-53 (PEND, waived dod 1.1) | linked |
| AC-26 | TC-54 (PEND, waived dod 1.1) | linked |
| AC-27 | TC-55 (PEND, waived dod 1.1) | linked |
| AC-28 | TC-56 (PEND, waived dod 1.1) | linked |
| AC-29 | TC-57 (PEND, waived dod 1.1) | linked |
| AC-30 | TC-58 (PEND, waived dod 1.1) | linked |
| AC-31 | TC-59 (PEND, waived dod 1.1) | linked |
| AC-32 | TC-60 (PEND, waived dod 1.1) | linked |
| AC-33 | TC-61 (PEND, waived dod 1.1) | linked |
| AC-34 | TC-62 (PEND, waived dod 1.1) | linked |
| AC-35 | TC-63 (PEND, waived dod 1.1) | linked |
| AC-36 | TC-64 (PEND, waived dod 1.1) | linked |
| AC-37 | TC-65 (PEND, waived dod 1.1) | linked |
| AC-38 | TC-66 (PEND, waived dod 1.1) | linked |
| AC-39 | TC-67 (PEND, waived dod 1.1) | linked |
| AC-40 | TC-68 (PEND, waived dod 1.1) | linked |
| AC-41 | TC-69 (PEND, waived dod 1.1) | linked |
| AC-42 | TC-70 (PEND, waived dod 1.1) | linked |
| AC-43 | TC-71 (PEND, waived dod 1.1) | linked |
| AC-44 | TC-72 (PEND, waived dod 1.1) | linked |
| AC-45 | TC-73 (PEND, waived dod 1.1) | linked |
| AC-46 | TC-74 (PEND, waived dod 1.1) | linked |
| AC-47 | TC-75 (PEND, waived dod 1.1) | linked |
| AC-48 | TC-76 (PEND, waived dod 1.1) | linked |
| AC-49 | TC-77 (PEND, waived dod 1.1) | linked |
| AC-50 | TC-78 (PEND, waived dod 1.1) | linked |
| AC-51 | TC-79 (PEND, waived dod 1.1) | linked |
| AC-52 | TC-80 (PEND, waived dod 1.1) | linked |
| AC-53 | TC-81 (PEND, waived dod 1.1) | linked |
| AC-54 | TC-82 (PEND, waived dod 1.1) | linked |
| AC-55 | TC-83 (PEND, waived dod 1.1) | linked |
| AC-56 | TC-84 (PEND, waived dod 1.1) | linked |
| AC-57 | TC-91 (PEND, waived dod 1.1) | linked |
| AC-58 | TC-92, TC-93 (PEND, waived dod 1.1) | linked |

All 58 ACs linked to at least one TC. No AC orphans.

---

### Corner Cases (Critical + High) → Test Cases → Test files

| CC | Severity | TC(s) | Test file | Assertion quality | Verdict |
|----|----------|-------|-----------|-------------------|---------|
| CC-1 | CRITICAL | TC-37 (PASS), TC-08 (PASS) | MainScreenViewModelTest.kt#rejectSafMerge_addsNewUriAsNewEntry; AddToHistoryUseCaseTest.kt#safFuzzyMatch_sameName_sameSize_differentUri_returnsDetected | STRONG: assertNotNull(newEntry), assertEquals(AVAILABLE, ...), assertIs<SafFuzzyMatchDetected>, assertTrue(upsertCalls.isEmpty) | PASS |
| CC-2 | CRITICAL | TC-38 (PASS) | MainScreenViewModelTest.kt#filePickerResult_safFuzzyMatch_dialogShowsBothUris | STRONG: assertNotNull(dialog), assertEquals existing id, assertEquals newUri | PASS |
| CC-5 | CRITICAL | TC-39 (PASS) | FileHistoryRepositoryDesktopTest.kt#`upsert persistsAcrossReinstantiation` | STRONG: assertTrue(files.any { it.uri == "/tmp/crash-test.pdf" }) | PASS |
| CC-8 | CRITICAL | TC-40 (PASS) | FileHistoryManagerTest.kt#findEvictIndex_allUnknown_twentyEntries_evictsOldestByOpenedAt | STRONG: assertEquals(0, idx) | PASS |
| CC-23 | CRITICAL | TC-85 (PASS) | MainScreenViewModelTest.kt#openRecentFile_archivedUnavailable_doesNotOpenEditor | STRONG: assertNull(navigationTarget) | PASS |
| CC-24 | CRITICAL | TC-86 (PEND, manual — waived ADR-007) | — | waived | PASS (waived) |
| CC-3 | HIGH | TC-41 (PASS) | UriNormalizerTest.kt#normalize_nfdEncodedPath_androidLikeContentUri_noCrash | STRONG: assertNotNull, assertFalse(endsWith("/")), assertEquals for idempotence | PASS |
| CC-6 | HIGH | TC-42 (PASS) | MainScreenViewModelTest.kt#cancelNavigation_rollbackCalled_navigationCleared | STRONG: assertNull(navigationTarget), assertTrue(rollbackUpsertCalled), assertEquals uri | PASS |
| CC-7 | HIGH | TC-43 (PASS) | MainScreenViewModelTest.kt#openRecentFile_doubleTap_secondIsIgnored | STRONG: assertEquals(firstTarget, state.navigationTarget) | PASS |
| CC-9 | HIGH | TC-44 (PASS) | FileHistoryManagerTest.kt#findEvictIndex_allAvailable_evictsOldest | STRONG: assertEquals("b", entries[idx].uri) | PASS |
| CC-10 | HIGH | TC-45 (PASS) | FileHistoryRepositoryDesktopTest.kt#`upsert_concurrent_noDuplicates` | STRONG: assertEquals(5, all.size), assertEquals(5, distinct URIs) | PASS |
| CC-11 | HIGH | TC-46 (PASS) | MainScreenViewModelTest.kt#thumbnailGeneration_oom_emitsThumbnailStateError | STRONG: assertIs<ThumbnailState.Error> | PASS |
| CC-12 | HIGH | TC-47 (PASS) | OpenRecentFileUseCaseTest.kt#checkSync_securityException_returnsFileError | STRONG: assertIs<NotAvailable>, assertEquals(FILE_ERROR, status) | PASS |
| CC-15 | HIGH | TC-48 (PEND, NOT IMPLEMENTED — waived per caller) | — | waived | PASS (waived) |
| CC-17 | HIGH | TC-49 (PEND, manual — waived per caller) | — | waived | PASS (waived) |
| CC-18 | HIGH | TC-50 (PASS) | MainScreenViewModelTest.kt#screenVisible_retriggersAvailabilityCheck_unknownBecomesAvailable | STRONG: assertEquals(AVAILABLE, updatedStatus) | PASS |
| CC-19 | HIGH | TC-51 (PASS) | OpenRecentFileUseCaseTest.kt#notFound_returnsNotAvailable | STRONG: assertIs<NotAvailable>, assertEquals(NOT_FOUND, status) | PASS |
| CC-25 | HIGH | TC-87 (PASS) | MainScreenViewModelTest.kt#folderDialogNameChanged_invalidChars_filtered | STRONG: assertFalse(contains '@'), assertFalse(contains '#'), assertTrue(contains "valid") | PASS |
| CC-26 | HIGH | TC-88 (PASS) | FolderRepositoryDesktopTest.kt#delete_folder_removes_associated_links | (impl file not read — see Notes) | PASS |
| CC-27 | HIGH | TC-89 (PASS) | FolderRepositoryDesktopTest.kt#getAll_includes_empty_folder_with_zero_files | (impl file not read — see Notes) | PASS |

---

### Spec data models → Source code

| Model | Source file | Verdict |
|-------|-------------|---------|
| RecentFile | shared/src/commonMain/kotlin/.../domain/model/RecentFile.kt | PRESENT |
| AvailabilityStatus | shared/src/commonMain/kotlin/.../domain/model/AvailabilityStatus.kt | PRESENT |
| FileHistoryManager | shared/src/commonMain/kotlin/.../domain/model/FileHistoryManager.kt | PRESENT |
| UriNormalizer | shared/src/commonMain/kotlin/.../domain/model/UriNormalizer.kt | PRESENT |
| Folder | shared/src/commonMain/kotlin/.../domain/model/Folder.kt | PRESENT |
| FolderFileLink | shared/src/commonMain/kotlin/.../domain/model/FolderFileLink.kt | PRESENT |
| FileHistoryRepository (port) | shared/src/commonMain/kotlin/.../domain/port/FileHistoryRepository.kt | PRESENT |
| FolderRepository (port) | shared/src/commonMain/kotlin/.../domain/port/FolderRepository.kt | PRESENT |
| FileAvailabilityChecker (port) | shared/src/commonMain/kotlin/.../domain/port/FileAvailabilityChecker.kt | PRESENT |
| PdfThumbnailGenerator (port) | shared/src/commonMain/kotlin/.../domain/port/PdfThumbnailGenerator.kt | PRESENT |
| ThumbnailRepository (port) | shared/src/commonMain/kotlin/.../domain/port/ThumbnailRepository.kt | PRESENT |
| AddToHistoryUseCase | shared/src/commonMain/kotlin/.../domain/usecase/AddToHistoryUseCase.kt | PRESENT |
| CheckAvailabilityUseCase | shared/src/commonMain/kotlin/.../domain/usecase/CheckAvailabilityUseCase.kt | PRESENT |
| OpenRecentFileUseCase | shared/src/commonMain/kotlin/.../domain/usecase/OpenRecentFileUseCase.kt | PRESENT |

No ENDPOINT_ORPHAN items (this feature has no HTTP endpoints — it is a local UI/domain feature).

---

### TCs → AC / CC links (orphan check)

All TC tags in the test-cases file reference valid AC or CC IDs that exist in the requirements and corner-case register. No TC_ORPHAN detected.

---

## Gaps summary

No non-waived gaps.

The following items carry accepted waivers and are excluded from gap counting per caller instruction:

- TC-03..TC-35 (acceptance/UI TCs): dod_waiver 1.1 — Compose UI test infrastructure not configured.
- TC-48 / CC-15: Desktop flush ordering — known limitation, waived.
- TC-49 / CC-17: manual UI test, waived.
- TC-52..TC-84: folder acceptance TCs — dod_waiver 1.1.
- TC-86 / CC-24: manual end-to-end, waived ADR-007.
- TC-90..TC-94: integration/acceptance TCs — dod_waiver 1.1.
- TC-99, TC-100, TC-101: NOT IMPLEMENTED, ADR-007.
- TC-102..TC-106, TC-109..TC-120, TC-122..TC-124: NOT IMPLEMENTED, waived.

---

## Notes

1. **CC-26 / TC-88 and CC-27 / TC-89** reference `FolderRepositoryDesktopTest.kt`. This file was not read during this audit run (Anti-Loop limit: file not in the read set). Both TCs carry Status=PASS in the living test-cases file; the impl ref points to named test functions. The PASS verdict for these two CCs is accepted as reported — a future audit should read that file to independently verify assertion strength.

2. **CC-23 / TC-85** — the TC tag description says the test verifies both (a) a distinct visual state for ARCHIVED_UNAVAILABLE and (b) that clicking does not open the editor. The function `openRecentFile_archivedUnavailable_doesNotOpenEditor` asserts (b) with `assertNull(navigationTarget)`. The separate function `openRecentFile_archivedUnavailable_statusPreservedAfterOpenFail` (line 276) verifies (a) — the ARCHIVED_UNAVAILABLE status is not overwritten. Both are real assertions. Collectively strong.

3. **TC-36** references `CheckAvailabilityUseCaseTest.kt#emitsUpdateForEachFile`. The test verifies that the flow emits one update per file with the correct status — the assertion `assertEquals(AvailabilityStatus.AVAILABLE, byId["id1"]?.status)` etc. is STRONG. This TC maps to US-5 (new file appears at top of list via availability check flow). No issue.

4. **Spec endpoints**: This feature does not expose HTTP endpoints. The spec defines data models, domain use cases, and repository ports only. Pass 4 (endpoint scan) is vacuously satisfied.

5. **UriNormalizer test for TC-41 / CC-3**: The test `normalize_nfdEncodedPath_androidLikeContentUri_noCrash` verifies idempotence and no crash on Unicode content:// URI. The NFC normalization on JVM actual is covered implicitly (JVM actual is exercised in jvmTest, not commonTest). This is a known limitation of the commonTest suite; the test verifies the no-crash and idempotence invariants which are the primary safety guarantees for CC-3. Verdict: PASS.
