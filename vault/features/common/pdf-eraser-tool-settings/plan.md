---
genre: feature-plan
title: План реализации — Инструмент-ластик и панель настроек пера / ластика
topic: feature
module: common
status: DRAFT
updated: 2026-05-09
---

# План реализации — pdf-eraser-tool-settings

> Этот файл — изменяемая часть фичи. spec.md рядом — frozen после CONFIRM.
> Все ссылки `AC-*` / `EC-*` / `TC-*` — на спецификацию `spec.md` соседнего файла.

## Slice budget

| Параметр | Лимит | Текущее |
|----------|-------|---------|
| max_steps | 8 | 7 |
| max_files_per_step | 6 | 4 |
| max_lines_per_step | 350 | TBD (заполняется в EXECUTE) |
| max_tokens_per_step | 30 000 | TBD (заполняется в 5.1 EXTRACT) |
| missing_runnable_steps | 0 | 0 |
| internal_steps (требует policies.allow_internal_steps=true) | 0 | 1 |

Отметка: один internal-шаг (Шаг 1 — добавление доменных моделей и расширение
`AnnotationRepository`-сигнатур) не имеет user-visible Runnable. Это backend-only слой,
который читается из последующих UI-шагов. Если `policies.allow_internal_steps != true` —
@Main BLOCK на этом этапе и потребует от PO либо включить флаг, либо склеить шаг 1
с шагом 2 (тогда max_lines_per_step может быть превышен).

## Implementation plan

- [x] Step 1: Доменные модели + persistence-схема
  - Goal: ввести `PenSettings`, `EraserSettings` (+ `EraserShape`), `ToolMode`,
    `ToolsBundle`; расширить `AnnotationData`/`AnnotationBundle`/`AnnotationRepository.save`;
    обновить JVM- и Android-реализации `AnnotationRepository`. Поверхность чисто backend —
    UI пока на старых моделях.
  - Owned: AC-15 (часть, persistence), AC-16, AC-17, AC-19, EC-12, EC-13
  - Files:
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/PenSettings.kt (new)
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/EraserSettings.kt (new)
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/ToolMode.kt (new)
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/AnnotationRepository.kt (modify)
    - app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/AnnotationRepositoryJvm.kt (modify)
    - app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/AnnotationRepositoryAndroid.kt (modify)
  - Public signatures:
    - `@Serializable data class PenSettings(color, strokeWidth, alpha)` + companion (DEFAULT_*, PRESET_COLORS, MIN/MAX)
    - `@Serializable enum class EraserShape { CIRCLE, SQUARE }`
    - `@Serializable data class EraserSettings(shape, sizeNormalized)` + companion
    - `enum class ToolMode { NONE, PEN, ERASER }`
    - `@Serializable data class ToolsBundle(pen, eraser)`
    - `AnnotationRepository.save(pdfPath, annotations, scale, pen, eraser): Result<Unit>`
    - `AnnotationBundle(pages, scale, pen, eraser)`
  - Guidelines: forbidden_patterns (`!!`, lateinit, hardcoded, `@Suppress` без issue-id),
    KMP — никакого Android Context в commonMain.
  - Test strategy: tdd_first
  - Runnable: internal — backend-слой настроек ещё не виден пользователю до Шага 5;
    верификация через unit-тесты на сериализацию и обратную совместимость.

- [x] Step 2: Точечное стирание в `PdfDrawingState`
  - Goal: добавить `erasePointsInZone(centerX, centerY, halfSizeNormalized, shape): Boolean`
    с алгоритмом разрезания штриха на подштрихи (CIRCLE / SQUARE). Старые методы остаются
    без изменений.
  - Owned: EC-3, EC-4, EC-5, EC-6, EC-7, AC-13 (доменная часть), AC-10 (CIRCLE/SQUARE метрика)
  - Files:
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/PdfDrawingState.kt (modify)
  - Public signatures:
    - `fun PdfDrawingState.erasePointsInZone(centerX: Float, centerY: Float, halfSizeNormalized: Float, shape: EraserShape): Boolean`
  - Guidelines: forbidden_patterns; функция не должна стать god-method (вынести
    `splitPath(path, predicate): List<DrawingPath>` приватной helper-функцией).
  - Test strategy: tdd_first
  - Runnable: internal — UI-вход появится в Шаге 4; пока — unit-тесты `PdfDrawingStateTest`,
    которые можно гонять локально (`./gradlew :app:byCompose:common:test`).

- [x] Step 3: StrokeCap.Round + StrokeJoin.Round в `DrawablePdfPage` (минимальный визуальный fix)
  - Goal: обновить вызовы `drawPath(... style = Stroke(width=..., cap=StrokeCap.Round,
    join=StrokeJoin.Round))` для отрисовки `currentPaths` и `currentPath`. Никаких других
    правок этого файла на этом шаге — изоляция изменения для проверки AC-9 в одиночку.
  - Owned: AC-9
  - Files:
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DrawablePdfPage.kt (modify)
  - Public signatures: без изменений
  - Guidelines: dp-токены, `MaterialTheme.colorScheme`; никаких ручных цветов.
  - Test strategy: test_after (визуальный AC, юнит-тестов на отрисовку нет — TC-25 manual)
  - Runnable: запусти приложение, открой любой PDF, включи рисование, проведи штрих —
    концы и стыки округлены.

- [x] Step 4: `DrawablePdfPage` — поддержка `ToolMode`, settings-параметров, ластика и индикатора
  - Goal: заменить параметр `isDrawingEnabled: Boolean` на `toolMode: ToolMode` +
    `penSettings: PenSettings` + `eraserSettings: EraserSettings`. Развести обработку жестов:
    PEN — текущая логика (с использованием `penSettings`), ERASER — вызов `erasePointsInZone`
    каждый кадр + рисование индикатора зоны. NONE — без `pointerInput` (как раньше при `false`).
    `DetailsContent` обновляется к новой сигнатуре через временные дефолты до Шага 5.
  - Owned: AC-9 (повторная проверка с настройками), AC-12, AC-13, AC-14, EC-1, EC-2, EC-8
  - Files:
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DrawablePdfPage.kt (modify)
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DetailsContent.kt (modify — временный wiring через дефолты до Шага 6)
  - Public signatures:
    - `fun DrawablePdfPage(bitmap, pdfDrawingState, toolMode: ToolMode, penSettings: PenSettings, eraserSettings: EraserSettings, modifier)`
  - Guidelines: forbidden_patterns; никаких side-effect в @Composable теле без
    LaunchedEffect/DisposableEffect; recomposition-safe чтения mutableStateOf.
  - Test strategy: mixed (unit-тест жестового слоя — best-effort; основная верификация — manual)
  - Runnable: открой PDF; в `DetailsContent` временно держи `toolMode = ERASER` через
    `mutableStateOf` (без UI-кнопок ластика — они будут в Шаге 5); проведение пальцем по
    штриху удаляет точки и разрезает штрих; индикатор виден под пальцем.

- [x] Step 5: `PenSettingsPanel` + `EraserSettingsPanel` (composables)
  - Goal: реализовать оба composables (commonMain) — слайдеры, LazyRow пресетов цвета,
    переключатель формы ластика. Все размеры — private dp-токены файлов; все цвета —
    `MaterialTheme.colorScheme.*`.
  - Owned: AC-6, AC-7, AC-8, AC-10, AC-11, AC-18, EC-10, EC-11, EC-15
  - Files:
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/PenSettingsPanel.kt (new)
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/EraserSettingsPanel.kt (new)
  - Public signatures:
    - `@Composable fun PenSettingsPanel(settings: PenSettings, onChange: (PenSettings) -> Unit, modifier: Modifier = Modifier)`
    - `@Composable fun EraserSettingsPanel(settings: EraserSettings, onChange: (EraserSettings) -> Unit, modifier: Modifier = Modifier)`
  - Guidelines: forbidden_patterns; никаких хардкоженных dp в теле composable.
  - Test strategy: test_after (композиционные тесты — best-effort; для compose-multiplatform
    инфраструктура UI-тестов отмечена как tech-debt в `vault/tech-debt/common/compose-ui-test-infra.md`).
    Если инфра доступна — TC-20, TC-21 как unit; иначе — manual в Шаге 7.
  - Runnable: построй модуль, открой `PenSettingsPanel(PenSettings()) { }` через
    composable preview / временную точку входа — три контрола видны и реагируют.

- [ ] Step 6: `PdfFloatingToolbar` — кнопки Перо/Ластик + раскрывающаяся секция настроек
  - Goal: расширить toolbar новой сигнатурой (см. spec.md § Публичные сигнатуры);
    кнопка «Перо» / «Ластик» — взаимоисключаемые тогглы; под колонкой кнопок отображается
    либо `PenSettingsPanel`, либо `EraserSettingsPanel`, либо ничего — по `toolMode`.
    Сохранить все существующие AC-1..AC-13 предыдущей фичи (проверяется в TRACE 5.8).
  - Owned: AC-1, AC-2, AC-3, AC-4, AC-5, AC-18
  - Files:
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/PdfFloatingToolbar.kt (modify)
  - Public signatures:
    - `fun PdfFloatingToolbar(toolMode, onToolModeChange, penSettings, onPenSettingsChange, eraserSettings, onEraserSettingsChange, hasAnnotations, isSaving, onSave, scale, onZoomIn, onZoomOut, modifier)`
  - Guidelines: forbidden_patterns; private dp-токены; recomposition-safe чтения.
  - Test strategy: test_after (TC-22, TC-23, TC-24 — unit при наличии compose-test-infra,
    иначе manual)
  - Runnable: открой PDF; кнопка «Перо» включает перо и раскрывает PenSettingsPanel;
    кнопка «Ластик» — наоборот; повторное нажатие на активный инструмент сворачивает.

- [ ] Step 7: `DetailsContent` — финальный wiring + persistence applied/saved
  - Goal: завести `var penSettings`, `var eraserSettings`, `var toolMode` в `DetailsContent`;
    при загрузке `bundle` применять `bundle.pen` / `bundle.eraser`; при `onSave` передавать
    их в `AnnotationRepository.save(...)`. `LaunchedEffect(toolMode)` финализирует
    незавершённые штрихи на всех `drawingStates` при переключении инструмента (EC-1, EC-2).
    Удалить старый `isDrawingEnabled: Boolean`-флаг из стейта `DetailsContent`.
  - Owned: AC-2..AC-5, AC-14, AC-15, AC-16, AC-17, AC-19, EC-1, EC-2, EC-9, EC-14
  - Files:
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DetailsContent.kt (modify)
  - Public signatures: без изменений (DetailsContent — entry point, не публичен снаружи модуля).
  - Guidelines: forbidden_patterns; recomposition-safe; никаких side-effect вне
    LaunchedEffect / DisposableEffect.
  - Test strategy: test_after (manual — TC-30, TC-31, TC-33 главные; unit на этом уровне
    зависит от compose-test-infra)
  - Runnable: открой PDF, поменяй цвет / толщину пера, нарисуй штрих, сохрани, закрой и
    снова открой PDF — слайдеры и пресет вернулись к сохранённым значениям; штрихи
    отображаются.

## Diff-review

(заполняется на шаге 5.10 — после прохождения всех шагов EXECUTE)

## Definition of Done

(заполняется @Verifier MODE=DOD на шаге 5.9)
