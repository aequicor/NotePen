---
genre: reference
title: "Code → Test-Case Map"
topic: testing
triggers: ["area map", "related test cases", "which tests"]
confidence: high
source: derived
updated: 2026-05-30T00:00:00Z
---

# NotePen — Code → Test-Case Map

This file maps source paths to the area and its regression (RC-*) and AI-vision (AV-*) cases; it is consumed by `tools/uitest/related-cases-hook.ps1` (the edit-time PostToolUse hook) and documented in `/TESTING.md` § Testing discipline. Authored per `testing/TEST-CASE-STANDARD.md` § 7.

## Map

| Path / glob | Area | Related cases | Run |
|---|---|---|---|
| `.github/workflows/release.yml` | DESKTOP | RC-DESKTOP-022 | release-checklist |
| `app/byCompose/android/src/androidMain/kotlin/ru/kyamshanov/replacementPlace/App.kt` | ANNOT | RC-ANNOT-001, AV-ANNOT-01 | ai-vision AV-ANNOT-01 |
| `app/byCompose/android/src/androidMain/kotlin/ru/kyamshanov/replacementPlace/MainActivity.kt` | STARTUP | RC-STARTUP-003, RC-STARTUP-004, RC-SYNC-006, AV-STARTUP-01, AV-SYNC-01 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/blur/**` | UI | RC-UI-005, RC-UI-006, RC-UI-012, RC-UI-013, RC-UI-014, RC-UI-016, RC-UI-017, AV-UI-04, +more | ./gradlew :app:byCompose:blur:test |
| `app/byCompose/common/build.gradle.kts` | PDF | RC-PDF-008 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/AnnotationRepositoryAndroid.kt` | ANNOT | RC-ANNOT-001, AV-ANNOT-01 | ai-vision AV-ANNOT-01 |
| `app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/AppContextHolder.kt` | ANNOT | RC-ANNOT-001, AV-ANNOT-01 | ai-vision AV-ANNOT-01 |
| `app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/DocumentName.android.kt` | ANNOT | RC-ANNOT-003, RC-UX-004, RC-UX-007, AV-ANNOT-02 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/EditorBackHandler.android.kt` | ANNOT | RC-ANNOT-004, AV-ANNOT-03 | ai-vision AV-ANNOT-03 |
| `app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/ImmersiveEditorMode.android.kt` | EDITOR | RC-EDITOR-003, AV-EDITOR-03 | ai-vision AV-EDITOR-03 |
| `app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/ToolMenusPlacement.android.kt` | ANDROID | RC-ANDROID-005 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/WindowSize.android.kt` | COMMON | RC-COMMON-004 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/book/AndroidBookPdfRenderer.kt` | COMMON | RC-COMMON-003 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/book/AndroidEbookToPdfConverter.kt` | UX | RC-UX-004, RC-UX-010, AV-UX-05 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/mainscreen/infrastructure/AndroidThumbnailGenerator.kt` | UX | RC-UX-010, AV-UX-05 | ai-vision AV-UX-05 |
| `app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/mainscreen/infrastructure/PdfThumbnailGeneratorAndroid.kt` | ANDROID | RC-ANDROID-008, RC-ANDROID-009, AV-ANDROID-04 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/mainscreen/platform/FilePicker.android.kt` | UX | RC-UX-004 | release-checklist |
| `app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/pdf/infrastructure/**` | ANDROID | RC-ANDROID-008, RC-ANDROID-009, AV-ANDROID-04 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfPagesViewer.android.kt` | ANDROID | RC-ANDROID-004, RC-ANDROID-007, AV-ANDROID-01, AV-ANDROID-03 | ai-vision AV-ANDROID-01 |
| `app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfViewerState.android.kt` | ANDROID | RC-ANDROID-001, RC-ANDROID-002, RC-ANDROID-003, RC-ANDROID-004, RC-PDF-002, AV-ANDROID-01, AV-PDF-01 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/pdfviewer/pdfAndroidGestures.kt` | ANDROID | RC-ANDROID-001, RC-ANDROID-004, RC-PDF-002, AV-ANDROID-01, AV-PDF-01 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/App.kt` | STARTUP | RC-STARTUP-003 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DetailsContent.kt` | COMMON | RC-COMMON-002, RC-COMMON-004, RC-INPUT-001, RC-PDF-006, RC-MAG-011, RC-READER-021, RC-SESSION-001, RC-UX-002, +more | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DocumentName.kt` | ANNOT | RC-ANNOT-003, RC-UX-007, AV-ANNOT-02 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/EditorBackHandler.kt` | ANNOT | RC-ANNOT-004, AV-ANNOT-03 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/EditorPanel.kt` | MAG | RC-MAG-008, RC-MAG-009, RC-MAG-010, RC-EDITOR-006, RC-READER-018, RC-RENDER-008, RC-SESSION-002, RC-UX-006, +more | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/LiquidGlassTopBar.kt` | UI | RC-UI-007, AV-UI-06 | ai-vision AV-UI-06 |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/PageIndicatorAirbar.kt` | READER | RC-READER-011, AV-READER-05 | ai-vision AV-READER-05 |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/PageThumbnailsSidebar.kt` | UI | RC-UI-008, RC-UI-009, RC-UI-010, RC-SIDEBAR-001, RC-VIEWER-011, AV-UI-07, AV-UI-08, AV-UI-09, +more | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/PencilModeSupport.kt` | PDF | RC-PDF-006, AV-PDF-04 | ai-vision AV-PDF-04 |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/PortraitTopBar.kt` | COMMON | RC-COMMON-001, RC-READER-012, RC-READER-025, RC-READER-026, RC-SESSION-002, RC-UX-002, RC-UX-009, AV-COMMON-01, +more | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/SessionsDialog.kt` | SESSION | RC-SESSION-001, AV-SESSION-01 | ai-vision AV-SESSION-01 |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/SessionsMenu.kt` | SESSION | RC-SESSION-001, RC-SESSION-002, RC-SESSION-003, RC-UI-015, AV-SESSION-01, AV-SESSION-02, AV-SESSION-03, AV-UI-13 | ai-vision AV-SESSION-02 |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/ToolMenusPlacement.kt` | ANDROID | RC-ANDROID-005 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/ToolRail.kt` | READER | RC-READER-001, RC-READER-003, RC-READER-010, RC-READER-012, RC-READER-018, RC-TOOLRAIL-001, RC-UX-002, RC-UX-003, +more | ai-vision AV-READER-01 |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/ToolSettingsFloatingPanel.kt` | ANDROID | RC-ANDROID-006, AV-ANDROID-02 | ai-vision AV-ANDROID-02 |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/WindowSize.kt` | COMMON | RC-COMMON-004 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/magnifier/MagnifierInputPanel.kt` | INPUT | RC-INPUT-002, AV-INPUT-02 | ai-vision AV-INPUT-02 |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/platform/DragAndDropSupport.kt` | DESKTOP | RC-DESKTOP-002 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/ui/folder/FolderContentsViewModel.kt` | UX | RC-UX-007 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/ui/peer/PeerCatalogContent.kt` | COMMON | RC-COMMON-004 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/ui/screen/MainContent.kt` | COMMON | RC-COMMON-004 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModel.kt` | UX | RC-UX-007 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfDesktopPagesViewer.kt` | DESKTOP | RC-DESKTOP-005, RC-DESKTOP-006, RC-DESKTOP-017, RC-DESKTOP-018, AV-DESKTOP-02, AV-DESKTOP-05 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/qrconnect/HostQrPairingViewModel.kt` | SYNC | RC-SYNC-005 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/tabs/PdfDocumentState.kt` | UX | RC-UX-001, RC-UX-009, RC-RENDER-005, RC-VIEWER-004, AV-RENDER-02, AV-VIEWER-02 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/tabs/TabBar.kt` | TABS | RC-TABS-001, RC-TABS-002, RC-TABS-004, RC-READER-011, RC-SESSION-002, RC-SESSION-004, AV-TABS-01, AV-TABS-02, +more | ai-vision AV-TABS-01 |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/tabs/TabSession.kt` | TABS | RC-TABS-003, RC-ANNOT-003, AV-TABS-03, AV-ANNOT-02 | ai-vision AV-TABS-03 |
| `app/byCompose/common/src/jvmAndroidMain/kotlin/ru/kyamshanov/notepen/AnnotationRepositoryJvmAndroid.kt` | ANNOT | RC-ANNOT-001, RC-ANNOT-002, RC-ANNOT-007, RC-ANNOT-009, RC-EDITOR-004, RC-EDITOR-005, RC-SYNC-011, AV-ANNOT-01, +more | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/jvmAndroidMain/kotlin/ru/kyamshanov/notepen/reflow/FileSystemReflowDocumentDiskCache.kt` | REFLOW | RC-REFLOW-009 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/jvmAndroidMain/kotlin/ru/kyamshanov/notepen/reflow/ReflowBinaryFormat.kt` | REFLOW | RC-REFLOW-008 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/DocumentName.jvm.kt` | ANNOT | RC-ANNOT-003, RC-UX-007, AV-ANNOT-02 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/ToolMenusPlacement.jvm.kt` | ANDROID | RC-ANDROID-005 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/book/JvmBookPdfRenderer.kt` | COMMON | RC-COMMON-003, RC-READER-007, RC-READER-008 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/infrastructure/PdfThumbnailGeneratorDesktop.kt` | READER | RC-READER-009 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/mainscreen/infrastructure/PdfThumbnailGeneratorDesktop.kt` | DESKTOP | RC-DESKTOP-003, RC-DESKTOP-004, AV-DESKTOP-01 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/mainscreen/platform/DragAndDropSupport.jvm.kt` | DESKTOP | RC-DESKTOP-002 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/mainscreen/ui/component/ThumbnailView.kt` | DESKTOP | RC-DESKTOP-003, AV-DESKTOP-01 | ai-vision AV-DESKTOP-01 |
| `app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/pdf/infrastructure/JvmPdfDocument.kt` | PDF | RC-PDF-007 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/pdf/infrastructure/JvmPdfPageRenderer.kt` | PDF | RC-PDF-007, RC-PDF-008, RC-RENDER-007, AV-RENDER-04 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfPagesViewer.jvm.kt` | PDF | RC-PDF-001 | ./gradlew :app:byCompose:common:test |
| `app/byCompose/desktop/build.gradle.kts` | DESKTOP | RC-DESKTOP-020, RC-DESKTOP-022, RC-DESKTOP-023 | release-checklist |
| `app/byCompose/desktop/proguard-rules.pro` | DESKTOP | RC-DESKTOP-019, RC-DESKTOP-020, RC-DESKTOP-021 | release-checklist |
| `app/byCompose/desktop/src/desktopMain/kotlin/main.kt` | DESKTOP | RC-DESKTOP-001, RC-DESKTOP-003, RC-STARTUP-003, RC-STARTUP-004, RC-SYNC-006, AV-DESKTOP-01, AV-STARTUP-01, AV-SYNC-01 | release-checklist |
| `app/byCompose/uikit/src/commonMain/kotlin/ru/kyamshanov/notepen/WheelCarousel.kt` | UI | RC-UI-001, RC-UI-002, RC-UIKIT-001, RC-UIKIT-002, RC-UIKIT-003, RC-UIKIT-004, RC-READER-019, RC-READER-026, +more | ./gradlew :app:byCompose:uikit:test |
| `drawing/api/src/commonMain/kotlin/ru/kyamshanov/notepen/annotation/domain/StrokeSimplifier.kt` | ANNOT | RC-ANNOT-008 | ./gradlew :drawing:api:test |
| `drawing/api/src/commonMain/kotlin/ru/kyamshanov/notepen/annotation/domain/model/AnnotationViewState.kt` | ANNOT | RC-ANNOT-009, RC-EDITOR-004 | ./gradlew :drawing:api:test |
| `drawing/api/src/commonMain/kotlin/ru/kyamshanov/notepen/annotation/domain/model/PageRotation.kt` | VIEWER | RC-VIEWER-003 | ./gradlew :drawing:api:test |
| `drawing/api/src/commonMain/kotlin/ru/kyamshanov/notepen/annotation/domain/model/SpreadSplit.kt` | VIEWER | RC-VIEWER-005 | ./gradlew :drawing:api:test |
| `drawing/api/src/commonMain/kotlin/ru/kyamshanov/notepen/annotation/domain/port/AnnotationRepository.kt` | ANNOT | RC-ANNOT-009, RC-EDITOR-004 | ./gradlew :drawing:api:test |
| `drawing/api/src/commonMain/kotlin/ru/kyamshanov/notepen/drawing/api/PdfDrawingState.kt` | ANNOT | RC-ANNOT-008 | ./gradlew :drawing:api:test |
| `drawing/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/MultiPageDrawingController.kt` | MAG | RC-MAG-008, AV-MAG-07 | ./gradlew :drawing:impl:test |
| `drawing/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/magnifier/**` | VIEWER | RC-VIEWER-001, RC-VIEWER-002, RC-MAG-002, AV-VIEWER-01, AV-MAG-01 | ./gradlew :drawing:impl:test |
| `library/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/library/impl/DefaultLibraryRegistry.kt` | LIBRARY | RC-LIBRARY-001 | ./gradlew :library:impl:test |
| `library/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/library/impl/GitHubLibrary.kt` | LIBRARY | RC-LIBRARY-004 | ./gradlew :library:impl:test |
| `library/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/library/impl/LocalFolderLibrary.kt` | LIBRARY | RC-LIBRARY-002 | ./gradlew :library:impl:test |
| `qr-connect/src/commonMain/kotlin/ru/kyamshanov/notepen/qrconnect/application/HostQrPairingCoordinator.kt` | SYNC | RC-SYNC-005 | ./gradlew :qr-connect:test |
| `reflow/api/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/api/ReaderSettingsReducer.kt` | READER | RC-READER-016, RC-READER-017 | ./gradlew :reflow:api:test |
| `reflow/api/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/api/ReflowParserVersion.kt` | REFLOW | RC-REFLOW-008, RC-REFLOW-009 | ./gradlew :reflow:api:test |
| `reflow/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/ReflowAssembler.kt` | READER | RC-READER-004, RC-READER-005, RC-READER-013, RC-READER-014, RC-READER-015, RC-REFLOW-001, RC-REFLOW-002, RC-REFLOW-003, +more | ./gradlew :reflow:impl:test |
| `reflow/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/TableNoiseGuard.kt` | REFLOW | RC-REFLOW-005, RC-REFLOW-006, RC-REFLOW-007 | ./gradlew :reflow:impl:test |
| `reflow/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/lattice/LatticeTableRefiner.kt` | REFLOW | RC-REFLOW-006 | ./gradlew :reflow:impl:test |
| `reflow/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/ui/BlockHeightCalculator.kt` | UX | RC-UX-008 | ./gradlew :reflow:impl:test |
| `reflow/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/ui/ReaderAirbar.kt` | READER | RC-READER-002, RC-READER-019, RC-READER-022, RC-READER-023, RC-READER-027, RC-READER-028, AV-READER-02, AV-READER-08, +more | ai-vision AV-READER-02 |
| `reflow/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/ui/ReflowReader.kt` | REFLOW | RC-REFLOW-010, RC-REFLOW-011, RC-VIEWER-007, RC-VIEWER-008, RC-UX-005, RC-UX-006, RC-UX-008, AV-REFLOW-01, +more | ai-vision AV-REFLOW-01 |
| `reflow/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/ui/ReflowSelectionState.kt` | VIEWER | RC-VIEWER-007, RC-VIEWER-008, AV-VIEWER-03 | ./gradlew :reflow:impl:test |
| `reflow/impl/src/jvmMain/kotlin/ru/kyamshanov/notepen/reflow/JvmPdfReflowExtractor.kt` | READER | RC-READER-006 | ./gradlew :reflow:impl:jvmTest |
| `rendering/impl/src/androidMain/kotlin/ru/kyamshanov/notepen/lowlatency/LowLatencyStrokeOverlay.android.kt` | MARKER | RC-MARKER-002, AV-MARKER-02 | ai-vision AV-MARKER-02 |
| `rendering/impl/src/androidMain/kotlin/ru/kyamshanov/notepen/pdf/infrastructure/AndroidPdfPageRenderer.kt` | VIEWER | RC-VIEWER-004, AV-VIEWER-02 | ai-vision AV-VIEWER-02 |
| `rendering/impl/src/androidMain/kotlin/ru/kyamshanov/notepen/pdfviewer/**` | VIEWER | RC-RENDER-004, RC-RENDER-012, RC-VIEWER-012, RC-VIEWER-013, RC-EDITOR-006, RC-RENDER-008, AV-RENDER-01, AV-VIEWER-05, +more | ./gradlew :rendering:impl:test |
| `rendering/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/DrawablePdfPage.kt` | ANNOT | RC-ANNOT-006, RC-ANNOT-010, RC-PDF-003, RC-PDF-004, RC-PDF-005, RC-RENDER-006, RC-MAG-004, RC-MARKER-001, +more | ./gradlew :rendering:impl:test |
| `rendering/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/magnifier/MagnifierGeometry.kt` | MAG | RC-MAG-013, RC-MAG-014 | ./gradlew :rendering:impl:test |
| `rendering/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/magnifier/MagnifierInputPanel.kt` | MAG | RC-MAG-003, RC-MAG-006, RC-MAG-007, RC-MARKER-003, RC-RENDER-011, AV-MAG-02, AV-MAG-05, AV-MAG-06, +more | ./gradlew :rendering:impl:test |
| `rendering/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/magnifier/MagnifierState.kt` | MAG | RC-MAG-001, RC-MAG-002, RC-MAG-005, RC-MAG-015, AV-MAG-01, AV-MAG-04, AV-MAG-12 | ./gradlew :rendering:impl:test |
| `rendering/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/magnifier/MagnifierTargetGestureController.kt` | MAG | RC-MAG-015, AV-MAG-12 | ai-vision AV-MAG-12 |
| `rendering/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfBitmapCache.kt` | DESKTOP | RC-DESKTOP-017, RC-DESKTOP-018, AV-DESKTOP-05 | ./gradlew :rendering:impl:test |
| `rendering/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfPagesLayout.kt` | RENDER | RC-RENDER-001, RC-RENDER-002, RC-RENDER-003, RC-RENDER-009, RC-RENDER-010, RC-DESKTOP-007, RC-VIEWER-006, RC-VIEWER-010, +more | ./gradlew :rendering:impl:test |
| `rendering/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfViewerState.kt` | DESKTOP | RC-DESKTOP-005, RC-DESKTOP-006, RC-DESKTOP-008, AV-DESKTOP-02 | ./gradlew :rendering:impl:test |
| `rendering/impl/src/jvmMain/kotlin/ru/kyamshanov/notepen/pdf/infrastructure/JvmPdfPageRenderer.kt` | VIEWER | RC-VIEWER-004, AV-VIEWER-02 | ai-vision AV-VIEWER-02 |
| `rendering/impl/src/jvmMain/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfPagesViewer.jvm.kt` | DESKTOP | RC-DESKTOP-009, RC-DESKTOP-010, RC-DESKTOP-011, RC-DESKTOP-016, RC-PDF-001, RC-MAG-005, RC-RENDER-004, AV-DESKTOP-03, +more | ./gradlew :rendering:impl:jvmTest |
| `rendering/impl/src/jvmMain/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfViewerState.jvm.kt` | DESKTOP | RC-DESKTOP-005, RC-DESKTOP-016, RC-RENDER-008, RC-VIEWER-010, AV-RENDER-05 | ./gradlew :rendering:impl:jvmTest |
| `rendering/impl/src/jvmMain/kotlin/ru/kyamshanov/notepen/tablet/WindowsPenFix.kt` | DESKTOP | RC-DESKTOP-012, RC-DESKTOP-014, AV-DESKTOP-04 | release-checklist |
| `rendering/impl/src/jvmMain/kotlin/ru/kyamshanov/notepen/tablet/WindowsPointerHook.kt` | DESKTOP | RC-DESKTOP-012, RC-DESKTOP-013, RC-DESKTOP-015 | release-checklist |
| `shared/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/domain/usecase/AddToHistoryUseCase.kt` | UX | RC-UX-007 | ./gradlew :shared:test |
| `sync/src/commonMain/kotlin/ru/kyamshanov/notepen/sync/domain/CacheEvictor.kt` | LIBRARY | RC-LIBRARY-003 | ./gradlew :sync:jvmTest |
| `sync/src/commonMain/kotlin/ru/kyamshanov/notepen/sync/domain/CatalogDiffOrphanDetector.kt` | SYNC | RC-SYNC-007 | ./gradlew :sync:jvmTest |
| `sync/src/commonMain/kotlin/ru/kyamshanov/notepen/sync/domain/HostAnnotationProjection.kt` | SYNC | RC-SYNC-008, RC-SYNC-009, RC-SYNC-011, AV-SYNC-02 | ./gradlew :sync:jvmTest |
| `sync/src/commonMain/kotlin/ru/kyamshanov/notepen/sync/domain/HostHeadlessAnnotationHandler.kt` | SYNC | RC-SYNC-008 | ./gradlew :sync:jvmTest |
| `sync/src/commonMain/kotlin/ru/kyamshanov/notepen/sync/domain/RemoteCatalogProvider.kt` | SYNC | RC-SYNC-010 | ./gradlew :sync:jvmTest |
| `sync/src/commonMain/kotlin/ru/kyamshanov/notepen/sync/domain/model/StrokeDelta.kt` | SYNC | RC-SYNC-003 | ./gradlew :sync:jvmTest |
| `sync/src/commonMain/kotlin/ru/kyamshanov/notepen/sync/domain/port/CacheFileStore.kt` | LIBRARY | RC-LIBRARY-003 | ./gradlew :sync:jvmTest |
| `sync/src/commonMain/kotlin/ru/kyamshanov/notepen/sync/infrastructure/KtorSyncClient.kt` | SYNC | RC-SYNC-001 | ./gradlew :sync:jvmTest |
| `sync/src/commonMain/kotlin/ru/kyamshanov/notepen/sync/infrastructure/SqlDelightPendingDeltaQueue.kt` | STARTUP | RC-STARTUP-001, RC-STARTUP-002 | ./gradlew :sync:jvmTest |
| `sync/src/jvmMain/kotlin/ru/kyamshanov/notepen/sync/infrastructure/KtorPeerServer.kt` | SYNC | RC-SYNC-002, RC-SYNC-004 | ./gradlew :sync:jvmTest |
| `tools/marker/src/commonMain/kotlin/ru/kyamshanov/notepen/tools/marker/MarkerRenderer.kt` | MARKER | RC-MARKER-001, AV-MARKER-01 | ai-vision AV-MARKER-01 |

## Areas

- ANDROID — 9 cases (5 RC, 4 AV) — modules: `app/byCompose/common` (androidMain pdfviewer, pdf/infrastructure, mainscreen, ToolMenusPlacement)
- ANNOT — 10 cases (8 RC, 6 AV) — modules: `app/byCompose/common` (AnnotationRepository*, DocumentName, EditorBackHandler), `drawing/api`, `rendering/impl`
- COMMON — 4 cases (4 RC, 2 AV) — modules: `app/byCompose/common` (PortraitTopBar, WindowSize, book, MainContent, PeerCatalogContent, DetailsContent)
- DESKTOP — 23 cases (13 RC, 5 AV) — modules: `app/byCompose/desktop` (main.kt, proguard, build), `app/byCompose/common` (jvmMain DnD/thumbnails), `rendering/impl` (jvmMain tablet, PdfPagesViewer.jvm, PdfBitmapCache)
- EDITOR — 6 cases (4 RC, 4 AV) — modules: `app/byCompose/common` (DetailsContent, ImmersiveEditorMode), `rendering/impl`, `drawing/api`
- INPUT — 2 cases (2 RC, 2 AV) — modules: `app/byCompose/common` (DetailsContent, magnifier/MagnifierInputPanel)
- LIBRARY — 4 cases (4 RC, 0 AV) — modules: `library/impl`, `sync` (CacheEvictor, CacheFileStore)
- MAG — 15 cases (15 RC, 12 AV) — modules: `rendering/impl` (magnifier), `drawing/impl` (magnifier), `app/byCompose/common` (EditorPanel)
- MARKER — 3 cases (3 RC, 3 AV) — modules: `rendering/impl` (DrawablePdfPage, lowlatency, MagnifierInputPanel), `tools/marker`
- PDF — 8 cases (8 RC, 4 AV) — modules: `app/byCompose/common` (pdf/infrastructure, pdfviewer, build), `rendering/impl`, `gradle`
- READER — 28 cases (28 RC, 17 AV) — modules: `app/byCompose/common` (ToolRail, DetailsContent, EditorPanel, PageIndicatorAirbar), `reflow/impl` (ReaderAirbar, ReflowReader), `reflow/api`
- REFLOW — 11 cases (11 RC, 2 AV) — modules: `reflow/impl` (ReflowAssembler, TableNoiseGuard, lattice, ReflowReader), `reflow/api`, `app/byCompose/common` (reflow)
- RENDER — 12 cases (12 RC, 6 AV) — modules: `rendering/impl` (PdfPagesLayout, PdfViewerState, DrawablePdfPage, pdfviewer), `app/byCompose/common`
- SESSION — 4 cases (4 RC, 4 AV) — modules: `app/byCompose/common` (SessionsMenu, SessionsDialog, DetailsContent, EditorPanel, tabs/TabBar)
- SIDEBAR — 1 case (1 RC, 1 AV) — modules: `app/byCompose/common` (PageThumbnailsSidebar)
- STARTUP — 4 cases (4 RC, 1 AV) — modules: `sync` (SqlDelightPendingDeltaQueue), `app/byCompose/common` (App.kt), `app/byCompose/desktop`, `app/byCompose/android`
- SYNC — 12 cases (12 RC, 3 AV) — modules: `sync` (domain, infrastructure), `qr-connect`, `app/byCompose/common`
- TABS — 4 cases (4 RC, 4 AV) — modules: `app/byCompose/common` (tabs/TabBar, tabs/TabSession, DetailsContent)
- TOOLRAIL — 1 case (1 RC, 1 AV) — modules: `app/byCompose/common` (ToolRail)
- UI — 17 cases (17 RC, 14 AV) — modules: `app/byCompose/blur`, `app/byCompose/uikit` (WheelCarousel), `app/byCompose/common` (PageThumbnailsSidebar, DetailsContent, LiquidGlassTopBar)
- UIKIT — 4 cases (4 RC, 3 AV) — modules: `app/byCompose/uikit` (WheelCarousel)
- UX — 10 cases (10 RC, 5 AV) — modules: `app/byCompose/common` (tabs/PdfDocumentState, ToolRail, DocumentName, EditorPanel), `reflow/impl`, `shared`
- VIEWER — 13 cases (13 RC, 6 AV) — modules: `drawing/api` (PageRotation, SpreadSplit), `drawing/impl` (magnifier), `rendering/impl` (PdfPagesLayout, PdfPagesViewer), `reflow/impl`

## How the hook uses this

- The hook reads the edited file path and does a longest-prefix match against the `Path / glob` column of the Map table; on a hit it injects that row's Related cases plus the Run hint as a standing instruction for the rest of the session.
- If no row matches the edited path, the hook is a no-op (no instruction injected).
- New behavior in a file with no matching row should get a new row added here (area + RC/AV ids + run hint) per `testing/TEST-CASE-STANDARD.md` § 7 so the hook resolves it next time.
