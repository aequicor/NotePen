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
| TC-20 | common | AC-6, AC-7, AC-8 | Снапшот `PenSettingsPanel`: оба слайдера + LazyRow пресетов рендерятся; `onChange` зовётся при клике на пресет / при движении слайдера | PEND | (pending) |
| TC-21 | common | AC-10, AC-11 | Снапшот `EraserSettingsPanel`: переключатель формы и слайдер размера; `onChange` зовётся при переключении формы | PEND | (pending) |
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
