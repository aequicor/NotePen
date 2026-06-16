# API uikit-sandbox и границы feature-модуля

## Контекст

Новые и постепенно мигрируемые feature-модули NotePen должны быть читаемы снаружи через корневой публичный фасад, когда такой фасад действительно нужен внешним Gradle-модулям. Старый `api/impl` split остаётся допустимым только для крупных автономных фич с цельным user journey и реальной потребностью отделить контракт от реализации на уровне Gradle-модулей. `uikit-sandbox` служит запускаемой витриной UI; его единственный внешний потребитель — дочерние Android/Desktop wrapper-модули, поэтому публичный API parent-модуля ограничен `UikitSandboxApp`.

## Источник истины

- `AGENTS.md` и `CLAUDE.md` — правила для ИИ-агентов.
- `app/byCompose/uikit-sandbox/README.md` — краткая структура эталонного модуля.
- `app/byCompose/uikit-sandbox/src/commonMain/kotlin/ru/kyamshanov/notepen/uikitsandbox/`:
  - `UikitSandboxApp.kt`
- `app/byCompose/uikit-sandbox/src/commonMain/kotlin/ru/kyamshanov/notepen/uikitsandbox/UikitSandboxComponent.kt` — internal component contract для UI внутри parent-модуля.
- `app/byCompose/uikit-sandbox/src/commonMain/kotlin/ru/kyamshanov/notepen/uikitsandbox/presentation/component/UikitSandboxComponentFactory.kt` — internal Decompose/domain/data factory без Compose rendering.
- `app/byCompose/uikit-sandbox/src/commonMain/kotlin/ru/kyamshanov/notepen/uikitsandbox/presentation/store/` — MVIKotlin state machine:
  - `UikitSandboxStore.kt` — публичные для presentation intent/state/label контракты Store.
  - `UikitSandboxStoreFactory.kt` — только сборка Store: bootstrapper, executor, reducer.
  - `UikitSandboxExecutor.kt` — side effects, вызовы use case-ов, `dispatch(Msg)` и `publish(Label)`.
  - `UikitSandboxReducer.kt` — только `State + Msg -> State`.
  - `UikitSandboxAction.kt` и `UikitSandboxMsg.kt` — bootstrap actions и reducer messages.
- `app/byCompose/uikit-sandbox/src/commonMain/kotlin/ru/kyamshanov/notepen/uikitsandbox/presentation/model/UikitSandboxModelMappers.kt` — мапперы Store/component/domain моделей и ошибок.
- `app/byCompose/uikit-sandbox/src/commonMain/kotlin/ru/kyamshanov/notepen/uikitsandbox/presentation/ui/` — standalone showcase UI:
  - `UikitSandboxAppContent.kt`
  - `FoundationToolShowcase.kt`
  - `ProjectFragmentsShowcase.kt`
  - `OverlayFlowShowcase.kt`
  - `SandboxPlatform.kt`
- `app/byCompose/uikit-sandbox/android/` и `app/byCompose/uikit-sandbox/desktop/` — тонкие Android/Desktop launch-модули.

## Инварианты

- В корне пакета фичи лежит только API, который реально нужен внешним Gradle-модулям. Для `uikit-sandbox` это только `UikitSandboxApp`.
- Не делать `UikitSandboxFeature`, `Dependencies` или `Result` публичными “для примера”, если они используются только внутри parent-модуля.
- `UikitSandboxApp` — публичная standalone-точка входа для wrapper-приложений. Она создаёт internal component factory и не раскрывает `presentation.*` наружу.
- `UikitSandboxComponentFactory` лежит в `presentation/component/`, создаёт Decompose component и domain/data graph, но не содержит Compose rendering.
- `Default*Component` лежит в `presentation/component/` и остаётся `internal`.
- Stores, executors, reducers, actions, messages, intents и labels лежат в `presentation/store/`; executor испускает labels/messages, reducer только возвращает новый state.
- Мапперы component/domain/store моделей лежат в `presentation/model/`; adapter-ы вроде `StateFlow -> Value` лежат в `presentation/utils/`.
- Compose internals лежат в `presentation/ui/`.
- Repositories, data sources, DTO, mappers и DI wiring лежат ниже `data/` или `di/` и остаются `internal`.
- UI работает только с internal `UikitSandboxComponent`; Store/Intent/Msg/Label не импортируются из UI и не видны app-модулям.
- `api/impl` Gradle-модули применяются только для крупных изолированных фич, где `internal` внутри одного модуля недостаточен.
- Все non-private declarations в `uikit-sandbox`, включая `internal`, документируются русскоязычным KDoc; публичный API wrapper-ов не должен раскрывать internal component graph.
- Standalone запуск добавляется дочерними модулями `:app:byCompose:uikit-sandbox:desktop` и `:app:byCompose:uikit-sandbox:android`; не превращать parent sandbox в Android application, иначе он перестанет быть эталоном feature-модуля.
- Переключатель ориентации — общий `expect` в showcase UI: Android actual меняет `Activity.requestedOrientation`, JVM actual ничего не делает, потому что desktop preview меняет только аспект внутреннего фрейма.

## Логика Store

Store читается сверху вниз по ролям, а не как один большой файл. `UikitSandboxStoreFactory` должен оставаться wiring-слоем: он создаёт Store, подключает `SimpleBootstrapper(UikitSandboxAction.Start)`, `UikitSandboxExecutor` и `UikitSandboxReducer`, но не содержит бизнес-ветвлений.

Executor отвечает за всё, что не является чистым изменением state: старт наблюдения, отмену предыдущего observe job, вызовы `ObserveSandboxWidgetsUseCase`, `RefreshSandboxWidgetsUseCase`, `ToggleSandboxWidgetPinnedUseCase`, обработку `SandboxOutcome`, отправку `UikitSandboxMsg` через `dispatch` и одноразовых эффектов через `publish(Label)`. Если логика должна вызвать suspend/use case, создать coroutine, выбрать label или преобразовать ошибку в сообщение, она принадлежит executor-у или mapper-у, но не reducer-у.

Reducer остаётся чистой функцией состояния. Он не вызывает use case-ы, не публикует labels, не читает flow и не делает маппинг домена в UI. Его единственная задача — принять `UikitSandboxMsg` и вернуть новый `UikitSandboxStore.State`: loading/error/filter/widgets изменяются только здесь.

`UikitSandboxMsg` описывает изменения, понятные reducer-у (`Refreshing`, `FilterChanged`, `WidgetsChanged`, `Failed`, `Completed`). `UikitSandboxStore.Intent` описывает действия пользователя на границе component/store, а `UikitSandboxStore.Label` — одноразовые эффекты наружу (`OpenWidget`, `ShowMessage`). Мапперы между component/domain/store моделями и текстами ошибок лежат в `presentation/model/`, чтобы executor и reducer не разрастались преобразованиями.

## Проверка

- `./gradlew :app:byCompose:uikit-sandbox:check`
- `./gradlew :app:byCompose:uikit-sandbox:desktop:assemble`
- `./gradlew :app:byCompose:uikit-sandbox:android:assembleDebug`

## Связанные тесты

- `app/byCompose/uikit-sandbox/src/commonTest/kotlin/ru/kyamshanov/notepen/uikitsandbox/domain/ObserveSandboxWidgetsUseCaseTest.kt`
