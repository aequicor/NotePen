---
genre: feature-spec
title: Панель инструментов и Material 3 дизайн экрана просмотра PDF
topic: feature
module: common
status: DRAFT
updated: 2026-05-09
---

# Панель инструментов и Material 3 дизайн экрана просмотра PDF

> Статус: DRAFT
> Модуль: common
> Владелец: PO

<!--
  ЗАМОРОЖЕН при CONFIRM. После этого файл доступен только для чтения.
  Изменяемое состояние (шаги плана, вердикт DoD, маркеры replanning)
  хранится в соседнем plan.md.
-->

## Почему

Экран просмотра PDF (`DetailsContent`) использует примитивные кнопки `Button("+")` / `Button("-")`
без стиля и не предоставляет возможности сохранить нарисованные аннотации.
Режим рисования всегда включён, что делает невозможной прокрутку через `LazyColumn`.
Плавающая панель инструментов (Material 3) с переключателем режима рисования,
сохранением аннотаций, стильными кнопками масштаба и вертикальной полосой прокрутки
делает экран функциональным и согласованным с дизайн-системой.

## Критерии приёмки

| ID | Дано | Когда | Тогда |
|----|------|-------|-------|
| AC-1 | Экран просмотра PDF открыт | Экран отображается | Видна плавающая панель инструментов с кнопками: переключатель рисования, сохранение, ZoomIn / ZoomOut + текущий процент |
| AC-2 | Режим рисования **выключен** (по умолчанию) | Пользователь свайпает по PDF | `LazyColumn` прокручивается; `DrawablePdfPage` не перехватывает жест |
| AC-3 | Пользователь включил режим рисования | Пользователь свайпает по PDF | Свайп создаёт линию на `Canvas`; `LazyColumn` не прокручивается |
| AC-4 | Нарисованы аннотации хотя бы на одной странице | Нажата кнопка «Сохранить» | Файл `<имя_pdf>.notepen.json` создаётся рядом с PDF; содержит данные штрихов всех страниц |
| AC-5 | Нет нарисованных аннотаций ни на одной странице | Экран отображается | Кнопка «Сохранить» заблокирована (`enabled = false`) |
| AC-6 | Сохранение завершено успешно | Файл создан | Пользователь видит кратковременный Snackbar об успехе |
| AC-7 | Масштаб текущий = X% | Нажат ZoomIn | Масштаб увеличивается на 10%; лейбл показывает «(X+10)%» |
| AC-8 | Масштаб текущий = X% | Нажат ZoomOut | Масштаб уменьшается на 10%; лейбл показывает «(X-10)%» |
| AC-9 | Масштаб = 200% | ZoomIn отображается | Кнопка ZoomIn заблокирована (`enabled = false`) |
| AC-10 | Масштаб = 10% | ZoomOut отображается | Кнопка ZoomOut заблокирована (`enabled = false`) |
| AC-11 | Desktop (JVM), многостраничный PDF | LazyColumn отображается | Вертикальная полоса прокрутки видна справа от LazyColumn |
| AC-12 | Android, многостраничный PDF | LazyColumn отображается | Нативная сенсорная прокрутка работает (отдельная полоса не требуется) |
| AC-13 | Любая тема (светлая / тёмная) | Экран отображается | Все элементы панели используют токены `MaterialTheme.colorScheme.*`; хардкода цветов нет |

## Граничные случаи

| ID | Серьёзность | Сценарий | Ожидаемое поведение |
|----|-------------|----------|---------------------|
| EC-1 | Critical | Ошибка записи файла аннотаций (нет места / нет прав) | Snackbar с сообщением об ошибке; файл не создаётся; аннотации в памяти не утеряны |
| EC-2 | High | Переключение режима рисования во время активного штриха (`isDrawing = true`) | `finishDrawing()` вызывается перед отключением; незавершённый штрих фиксируется или сбрасывается согласно существующей логике `PdfDrawingState` |
| EC-3 | High | Нажатие «Сохранить» повторно, когда предыдущее сохранение ещё выполняется | Кнопка заблокирована на время сохранения (`isSaving = true`) |
| EC-4 | Medium | `ZoomOut` при scale = 10% | `ZoomOut` заблокирован; значение ≥ 10% — инвариант соблюдается |
| EC-5 | Medium | `ZoomIn` при scale = 200% | `ZoomIn` заблокирован; значение ≤ 200% — инвариант соблюдается |
| EC-6 | Low | Смена темы на лету | Material 3 токены реактивно обновляются; хардкода нет |

## Как это работает

### 1. DrawablePdfPage — параметр `isDrawingEnabled`

```kotlin
@Composable
fun DrawablePdfPage(
    bitmap: ImageBitmap,
    pdfDrawingState: PdfDrawingState,
    isDrawingEnabled: Boolean,
    modifier: Modifier = Modifier
)
```

Блок `pointerInput` с `detectDragGestures` применяется только если `isDrawingEnabled == true`.
Ключ `pointerInput` включает `isDrawingEnabled`, чтобы Compose перестраивал узел при переключении.

### 2. Подъём PdfDrawingState

Состояние поднимается из `remember(bm)` внутри `items { }` на уровень `DetailsContent`:

```kotlin
val drawingStates = remember { mutableStateMapOf<Int, PdfDrawingState>() }
// внутри items:
val pdfDrawingState = drawingStates.getOrPut(pageIndex) { PdfDrawingState() }
```

Признак наличия аннотаций:

```kotlin
val hasAnnotations by remember {
    derivedStateOf { drawingStates.values.any { it.currentPaths.isNotEmpty() } }
}
```

### 3. Сериализуемые модели

`DrawingPoint` и `DrawingPath` помечаются `@Serializable`.
`Color` сериализуется как `Long` (ARGB) через custom serializer `ColorAsLongSerializer`.

### 4. AnnotationRepository — expect/actual

```kotlin
// commonMain — interface
interface AnnotationRepository {
    suspend fun save(pdfPath: String, annotations: Map<Int, List<DrawingPath>>): Result<Unit>
}

// commonMain — expect factory
expect fun createAnnotationRepository(): AnnotationRepository

// jvmMain — AnnotationRepositoryJvm
// Пишет <pdfPath>.notepen.json через Json { prettyPrint = false }
// actual fun createAnnotationRepository(): AnnotationRepository = AnnotationRepositoryJvm()

// androidMain — AnnotationRepositoryAndroid
// То же через java.io.File
// actual fun createAnnotationRepository(): AnnotationRepository = AnnotationRepositoryAndroid()
```

Формат файла:
```json
{ "pages": { "0": [ { "points": [...], "colorArgb": -16777216, "strokeWidth": 10.0 } ] } }
```

### 5. ScrollablePdfColumn — expect/actual

```kotlin
// commonMain
@Composable
expect fun ScrollablePdfColumn(
    state: LazyListState,
    modifier: Modifier,
    content: LazyListScope.() -> Unit
)

// jvmMain: Row { LazyColumn(state) + VerticalScrollbar(rememberScrollbarAdapter(state)) }
// androidMain: LazyColumn(state, modifier, content)
```

### 6. PdfFloatingToolbar

```kotlin
@Composable
fun PdfFloatingToolbar(
    isDrawingEnabled: Boolean,
    onToggleDrawing: () -> Unit,
    hasAnnotations: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
    scale: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
)
```

`Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp)` → `Column` с `IconButton`-ами.

### 7. DetailsContent — изменения

- `var isDrawingEnabled by remember { mutableStateOf(false) }`
- `var isSaving by remember { mutableStateOf(false) }`
- Диапазон масштаба: 10–200 (шаг 10)
- `LazyColumn` → `ScrollablePdfColumn`
- Старые `Button("+")` / `Button("-")` / `Text(scale.toString())` удаляются
- `PdfFloatingToolbar` добавляется в `Box` с `Alignment.BottomStart`, `padding(16.dp)`
- Кнопка «Назад» (`IconButton` + `Alignment.TopStart`) — без изменений

### Публичные сигнатуры (изменённые / новые)

```kotlin
// DrawablePdfPage.kt — добавлен параметр
fun DrawablePdfPage(bitmap, pdfDrawingState, isDrawingEnabled: Boolean, modifier)

// PdfFloatingToolbar.kt — новый файл
fun PdfFloatingToolbar(isDrawingEnabled, onToggleDrawing, hasAnnotations, isSaving, onSave, scale, onZoomIn, onZoomOut, modifier)

// ScrollablePdfColumn.kt — новый expect/actual
expect fun ScrollablePdfColumn(state: LazyListState, modifier: Modifier, content: LazyListScope.() -> Unit)

// AnnotationRepository.kt — новый интерфейс
interface AnnotationRepository { suspend fun save(pdfPath: String, annotations: Map<Int, List<DrawingPath>>): Result<Unit> }

// ColorAsLongSerializer.kt — новый serializer (commonMain)
object ColorAsLongSerializer : KSerializer<Color>
```

## Тест-план

| TC ID | Тип | Описание | Проверяет |
|-------|-----|----------|-----------|
| TC-1 | unit | `DrawablePdfPage(isDrawingEnabled=false)` — drag не вызывает `startDrawing()` | AC-2 |
| TC-2 | unit | `DrawablePdfPage(isDrawingEnabled=true)` — drag вызывает `startDrawing()` | AC-3 |
| TC-3 | unit | `PdfFloatingToolbar(hasAnnotations=false)` — кнопка Save имеет `enabled=false` | AC-5 |
| TC-4 | unit | `PdfFloatingToolbar(scale=200)` — ZoomIn имеет `enabled=false` | AC-9 |
| TC-5 | unit | `PdfFloatingToolbar(scale=10)` — ZoomOut имеет `enabled=false` | AC-10 |
| TC-6 | unit | `AnnotationRepositoryJvm.save(...)` создаёт корректный `.notepen.json` | AC-4 |
| TC-7 | unit | `AnnotationRepositoryJvm.save(...)` при IOException возвращает `Result.failure` | EC-1 |
| TC-8 | unit | `AnnotationRepositoryAndroid.save(...)` создаёт корректный `.notepen.json` | AC-4 |
| TC-9 | unit | `PdfFloatingToolbar(isSaving=true)` — кнопка Save имеет `enabled=false` | EC-3 |
| TC-10 | manual | Desktop: вертикальная полоса прокрутки видна при многостраничном PDF | AC-11 |
| TC-11 | manual | Рисование выкл → свайп прокручивает PDF, штрих не создаётся | AC-2 |
| TC-12 | manual | Рисование вкл → свайп создаёт штрих, LazyColumn не прокручивается | AC-3 |
| TC-13 | manual | «Сохранить» → файл `.notepen.json` создаётся, Snackbar показан | AC-4, AC-6 |
| TC-14 | manual | Ошибка сохранения → Snackbar с ошибкой; аннотации в памяти живы | EC-1 |

## UI / UX

**Плавающая панель инструментов** (`PdfFloatingToolbar`):
- `Surface(shape=RoundedCornerShape(16.dp), tonalElevation=3.dp)`, цвет фона = `MaterialTheme.colorScheme.surfaceVariant`
- Позиция: `Box` `Alignment.BottomStart`, `padding(16.dp)`
- Содержимое — вертикальный `Column(horizontalAlignment=CenterHorizontally)`:
  - **Рисование**: `IconButton` с `Icons.Default.Edit` (вкл.) / `Icons.Default.EditOff` (выкл.); когда вкл. — иконка тонирована `MaterialTheme.colorScheme.primary`
  - **Сохранить**: `IconButton(enabled=hasAnnotations && !isSaving)` с `Icons.Default.Save`; при `isSaving=true` показывает `CircularProgressIndicator(modifier=Modifier.size(24.dp))` вместо иконки
  - **ZoomIn**: `IconButton(enabled=scale<200)` с `Icons.Default.ZoomIn`
  - **Масштаб**: `Text("$scale%", style=MaterialTheme.typography.labelSmall)`
  - **ZoomOut**: `IconButton(enabled=scale>10)` с `Icons.Default.ZoomOut`

**Полоса прокрутки** (Desktop): `VerticalScrollbar` справа от `LazyColumn` через `rememberScrollbarAdapter`.

**Кнопка «Назад»**: без изменений — `IconButton` с `Alignment.TopStart`, `padding(16.dp)`.

Все цвета — через `MaterialTheme.colorScheme.*`. Хардкод `Color.LightGray` в `Box(background)` заменяется на `MaterialTheme.colorScheme.background`.

## Открытые вопросы

(нет)
