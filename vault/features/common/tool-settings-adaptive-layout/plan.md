# Plan — ToolSettingsFloatingPanel: Adaptive SubcomposeLayout

**Spec:** `vault/features/common/tool-settings-adaptive-layout/spec.md`  
**Risk:** standard  
**Start commit:** _(recorded at EXECUTE start)_

---

## Steps

### Step 1 — Добавить AdaptiveSettingsRow + вспомогательные типы

**Files changed:**
- `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/ToolSettingsFloatingPanel.kt`

**What:**
1. Добавить `private data class SlotItem(icon, label, content)`  
2. Добавить `private fun greedyFit(naturalWidths, maxWidth, gapPx, paddingPx, iconButtonWidthPx): BooleanArray` — чистая функция, тестируемая отдельно  
3. Добавить `private var expandedSlotIndex: Int?` как `remember { mutableStateOf(null) }` внутри `AdaptiveSettingsRow`  
4. Добавить `@Composable private fun AdaptiveSettingsRow(slots, modifier)` на основе `SubcomposeLayout`:
   - Проход 1: subcompose("measure_$i") → натуральная ширина  
   - `greedyFit(...)` → `BooleanArray`  
   - Проход 2: subcompose("layout") → Row с FullSlot / CollapsedSlot  
5. `CollapsedSlot` = `IconButton` + `AnimatedVisibility(expandHorizontally+fadeIn / shrinkHorizontally+fadeOut)`  
6. Добавить `private const val ICON_BUTTON_WIDTH_DP = 40` (совпадает с `COMPACT_ICON_BUTTON_SIZE` для консистентности)

**TDD order:**
- Сначала написать тесты для `greedyFit` (unit, `commonTest`)
- Затем реализовать функцию

**Gate:** approve

---

### Step 2 — Переписать PenSettingsRow и EraserSettingsRow

**Files changed:**
- `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/ToolSettingsFloatingPanel.kt`

**What:**
1. `PenSettingsRow`: заменить `Row { horizontalScroll(...) }` на `AdaptiveSettingsRow(slots = listOf(...))`  
   - Slot 0: icon=LineWeight, label="Толщина", content={ SliderWithValueField(strokeWidth) }  
   - Slot 1: icon=Opacity, label="Прозрачность", content={ SliderWithValueField(alpha) }  
   - Slot 2: icon=Palette, label=null, content={ Row { ColorPresetDot… } }  
2. `EraserSettingsRow`: заменить `Row { horizontalScroll(...) }` на `AdaptiveSettingsRow(slots = listOf(...))`  
   - Slot 0: icon=Category, label="Форма", content={ Row { FilterChip… } }  
   - Slot 1: icon=PhotoSizeSelectLarge, label="Размер", content={ SliderWithValueField(sizeNormalized) }  
3. Удалить импорты `horizontalScroll`, `rememberScrollState` (если больше не используются)

**TDD order:**
- Тест EC-4 (смена toolMode → expandedIndex = null) через compose test или через state logic  
- Тест AC-6 (раскрытие нового slot-а сворачивает предыдущий)  
- Реализация

**Gate:** approve → ground-truth (screenshot)

---

## Diff-review

_(заполняется @Main после EXECUTE)_

---

## Status

- [ ] Step 1
- [ ] Step 2
