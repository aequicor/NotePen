# UIKit Sandbox

`:app:byCompose:uikit-sandbox` is a living architecture sample for NotePen feature internals
and the shared UI for the standalone UIKit showcase apps.

Use it as the default template when adding or refactoring features:

- the root package exposes only the API needed by other Gradle modules:
  `UikitSandboxApp.kt`;
- the sample component contract stays `internal` because it is consumed only inside this module;
- `domain/` is pure Kotlin: models, repository ports, use cases, domain failures.
- `data/` implements domain ports: local source shaped like a Room DAO, Ktor remote source, DTOs, explicit mappers.
- `presentation/component/` owns Decompose implementations and internal component factories.
- `presentation/store/` owns MVIKotlin stores, reducers, executors, actions, messages, intents, and labels.
- `presentation/model/` owns presentation mappers between component/domain/store models.
- `presentation/ui/` owns Compose content.
- `presentation/utils/` owns presentation-local adapters.
- `di/` wires dependencies with Koin factories. Stores are not registered in DI; each component creates its own store through `instanceKeeper.getStore`.
- `utils/` holds cross-layer helpers such as dispatcher abstractions and cancellation-safe calls.

The parent module stays a feature/library module. Standalone launchers live below it:

- `:app:byCompose:uikit-sandbox:desktop` runs a Compose Desktop app.
- `:app:byCompose:uikit-sandbox:android` installs an Android debug app.

The showcase app displays UIKit controls, project screen fragments, dialogs, sheets, snackbars,
notifications, route transitions, light/dark themes, RU/EN labels, and a portrait/landscape preview
toggle. Android also applies the orientation toggle to the host Activity; Desktop keeps it as an
in-window preview aspect switch.

## Module API

External code uses this module only to launch the standalone showcase app:

```text
uikitsandbox/
  UikitSandboxApp.kt

  domain/
  data/
  presentation/
    component/
    model/
    store/
    ui/
    utils/
  di/
  utils/
```

Rules:

- `UikitSandboxApp` is the only public API because the Android/Desktop wrapper modules need a stable entry point;
- `UikitSandboxComponent` is `internal`; it is not a public contract until another module actually consumes it;
- `UikitSandboxComponentFactory` is `internal` and creates the Decompose component without importing Compose UI;
- Decompose implementations live in `presentation/component/` and are `internal`;
- presentation model mappers live in `presentation/model/`;
- presentation adapters live in `presentation/utils/`;
- stores, reducers, executors, repositories, data sources, DTOs, and mappers are `internal`;
- Compose content receives only `UikitSandboxComponent`; it never imports stores or intents;
- `explicitApi()` is enabled so every public declaration is intentional.

## api/impl Split

Prefer a single feature module with `internal` implementation details.

Create separate `:feature:api` / `:feature:impl` modules only when all conditions hold:

- the feature is isolated from other features;
- it owns a complete user journey;
- it is large enough that module boundaries pay for themselves, roughly 50+ classes;
- consumers need only public contracts/factories, while implementation details must stay physically hidden.

Do not use `api/impl` only because a class can be `internal` inside one module.

## Verification

```bash
./gradlew :app:byCompose:uikit-sandbox:check
./gradlew :app:byCompose:uikit-sandbox:desktop:assemble
./gradlew :app:byCompose:uikit-sandbox:android:assembleDebug
./gradlew :app:byCompose:uikit-sandbox:runDesktop
./gradlew :app:byCompose:uikit-sandbox:installAndroidDebug
```
