# Spec — ToolSettingsFloatingPanel: Adaptive SubcomposeLayout

**Status:** DRAFT → FROZEN at CONFIRM  
**Risk:** standard  
**Module:** common  
**File:** `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/ToolSettingsFloatingPanel.kt`

---

## Why

`PenSettingsRow` и `EraserSettingsRow` сейчас используют `horizontalScroll`, что даёт
нехорошую UX на узких экранах (неожиданный сдвиг тулбара, отсутствие визуального сигнала
о скрытых элементах). Фиксированный dp-порог не работает на многоразмерных дисплеях.

`SubcomposeLayout` позволяет измерить натуральную ширину каждого slot-а за первый проход
и точно решить, что помещается, без магических констант.

---

## Acceptance Criteria

| # | Критерий | Приоритет |
|---|----------|-----------|
| AC-1 | `PenSettingsRow` не содержит `horizontalScroll`; вместо него — `SubcomposeLayout`-based adaptive row | High |
| AC-2 | `EraserSettingsRow` не содержит `horizontalScroll`; вместо него — `SubcomposeLayout`-based adaptive row | High |
| AC-3 | Slot-ы, не умещающиеся по ширине (greedy left-to-right), рендерятся как `IconButton` (иконка соответствующего параметра) | High |
| AC-4 | Нажатие на `IconButton` раскрывает slot inline с `expandHorizontally(expandFrom = Alignment.Start) + fadeIn()` | High |
| AC-5 | Сворачивание slot-а — `shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()` | High |
| AC-6 | Одновременно раскрыт не более одного slot-а; раскрытие нового автоматически сворачивает предыдущий | High |
| AC-7 | Публичная сигнатура `ToolSettingsFloatingPanel` не изменяется | Critical |
| AC-8 | Приватная функция `SliderWithValueField` не изменяется | High |
| AC-9 | Все slot-ы всегда видны (иконка или полный вид) — ни один параметр не теряется | High |

---

## Edge Cases

| # | Сценарий |
|---|----------|
| EC-1 | Вся строка умещается → все slot-ы показаны inline, иконок нет |
| EC-2 | Ни один slot не умещается → только иконки; тап раскрывает один slot |
| EC-3 | Последний slot частично умещается → он схлопывается, предыдущие остаются inline |
| EC-4 | При смене `toolMode` (PEN→ERASER) раскрытый slot сбрасывается в null |
| EC-5 | Изменение ширины контейнера (поворот, resize) → SubcomposeLayout пересчитывает при следующем recompose |

---

## How It Works

### SubcomposeLayout: два прохода в одном measure-блоке

```
SubcomposeLayout { constraints ->
  // Проход 1: измеряем натуральную ширину каждого slot-а
  val naturalWidths = slots.indices.map { i ->
      subcompose("measure_$i") { FullSlot(slots[i]) }
          .first()
          .measure(Constraints())   // без ограничений → натуральная ширина
          .width
  }

  // Greedy left-to-right: slot помещается, если накопленная ширина + gap + slotWidth <= maxWidth
  val fits: BooleanArray = greedyFit(naturalWidths, constraints.maxWidth, gapPx, paddingPx, iconButtonWidthPx)

  // Проход 2: рендеримый layout с AnimatedVisibility для collapsed slot-ов
  val placeable = subcompose("layout") {
      Row { slots.forEachIndexed { i, slot ->
          if (fits[i]) FullSlot(slot)
          else CollapsedSlot(slot, expanded = expandedIndex == i, onToggle = { ... })
      }}
  }.first().measure(constraints.copy(minWidth = 0, minHeight = 0))

  layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}
```

### Анимация (аналог ExpandableRow из CompactToolPanel)

```kotlin
AnimatedVisibility(
    visible = expandedIndex == slotIndex,
    enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
    exit  = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
) { slot.content() }
```

### Slot-ы PenSettingsRow

| index | Иконка | Лейбл | Контент |
|-------|--------|-------|---------|
| 0 | `LineWeight` | "Толщина" | `SliderWithValueField(strokeWidth)` |
| 1 | `Opacity` | "Прозрачность" | `SliderWithValueField(alpha)` |
| 2 | `Palette` | _(нет)_ | `Row(ColorPresetDot…)` |

### Slot-ы EraserSettingsRow

| index | Иконка | Лейбл | Контент |
|-------|--------|-------|---------|
| 0 | `Category` | "Форма" | `Row(FilterChip…)` |
| 1 | `PhotoSizeSelectLarge` | "Размер" | `SliderWithValueField(sizeNormalized)` |

---

## UI

### Широкий экран (все помещаются)

```
╔══════════════════════════════════════════════════════════════╗
║  Толщина  [━━━━●━━━] [8 dp]  Прозрачность  [━━●━━━━] [70%]  ● ● ● ● ● ║
╚══════════════════════════════════════════════════════════════╝
```

### Узкий экран (slot-1 не помещается)

```
╔════════════════════════════════════════════╗
║  Толщина  [━━━━●━━━] [8 dp]  💧  🎨  ║
╚════════════════════════════════════════════╝
```

### После нажатия на 💧 (Прозрачность)

```
╔══════════════════════════════════════════════════════════╗
║  Толщина  [━━━━●━━━] [8 dp]  💧  [━━●━━━━] [70%]  🎨  ║
╚══════════════════════════════════════════════════════════╝
```
_(slider раскрывается с push-анимацией expandHorizontally; 🎨 смещается вправо)_

---

## Test Plan

| # | Тест | Тип |
|---|------|-----|
| T-1 | Все slot-ы помещаются → `fits` = [true, true, true] | unit (чистая функция greedyFit) |
| T-2 | Ни один не помещается → `fits` = [false, false, false] | unit |
| T-3 | Первый помещается, остальные нет → [true, false, false] | unit |
| T-4 | Раскрытие нового slot-а сворачивает предыдущий (state logic) | unit / compose |
| T-5 | EC-4: смена toolMode сбрасывает expandedIndex | compose |
