---
genre: feature-plan
title: Implementation plan — pdf-toolbar-redesign
topic: feature
updated: 2026-05-09
---

# Implementation plan & DoD — pdf-toolbar-redesign

> Spec: ./spec.md (FROZEN at CONFIRM)
> Test cases (live): ./test-cases.md
> Status: PLANNING

## Slice budget

| Cap | Limit | Current |
|-----|-------|---------|
| max_steps | 8 | 6 |
| max_files_per_step | 5 | 4 |
| max_lines_per_step | 200 | ~150 |

## Implementation plan

- [ ] Step 1: DrawablePdfPage — добавить параметр `isDrawingEnabled`
      Owned ACs/ECs/TCs: AC-2, AC-3, EC-2, TC-1, TC-2
      Files:
        - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DrawablePdfPage.kt
      Public signatures:
        - fun DrawablePdfPage(bitmap: ImageBitmap, pdfDrawingState: PdfDrawingState, isDrawingEnabled: Boolean, modifier: Modifier)
      Test strategy: tdd_first
      Runnable: internal — изолированное изменение сигнатуры; вызывающий код (DetailsContent) меняется на шаге 6

- [ ] Step 2: Сериализуемые модели + подъём PdfDrawingState
      Owned ACs/ECs/TCs: AC-4, AC-5
      Files:
        - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/PdfDrawingState.kt
        - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/ColorAsLongSerializer.kt
      Public signatures:
        - @Serializable data class DrawingPoint(...)
        - @Serializable data class DrawingPath(...)
        - object ColorAsLongSerializer : KSerializer<Color>
      Test strategy: tdd_first
      Runnable: internal — подготовка моделей для AnnotationRepository на шаге 3

- [ ] Step 3: AnnotationRepository — интерфейс + jvmMain + androidMain реализации
      Owned ACs/ECs/TCs: AC-4, AC-6, EC-1, EC-3, TC-6, TC-7, TC-8
      Files:
        - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/AnnotationRepository.kt
        - app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/AnnotationRepositoryJvm.kt
        - app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/AnnotationRepositoryAndroid.kt
      Public signatures:
        - interface AnnotationRepository { suspend fun save(pdfPath: String, annotations: Map<Int, List<DrawingPath>>): Result<Unit> }
        - expect fun createAnnotationRepository(): AnnotationRepository
        - actual fun createAnnotationRepository(): AnnotationRepository (jvmMain)
        - actual fun createAnnotationRepository(): AnnotationRepository (androidMain)
      Test strategy: tdd_first
      Runnable: internal — репозиторий готов, но ещё не подключён к UI

- [ ] Step 4: ScrollablePdfColumn — expect/actual полоса прокрутки
      Owned ACs/ECs/TCs: AC-11, AC-12, TC-10
      Files:
        - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/ScrollablePdfColumn.kt
        - app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/ScrollablePdfColumn.jvm.kt
        - app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/ScrollablePdfColumn.android.kt
      Public signatures:
        - expect fun ScrollablePdfColumn(state: LazyListState, modifier: Modifier, content: LazyListScope.() -> Unit)
      Test strategy: test_after
      Runnable: internal — composable компилируется, но ещё не используется в DetailsContent

- [ ] Step 5: PdfFloatingToolbar — новый composable
      Owned ACs/ECs/TCs: AC-1, AC-5, AC-7, AC-8, AC-9, AC-10, AC-13, EC-3, TC-3, TC-4, TC-5, TC-9
      Files:
        - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/PdfFloatingToolbar.kt
      Public signatures:
        - fun PdfFloatingToolbar(isDrawingEnabled, onToggleDrawing, hasAnnotations, isSaving, onSave, scale, onZoomIn, onZoomOut, modifier)
      Test strategy: tdd_first
      Runnable: internal — composable компилируется; превью можно открыть в IDE

- [ ] Step 6: DetailsContent — интеграция всех компонентов
      Owned ACs/ECs/TCs: AC-1..AC-13, EC-1..EC-6, TC-11, TC-12, TC-13, TC-14
      Files:
        - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DetailsContent.kt
      Guidelines: кнопку «Назад» не трогать; min_scale=10, max_scale=200
      Test strategy: test_after
      Runnable: запустить Desktop-приложение → открыть PDF → убедиться, что панель инструментов видна, переключение рисования / масштаб / сохранение работают

## Replan log

(пусто)

## Diff-review (заполняется при 5.10)

| Field | Value |
|-------|-------|
| Total files changed | — |
| Total +/-lines | — |
| Files NOT in any step.Files | — |
| Out-of-module touches | — |
| PO verdict | — |

## Definition of Done

(заполняется @Verifier MODE=DOD при CLOSE)
