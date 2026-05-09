---
genre: test-cases
title: Test cases — pdf-toolbar-redesign
topic: feature
updated: 2026-05-09
---

# Test cases — pdf-toolbar-redesign

> Spec: ./spec.md | Plan: ./plan.md
> Legend: PASS / FAIL / SKIP / PENDING

| TC ID | Тип | Шаг | Описание | Проверяет | Статус |
|-------|-----|-----|----------|-----------|--------|
| TC-1 | unit | 1 | `DrawablePdfPage(isDrawingEnabled=false)` — drag-жест не вызывает `PdfDrawingState.startDrawing()` | AC-2 | SKIP (compose-ui-test-infra — vault/tech-debt/common/compose-ui-test-infra.md) |
| TC-2 | unit | 1 | `DrawablePdfPage(isDrawingEnabled=true)` — drag-жест вызывает `startDrawing()` и `addPoint()` | AC-3 | SKIP (compose-ui-test-infra — vault/tech-debt/common/compose-ui-test-infra.md) |
| TC-3 | unit | 5 | `PdfFloatingToolbar(hasAnnotations=false, isSaving=false)` — кнопка Save отрисована с `enabled=false` | AC-5 | SKIP (compose-ui-test-infra) |
| TC-4 | unit | 5 | `PdfFloatingToolbar(scale=200)` — кнопка ZoomIn отрисована с `enabled=false` | AC-9 | SKIP (compose-ui-test-infra) |
| TC-5 | unit | 5 | `PdfFloatingToolbar(scale=10)` — кнопка ZoomOut отрисована с `enabled=false` | AC-10 | SKIP (compose-ui-test-infra) |
| TC-6 | unit | 3 | `AnnotationRepositoryJvm.save("test.pdf", mapOf(0 to listOf(path)))` создаёт файл `test.pdf.notepen.json` с корректным JSON | AC-4 | PENDING |
| TC-7 | unit | 3 | `AnnotationRepositoryJvm.save(...)` при недоступном пути (директория не существует) возвращает `Result.failure(IOException)` | EC-1 | PENDING |
| TC-8 | unit | 3 | `AnnotationRepositoryAndroid.save("test.pdf", mapOf(0 to listOf(path)))` создаёт файл `test.pdf.notepen.json` с корректным JSON | AC-4 | SKIP (требует Android instrumented tests; логика идентична AnnotationRepositoryJvm — TC-6 покрывает контракт) |
| TC-9 | unit | 5 | `PdfFloatingToolbar(isSaving=true)` — кнопка Save отрисована с `enabled=false` | EC-3 | SKIP (compose-ui-test-infra) |
| TC-10 | manual | 4 | Desktop: открыть многостраничный PDF → вертикальная полоса прокрутки видна справа от LazyColumn | AC-11 | PENDING |
| TC-11 | manual | 6 | Режим рисования выкл (по умолчанию) → провести пальцем/мышью → LazyColumn прокручивается, штрих не появляется | AC-2 | PENDING |
| TC-12 | manual | 6 | Включить режим рисования → провести мышью → штрих появляется, LazyColumn не прокручивается | AC-3 | PENDING |
| TC-13 | manual | 6 | Нарисовать аннотацию → нажать «Сохранить» → рядом с PDF появился `.notepen.json`, Snackbar «Сохранено» виден | AC-4, AC-6 | PENDING |
| TC-14 | manual | 6 | Сохранить в путь без прав записи → Snackbar с ошибкой; нарисованные аннотации остаются в памяти | EC-1 | PENDING |
