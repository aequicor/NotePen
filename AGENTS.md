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
| Architecture sandbox check | `./gradlew :app:byCompose:uikit-sandbox:check` |
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

`settings.gradle.kts` is the source of truth. The repository currently mixes older api/impl modules and newer feature modules. The target architecture for new/refactored code is **feature-based modules** with Clean Architecture packages inside the module: `domain/`, `data/`, `presentation/`, `utils/`, `di/`.

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
- **`:app:byCompose:uikit-sandbox`** — living architecture sample for a normal feature module. It demonstrates `domain/data/presentation/utils/di`, Decompose + MVIKotlin component/store wiring, Koin factory wiring, Ktor-shaped remote data source, Room-shaped local data source contract, and tests.

**Dependency direction:** app entry points and app glue compose feature factories; feature modules depend on shared/domain/design-system contracts; `data` implements `domain` ports; `presentation` depends on `domain` use cases, not on data sources. Feature modules never depend on another feature directly unless an explicit public API module exists and the dependency is architectural, not incidental.

## Architecture conventions

- **Default feature shape:** one feature module with `domain/`, `data/`, `presentation/`, `utils/`, `di/`. Keep implementation details `internal`; expose only component interfaces, factories, and deliberate domain contracts.
- **Feature facade:** every feature module must be readable from the root package first. The root package contains only public API: `<Name>Component.kt`, `<Name>Feature.kt`, `<Name>Dependencies.kt`, `<Name>Result.kt` (or equivalent navigation/result contracts). Put Decompose implementations in `presentation/component/`, stores/reducers/executors in `presentation/store/`, Compose internals in `presentation/ui/`, and repositories/data sources/DTOs/mappers/DI below `data/` or `di/`. Keep implementations `internal`.
- **External usage:** app modules and other features depend on the root facade only. They create/render through `<Name>Feature` and `<Name>Component`; they do not import `data.*`, `domain.*`, `presentation.*`, `di.*`, `utils.*`, or `impl.*` from another feature. UI code talks to `<Name>Component`, never to a MVIKotlin Store.
- **api/impl split is exceptional.** Use `:feature:api` / `:feature:impl` only when the feature is fully isolated from other features, owns a complete user journey, is large enough that module boundaries pay off (roughly 50+ classes), and consumers need only contracts/factories while implementation must be physically hidden. Do not split merely because `internal` would also work.
- **Domain:** `domain/{model,repository,usecase,exception}` is pure Kotlin. No Android SDK, Compose, Decompose, MVIKotlin, Ktor, Room/SQLDelight, DI, logging frameworks, platform file APIs, or hardcoded `Dispatchers.*`. Use cases are small classes with `operator fun invoke`; repository interfaces live in domain and are implemented in data.
- **Data:** `data/` contains repository implementations, local/remote data sources, DTO/entities, and mappers. Room/SQLDelight DAOs and Ktor clients stay behind data-source interfaces. DTO/entity types never cross into domain or presentation state.
- **Presentation:** `presentation/component/` contains Decompose component implementations only; `presentation/store/` contains MVIKotlin stores, reducers, executors, intents, messages, and labels; `presentation/ui/` contains Compose content; `presentation/utils/` contains presentation-local adapters and mappers. UI talks only to component interfaces and never imports MVIKotlin stores/intents/messages directly.
- **DI:** Koin modules live in `di/` and provide repositories, use cases, long-lived clients, dispatchers, and component factories. Stores are not Koin singletons; each component owns its store via `instanceKeeper.getStore { ... }`.
- **expect/actual** is the only mechanism for platform differences — no `if (platform)` branching. Key actual pairs: PDF rasterization & document loading, tablet/stylus input (`WinTab` on Windows, Cocoa on macOS, Android stylus), low-latency overlay, and small utilities (UUID, unicode normalization).
- **Navigation** is Decompose. Parent components own `StackNavigation`/`SlotNavigation`; child components communicate upward through callbacks. `Config` objects are `@Serializable`, data-only, and contain no lambdas, stores, components, or platform objects.
- **MVIKotlin:** stores live in presentation, reducers are pure, executors call use cases, labels represent one-off effects, and `runCatching` in suspend code must rethrow `CancellationException` or use a safe wrapper.
- **Coroutines:** inject `CoroutineDispatcher`/`CoroutineScope` rather than referencing `Dispatchers.*` or `GlobalScope` directly (testability + KMP portability). Keep `suspend` chains main-safe.
- `:*:api` and other library modules favor `explicitApi()` and KDoc on public declarations.
- **KDoc:** project documentation language is Russian. Every public Kotlin interface, class, method, property, variable, and constant must have Russian KDoc. KDoc for public data classes must list all `@property` entries; public functions must document every `@param`, `@return` when non-`Unit`, inputs, outputs, and possible exceptions or explicitly say that exceptions are not thrown outward. Complex internal methods should also have KDoc or concise code comments explaining the algorithm, invariants, side effects, and failure modes. Put KDoc before annotations.
- Before creating a new feature, inspect `:app:byCompose:uikit-sandbox` and follow its structure unless there is a documented reason not to.

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

## Knowledge docs (КД)

Project principle: **code is the documentation**. A КД is only a short navigation note for future agents, not a duplicate spec or code retelling.

Codex must create or update a КД automatically when a task introduces or materially changes a non-obvious architectural decision, rendering/sync/storage pipeline, cross-platform actual pair, cache/invalidation rule, or workaround that future maintainers must preserve. Prefer Russian.

КД lives under `vault/kd/<module>/<slug>.md` and should be concise:

- problem/context in 2-4 sentences;
- where the real source of truth lives, with file paths and key symbols;
- invariants and sharp edges that are not obvious from a local diff;
- verification commands that proved the behavior;
- links to related tests or follow-up debt.

Do not create КД for trivial UI copy, one-line fixes, formatting, dependency bumps with no code impact, or behavior that is already obvious from names and tests. If an existing КД covers the area, update it instead of creating a parallel note.

## Packaging & release

- **Desktop:** `createReleaseDistributable` builds the app-image (ProGuard + obfuscation on). macOS/Linux installers via jpackage (`TargetFormat.Dmg`/`Deb`). **Windows is packaged by Inno Setup** ([installer/windows/notepen.iss](installer/windows)), not jpackage — extend Windows file associations there. A portable no-install Windows ZIP is produced by the `packageReleasePortableZip` task.
- **Release tags must be `v1.0.0` or higher** — the macOS jpackage build rejects `0.x` versions, so never tag a `v0.x` release.
