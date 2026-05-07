---
genre: test-cases
title: "Тест-кейсы: Перетаскивание PDF в папки"
module: common
feature: drag-drop-pdf
status: RECONCILE
updated: 2026-05-07
---

# Тест-кейсы — Перетаскивание PDF в папки (drag-drop-pdf)

**Модуль:** common
**Фича:** drag-drop-pdf
**Источник:** `vault/features/common/drag-drop-pdf/feature.md` § Test plan

---

## Как работает этот файл

Живой документ, принадлежащий `@TestKeeper`:

- `MODE=GENERATE` — создаёт этот файл из `feature.md § Test plan`.
- `MODE=DRAFT` — добавляет impl-уровневые TCs (unit-edge, integration, error) после написания плана реализации.
- `MODE=EXECUTE` — запускает тест-сьют после `@CodeWriter` и обновляет `Status` для каждого TC.
- `MODE=RECONCILE` — после последнего шага привязывает ссылки `Test impl` и перезапускает полный сьют.
- `MODE=RERUN` — повторная проверка конкретного TC после исправления или обхода PO.
- `MODE=APPEND` — добавляет новую строку TC из свободного описания бага.

`@BugFixer` меняет `Status` FAIL→PASS и запись в Defects log OPEN→FIXED после исправления.

PO может редактировать файл напрямую — изменить Status на FAIL, добавить строку, дописать Note — и `/kit-fix` подхватит через `MODE=SCAN`.

Колонка `Notes` не заполняется AI-агентами автоматически: она зарезервирована для наблюдений человека при ручном тестировании.

---

## Легенда статусов

`PEND` (не запущен) • `PASS` • `FAIL` • `SKIP`

## Жизненный цикл дефекта

`OPEN` → `FIXED` → `VERF`

---

## Тест-кейсы

| TC ID | Status | Type | Description | Verifies | Test impl | Notes |
|-------|--------|------|-------------|----------|-----------|-------|
| TC-1 | PASS | unit | ViewModel: `DragStarted` переводит `dragState` в `Dragging` с корректными `fileId`/`fileUri` | AC-1 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/MainScreenIntentTest.kt:11, app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/model/DragStateTest.kt:19, app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt:484, app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/component/RecentFileCardStateTest.kt:76 | |
| TC-2 | PASS | unit | ViewModel: `DragCancelled` переводит `dragState` обратно в `Idle` | AC-3, AC-4 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/MainScreenIntentTest.kt:38, app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt:502, app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/component/RecentFileCardStateTest.kt:92 | |
| TC-3 | PASS | unit | ViewModel: `DropOnFolder` вызывает `folderRepository.addFile(folderId, fileUri)` и возвращает `dragState = Idle` | AC-2 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt:519 | |
| TC-4 | PASS | unit | ViewModel: `DropOnFolder` обновляет `fileCount` папки без повторной загрузки всего списка | AC-7 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt:843 | |
| TC-5 | PASS | unit | ViewModel: `DropOnFolder` при `dragState = Idle` (нет активного drag) — операция игнорируется | AC-3 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt:543 | |
| TC-6 | PASS | unit | ViewModel: `DropOnFolder` при `isLoading = true` — операция игнорируется | EC-4 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt:560 | |
| TC-7 | PASS | unit-edge | ViewModel: `DropOnFolder` → `FileDuplicateInFolderException` → `successEvent = FileAlreadyInFolder`, `dragState = Idle` | AC-5 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt:590 | |
| TC-8 | PASS | unit-edge | ViewModel: `DropOnFolder` → `FolderNotFoundException` → `errorEvent = FolderOperationFailed`, `dragState = Idle` | AC-11, EC-1 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt:645 | |
| TC-9 | PASS | unit-edge | ViewModel: `DropOnFolder` → `FileNotInHistoryException` → `errorEvent = FolderOperationFailed`, `dragState = Idle` | EC-3 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt:703 | |
| TC-10 | PASS | unit-edge | ViewModel: быстрые последовательные `DropOnFolder` в одну папку с одним файлом — только первый вызов проходит, второй получает `FileDuplicateInFolderException` | EC-9 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt:761 | |
| TC-11 | PASS | unit-edge | ViewModel: параллельный `DragStarted` и `launchAvailabilityCheck` — `dragState` не затирает `availabilityStatus` и наоборот | EC-6 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt:800 | |
| TC-12 | PASS | unit | ViewModel: `DropOnFolder` когда папки нет на экране (folders пустой список) — `FolderNotFoundException` обрабатывается корректно | AC-9 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt:881 | |
| TC-13 | PASS | unit | ViewModel: файл в состоянии `ARCHIVED_UNAVAILABLE` перетащен в папку — `addFile` вызывается, статус не изменяется | AC-12 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt:916 | |
| TC-14 | PASS | integration | Desktop: `FolderRepositoryDesktop.addFile` → успешное добавление → `getFilesInFolder` содержит URI | AC-2, AC-10 | app/byCompose/common/src/jvmTest/kotlin/ru/kyamshanov/notepen/mainscreen/infrastructure/FolderRepositoryDesktopTest.kt#addFile_success_fileAppearsInGetFilesInFolder | |
| TC-15 | PASS | integration | Desktop: `FolderRepositoryDesktop.addFile` → повторное добавление → `FileDuplicateInFolderException` | AC-5 | app/byCompose/common/src/jvmTest/kotlin/ru/kyamshanov/notepen/mainscreen/infrastructure/FolderRepositoryDesktopTest.kt#addFile_duplicate_throwsFileDuplicateInFolderException | |
| TC-16 | PASS | integration | Desktop: `FolderRepositoryDesktop.addFile` с несуществующим `folderId` → `FolderNotFoundException` | EC-1 | app/byCompose/common/src/jvmTest/kotlin/ru/kyamshanov/notepen/mainscreen/infrastructure/FolderRepositoryDesktopTest.kt#addFile_unknownFolderId_throwsFolderNotFoundException | |
| TC-17 | SKIP | integration | Desktop: `FolderRepositoryDesktop.addFile` с `uri`, отсутствующим в истории → `FileNotInHistoryException` | EC-3 | (skip: history check is enforced at ViewModel level, not repository level; EC-3 covered by TC-9 PASS) | |
| TC-18 | PEND | e2e | Android: перетащить карточку файла на папку мышью/пальцем → Snackbar «Файл добавлен», счётчик папки +1 | AC-2, AC-8 | (pending) | |
| TC-19 | PEND | e2e | Desktop: перетащить карточку файла на папку мышью → Snackbar «Файл добавлен», счётчик +1 | AC-10 | (pending) | |
| TC-26 | PASS | unit | ViewModel: DropOnFolder — поведение идентично на широком экране (LazyVerticalGrid, AC-8); dragState→None, successEvent=FileAddedToFolder | AC-8 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt#dropOnFolder_wideLayout_stateIdenticalToNarrowLayout | |
| TC-20 | PEND | manual | Android: начать drag, уйти в фон, вернуться — drag отменён, экран в нормальном состоянии | EC-5 | (pending) | |
| TC-21 | PEND | manual | Desktop: перетащить внешний файл из проводника ОС на FolderCard — drop отклонён, ничего не произошло | EC-11 | (pending) | |
| TC-22 | PEND | manual | Android: поворот экрана в момент drag — drag отменён, состояние не повреждено | EC-12 | (pending) | |
| TC-23 | PEND | manual | Визуальная проверка: карточка файла становится полупрозрачной при drag; папка-цель подсвечивается при hover > 400 мс | AC-1, AC-6 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/component/RecentFileCardStateTest.kt:60 (alpha), :22 (isBeingDragged), app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/component/FolderCardStateTest.kt:21 (isHovered) — unit PASS; визуальный hover-тайм требует PO-верификации | |
| TC-24 | PASS | unit-edge | ViewModel: `DropOnFolder` с именем папки длиной > 40 символов — имя усекается до 40 символов + «…» в `SuccessEvent.FileAddedToFolder` | EC-8 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt:615 | |
| TC-25 | PASS | unit | ViewModel: `OnSuccessEventHandled` очищает `successEvent` (→ null) в состоянии после успешного drop | AC-2 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt:844 | |

**Type:** `unit` | `unit-edge` | `integration` | `error` | `e2e` | `manual`.
**Verifies:** идентификаторы AC/EC из `feature.md`, перечисленные через запятую.
**Test impl:** заполняется `@TestKeeper RECONCILE` — `tests/path/to/file.kt:line`.

---

## Покрытие

### Критерии приёмки (AC)

| AC | Покрывающие TC |
|----|---------------|
| AC-1 | TC-1, TC-23 |
| AC-2 | TC-3, TC-14, TC-18, TC-25 |
| AC-3 | TC-2, TC-5 |
| AC-4 | TC-2 |
| AC-5 | TC-7, TC-15 |
| AC-6 | TC-23 |
| AC-7 | TC-4 |
| AC-8 | TC-18, TC-26 |
| AC-9 | TC-12 |
| AC-10 | TC-14, TC-19 |
| AC-11 | TC-8 |
| AC-12 | TC-13 |

### Граничные случаи (EC) — Critical/High

| EC | Severity | Покрывающие TC |
|----|----------|---------------|
| EC-1 | Critical | TC-8, TC-16 |
| EC-2 | Critical | TC-10 |
| EC-3 | Critical | TC-9, TC-17 |
| EC-4 | High | TC-6 |
| EC-5 | High | TC-20 |
| EC-6 | High | TC-11 |
| EC-8 | Medium | TC-24 |

---

## Журнал дефектов

> Только добавление. Каждая запись ссылается на TC по идентификатору.

_(пусто — дефекты появятся при обнаружении в ходе тестирования)_
