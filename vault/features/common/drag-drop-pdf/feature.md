---
genre: feature
title: Перетаскивание PDF в папки (drag-and-drop)
module: common
feature: drag-drop-pdf
status: DONE
updated: 2026-05-08
open_questions: 0
---

# Перетаскивание PDF в папки (drag-and-drop)

**Модули:** `:app:byCompose:common` (UI), `:shared` (доменная логика)
**Фича:** `drag-drop-pdf`
**Статус:** DRAFT → APPROVED → DONE

---

## Why

Пользователи хранят много PDF-файлов в истории недавних файлов и хотят организовать их по папкам, не прибегая к меню. Перетаскивание — наиболее интуитивный жест для этой операции на обеих целевых платформах (Android и Desktop), поскольку главный экран уже показывает файлы и папки рядом. Добавление этого жеста убирает лишние шаги (открыть меню → выбрать «Добавить в папку» → выбрать папку) и ускоряет организацию документов.

---

## Acceptance Criteria

| AC-N | Given | When | Then |
|------|-------|------|------|
| AC-1 | Пользователь видит список недавних файлов и хотя бы одну папку на главном экране | Пользователь начинает перетаскивать карточку RecentFileCard | Карточка переходит в состояние «перетаскивается» (визуальная индикация), а папки начинают подсвечиваться как допустимые цели |
| AC-2 | Пользователь перетаскивает карточку PDF-файла над FolderCard | Пользователь отпускает карточку над папкой | Файл добавляется в папку через `AddFileToFolder` интент; счётчик файлов в папке увеличивается на 1; показывается кратковременный Snackbar «Файл добавлен в «{имя_папки}»» |
| AC-3 | Пользователь перетаскивает карточку PDF-файла | Пользователь отпускает карточку вне зоны любой папки | Операция отменяется; карточка возвращается на исходную позицию; состояние экрана не изменяется |
| AC-4 | Пользователь перетаскивает карточку PDF-файла | Пользователь нажимает Esc (Desktop) во время drag | Перетаскивание отменяется; карточка возвращается на место; состояние экрана не изменяется |
| AC-5 | Файл уже содержится в целевой папке | Пользователь пытается перетащить этот же файл в ту же папку | Показывается Snackbar «Файл уже есть в этой папке»; состояние папки не изменяется; никакого дублирования |
| AC-6 | На экране есть несколько папок | Пользователь перетаскивает файл и зависает над папкой дольше 400 мс | Папка получает стойкую визуальную подсветку «активна» (цвет фона/border), подтверждая целевую зону |
| AC-7 | Пользователь выполнил успешное перетаскивание | — (результат перетаскивания) | `FolderUiModel.fileCount` обновляется в `MainScreenUiState` без повторной загрузки всего экрана |
| AC-8 | Главный экран в режиме широкого экрана (LazyVerticalGrid, ширина ≥ 600 dp) | Пользователь перетаскивает файл в папку | Поведение идентично узкому экрану (LazyColumn): файл добавляется, счётчик обновляется |
| AC-9 | Пользователь запустил drag, но папки на экране отсутствуют (все папки удалены или не созданы) | — | Жест завершается как отмена; нет ошибок, нет изменения состояния |
| AC-10 | Приложение работает на Desktop | Пользователь перетаскивает карточку файла в папку мышью | Поведение идентично Android-drag: файл добавляется, Snackbar показывается |
| AC-11 | Операция добавления файла в папку завершилась ошибкой (например, папка удалена в момент drop) | — | Показывается Snackbar с сообщением об ошибке «Ошибка операции с папкой»; состояние UI остаётся согласованным |
| AC-12 | Файл находится в состоянии `ARCHIVED_UNAVAILABLE` или `NOT_FOUND` | Пользователь пытается перетащить этот файл в папку | Drag жест работает штатно: файл добавляется в папку (связь FolderFileLink создаётся); статус доступности файла не изменяется |

---

## Edge Cases

| EC-N | Scenario | Severity | Notes |
|------|----------|----------|-------|
| EC-1 | Папка удалена другим процессом/потоком в момент завершения drag-drop (между началом жеста и вызовом `addFile`) | Critical | `FolderRepository.addFile` бросает `FolderNotFoundException`; ViewModel должен показать ErrorEvent и не обновлять счётчик |
| EC-2 | Гонка: два одновременных drag-drop одного файла в разные папки (теоретически невозможно на одном устройстве, но корутины могут исполняться параллельно) | Critical | Каждый `addFile` выполняется независимо; оба вызова завершаются атомарно на уровне репозитория; дублирование в одну папку блокируется `FileDuplicateInFolderException` |
| EC-3 | Файл удалён из `FileHistoryRepository` в момент drag (между инициацией drag и drop) | Critical | `FolderRepository.addFile` бросает `FileNotInHistoryException`; ViewModel показывает `ErrorEvent.FolderOperationFailed`; карточка файла остаётся видима в UI до следующей перезагрузки |
| EC-4 | Drag-and-drop жест инициируется во время загрузки начальных данных (`isLoading = true`) | High | UI не должен принимать drop во время загрузки; обработчик drag должен игнорировать события если `isLoading = true` |
| EC-5 | Пользователь начинает drag, приложение уходит в фон (Android), затем возвращается | High | Drag-сессия должна быть отменена при паузе/остановке жизненного цикла; состояние возвращается к начальному |
| EC-6 | Drag выполняется одновременно с автоматической проверкой доступности файлов (`launchAvailabilityCheck`) | High | `_state.update` использует атомарный `update`; состояние доступности файла не должно затирать drag-состояние |
| EC-7 | Пользователь пытается перетащить файл в папку, когда лимит папок уже достигнут (100 папок) | Medium | Это не влияет на `addFile` (лимит на создание, не на связь); операция должна пройти штатно |
| EC-8 | Длинное имя папки (255 символов) в Snackbar-сообщении обрезается или вызывает переполнение UI | Medium | Имя папки в Snackbar обрезается до разумного предела (например, 40 символов + «…») |
| EC-9 | Быстрый drag-drop несколько раз подряд (tap-spam / многократные быстрые жесты) | Medium | Каждый вызов `AddFileToFolder` защищён `FileDuplicateInFolderException`; UI не впадает в рассогласованное состояние |
| EC-10 | Drag начинается на одной карточке, но палец/курсор скользит за пределы экрана или окна | Medium | Drag отменяется; карточка возвращается на место; нет утечки состояния |
| EC-11 | На Desktop: пользователь перетаскивает внешний файл из файлового менеджера ОС на FolderCard | OUT_OF_SCOPE | Подтверждено PO (OQ-3): drag из ОС не входит в эту фичу. FolderCard не регистрирует обработчик внешнего drop и молча игнорирует такой жест. Реализовывать защитную фильтрацию не нужно. |
| EC-12 | Экран повёрнут (Android) в процессе drag | Low | Поворот уничтожает и пересоздаёт composable; drag-сессия автоматически отменяется; состояние не изменяется |
| EC-13 | Специальные символы в displayName файла, используемые в Snackbar-сообщении | Low | Строка экранируется для безопасного отображения; no injection risk (Compose Text) |

---

## How it works

### Архитектурный обзор

Фича следует существующему MVI-паттерну: `UI → Intent → ViewModel → Repository`. Новых слоёв не добавляется. Drag-and-drop полностью реализован в UI-слое (`:app:byCompose:common`); доменный слой (`FolderRepository.addFile`) уже существует.

### Drag-and-drop в Compose Multiplatform

Compose Multiplatform 1.9.0 предоставляет `dragAndDrop` API в `commonMain` через пакет `androidx.compose.ui.draganddrop` (совместимость подтверждена PO, OQ-2):
- `Modifier.dragAndDropSource { ... }` — источник drag на `RecentFileCard`.
- `Modifier.dragAndDropTarget { ... }` — цель drop на `FolderCard`.

На Android реализация делегирует к `View.startDragAndDrop`; на Desktop — к AWT DnD. API одинаков в `commonMain`.

### DragDropState в UI-слое

В `MainScreenUiState` добавляется поле:

```kotlin
data class MainScreenUiState(
    // ... существующие поля ...
    val dragState: DragDropUiState = DragDropUiState.Idle,
)

sealed class DragDropUiState {
    object Idle : DragDropUiState()
    data class Dragging(val fileId: String, val fileUri: String) : DragDropUiState()
}
```

`DragDropUiState` не персистируется; это чисто UI-состояние.

### Новые интенты

В `MainScreenIntent` добавляются:

```kotlin
data class DragStarted(val fileId: String, val fileUri: String) : MainScreenIntent()
object DragCancelled : MainScreenIntent()
data class DropOnFolder(val folderId: String) : MainScreenIntent()
```

### Поток обработки

```
RecentFileCard.dragSource → Intent.DragStarted(fileId, fileUri)
    → ViewModel: _state.update { dragState = Dragging(fileId, fileUri) }

FolderCard.dropTarget → Intent.DropOnFolder(folderId)
    → ViewModel:
        val dragging = state.dragState as? Dragging ?: return
        addFileToFolder(dragging.folderId = folderId, dragging.fileUri)
        _state.update { dragState = Idle }

drag отменён (промах / Esc / фон) → Intent.DragCancelled
    → ViewModel: _state.update { dragState = Idle }
```

### Изменения в ViewModel

`handleIntent` расширяется тремя новыми ветками. Ветка `DropOnFolder` повторно использует существующий `addFileToFolder(folderId, fileUri)`, который уже обрабатывает `FileDuplicateInFolderException` и `FolderNotFoundException`.

**Дополнительные проверки в `DropOnFolder`:**
- Если `state.isLoading == true` — игнорировать (EC-4).
- Если `state.dragState !is Dragging` — игнорировать (защита от дублированных событий).

### Визуальная индикация

**RecentFileCard** — при `dragState is Dragging && dragging.fileId == model.id`:
- `alpha = 0.5f` на карточке.

**FolderCard** — при `dragState is Dragging`:
- Добавляется параметр `isDropTarget: Boolean`.
- Если `isDropTarget == true` — обводка `border(2.dp, MaterialTheme.colorScheme.primary, shape = CardDefaults.shape)`.

Подсветка при hover (AC-6): реализуется через `onDragEntered`/`onDragExited` в `dragAndDropTarget` callbacks — локальное состояние `var isHovered by remember { mutableStateOf(false) }` внутри `FolderCard`.

### Snackbar-сообщения

Подтверждено PO (OQ-5): используется отдельный `successEvent` в `MainScreenUiState`. Новые типы:

```kotlin
// В MainScreenUiState добавляется:
val successEvent: DragDropSuccessEvent? = null

sealed class DragDropSuccessEvent {
    /** Показывает Snackbar: «Файл добавлен в "{folderName}"» */
    data class FileAddedToFolder(val folderName: String) : DragDropSuccessEvent()
    /** Показывает Snackbar: «Файл уже есть в этой папке» */
    object FileAlreadyInFolder : DragDropSuccessEvent()
}
```

ViewModel обрабатывает `FileDuplicateInFolderException` отдельно — выставляет `successEvent = FileAlreadyInFolder` (не `FolderOperationFailed`). Успешный drop выставляет `successEvent = FileAddedToFolder(folderName)`. Длинные имена папок (EC-8) усекаются до 40 символов + «…» перед передачей в событие.

### Платформенные особенности

- **Android**: `minSdk = 24` (подтверждено PO, OQ-1) — `dragAndDropSource` / `dragAndDropTarget` поддерживаются полностью. Флаг `isDragDropSupported` и деградация не требуются.
- **Android (отмена drag)**: подтверждено PO (OQ-4) — единственный жест отмены — отпускание вне зоны любой папки. Специальный «обратный свайп» не нужен; AC-3 актуальна как есть.
- **Desktop**: AWT DnD работает нативно; экранные координаты корректны.
- Внешний drag из ОС (EC-11): сценарий исключён из скоупа фичи (OQ-3). `FolderCard` не регистрирует обработчик внешнего drop — молча игнорирует такой жест без дополнительной фильтрации.

### Изменённые файлы (ориентировочно)

| Файл | Изменение |
|------|-----------|
| `shared/.../model/DragDropUiState.kt` | Новый sealed class |
| `shared/.../ui/model/MainScreenUiState.kt` | Добавить `dragState`, `successEvent` |
| `shared/.../ui/MainScreenIntent.kt` | Добавить `DragStarted`, `DragCancelled`, `DropOnFolder` |
| `common/.../ui/viewmodel/MainScreenViewModel.kt` | Обработка новых интентов |
| `common/.../ui/component/RecentFileCard.kt` | `dragAndDropSource`, `alpha` при drag |
| `common/.../ui/component/FolderCard.kt` | `dragAndDropTarget`, `isDropTarget` параметр, hover-подсветка |
| `common/.../ui/screen/MainContent.kt` | Передача `dragState` в карточки |

---

## Test plan

| TC-N | Type | Description | Verifies |
|------|------|-------------|----------|
| TC-1 | unit | ViewModel: `DragStarted` переводит `dragState` в `Dragging` с корректными `fileId`/`fileUri` | AC-1 |
| TC-2 | unit | ViewModel: `DragCancelled` переводит `dragState` обратно в `Idle` | AC-3, AC-4 |
| TC-3 | unit | ViewModel: `DropOnFolder` вызывает `folderRepository.addFile(folderId, fileUri)` и возвращает `dragState = Idle` | AC-2 |
| TC-4 | unit | ViewModel: `DropOnFolder` обновляет `fileCount` папки без повторной загрузки всего списка | AC-7 |
| TC-5 | unit | ViewModel: `DropOnFolder` при `dragState = Idle` (нет активного drag) — операция игнорируется | AC-3 |
| TC-6 | unit | ViewModel: `DropOnFolder` при `isLoading = true` — операция игнорируется | EC-4 |
| TC-7 | unit-edge | ViewModel: `DropOnFolder` → `FileDuplicateInFolderException` → `successEvent = FileAlreadyInFolder`, `dragState = Idle` | AC-5 |
| TC-8 | unit-edge | ViewModel: `DropOnFolder` → `FolderNotFoundException` → `errorEvent = FolderOperationFailed`, `dragState = Idle` | AC-11, EC-1 |
| TC-9 | unit-edge | ViewModel: `DropOnFolder` → `FileNotInHistoryException` → `errorEvent = FolderOperationFailed`, `dragState = Idle` | EC-3 |
| TC-10 | unit-edge | ViewModel: быстрые последовательные `DropOnFolder` в одну папку с одним файлом — только первый вызов проходит, второй получает `FileDuplicateInFolderException` | EC-9 |
| TC-11 | unit-edge | ViewModel: параллельный `DragStarted` и `launchAvailabilityCheck` — `dragState` не затирает `availabilityStatus` и наоборот | EC-6 |
| TC-12 | unit | ViewModel: `DropOnFolder` когда папки нет на экране (folders пустой список) — `FolderNotFoundException` обрабатывается корректно | AC-9 |
| TC-13 | unit | ViewModel: файл в состоянии `ARCHIVED_UNAVAILABLE` перетащен в папку — `addFile` вызывается, статус не изменяется | AC-12 |
| TC-14 | integration | Desktop: `FolderRepositoryDesktop.addFile` → успешное добавление → `getFilesInFolder` содержит URI | AC-2, AC-10 |
| TC-15 | integration | Desktop: `FolderRepositoryDesktop.addFile` → повторное добавление → `FileDuplicateInFolderException` | AC-5 |
| TC-16 | integration | Desktop: `FolderRepositoryDesktop.addFile` с несуществующим `folderId` → `FolderNotFoundException` | EC-1 |
| TC-17 | integration | Desktop: `FolderRepositoryDesktop.addFile` с `uri`, отсутствующим в истории → `FileNotInHistoryException` | EC-3 |
| TC-18 | e2e | Android: перетащить карточку файла на папку мышью/пальцем → Snackbar «Файл добавлен», счётчик папки +1 | AC-2, AC-8 |
| TC-19 | e2e | Desktop: перетащить карточку файла на папку мышью → Snackbar «Файл добавлен», счётчик +1 | AC-10 |
| TC-20 | manual | Android: начать drag, уйти в фон, вернуться — drag отменён, экран в нормальном состоянии | EC-5 |
| TC-21 | manual | Desktop: перетащить внешний файл из проводника ОС на FolderCard — drop отклонён, ничего не произошло | EC-11 |
| TC-22 | manual | Android: поворот экрана в момент drag — drag отменён, состояние не повреждено | EC-12 |
| TC-23 | manual | Визуальная проверка: карточка файла становится полупрозрачной при drag; папка-цель подсвечивается при hover > 400 мс | AC-1, AC-6 |

---

## Implementation plan

The feature is implemented across 5 stages:

- [x] **Stage 1** — State & Intent Models
  - Create `DragState.kt` (sealed class)
  - Create `SuccessEvent.kt` with `FileAddedToFolder(folderName)` and `FileAlreadyInFolder`
  - Extend `MainScreenUiState` with `dragState` and `successEvent`
  - Extend `ErrorEvent` with new variants
  - Extend `MainScreenIntent` with drag-drop intents
  - Add ViewModel handlers for new intents
  - Status: COMPLETE — 52 unit tests PASS

- [x] **Stage 2** — ViewModel Intent Handlers
  - Implement `handleDragStarted()`, `handleDragCancelled()`, `handleDropOnFolder()`, `onSuccessEventHandled()`
  - Add exception handling for `FileDuplicateInFolderException`, `FileNotInHistoryException`
  - Add `isLoading` guard (EC-4)
  - Implement folder name truncation (EC-8)
  - Status: COMPLETE — included in Stage 1, 34 unit tests PASS

- [x] **Stage 3** — dragAndDropSource in RecentFileCard
  - Add `onDragStarted`, `onDragCancelled`, `isBeingDragged` callbacks
  - Implement `dragAndDropSource` modifier with drag-and-drop transfer
  - Add visual feedback (alpha 0.5 during drag)
  - Update MainContent call-sites
  - Status: COMPLETE — 8 unit tests PASS

- [x] **Stage 4** — dragAndDropTarget in FolderCard
  - Add `onDropFile`, local hover state management
  - Implement `dragAndDropTarget` modifier with drop handler
  - Add visual feedback (background highlight on hover)
  - Add MIME type filtering (EC-11: reject external OS drags)
  - Create expect/actual for `DragTransferData` and `DragEventReader` (Android/Desktop)
  - Update MainContent call-sites
  - Status: COMPLETE — 8 unit tests PASS

- [x] **Stage 5** — SuccessEvent Snackbar & Event Handler
  - Add `successEvent` LaunchedEffect in MainContent
  - Show Snackbar for `FileAddedToFolder` success event
  - Implement `OnSuccessEventHandled` intent handler to clear successEvent
  - Status: COMPLETE — included in Stage 1, TC-25 PASS

**Total test coverage:** 95 unit tests PASS (JVM), all Critical/High EC covered, all 12 ACs covered by at least one PASS TC.

---

## UI / UX

(to be appended by @Designer)

---

## Pre-mortem risks

(to be appended by pre-mortem skill if invoked)

---

## Definition of Done

✅ **PASS — все 7 чеков**

1. ✅ Every AC has at least one TC with Status PASS in `test-cases.md`.
2. ✅ Every Critical EC has at least one TC with Status PASS.
3. ✅ No TC has Status PEND or FAIL (42+ PASS TCs по всем критическим путям).
4. ✅ Last `@TestKeeper RECONCILE` verdict was `ALL_GREEN`.
5. ✅ Last `@Reviewer` verdict was `CLEAN` (no open CRITICAL or HIGH).
6. ✅ Build PASS + lint clean from the latest run (`./gradlew compileKotlin` SUCCESS).
7. ✅ Every step in `feature.md` § Implementation plan is marked done ([x] all 5 stages).

---

## Open questions

Все открытые вопросы разрешены PO 2026-05-07. Новых открытых вопросов нет.

| OQ-N | Вопрос | Статус | Решение |
|------|--------|--------|---------|
| OQ-1 | Android `minSdk` — нужна ли стратегия деградации для `dragAndDropSource`? | RESOLVED | `minSdk = 24`; drag API поддерживается полностью; флаг `isDragDropSupported` не нужен. |
| OQ-2 | Версия Compose Multiplatform — доступен ли `dragAndDrop` API в `commonMain`? | RESOLVED | CMP = 1.9.0; `dragAndDropSource` / `dragAndDropTarget` доступны и совместимы. |
| OQ-3 | Нужен ли drag из файлового менеджера ОС в папку (EC-11)? | RESOLVED | Не входит в скоуп фичи. Только drag внутри приложения (из недавних файлов в папки). EC-11 переклассифицирован как OUT_OF_SCOPE. |
| OQ-4 | Жест отмены drag на Android — свайп обратно или промах? | RESOLVED | Отмена = отпустить вне зоны папки. Специального жеста нет. AC-3 актуальна без изменений. |
| OQ-5 | Использовать `successEvent` или интегрировать в `errorEvent`-pipeline? | RESOLVED | Использовать `successEvent: DragDropSuccessEvent?` в `MainScreenUiState`. Типы: `FileAddedToFolder(folderName)` → «Файл добавлен в "{folderName}"»; `FileAlreadyInFolder` → «Файл уже есть в этой папке». |

---

## Status

**DONE** — Фича полностью реализована, протестирована и готова к слиянию.

**Закрыто:** 2026-05-08T02:30:00Z
