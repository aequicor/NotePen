# Plan: PDF Gesture-based Undo (Ctrl+Z)

> Risk: standard | Mode: interactive

## Steps

### Step 1 — Rewrite `PdfDrawingState`: undoStack + saveUndoSnapshot() + new undo()
**Files:** `PdfDrawingState.kt`
- Добавить `val undoStack: ArrayDeque<List<DrawingPath>> = ArrayDeque()`
- Добавить `fun saveUndoSnapshot() { undoStack.addLast(currentPaths.toList()) }`
- Переписать `undo()`: если стек непустой — `currentPaths.clear(); currentPaths.addAll(undoStack.removeLast())`
- Написать unit-тесты в `PdfDrawingStateUndoTest.kt`

**ACs:** AC-1, AC-2, AC-3, EC-1, EC-2, EC-4

---

### Step 2 — Вызов `saveUndoSnapshot()` в `DrawablePdfPage`
**Files:** `DrawablePdfPage.kt`
- PEN `onDragStart`: вызвать `pdfDrawingState.saveUndoSnapshot()` перед `startDrawing()`
- ERASER `onDragStart`: вызвать `pdfDrawingState.saveUndoSnapshot()` перед `erasePointsInZone()`

**ACs:** AC-4, AC-5, AC-6

---

### Step 3 — Ctrl+Z в `DetailsContent`
**Files:** `DetailsContent.kt`
- Добавить импорты: `androidx.compose.ui.input.key.*`
- На внешний `Box` добавить `Modifier.onKeyEvent { e -> if (e.key == Key.Z && e.isCtrlPressed && e.type == KeyEventType.KeyDown) { drawingStates[firstVisiblePage]?.undo(); true } else false }`

**ACs:** AC-7, EC-3

---

### Step 4 — Замена иконки ластика в `PdfFloatingToolbar`
**Files:** `PdfFloatingToolbar.kt`
- Заменить `import androidx.compose.material.icons.filled.Delete` на `import androidx.compose.material.icons.filled.CleaningServices`
- В `ToolToggleButton` для ластика: `Icons.Default.Delete` → `Icons.Default.CleaningServices`

**ACs:** AC-8

---

## Diff-review

_Заполняется после EXECUTE._
