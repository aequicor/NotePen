# Test Cases: PDF Gesture-based Undo

## Unit — PdfDrawingState

| ID | Scenario | Input | Expected | AC / EC |
|----|----------|-------|----------|---------|
| TC-1 | undo на пустом стеке — no-op | `PdfDrawingState().undo()` | нет исключения, `currentPaths` пуст | EC-1 |
| TC-2 | saveUndoSnapshot сохраняет снапшот | добавить путь, вызвать `saveUndoSnapshot()` | `undoStack.size == 1`, стек содержит ту же копию путей | AC-2 |
| TC-3 | один жест → undo → пустое состояние | `saveUndoSnapshot()` (пустые пути), добавить путь вручную, `undo()` | `currentPaths` пуст, стек пуст | AC-3 |
| TC-4 | два снапшота → один undo → первый жест остаётся | snap1 (0 путей), добавить путь A, snap2 (1 путь), добавить путь B, `undo()` | `currentPaths` == [A] | AC-3, EC-2 |
| TC-5 | два снапшота → два undo → начальное состояние | как TC-4, затем второй `undo()` | `currentPaths` пуст | EC-2 |
| TC-6 | saveUndoSnapshot с пустым currentPaths | `saveUndoSnapshot()` без путей | `undoStack` содержит пустой список, no-op | EC-4 |

## Unit — снапшот только в onDragStart

| ID | Scenario | Input | Expected | AC / EC |
|----|----------|-------|----------|---------|
| TC-7 | Вызов saveUndoSnapshot один раз на жест | вызвать `saveUndoSnapshot()` один раз, затем `erasePointsInZone()` несколько раз | `undoStack.size == 1` | AC-6 |

## UI (ручная проверка)

| ID | Scenario | Expected | AC |
|----|----------|----------|----|
| TC-8 | Иконка ластика | В тулбаре иконка ластика — метёлка (CleaningServices), не корзина | AC-8 |
| TC-9 | Нарисовать один штрих → Ctrl+Z | Штрих исчезает | AC-7 |
| TC-10 | Нарисовать два штриха → Ctrl+Z → Ctrl+Z | Оба штриха поочерёдно исчезают | AC-7, EC-2 |
| TC-11 | Стереть часть → Ctrl+Z | Стёртые точки восстанавливаются | AC-5, AC-3 |
| TC-12 | Ctrl+Z без аннотаций | Ничего не происходит, нет краша | EC-1, EC-3 |
