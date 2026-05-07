# feat-main-screen-redesign

**Тип:** FEATURE
**Модули:** `:app:byCompose:common`, `:shared`
**Статус:** В работе

## Описание

Переработать главный экран приложения NotePen:
- Список недавних файлов с превью (миниатюра первой страницы PDF, генерируется на лету)
- Кнопка «Открыть» — открытие файла через нативный проводник (Android / Desktop)
- Адаптивная вёрстка: смартфоны + стандартные мониторы
- Хранение истории последних файлов локально (DataStore или аналог)
- Платформы: Android + Desktop (Compose Multiplatform)

## Уточнения от PO

- Модули: common + shared
- История файлов: хранится локально, механизма ещё нет → нужно реализовать
- Превью: миниатюра первой страницы PDF, генерируется на лету
- Дизайн: разработать самостоятельно через @Designer
- Адаптивность: корректное отображение на смартфонах и стандартных мониторах

## Артефакты

- requirements file: vault/concepts/common/requirements/main_screen_redesign.md
- corner cases: vault/concepts/common/plans/main_screen_redesign-corner-cases.md
- test cases: vault/reference/common/test-cases/main_screen_redesign-test-cases.md
- spec: vault/reference/common/spec/main_screen_redesign.md

---

## Контрольные точки

## 2026-05-07T00:01:00Z
- ВЫПОЛНЕНО: intake requirements pipeline — Feature: main_screen_redesign, Module: common

## 2026-05-07T00:10:00Z
- ВЫПОЛНЕНО: CCR business loop завершён (3 итерации + решения PO); BA создал 23 AC
- ВЫПОЛНЕНО: corner case register — 4 Critical, 11 High, 5 Medium, 2 Low
- ДАЛЕЕ: QA REQUIREMENTS draft

## 2026-05-07T00:30:00Z
- ВЫПОЛНЕНО: requirements pipeline полностью завершён (BA×1, CCR-business×3+PO, CCR-technical×3, CC register, QA 51 TC, SA spec, CoverageChecker PASS, ConsistencyChecker PASS)
- ДАЛЕЕ: sign-off PO

## 2026-05-07T01:00:00Z
- ВЫПОЛНЕНО: фича папок добавлена в требования (AC-24–AC-56), спецификацию, реестр угловых случаев (CC-23–CC-28) и тест-кейсы (TC-52–TC-90, итого 90 TC)
- ВЫПОЛНЕНО: ConsistencyChecker PASS после серии исправлений нумерации AC
- ДАЛЕЕ: sign-off PO → дизайн + поиск + план

## 2026-05-07T01:30:00Z
- ВЫПОЛНЕНО: OQ-2 закрыт (AC-57/AC-58, lastPageIndex, BL-14, updateLastPage, NavigationTarget sealed, clamp)
- ВЫПОЛНЕНО: ConsistencyChecker PASS — все 58 AC согласованы с спецификацией, 93 TC

## 2026-05-07T01:35:00Z
- ВЫПОЛНЕНО: PO одобрил requirements package via /kit-approve
- ДАЛЕЕ: Step 2 SEARCH + Step 3 DESIGN (параллельно) → Step 5 PLAN

## 2026-05-07T18:30:00Z
- ВЫПОЛНЕНО: SEARCH (vault) — паттерны найдены
- ВЫПОЛНЕНО: DESIGN — @Designer создал полный Material 3 дизайн (7 состояний, адаптивная сетка, компоненты)
- ВЫПОЛНЕНО: PLAN — plan.md + 7 stage-файлов создан (vault/concepts/common/plans/main_screen_redesign-plan.md)
- ВЫПОЛНЕНО: Pre-mortem — 8 рисков, 5 ACT-NOW (R1 serialization, R2 nav smoke, R4 FileLock IO, R5 M3 only, R6 TestDispatcher)
- ВЫПОЛНЕНО: QA IMPL DRAFT — добавлено TC-94..TC-125 (32 impl-level TCs, итого 125 TC)
- ДАЛЕЕ: CONFIRM → PO /kit-approve → EXECUTE

## 2026-05-07T18:35:00Z
- ВЫПОЛНЕНО: PO одобрил план via /kit-approve
- ДАЛЕЕ: EXECUTE — Stage 01

## 2026-05-07T19:10:00Z
- ВЫПОЛНЕНО: Stage 01 — домен + порты + DTO + FileHistoryManager (11 тестов, PASS)
- Исправлено: @Serializable убран с доменных сущностей (созданы DTO в infrastructure/dto), ADR-002 в DECISIONS.md
- NOT_RUN_GAP: TC-94, TC-99, TC-100, TC-40, TC-124 (edge-cases, закроются в QA IMPL FINAL)
- ДАЛЕЕ: Stage 02 — Use cases

## 2026-05-07T20:00:00Z
- ВЫПОЛНЕНО: Stage 02 — AddToHistoryUseCase, CheckAvailabilityUseCase, OpenRecentFileUseCase, порты FolderRepository/ThumbnailRepository/PdfThumbnailGenerator/FileAvailabilityChecker (26 тестов, PASS)
- Исправлено: ioDispatcher инъекция (ADR-003), broad Exception с логированием (ADR-004), null-guard в SAF fuzzy-match, тесты Semaphore(5) и timeout
- NOT_RUN_GAP: TC-07, TC-08, TC-23, TC-27, TC-37, TC-38 (UI/ViewModel — закроются в Stage 03-04)
- ДАЛЕЕ: Stage 03 — MainScreenViewModel + UiState + FilePicker expect

## 2026-05-07T20:45:00Z
- ВЫПОЛНЕНО: Stage 03 — MainScreenViewModel (MVI), UiState-модели, MainScreenIntent, FilePicker expect/actuals, 9 тестов (PASS)
- Исправлено: bare Exception в loadInitialData → CancellationException re-throw + лог + errorEvent; cancelNavigation → best-effort лог; двойной getAll() → один вызов; open убран с use-case классов
- ДАЛЕЕ: Stage 04 — UI-компоненты + MainContent

## 2026-05-07T21:30:00Z
- ВЫПОЛНЕНО: Stage 04 — ThumbnailView, StatusBadge, RecentFileCard, FolderCard, EmptyState, диалоги, MainContent (адаптив Scaffold), MainScreenComponent, ThumbnailPainter expect/actual
- Исправлено: compose.material убран из deps; blocking I/O → produceState+IO; OpenCreateFolderDialog intent; LazyVerticalGrid не вложен в LazyColumn; иконки из спеки; ADR-005
- ДАЛЕЕ: Stage 05 — Инфраструктура Android

## 2026-05-07T22:15:00Z
- ВЫПОЛНЕНО: Stage 05 — FileHistoryRepositoryAndroid, FolderRepositoryAndroid, FileAvailabilityCheckerAndroid, PdfThumbnailGeneratorAndroid, ThumbnailRepositoryAndroid, FilePicker Android actual
- Исправлено: bitmap.recycle() в finally-блоке (CC-11 OOM); evictIfNeeded total только по .png-файлам
- ДАЛЕЕ: Stage 06 — Инфраструктура Desktop

## 2026-05-07T22:50:00Z
- ВЫПОЛНЕНО: Stage 06 — FileHistoryRepositoryDesktop (Mutex+FileLock), FolderRepositoryDesktop, FileAvailabilityCheckerDesktop, PdfThumbnailGeneratorDesktop (PdfBox Loader API), ThumbnailRepositoryDesktop, FilePicker Desktop (EventQueue.invokeAndWait)
- Исправлено: FilePicker диалог через EDT invokeAndWait (HIGH); тесты Stage 06 написаны (jvmTest)
- ДАЛЕЕ: Stage 07 — Навигация + wiring

## 2026-05-07T23:30:00Z
- ВЫПОЛНЕНО: Stage 07 — MainComponent интерфейс, DefaultRootComponent с factory-callback, RootContent (LaunchedEffect навигации), DetailsComponent.saveLastPageIndex (BL-14/AC-57), удалены ListComponent/DefaultListComponent/ListContent, DI wiring Android/Desktop
- Исправлено: as? safe cast (нет ClassCastException); Material2→3 в DetailsContent и App; !! убран; BL-14 реализован через coroutineScope; ADR-006 для onOpenFilePicker
- ДАЛЕЕ: QA IMPL FINAL → CCR IMPL → Traceability → DoD Gate

## 2026-05-08T00:15:00Z
- ВЫПОЛНЕНО: SecurityReviewer HIGH fixes — dimension bounds check (1..4096) в PdfThumbnailGeneratorAndroid/Desktop; атомарный rename через Files.move(REPLACE_EXISTING) в FileHistoryRepositoryDesktop и FolderRepositoryDesktop
- ВЫПОЛНЕНО: TestRunner AUTO_VERIFY — 22 TC PEND→PASS
- dod_waiver: 6.1 — coverage tool not configured (JaCoCo/Kover не подключён в проекте)
- dod_waiver: 6.2 — coverage tool not configured (threshold check невозможен без настройки)
- ДАЛЕЕ: TraceabilityChecker re-run → DoD Gate re-run
