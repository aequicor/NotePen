# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## What this is

NotePen — a Kotlin Multiplatform PDF/document annotation app with an infinite canvas, tuned for stylus input. Targets **Android** and **Desktop/JVM** (Windows/macOS/Linux). Single shared codebase (Compose Multiplatform UI, Decompose navigation), per-platform actuals for rendering, file access, and tablet input. Peer-to-peer sync of annotations runs over the local network (Ktor WebSocket + mDNS), no cloud.

Package root: `ru.kyamshanov.notepen`.

## Commands

| Task | Command |
|---|---|
| Build everything | `./gradlew build` |
| Run desktop app | `./gradlew runDesktop` (alias for `:app:byCompose:desktop:run`) |
| Install Android debug | `./gradlew :app:byCompose:android:installDebug` |
| All unit tests | `./gradlew test` |
| Tests for one module | `./gradlew :sync:jvmTest` |
| A single test class/method | `./gradlew :sync:jvmTest --tests "ru.kyamshanov.notepen.sync.domain.SyncEngineTest"` |
| Lint + static analysis | `./gradlew detekt` |
| Format check / autoformat | `./gradlew ktlintCheck` / `./gradlew ktlintFormat` |
| Format ONE file | `./gradlew ktlintFormatFile -PktlintFile=<absolute path>` |
| Full verification | `./gradlew check` (build + tests + ktlintCheck + detekt) |

Most logic tests live in `commonTest`/`jvmTest` and run on the JVM. The Android entry point is `app/byCompose/android/.../replacementPlace/MainActivity.kt`; the desktop entry is `app/byCompose/desktop/src/desktopMain/kotlin/main.kt` (`mainClass = "MainKt"`), which also does the desktop DI wiring (server, mDNS, ViewModels).

## Toolchain & build specifics

- **JDK split:** library and Android modules compile to **JVM 11**. The desktop module pins a **JetBrains Runtime (JBR) 25** toolchain — required for the custom Windows title bar (`setupJbrTitleBar`). foojay cannot auto-provision JBR; a machine without one auto-detected must set `org.gradle.java.installations.paths` in its user `gradle.properties`.
- **Version catalog:** all dependencies/plugins are declared in [gradle/libs.versions.toml](gradle/libs.versions.toml). Add deps there, reference as `libs.*` / `projects.*` (type-safe project accessors are enabled).
- **Configuration cache is ON** (`org.gradle.configuration-cache=true`). Build logic must stay configuration-cache compatible.
- `-Xexpect-actual-classes` is enabled in several modules — expect/actual **classes** (not just functions) are used.
- **SQLDelight** generates `NotePenSyncDatabase` (package `ru.kyamshanov.notepen.sync.db`) in `:sync`. Migration-verify tasks are deliberately disabled (the bundled sqlite-jdbc native lib fails to load in the Gradle daemon on Windows); revisit when the first `.sqm` migration lands.
- App version comes from the `app.version` Gradle property (drives Android `versionName` and the desktop package version). Android release signing reads `ANDROID_KEYSTORE_PATH` / `ANDROID_KEY_ALIAS` / `ANDROID_STORE_PASSWORD` / `ANDROID_KEY_PASSWORD` from the environment.

## Module map

`settings.gradle.kts` is the source of truth. Modules follow an **api/impl split**: `:*:api` holds interfaces + models (often `explicitApi()`), `:*:impl` holds concrete implementations and platform actuals.

- **`:shared`** — domain core + navigation contracts. Decompose components (`RootComponent`/`DefaultRootComponent`, `DetailsComponent`, `MainComponent`, etc.) and the `mainscreen`/`pdf`/`shortcuts` domains (ports, models, use cases). Almost everything depends on this.
- **`:drawing:api` / `:drawing:impl`** — stroke model (`DrawingPath`, `DrawingPoint`), `PdfDrawingState` (Compose snapshot state for live strokes), shape recognition/simplification ports; impl has the multi-page gesture controller + magnifier.
- **`:rendering:api` / `:rendering:impl`** — `PageRasterizer` + bitmap-cache models in api; impl has `DrawablePdfPage` (the Canvas that composites PDF raster + strokes), the multi-page viewer, tablet/stylus input, and the low-latency overlay. PDF rasterization is **PDFBox** on JVM, Android `PdfRenderer` on Android.
- **`:reflow:api` / `:reflow:impl`** — text-reflow reading mode: `PdfReflowExtractor` classifies content (`PdfContentKind`) and extracts a `ReflowDocument`; impl assembles reading order, repaginates, and maps strokes to text. PDFBox (JVM) / pdfbox-android.
- **`:tools:marker`** — the marker/highlighter tool UI built on `:drawing:api`.
- **`:sync`** — annotation sync. Domain: `SyncEngine` (per-document last-writer-wins merge of `StrokeDelta`), `SyncEngineRegistry`, host projection, ports (`PeerServer`, `SyncClient`, `PendingDeltaQueue`, …). Infrastructure: `KtorPeerServer` (host, jvmMain), `KtorSyncClient`, SQLDelight-backed offline delta queue.
- **`:server`** — thin host-side aggregation module (`api(projects.sync)`), reserved for a future standalone host entrypoint. The actual WebSocket server lives in `:sync` jvmMain.
- **`:qr-connect`** — QR pairing. zxing (QR generation, JVM) + ML Kit barcode + CameraX (scanning, Android). Depends on `:sync`.
- **`:app:byCompose:common`** — shared Compose UI and app glue: screen components, ViewModels, dialogs, the `book`/`epub` document-conversion layer, PDF loader actuals. Depends on every feature module.
- **`:app:byCompose:android` / `:app:byCompose:desktop`** — platform application modules (entry points, DI wiring, packaging).
- **`:app:byCompose:theme` / `:app:byCompose:uikit`** — design-system modules (theming, reusable Compose components).

**Dependency direction:** `app:*` → feature `:*:impl` → `:*:api` / `:shared`. Feature modules never depend back on `app`; `domain` packages never import `infrastructure` or platform/SDK types.

## Architecture conventions

- **Clean-architecture layering inside modules**, by package, consistently: `domain/{model,port,usecase,exception}` (pure Kotlin — no Android/Ktor/SQLDelight/coroutine-dispatcher imports) ← `infrastructure/` (port implementations, DTO mapping) and `presentation`/`ui` (Compose + ViewModels). Ports are declared in `domain/port` and implemented in `infrastructure` or in platform source sets (`androidMain`/`jvmMain`).
- **expect/actual** is the only mechanism for platform differences — no `if (platform)` branching. Key actual pairs: PDF rasterization & document loading, tablet/stylus input (`WinTab` on Windows, Cocoa on macOS, Android stylus), low-latency overlay, and small utilities (UUID, unicode normalization).
- **Navigation** is Decompose: a `StackNavigation<Config>` in `DefaultRootComponent` switches between Main (library/recents/folders/peers), Details (the PDF editor), PeerCatalog, and FolderContents. Component *interfaces* live in `:shared`; UI + some impls live in `:app:byCompose:common`.
- **Coroutines:** inject `CoroutineDispatcher`/`CoroutineScope` rather than referencing `Dispatchers.*` or `GlobalScope` directly (testability + KMP portability). Keep `suspend` chains main-safe.
- `:*:api` and other library modules favor `explicitApi()` and KDoc on public declarations.

## Core runtime flow (stroke → render → sync)

This crosses several modules and is the thing to understand first:

1. Stylus input on `DrawablePdfPage` (`:rendering:impl`) drives `PdfDrawingState` (`:drawing:api`): `startDrawing` → `addPoint` → `finishDrawing`, producing a normalized `DrawingPath`.
2. `DrawablePdfPage` recomposes from `PdfDrawingState` and composites the stroke over the cached PDF page bitmap (Android uses a low-latency overlay during active strokes).
3. The editor turns the finished stroke into a `StrokeDelta.Added` and calls `SyncEngine.applyLocal(...)` (`:sync`). The engine stamps a logical clock, persists to the SQLDelight pending-delta queue, and broadcasts via `KtorPeerServer`/`KtorSyncClient`.
4. Remote deltas arrive at `SyncEngine.processPeer(...)`, merge last-writer-wins (tombstones win over concurrent adds), and are applied back into the relevant `PdfDrawingState`. On desktop a headless host projection can buffer/save deltas with no editor open.

## Quality workflow

- detekt and ktlint are auto-applied to every real Kotlin module via the `subprojects {}` block in the root [build.gradle.kts](build.gradle.kts). Config: [config/detekt/detekt.yml](config/detekt/detekt.yml) with `buildUponDefaultConfig = true` (only deviations from defaults are listed).
- **Each module has its own `detekt-baseline.xml`** capturing pre-existing findings. When detekt flags legacy code unrelated to your change, regenerate the module's baseline (`./gradlew :<module>:detektBaseline`) rather than refactoring around it. Don't silence findings with `@Suppress`; fix the cause or adjust the rule in `detekt.yml` with justification.
- Screenshot tests use **Roborazzi + Compose Desktop UI test** in `:reflow:impl` (`jvmTest`). Record golden images with `-Proborazzi.test.record=true`; otherwise the test verifies against committed images.

## Packaging & release

- **Desktop:** `createReleaseDistributable` builds the app-image (ProGuard + obfuscation on). macOS/Linux installers via jpackage (`TargetFormat.Dmg`/`Deb`). **Windows is packaged by Inno Setup** ([installer/windows/notepen.iss](installer/windows)), not jpackage — extend Windows file associations there. A portable no-install Windows ZIP is produced by the `packageReleasePortableZip` task.
- **Release tags must be `v1.0.0` or higher** — the macOS jpackage build rejects `0.x` versions, so never tag a `v0.x` release.
