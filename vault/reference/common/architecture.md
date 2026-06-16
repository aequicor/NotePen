---
title: NotePen — Architecture Overview
kind: reference
tags:
  - architecture
  - kmp
  - clean-architecture
  - modules
---

# NotePen — Architecture Overview

Kotlin Multiplatform (KMP) приложение для просмотра и аннотирования PDF.
Поддерживаемые платформы: **Android** и **Desktop (JVM/macOS/Windows)**.

---

## Gradle-модули

```
:shared                         — домен, use cases, навигация (Decompose)
:app:byCompose:common           — UI (Compose) + платформенная инфраструктура
:app:byCompose:theme            — Material 3 тема
:app:byCompose:uikit            — переиспользуемые UI-компоненты
:app:byCompose:uikit-sandbox    — эталон feature-модуля с фасадом и слоями
:app:byCompose:android          — Android точка входа (Application/MainActivity)
:app:byCompose:desktop          — Desktop точка входа (main fun)
```

**Граф зависимостей модулей:**

```
:app:byCompose:android  ──┐
                           ├──▶ :app:byCompose:common ──▶ :shared
:app:byCompose:desktop  ──┘         │
                                    ├──▶ :app:byCompose:theme
                                    └──▶ :app:byCompose:uikit
```

`:shared` не зависит ни от одного другого модуля проекта — он чистый.

---

## Целевой стандарт feature-модуля

Новые и постепенно мигрируемые фичи оформляются как feature-based Gradle-модули. Снаружи модуль читается через корневой публичный фасад; внутренние слои скрыты `internal`.

```text
feature/<name>/
  src/commonMain/kotlin/.../<name>/
    <Name>Component.kt
    <Name>Feature.kt
    <Name>Dependencies.kt
    <Name>Result.kt

    domain/
    data/
    presentation/
      component/
      store/
      ui/
      utils/
    di/
    utils/
```

Корневой пакет содержит только публичный API: component interface, фабрику/feature entrypoint, dependencies contract, navigation/result contracts. `Default<Name>Component` лежит в `presentation/component/`, stores/reducers/executors — в `presentation/store/`, Compose internals — в `presentation/ui/`, presentation-local adapters/mappers — в `presentation/utils/`. Repositories, data sources, DTO, mappers и DI wiring лежат внутри `data/` и `di/`. Реализации по умолчанию `internal`.

Включайте `explicitApi()` для feature-модуля, чтобы публичный API был намеренным. UI работает только с `<Name>Component`; app-модули и другие фичи не импортируют `data.*`, `domain.*`, `presentation.*`, `di.*`, `utils.*` или `impl.*` чужой фичи.

Язык проектной Kotlin-документации — русский. Все публичные interface/class/method/property/variable/constant получают KDoc. Для публичных data class перечисляйте все `@property`, для публичных функций — все `@param`, `@return` при не-`Unit`, входные/выходные данные и возможные исключения либо явную отметку, что исключения наружу не выбрасываются. Сложные internal-методы документируются KDoc или короткими комментариями по алгоритму, инвариантам, side effects и failure modes.

Отдельные `:feature:api` / `:feature:impl` Gradle-модули используются только для крупных изолированных фич: цельный пользовательский путь, высокая автономность, примерно 50+ классов и реальная необходимость зависеть от контракта без реализации. Не вводите `api/impl` только ради замены `internal`.

Эталон: `:app:byCompose:uikit-sandbox`.

---

## Clean Architecture — направление зависимостей

```
presentation/ui  →  use cases  →  domain (entities, ports)
                                          ↑
              infrastructure (Android/Desktop) реализует ports
```

| Слой | Где живёт | Что содержит |
|---|---|---|
| **domain** | `:shared/commonMain/…/domain` | `model/`, `port/` (интерфейсы), `exception/` |
| **use cases** | `:shared/commonMain/…/domain/usecase` | один класс = одна операция |
| **infrastructure** | `:app:byCompose:common/{androidMain,jvmMain}/…/infrastructure` | реализации портов |
| **presentation** | `:app:byCompose:common/commonMain/…/ui` | ViewModel, UiState, экраны |
| **navigation** | `:shared/commonMain` (root) | Decompose компоненты |
| **platform** | `:app:byCompose:common/{androidMain,jvmMain}/…/platform` | expect/actual: FilePicker, ThumbnailPainter |

---

## :shared — детали

### Платформенные source sets
| Source set | Платформа |
|---|---|
| `commonMain` | весь shared-код |
| `androidMain` | Android-специфичные модели (если нужны) |
| `jvmMain` | Desktop-специфичные модели |
| `commonTest` | тесты domain и use cases |

### Пакет `mainscreen/domain/`

**model/**
- `RecentFile` — запись истории открытых файлов
- `Folder`, `FolderFileLink` — папки и связи файлов с ними
- `AvailabilityStatus` — доступность файла
- `UriNormalizer`, `UnicodeNormalization` — нормализация путей
- `UuidGenerator` — генерация идентификаторов (абстракция, не java.util.UUID напрямую)
- `FileHistoryManager` — доменная логика управления историей

**port/** (интерфейсы — инжектируются в use cases)
- `FileHistoryRepository` — CRUD истории открытых файлов
- `FolderRepository` — CRUD папок
- `ThumbnailRepository` — кэш миниатюр PDF
- `PdfThumbnailGenerator` — генерация миниатюр из PDF-страниц
- `FileAvailabilityChecker` — проверка доступности файла по URI

**usecase/**
- `AddToHistoryUseCase` — добавить файл в историю (макс. 20)
- `CheckAvailabilityUseCase` — проверить доступность файла
- `OpenRecentFileUseCase` — открыть файл из истории

**infrastructure/dto/** — DTO для JSON-сериализации (kotlinx.serialization):
`RecentFileDto`, `FolderDto`, `FolderFileLinkDto`, `AvailabilityStatusDto`

### Навигация (Decompose)
В корне `commonMain`:
- `RootComponent` / `DefaultRootComponent` — корневой навигационный компонент
- `MainComponent` — экран списка файлов
- `DetailsComponent` / `DefaultDetailsComponent` — экран просмотра PDF

---

## :app:byCompose:common — детали

### commonMain — UI-слой (feature `mainscreen`)

**ui/screen/**
- `MainScreenComponent` — Decompose-компонент главного экрана
- `MainContent` — root Composable главного экрана

**ui/viewmodel/**
- `MainScreenViewModel` — StateFlow-based ViewModel; оркестрирует use cases

**ui/model/**
- `MainScreenUiState`, `RecentFileUiModel`, `FolderUiModel` — UI-состояние
- `DialogState`, `DragState`, `ThumbnailState` — вспомогательные состояния
- `NavigationTarget`, `ErrorEvent`, `SuccessEvent` — одноразовые события

**ui/component/**
- `RecentFileCard`, `FolderCard`, `ThumbnailView`, `StatusBadge`, `EmptyState`
- `DragEventReader`, `DragTransferData` — drag-and-drop (expect/actual)

**ui/dialog/**
- `CreateFolderDialog`, `DeleteFolderDialog`, `SafMergeDialog`

**platform/** (expect/actual)
- `FilePicker` — нативный диалог выбора файла
- `ThumbnailPainter` — отображение миниатюры PDF

### PDF-viewer (commonMain, корневой пакет)
Компоненты PDF-просмотрщика пока живут в корне пакета (не в feature-папке):
`PdfFloatingToolbar`, `PdfDrawingState`, `ScrollablePdfColumn`, `PdfLoader`,
`PdfManager`, `PenSettings`, `EraserSettings`, `ToolMode`, `ToolSettingsFloatingPanel`,
`PageIndicatorAirbar`, `DrawablePdfPage`, `AnnotationRepository`

### androidMain — инфраструктура Android
- `FileHistoryRepositoryAndroid` — JSON в `filesDir`
- `FolderRepositoryAndroid` — JSON в `filesDir`
- `ThumbnailRepositoryAndroid` — файловый LRU-кэш
- `PdfThumbnailGeneratorAndroid` — `PdfRenderer` (Android API)
- `FileAvailabilityCheckerAndroid` — `ContentResolver` + `DocumentFile`
- `AnnotationRepositoryAndroid`
- `FilePicker.android.kt`, `ThumbnailPainter.android.kt`
- `DragEventReader.android.kt`, `DragTransferData.android.kt`

### jvmMain — инфраструктура Desktop
- `FileHistoryRepositoryDesktop` — JSON-файл + `Mutex` + `FileLock`
- `FolderRepositoryDesktop` — `folders.json` + atomic tmp-rename
- `ThumbnailRepositoryDesktop` — файловый LRU-кэш
- `PdfThumbnailGeneratorDesktop` — Apache PDFBox
- `FileAvailabilityCheckerDesktop` — `java.nio.file.Files`
- `AppDataDir` — платформенная директория данных (XDG / AppData / Library)
- `AnnotationRepositoryJvm`, `PdfManagerJvm`
- `FilePicker.jvm.kt`, `ThumbnailPainter.desktop.kt`
- `DragEventReader.jvm.kt`, `DragTransferData.jvm.kt`

---

## Ключевые библиотеки

| Библиотека | Роль |
|---|---|
| Decompose | Multiplatform навигация и lifecycle |
| Compose Multiplatform | UI (Android + Desktop) |
| kotlinx.serialization-json | Персистирование истории и папок |
| kotlinx.coroutines | Структурированная конкуренция |
| Apache PDFBox | Рендеринг PDF на Desktop |
| kotlin-logging | Логирование (common/android/jvm varaint) |

---

## Правила, которые нельзя нарушать

1. `:shared` не импортирует ничего из `:app:*` — только stdlib и kotlinx.
2. `domain/` не содержит Android SDK, Ktor, БД и диспетчеров (`Dispatchers.*`).
3. Диспетчеры инжектируются через конструктор — не хардкодятся.
4. Публичный контракт фичи находится в корне пакета; реализации по умолчанию `internal` и лежат ниже слоёв.
5. `api/impl` split применяется только для крупных автономных фич, где `internal` внутри одного модуля недостаточен.
6. `infrastructure/`/`data/` не протекает в presentation API: DTO/entity не появляются в component model или store state.
7. ViewModel/Component не содержит бизнес-логики — только orchestration, вызов use cases и маппинг в UiState/model.
