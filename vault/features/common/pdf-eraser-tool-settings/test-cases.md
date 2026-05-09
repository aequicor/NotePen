---
genre: feature-test-cases
title: Тест-кейсы — Инструмент-ластик и панель настроек пера / ластика
topic: feature
module: common
status: PEND
updated: 2026-05-09
---

# Test cases — pdf-eraser-tool-settings

> Сгенерировано @Verifier MODE=GENERATE из spec.md § Тест-план.
> Поле `Status` ∈ {PEND, PASS, FAIL, SKIP}. Все строки начинаются как PEND.
> Поле `Test impl` заполняется при @Verifier MODE=RECONCILE на шаге 5.7.

## Test cases

| TC ID | Module | Verifies | Description | Status | Test impl |
|-------|--------|----------|-------------|--------|-----------|
| TC-1  | common | AC-2, AC-4 | `ToolMode` начальное = NONE; переключение PEN → клик PEN снова даёт NONE | PEND | (pending) |
| TC-2  | common | AC-2, AC-3 | Активация ластика при активном пере деактивирует перо (взаимоисключаемость) | PEND | (pending) |
| TC-3  | common | EC-3 | `PdfDrawingState.erasePointsInZone` — стирание середины штриха разрезает на 2 подштриха; первая точка каждого имеет `isNewPath = true` | PEND | (pending) |
| TC-4  | common | EC-4 | `erasePointsInZone` — стирание начала / конца штриха не разрезает, а укорачивает | PEND | (pending) |
| TC-5  | common | EC-5 | `erasePointsInZone` — зона полностью накрывает штрих → штрих удаляется из `currentPaths` | PEND | (pending) |
| TC-6  | common | EC-6 | `erasePointsInZone` — несколько штрихов под зоной обрабатываются независимо | PEND | (pending) |
| TC-7  | common | EC-7 | `erasePointsInZone` — подштрихи с < 2 точками отбрасываются | PEND | (pending) |
| TC-8  | common | AC-10, AC-13 | `erasePointsInZone(shape=CIRCLE)` использует круговую метрику; SQUARE — квадратную | PEND | (pending) |
| TC-9  | common | EC-3 | `erasePointsInZone` сохраняет `color` / `strokeWidth` родительского штриха в подштрихах | PEND | (pending) |
| TC-10 | common | AC-17 | `PenSettings` дефолты: color=Black, strokeWidth=10f, alpha=1f | PEND | (pending) |
| TC-11 | common | AC-17 | `EraserSettings` дефолты: shape=CIRCLE, sizeNormalized=DEFAULT_SIZE_NORMALIZED | PEND | (pending) |
| TC-12 | common | EC-10, EC-11 | `EraserSettings.sizeNormalized` clamping через slider range: значения < MIN / > MAX отвергаются | PEND | (pending) |
| TC-13 | common | AC-15, AC-16 | `PenSettings` сериализуется/десериализуется через kotlinx.serialization, alpha сохраняется | PEND | (pending) |
| TC-14 | common | AC-15, AC-16 | `EraserSettings` сериализуется/десериализуется через kotlinx.serialization | PEND | (pending) |
| TC-15 | common | AC-15 | `AnnotationRepositoryJvm.save(...)` записывает JSON с полями `tools.pen` и `tools.eraser` | PEND | (pending) |
| TC-16 | common | AC-19 | `AnnotationRepositoryJvm.load(...)` со старым JSON (без `tools`) возвращает `AnnotationBundle` с дефолтным pen / eraser | PEND | (pending) |
| TC-17 | common | AC-16 | `AnnotationRepositoryJvm.load(...)` с новым JSON возвращает `AnnotationBundle` с pen / eraser из файла | PEND | (pending) |
| TC-18 | common | EC-12 | `AnnotationRepositoryJvm.save(...)` при IOException возвращает `Result.failure` | PEND | (pending) |
| TC-19 | common | AC-15 | `AnnotationRepositoryAndroid.save(...)` создаёт JSON с `tools` | PEND | (pending) |
| TC-20 | common | AC-6, AC-7, AC-8 | Снапшот `PenSettingsPanel`: оба слайдера + LazyRow пресетов рендерятся; `onChange` зовётся при клике на пресет / при движении слайдера | PART | PenSettingsPanelLogicTest.kt (state-mapping); panel render = manual (no compose-test-infra) |
| TC-21 | common | AC-10, AC-11 | Снапшот `EraserSettingsPanel`: переключатель формы и слайдер размера; `onChange` зовётся при переключении формы | PART | PenSettingsPanelLogicTest.kt (state-mapping); panel render = manual (no compose-test-infra) |
| TC-22 | common | AC-1 | `PdfFloatingToolbar(toolMode = NONE)` — ни одна секция настроек не отрисована | PEND | (pending) |
| TC-23 | common | AC-2 | `PdfFloatingToolbar(toolMode = PEN)` — `PenSettingsPanel` отрисован; `EraserSettingsPanel` нет | PEND | (pending) |
| TC-24 | common | AC-3 | `PdfFloatingToolbar(toolMode = ERASER)` — `EraserSettingsPanel` отрисован; `PenSettingsPanel` нет | PEND | (pending) |
| TC-25 | common | AC-9 | manual: перо вкл → штрих рисуется со скруглёнными концами и стыками (`StrokeCap.Round` + `StrokeJoin.Round`) | PEND | (manual) |
| TC-26 | common | AC-12 | manual: ластик вкл → удержание пальца показывает индикатор зоны выбранной формы / размера | PEND | (manual) |
| TC-27 | common | AC-13, EC-3 | manual: ластик вкл → проведение по штриху удаляет точки покадрово, штрих разрезается | PEND | (manual) |
| TC-28 | common | AC-14 | manual: после полного стирания всех штрихов ластик остаётся активным, меню настроек ластика открыто | PEND | (manual) |
| TC-29 | common | AC-15 | manual: «Сохранить» → `<имя>.notepen.json` содержит блок `tools` с актуальными значениями pen и eraser | PEND | (manual) |
| TC-30 | common | AC-16 | manual: закрыть и снова открыть PDF → слайдеры пера и ластика, выбранный пресет цвета и форма ластика — те же | PEND | (manual) |
| TC-31 | common | AC-19 | manual: PDF с аннотациями старого формата (без `tools`) — штрихи отображаются, инструменты — дефолтные | PEND | (manual) |
| TC-32 | common | AC-18, EC-15 | manual: тёмная тема → слайдеры, фон панели, индикатор ластика контрастны и согласованы с `MaterialTheme.colorScheme` | PEND | (manual) |
| TC-33 | common | EC-1, EC-2 | manual: переключение пера на ластик во время незавершённого штриха — штрих фиксируется, новый жест начинает стирание | PEND | (manual) |

## Defects log

| Date | Step | Severity | Source | Description | Status |
|------|------|----------|--------|-------------|--------|
| 2026-05-09 | 2 | medium | ground-truth-waived | Step 2 (`erasePointsInZone`) — REQUIRED_TYPE=backend (mutation-sample). User approved via `/kit-approve --no-ground-truth`. Не выполнен mutation-sample на новой extension-функции; покрытие подтверждено только unit-тестами `PdfDrawingStateEraseTest` (9 тестов, 138/138 jvmTest green). | WAIVED |
| 2026-05-09 | 3 | medium | ground-truth-waived | Step 3 (`StrokeCap.Round` / `StrokeJoin.Round` в `DrawablePdfPage`) — REQUIRED_TYPE=ui (визуальный AC-9, TC-25 manual). User approved via `/kit-approve --no-ground-truth`. Скриншот не приложен; визуальная верификация AC-9 будет повторена на Шаге 4 (когда `DrawablePdfPage` получит `penSettings`/`toolMode` wiring) или на финальной проверке Шага 7. | WAIVED |
| 2026-05-09 | 4 | medium | ground-truth-waived | Step 4 (`DrawablePdfPage` — `ToolMode`/`penSettings`/`eraserSettings` wiring + eraser gesture handling + zone indicator) — REQUIRED_TYPE=ui (TC-26/TC-27/TC-33 manual: индикатор зоны, покадровое стирание, EC-1/EC-2 переключение). User approved via `/kit-approve --no-ground-truth`. Скриншот/скринкаст не приложен; визуальная верификация будет выполнена на Шаге 6 (UI-тоглы Перо/Ластик в toolbar) и Шаге 7 (финальный wiring + persistence). | WAIVED |
| 2026-05-09 | 5 | medium | ground-truth-waived | Step 5 (`PenSettingsPanel` + `EraserSettingsPanel` composables) — REQUIRED_TYPE=ui (TC-20 / TC-21 panel render manual; state-mapping покрыт `PenSettingsPanelLogicTest`, 7 тестов). User approved via `/kit-approve --no-ground-truth`. Скриншот не приложен; визуальная верификация панелей произойдёт на Шаге 6 (panel embedding в `PdfFloatingToolbar`) и Шаге 7 (финальный wiring). | WAIVED |
| 2026-05-09 | 6 | high | code | Defect A — слайдеры «Толщина» / «Прозрачность» в `PenSettingsPanel` не применяются к рисованию. Корневая причина: `Modifier.pointerInput(toolMode)` в `DrawablePdfPage` использует только `toolMode` как ключ; замыкание `onDragStart` захватывает первое значение `penSettings`, и при изменении settings handler не перезапускается. Fix: ключ расширен до `pointerInput(toolMode, penSettings)`. Origin=code. Ground-truth: screenshot (inline). | FIXED |
| 2026-05-09 | 6 | high | code | Defect B — выбор цвета-пресета не применяется немедленно к следующему штриху. Та же корневая причина, что у Defect A — кэш `pointerInput`. Покрыто тем же fix-ом в `DrawablePdfPage`. Origin=code. Ground-truth: screenshot (inline). | FIXED |
| 2026-05-09 | 6 | medium | code | Defect C — у `FilterChip` «Круг» / «Квадрат» в `EraserSettingsPanel` hover/press-ripple квадратный (выглядит как «квадратное затемнение») вместо скруглённого. Корневая причина: `FilterChipDefaults.shape` = `RoundedCornerShape(8dp)`. Fix: явно задан `shape = CircleShape` для обоих чипов. Origin=code. Ground-truth: screenshot (inline). | FIXED |
| 2026-05-09 | 6 | medium | code | Defect D — у `Slider` в `ToolSettingsFloatingPanel` (PEN: Толщина/Прозрачность; ERASER: Размер) при положении thumb на min/max задняя линия (track) не видна — thumb перекрывает её из-за нового Material 3 thumb-track-gap (Material3 1.8.2 в Compose MP 1.9.0 рисует gap между thumb и track). Fix: использован `Slider(...)` overload со слотом `track = { SliderDefaults.Track(it, thumbTrackGapSize = 0.dp, drawStopIndicator = null) }` — track рисуется на всю ширину под thumb. Origin=code. Ground-truth: pending (manual screenshot). | FIXED |
| 2026-05-09 | 6 | medium | code | Defect E — рядом со слайдерами Толщина/Прозрачность/Размер не отображалось текущее значение и его нельзя было ввести вручную. Fix: рядом с каждым слайдером добавлен компактный `BasicTextField` (≈52dp, MaterialTheme.typography.bodySmall, тонкий outline) с парсингом при Done/Enter и clamping в `min..max` через существующие helpers (`applyStrokeWidth` / `applyAlpha` / `applySize`). Толщина — целое px (1..60), Прозрачность — целое % (0..100), Размер — целое промилле/normalized*1000 для отображения как простое число. Origin=code. Ground-truth: pending (manual screenshot). | FIXED |
| 2026-05-09 | 6 | medium | code | Defect F — суффиксы «dp» и «%» в редактируемых полях значений рядом со слайдерами рендерились визуально смещённо (как мелкий «верхний/нижний индекс» вместо инлайн-суффикса). Корневая причина: внутри `decorationBox` `BasicTextField`'s inner editable слот и сосед `Text(suffix)` имели разные baselines/line-height — `MaterialTheme.typography.bodySmall` на разных платформах добавляет дополнительный leading к lineHeight, в то время как inner-слот `BasicTextField` использует свою собственную метрику. Fix: явно зафиксирован общий `TextStyle` с `fontSize = VALUE_FIELD_FONT_SIZE = 12.sp` и `lineHeight = 12.sp` (zero extra leading), применённый и к `BasicTextField.textStyle`, и к `Text(suffix).style`; `Row` в `decorationBox` уже использовал `Alignment.CenterVertically` — теперь обе дочерние ноды имеют идентичные line-boxes и центрируются попиксельно. Origin=code. Ground-truth: pending (manual screenshot). | FIXED |

| 2026-05-09 | 6 | medium | code | Defect F-v2 (commit 527d06e — user-applied direct edit вне agent-сессии) — финальный fix Defect F: суффикс `Text("dp"/"%")` вынесен наружу `BasicTextField.decorationBox`, теперь рендерится sibling-ом `BasicTextField` в parent `Row(verticalAlignment = CenterVertically)`. Это окончательно решило baseline alignment на всех платформах (in-decorationBox shared TextStyle подход 1cee071 не дал попиксельного результата на real device). Origin=code. Ground-truth: screenshot (inline) — verified by user ("отлично"). | FIXED |
| 2026-05-09 | 6 | medium | ground-truth-waived | Step 6 (PdfFloatingToolbar toggles + ToolSettingsFloatingPanel floating glass panel, final state after defect-fix loop A→F + manual fix 527d06e) — REQUIRED_TYPE=ui (TC-22/TC-23/TC-24 toolbar toggle + panel embedding manual; Defect D/E/F screenshots pending). User approved final state via "отлично" → treated as `/kit-approve --no-ground-truth`. Все pending screenshot-артефакты (Defects D/E/F) waived: визуальная корректность подтверждена пользовательским approve. step_commits[6_final].sha = 527d06e (manual fix outside agent session). | WAIVED |
