---
genre: how-to
title: "Stage 07: Navigation wiring + lastPageIndex trigger + build cleanup"
topic: main-screen
module: common + shared
stage: 07
feature: main_screen_redesign
---

# Stage 07: Навигация, wiring, lastPageIndex trigger (`:common` + `:shared`)

**Модули:** `:app:byCompose:common`, `:shared`  
**Sourceset:** `commonMain`, `androidMain`, `jvmMain`  
**Статус:** TODO  
**Зависит от:** Stage 03–06

---

## Цель

Подключить новый `MainScreenComponent` в Decompose-навигацию, удалить старые заглушки (`ListComponent`, `ListContent`), реализовать trigger `updateLastPage` при выходе из редактора.

**ACT-NOW R2:** smoke-тест навигации — открытие файла из главного экрана ведёт в редактор и возвращает на главный экран.

---

## Шаг 1: Обновить `RootComponent.kt` (`:shared`)

```kotlin
// shared/src/commonMain/...
interface RootComponent {
    val stack: Value<ChildStack<*, Child>>
    fun onBackClicked(toIndex: Int)

    sealed class Child {
        class MainChild(val component: MainScreenComponent) : Child()   // ← было: ListChild
        class DetailsChild(val component: DetailsComponent) : Child()
    }
}
```

---

## Шаг 2: Обновить `DefaultRootComponent.kt` (`:shared`)

```kotlin
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val historyRepository: FileHistoryRepository,
    private val folderRepository: FolderRepository,
    private val addToHistory: AddToHistoryUseCase,
    private val checkAvailability: CheckAvailabilityUseCase,
    private val openRecentFileUseCase: OpenRecentFileUseCase,
    private val thumbnailRepository: ThumbnailRepository,
    private val thumbnailGenerator: PdfThumbnailGenerator,
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    override val stack: Value<ChildStack<*, RootComponent.Child>> =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = Config.Main,
            handleBackButton = true,
            childFactory = ::child,
        )

    private fun child(config: Config, ctx: ComponentContext): RootComponent.Child = when (config) {
        is Config.Main -> RootComponent.Child.MainChild(mainComponent(ctx))
        is Config.Details -> RootComponent.Child.DetailsChild(detailsComponent(ctx, config))
    }

    private fun mainComponent(ctx: ComponentContext) = MainScreenComponent(
        componentContext = ctx,
        historyRepository = historyRepository,
        folderRepository = folderRepository,
        addToHistory = addToHistory,
        checkAvailability = checkAvailability,
        openRecentFileUseCase = openRecentFileUseCase,
        thumbnailRepository = thumbnailRepository,
        thumbnailGenerator = thumbnailGenerator,
        onOpenEditor = { uri, lastPageIndex -> navigation.push(Config.Details(uri, lastPageIndex)) },
        onOpenFilePicker = { /* обрабатывается внутри MainScreenComponent через FilePicker */ },
    )

    private fun detailsComponent(ctx: ComponentContext, config: Config.Details) = DefaultDetailsComponent(
        componentContext = ctx,
        title = config.uri,
        onBackListener = {
            // BL-14: вызвать updateLastPage перед навигацией назад (Stage 07 шаг 4)
            navigation.pop()
        },
    )

    override fun onBackClicked(toIndex: Int) = navigation.popTo(toIndex)

    @Serializable
    private sealed interface Config {
        @Serializable data object Main : Config
        @Serializable data class Details(val uri: String, val lastPageIndex: Int = 0) : Config
    }
}
```

---

## Шаг 3: Обновить `RootContent.kt` (`:common`)

```kotlin
@Composable
fun RootContent(component: RootComponent, modifier: Modifier = Modifier) {
    Children(
        stack = component.stack,
        modifier = modifier,
        animation = stackAnimation(fade()),
    ) {
        when (val child = it.instance) {
            is RootComponent.Child.MainChild -> {
                val state by child.component.viewModel.state.collectAsState()
                // Обработка навигации из ViewModel
                LaunchedEffect(state.navigationTarget) {
                    when (val target = state.navigationTarget) {
                        is NavigationTarget.Editor -> {
                            // навигация выполняется через onOpenEditor callback
                            // ViewModel уже установил navigationTarget — обработано в MainScreenComponent
                            child.component.viewModel.onNavigationHandled()
                        }
                        NavigationTarget.FilePicker -> {
                            // FilePicker вызывается из MainContent
                        }
                        null -> {}
                    }
                }
                MainContent(
                    state = state,
                    onIntent = { child.component.viewModel.onIntent(it) },
                    modifier = modifier,
                )
            }
            is RootComponent.Child.DetailsChild -> DetailsContent(
                component = child.component,
                modifier = modifier,
            )
        }
    }
}
```

> Детали интеграции навигации (как `onOpenEditor` колбэк вызывается из ViewModel) — @CodeWriter уточняет в ходе реализации.

---

## Шаг 4: BL-14 — `updateLastPage` trigger

### Android

В `DetailsContent.kt` (или lifecycle-aware composable):

```kotlin
// Запускается при onPause/onStop — используем DisposableEffect с lifecycle
@Composable
fun DetailsContent(component: DetailsComponent, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                // BL-14: сохранить текущую страницу
                component.saveLastPageIndex()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // ... существующий UI ...
}
```

`DetailsComponent` получает новый метод:

```kotlin
interface DetailsComponent {
    // ... существующие ...
    fun saveLastPageIndex()  // вызывает historyRepository.updateLastPage(uri, currentPage)
}
```

### Desktop

```kotlin
// В DefaultRootComponent — при навигации назад (pop):
private fun detailsComponent(ctx: ComponentContext, config: Config.Details): DetailsComponent =
    DefaultDetailsComponent(
        componentContext = ctx,
        uri = config.uri,
        onBackListener = {
            // Desktop: сохранить страницу перед pop
            // onEditorClosed вызывает MainScreenComponent.viewModel.onIntent(ScreenVisible)
            scope.launch {
                historyRepository.updateLastPage(config.uri, currentPage)
                navigation.pop()
            }
        },
    )
```

---

## Шаг 5: DI — ручная инъекция зависимостей

Создать `DependencyContainer` (или `AppComponent`) в точке входа каждой платформы:

### Android (`MainActivity.kt` или аналог)

```kotlin
val historyRepo = FileHistoryRepositoryAndroid(applicationContext)
val folderRepo = FolderRepositoryAndroid(applicationContext)
val availabilityChecker = FileAvailabilityCheckerAndroid(applicationContext)
val thumbnailRepo = ThumbnailRepositoryAndroid(applicationContext)
val thumbnailGenerator = PdfThumbnailGeneratorAndroid(applicationContext)

val rootComponent = DefaultRootComponent(
    componentContext = defaultComponentContext(),
    historyRepository = historyRepo,
    folderRepository = folderRepo,
    addToHistory = AddToHistoryUseCase(historyRepo),
    checkAvailability = CheckAvailabilityUseCase(availabilityChecker, historyRepo),
    openRecentFileUseCase = OpenRecentFileUseCase(availabilityChecker),
    thumbnailRepository = thumbnailRepo,
    thumbnailGenerator = thumbnailGenerator,
)
```

### Desktop (`main.kt`)

Аналогично с Desktop-реализациями.

---

## Шаг 6: Удалить устаревшие файлы

- `shared/src/commonMain/.../ListComponent.kt` → **удалить**
- `shared/src/commonMain/.../DefaultListComponent.kt` → **удалить**
- `common/src/commonMain/.../ListContent.kt` → **удалить**

---

## Тесты Stage 07

| Тест | CC / AC |
|------|---------|
| Smoke-тест навигации: `OpenRecentFile` → `navigationTarget = Editor` → обработан → `null` | **ACT-NOW R2** |
| `updateLastPage` вызывается при onPause (Android) | AC-57 |
| Desktop: `updateLastPage` вызывается при закрытии окна редактора | AC-57 |
| Возврат на главный экран после закрытия редактора → `ScreenVisible` запускает CheckAvailability | AC-17 |
| Старые `ListComponent` и `ListContent` не присутствуют в коде | — |

---

## Acceptance Criteria, закрываемые этим этапом

AC-10 (навигация → редактор), AC-13 (навигация файл-пикер), AC-17 (Desktop screen-visible trigger), AC-57 (updateLastPage при выходе), AC-58 (lastPageIndex передаётся в редактор), CC-15 (Desktop sync).

---

## Контрольные точки Stage 07

- [ ] `./gradlew compileKotlin` — весь проект зелёный
- [ ] `./gradlew :shared:test` + `./gradlew :app:byCompose:common:test` — все тесты пройдены
- [ ] `./gradlew detekt ktlintCheck` — без ошибок
- [ ] **ACT-NOW R2:** запуск Desktop-приложения, открытие файла из диалога и из списка, возврат на главный экран
- [ ] `ListComponent.kt`, `DefaultListComponent.kt`, `ListContent.kt` — удалены из проекта
