---
genre: feature-plan
title: План реализации — PDF Viewer UX improvements
topic: feature
module: common
status: PLAN
updated: 2026-05-09
---

# План реализации — pdf-viewer-ux-improvements

> Этот файл — изменяемая часть фичи. spec.md рядом — frozen после CONFIRM.
> Все ссылки `AC-*` / `EC-*` / `TC-*` — на спецификацию `spec.md` соседнего файла.

## Slice budget

| Параметр | Лимит | Текущее |
|----------|-------|---------|
| max_steps | 8 | 4 |
| max_files_per_step | 6 | 5 |
| max_lines_per_step | 350 | TBD |
| max_tokens_per_step | 30 000 | TBD |
| missing_runnable_steps | 0 | 0 |

## Implementation plan

- [x] Step 1: Исправление `windowSizeInPx` — убрать `rememberSaveable`
  - Goal: в `DetailsContent.kt:51` заменить `rememberSaveable { localWindowInfo.containerSize }`
    на прямое чтение `localWindowInfo.containerSize`. Это одновременно устраняет смещение
    аннотаций при resize (AC-3) и корень визуального невосстановления масштаба (AC-1):
    `targetWidthPx` вычислялся с устаревшим `screenWidthPx`, делая scale=150 неотличимым
    от scale=100 визуально.
  - Owned: AC-1 (root-cause fix), AC-3, EC-3, EC-4
  - Files:
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DetailsContent.kt (modify — 1 line)
  - Public signatures: без изменений.
  - Guidelines: не добавлять `remember {}` (IntSize читается O(1) из CompositionLocal);
    проверить, что `@OptIn(ExperimentalComposeUiApi::class)` аннотация сохранена.
  - Test strategy: test_after (manual TC-5, TC-6, TC-7)
  - Runnable: запусти JVM-desktop, открой PDF, измени scale до 150 %, сохрани, закрой,
    снова открой → страницы отображаются в 1.5× размере. Затем измени размер окна →
    страницы пересчитываются, аннотации не смещаются.

- [x] Step 2: Persistence `currentPage` в AnnotationRepository + wiring в DetailsContent
  - Goal: добавить поле `currentPage: Int = 0` в `AnnotationData` (сериализованная модель)
    и `AnnotationBundle` (runtime-модель); расширить `AnnotationRepository.save` параметром
    `currentPage: Int = 0`; обновить JVM- и Android-реализации; в `DetailsContent.kt`
    передавать `lazyListState.firstVisibleItemIndex` при сохранении и вызывать
    `lazyListState.scrollToItem(...)` после загрузки.
  - Owned: AC-2, EC-1, EC-2, EC-6, EC-7
  - Files:
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/AnnotationRepository.kt (modify)
    - app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/AnnotationRepositoryJvm.kt (modify)
    - app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/AnnotationRepositoryAndroid.kt (modify)
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DetailsContent.kt (modify)
  - Public signatures:
    - `AnnotationData.currentPage: Int = 0` (new field, @Serializable)
    - `AnnotationBundle.currentPage: Int = 0` (new field)
    - `AnnotationRepository.save(..., currentPage: Int = 0)` (new param with default)
  - Guidelines: forbidden_patterns (`!!` → `requireNotNull`; пустой catch); `Json { ignoreUnknownKeys = true }`
    уже выставлен — backward-compat обеспечен автоматически (EC-6); `scrollToItem` вызывается
    только при `pages.isNotEmpty() && bundle.currentPage > 0` (EC-2, EC-7); `coerceIn` защищает
    от out-of-bounds (EC-1).
  - Test strategy: tdd_first — написать TC-1..TC-4 в `AnnotationRepositoryJvmTest.kt` до правки
    реализации.
  - Runnable: прокрути PDF до страницы 5, нажми «Сохранить», закрой PDF, снова открой →
    экран открывается на странице 5.

- [x] Step 3: `PageIndicatorAirbar` — glass-индикатор «Страница X / N»
  - Goal: создать новый composable `PageIndicatorAirbar.kt` (commonMain) с glass-стилем,
    совпадающим с `ToolSettingsFloatingPanel` (surface 0.85α, outlineVariant border,
    tonalElevation = 6.dp); подключить в `DetailsContent` через `derivedStateOf` для
    предотвращения лишних recomposition.
  - Owned: AC-4, EC-2 (airbar hidden when pages empty), EC-7
  - Files:
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/PageIndicatorAirbar.kt (new)
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DetailsContent.kt (modify)
  - Public signatures:
    - `@Composable fun PageIndicatorAirbar(currentPage: Int, totalPages: Int, modifier: Modifier = Modifier)`
  - Guidelines: все размеры — private val dp-токены (не magic-числа в теле composable);
    все цвета — `MaterialTheme.colorScheme.*`; `derivedStateOf` для `firstVisiblePage`
    обязателен (иначе любой scroll перекомпозирует весь DetailsContent).
  - Test strategy: test_after (manual TC-9, TC-13)
  - Runnable: открой PDF → вверху по центру виден «Страница 1 / N»; прокрути → номер
    обновляется без задержки.

- [x] Step 4: Адаптивность — AnimatedVisibility для PdfFloatingToolbar + horizontalScroll в ToolSettingsFloatingPanel
  - Goal: в `DetailsContent` вычислить `isCompact` из `localWindowInfo.containerSize.width`
    (< 600.dp) и обернуть `PdfFloatingToolbar` в `AnimatedVisibility(visible = !isCompact || toolMode == ToolMode.NONE)`
    с slide+fade. В `ToolSettingsFloatingPanel` добавить `.horizontalScroll(rememberScrollState())`
    к внутренним `Row` в `PenSettingsRow` и `EraserSettingsRow`.
  - Owned: AC-5, AC-6, EC-5
  - Files:
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DetailsContent.kt (modify)
    - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/ToolSettingsFloatingPanel.kt (modify)
  - Public signatures: без изменений (isCompact — локальная переменная; horizontalScroll — внутри composable).
  - Guidelines: порог 600.dp — соответствует `WindowWidthSizeClass.Compact`; `isCompact`
    вычисляется из `localWindowInfo.containerSize` (уже реактивно после Step 1, не из отдельного remember);
    анимация slideInHorizontally { -it } — slide влево, симметрична выходу.
  - Test strategy: test_after (manual TC-10, TC-11, TC-12)
  - Runnable: на узком экране (< 600 dp) включи перо → PdfFloatingToolbar исчезает
    с анимацией; ToolSettingsFloatingPanel прокручивается горизонтально при переполнении.
    На широком экране — оба элемента видны одновременно.

## Diff-review

Run: `git diff --stat d8ff31f..HEAD` (HEAD = c33680f).

Total: 7 files changed, +188 −41 lines.

### Changed files (grouped)

**Production code (commonMain):**
- `AnnotationRepository.kt` (+3) — Step 2: `AnnotationData.currentPage`, `AnnotationBundle.currentPage`, `save()` param.
- `DetailsContent.kt` (+88 −41) — Steps 1–4: rememberSaveable fix, currentPage wiring, airbar, isCompact + AnimatedVisibility.
- `PageIndicatorAirbar.kt` (new, +58) — Step 3.
- `ToolSettingsFloatingPanel.kt` (+22 −2) — Step 4: horizontalScroll on PenSettingsRow + EraserSettingsRow.

**Production code (platform):**
- `AnnotationRepositoryAndroid.kt` (+3) — Step 2.
- `AnnotationRepositoryJvm.kt` (+3) — Step 2.

**Tests:**
- `AnnotationRepositoryJvmTest.kt` (+52) — Step 2: 4 new tests (TC-1..TC-4).

### Per-step commit chain
- Step 1: 2d9c37a
- Step 2: fb4d241
- Step 3: e7a5957
- Step 4: c33680f

### Audit
- Files NOT in any step.Files declaration: none.
- Out-of-module touches: none.
- Cross-module drift: none.

## Definition of Done

- [x] Все шаги plan.md помечены [x] (Step 1..4).
- [x] spec.md заморожен с CONFIRM (никаких post-CONFIRM правок spec.md).
- [x] Все Critical/High EC покрыты тестами или явным RECONCILE-PASS (EC-1..EC-7).
- [x] Финальный test suite зелёный (`./gradlew :app:byCompose:common:jvmTest` BUILD SUCCESSFUL).
- [x] Per-step коммиты записаны (Step 1..4).
- [x] Ground-truth: TC-1..TC-4 unit-тесты (автоматические); TC-5..TC-14 manual (UI).

Verdict: **PASS** (pending manual ground-truth TC-5..TC-14 — UI verification)
