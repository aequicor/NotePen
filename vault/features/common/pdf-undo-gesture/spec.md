# Spec: PDF Gesture-based Undo (Ctrl+Z)

> Status: FROZEN after CONFIRM

## Why

Текущая реализация `PdfDrawingState.undo()` удаляет последний элемент списка `currentPaths` — то есть последний подштрих или фрагмент, а не целый жест. Пользователь ожидает, что Ctrl+Z отменит **весь** штрих пера или **всю** сессию стирания (от `onDragStart` до `onDragEnd`), совершённую одним жестом. Также требуется заменить иконку ластика в тулбаре на более подходящую.

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC-1 | `PdfDrawingState` содержит `undoStack: ArrayDeque<List<DrawingPath>>` |
| AC-2 | `saveUndoSnapshot()` сохраняет снапшот `currentPaths.toList()` в конец `undoStack` |
| AC-3 | `undo()` извлекает последний снапшот из `undoStack` и восстанавливает `currentPaths`; если стек пуст — no-op |
| AC-4 | `DrawablePdfPage` вызывает `pdfDrawingState.saveUndoSnapshot()` в `onDragStart` для `ToolMode.PEN` (до `startDrawing`) |
| AC-5 | `DrawablePdfPage` вызывает `pdfDrawingState.saveUndoSnapshot()` в `onDragStart` для `ToolMode.ERASER` (до `erasePointsInZone`) |
| AC-6 | Снапшот делается ровно один раз на жест (`onDragStart`), а не в `onDrag` |
| AC-7 | В `DetailsContent` внешний `Box` имеет `Modifier.onKeyEvent`, который при `Key.Z + isCtrlPressed` вызывает `undo()` на `drawingStates[firstVisiblePage]` |
| AC-8 | Иконка ластика в `PdfFloatingToolbar` — `Icons.Default.CleaningServices` вместо `Icons.Default.Delete` |

## Edge Cases

| ID | Scenario |
|----|----------|
| EC-1 | `undo()` при пустом `undoStack` — no-op, без исключения |
| EC-2 | Несколько последовательных Ctrl+Z — каждый отменяет ровно один жест |
| EC-3 | `drawingStates[firstVisiblePage]` отсутствует при Ctrl+Z — no-op |
| EC-4 | `saveUndoSnapshot()` при пустом `currentPaths` — сохраняет пустой список (корректно) |

## How it works

1. Перед началом жеста (`onDragStart`) в `DrawablePdfPage` вызывается `saveUndoSnapshot()` — делается снимок **текущего** состояния `currentPaths` (до любых изменений).
2. `undo()` достаёт этот снимок из стека и восстанавливает `currentPaths` через `clear() + addAll()`.
3. Для клавиатурного шортката `onKeyEvent` добавляется к внешнему `Box` в `DetailsContent` — он захватывает фокус через `Modifier.focusable()`.

## Test Plan

- Unit: `PdfDrawingState` — пустой стек, один жест-undo, два жеста + один undo, erase + undo.
- Unit: снапшот только в `onDragStart` (не умножается от `onDrag`).
- UI: визуально проверить, что иконка ластика изменилась.
- UI: нарисовать штрих → Ctrl+Z → штрих исчез; нарисовать два → Ctrl+Z → один остался.

## UI

Только замена иконки ластика: `Icons.Default.Delete` → `Icons.Default.CleaningServices`.
