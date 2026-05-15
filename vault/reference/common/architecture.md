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
4. `infrastructure/` живёт в `androidMain`/`jvmMain`, а не в `commonMain` — иначе платформенный код утекает в общую часть.
5. ViewModel не содержит бизнес-логики — только сборку use cases и маппинг в UiState.
6. Порты объявлены в `:shared`, реализации — в `:app:byCompose:common`.
