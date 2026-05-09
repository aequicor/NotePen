---
genre: feature-spec
title: Инструмент-ластик и панель настроек пера / ластика на экране PDF
topic: feature
module: common
status: FROZEN
updated: 2026-05-09
---

# Инструмент-ластик и панель настроек пера / ластика на экране PDF

> Статус: DRAFT (до CONFIRM). После CONFIRM файл замораживается.
> Модуль: common
> Владелец: PO
> Базируется на: `vault/features/common/pdf-toolbar-redesign/spec.md` (FROZEN). Не должна нарушать AC-1..AC-13 этой спецификации.

<!--
  При CONFIRM этот файл становится read-only. Изменяемое состояние (шаги
  плана, вердикт DoD, маркеры replanning) хранится в соседнем plan.md.
-->

## Почему

После рефакторинга панели инструментов (`pdf-toolbar-redesign`) пользователь может рисовать
по странице PDF единственным «пером» с фиксированным цветом / толщиной и не имеет
возможности стереть отдельный фрагмент аннотации — только отменить (`undo`) последний
штрих или очистить страницу полностью. Это блокирует основной сценарий конспектирования:
«пишу заметку → ошибся в одном слове → хочу стереть только это слово, не теряя соседних
штрихов».

Также штрихи рисуются прямыми сегментами с острыми концами и стыками — визуально
непохоже на реальный рукописный росчерк, что обесценивает эстетическую сторону
инструмента и провоцирует пользователей к ручному «сглаживанию» через мелкие движения.

Решение: добавить инструмент-ластик с **точечным разрезанием штрихов** (стирается только
та часть полилинии, что попала под зону ластика — соседние участки остаются как
самостоятельные подштрихи), а также вынести **настройки пера и ластика** в раскрывающуюся
панель: цвет / толщина / прозрачность для пера, форма / размер для ластика. Все настройки
персистируются вместе с аннотациями (тот же механизм, что у `scale`), так что между
открытиями PDF пользователь возвращается в свой привычный набор инструментов.

## Критерии приёмки

| ID | Дано | Когда | Тогда |
|----|------|-------|-------|
| AC-1 | Экран PDF открыт, ни перо, ни ластик не активны | Панель инструментов отображается | Видны две взаимоисключающие кнопки переключения: «Перо» и «Ластик»; обе в неактивном визуальном состоянии; меню настроек скрыто |
| AC-2 | Перо неактивно | Пользователь нажимает кнопку «Перо» | Перо активируется (`isPenEnabled = true`), ластик принудительно деактивируется (`isEraserEnabled = false`); под кнопкой раскрывается секция настроек пера |
| AC-3 | Ластик неактивен | Пользователь нажимает кнопку «Ластик» | Ластик активируется (`isEraserEnabled = true`), перо принудительно деактивируется (`isPenEnabled = false`); под кнопкой раскрывается секция настроек ластика |
| AC-4 | Перо активно | Пользователь снова нажимает «Перо» | Перо деактивируется; меню настроек пера скрывается; ни один инструмент не активен |
| AC-5 | Ластик активен | Пользователь снова нажимает «Ластик» | Ластик деактивируется; меню настроек ластика скрывается; ни один инструмент не активен |
| AC-6 | Перо активно, секция настроек пера раскрыта | Пользователь двигает слайдер «Толщина» | `PenSettings.strokeWidth` обновляется; следующий начатый штрих рисуется новой толщиной; уже нарисованные штрихи не меняются |
| AC-7 | Перо активно, секция настроек пера раскрыта | Пользователь двигает слайдер «Прозрачность» | `PenSettings.alpha` ∈ [0..1] обновляется; следующий штрих рисуется цветом с этим alpha; уже нарисованные штрихи не меняются |
| AC-8 | Перо активно, секция настроек пера раскрыта | Пользователь нажимает один из пресетов цвета в горизонтальной ленте | `PenSettings.colorRgb` обновляется; следующий штрих рисуется выбранным цветом (alpha остаётся из слайдера); уже нарисованные штрихи не меняются |
| AC-9 | Перо активно | Пользователь рисует штрих на странице | `Canvas` отображает линию со `StrokeCap.Round` и `StrokeJoin.Round`; концы и стыки скруглены |
| AC-10 | Ластик активно, секция настроек ластика раскрыта | Пользователь переключает форму («Круг» / «Квадрат») | `EraserSettings.shape` обновляется; индикатор ластика под пальцем отрисовывается выбранной формой |
| AC-11 | Ластик активно, секция настроек ластика раскрыта | Пользователь двигает слайдер «Размер» | `EraserSettings.sizeNormalized` обновляется; индикатор и зона стирания меняют размер на следующем кадре |
| AC-12 | Ластик активно | Пользователь касается экрана и удерживает палец | На странице под пальцем отрисовывается полупрозрачный индикатор зоны ластика выбранной формы и размера |
| AC-13 | Ластик активно, на странице есть штрих, проходящий через зону ластика | Пользователь двигает палец по штриху | На каждом кадре `addPoint` точки штриха, попавшие в зону ластика, удаляются; штрих разрезается на подштрихи (один штрих → 0..N штрихов); удаление видно сразу, без отпускания пальца |
| AC-14 | Ластик активно, на странице есть несколько штрихов | Пользователь стирает все штрихи на странице | После последнего удаления `currentPaths` страницы пуст; ластик остаётся активным; меню настроек ластика остаётся раскрытым |
| AC-15 | Перо или ластик активны, пользователь сменил их настройки | Пользователь нажимает «Сохранить» | `<имя_pdf>.notepen.json` содержит как штрихи, так и блок `tools` с актуальными `PenSettings` и `EraserSettings` |
| AC-16 | PDF был ранее сохранён с настройками пера / ластика | Пользователь повторно открывает тот же PDF | Загружаются и применяются `PenSettings` (color, alpha, strokeWidth) и `EraserSettings` (shape, sizeNormalized) — слайдеры, пресеты и индикатор отражают сохранённые значения |
| AC-17 | PDF открыт впервые (нет файла аннотаций) | Экран отображается | Применяются дефолтные настройки: цвет = чёрный, alpha = 1.0, strokeWidth = 10f (как сейчас), форма ластика = круг, размер ластика = `EraserSettings.DEFAULT_SIZE_NORMALIZED` |
| AC-18 | Любая тема (светлая / тёмная) | Панель настроек отображается | Все цвета слайдеров, фонов и иконок берутся из `MaterialTheme.colorScheme.*`; все размеры — из dp-токенов; хардкода цветов / размеров нет |
| AC-19 | Старый файл аннотаций (без блока `tools`) | Пользователь открывает PDF | Штрихи загружаются как раньше; настройки пера / ластика применяются дефолтные; сохранение поверх — добавляет блок `tools` (обратная совместимость) |

## Граничные случаи

| ID | Серьёзность | Сценарий | Ожидаемое поведение |
|----|-------------|----------|---------------------|
| EC-1 | Critical | Включение ластика, когда уже идёт незавершённый штрих пера (`isDrawing = true`) | `finishDrawing()` вызывается до переключения; незаконченный штрих фиксируется; ластик активируется чисто |
| EC-2 | Critical | Включение пера, когда выполняется активное стирание (палец на экране, ластик ведёт удаление) | Текущая операция стирания корректно завершается; перо активируется чисто (новый жест начинает рисование) |
| EC-3 | High | Точечное стирание середины штриха | Штрих разрезается на 2 подштриха; начальный и конечный сегменты остаются; цвет / толщина / alpha наследуются от исходного штриха |
| EC-4 | High | Стирание начала / конца штриха | Удаляется крайний участок; оставшаяся часть остаётся одним штрихом без разрезания |
| EC-5 | High | Зона ластика накрывает штрих целиком | Штрих удаляется полностью, `currentPaths` уменьшается на одну запись |
| EC-6 | High | Под зоной ластика — несколько штрихов одновременно | Каждый штрих обрабатывается независимо: разрезается / укорачивается / удаляется по своим правилам |
| EC-7 | Medium | Штрих после стирания вырождается в одну точку (`points.size < 2`) | Получившийся подштрих отбрасывается (подштрих < 2 точек не сохраняется) |
| EC-8 | Medium | Прокрутка `LazyColumn`, пока ластик активен | Жест ластика перехватывает только саму страницу `DrawablePdfPage`; за пределами страницы прокрутка работает (как и сейчас при `isPenEnabled`) |
| EC-9 | Medium | Пользователь меняет alpha пера во время незавершённого штриха | Текущий незавершённый штрих своих свойств не меняет; новые точки добавляются с прежним цветом; новое значение применится к следующему штриху |
| EC-10 | Medium | Слайдер «Размер ластика» = минимум | Размер не уходит ниже `EraserSettings.MIN_SIZE_NORMALIZED`; индикатор остаётся видимым |
| EC-11 | Medium | Слайдер «Размер ластика» = максимум | Размер не превышает `EraserSettings.MAX_SIZE_NORMALIZED` |
| EC-12 | Medium | Сохранение — ошибка записи (нет места / нет прав) | Snackbar с ошибкой; настройки пера / ластика в памяти живы; штрихи не теряются (поведение совместимо с EC-1 из `pdf-toolbar-redesign`) |
| EC-13 | Low | Файл аннотаций повреждён или содержит невалидный блок `tools` | Загрузка штрихов не падает; настройки пера / ластика применяются дефолтные; ошибка логируется |
| EC-14 | Low | Пользователь нажимает кнопку «Сохранить» при включённом инструменте, но без новых штрихов | Поведение совместимо с AC-5 предыдущей фичи: кнопка `enabled` зависит от `hasAnnotations`; если штрихов нет — кнопка заблокирована |
| EC-15 | Low | Смена темы на лету при раскрытой секции настроек | Material 3 токены реактивно обновляются; пресеты, слайдеры, индикатор перерисовываются согласованно |

## Как это работает

### 1. Доменные модели настроек

Новый файл `PenSettings.kt` (commonMain):

```kotlin
@Serializable
data class PenSettings(
    @Serializable(with = ColorAsLongSerializer::class)
    val color: Color = Color.Black,
    val strokeWidth: Float = DEFAULT_STROKE_WIDTH,
    val alpha: Float = 1f,
) {
    companion object {
        const val DEFAULT_STROKE_WIDTH = 10f
        const val MIN_STROKE_WIDTH = 1f
        const val MAX_STROKE_WIDTH = 60f
        val PRESET_COLORS: List<Color> = listOf(
            Color.Black, Color(0xFFE53935), Color(0xFF1E88E5),
            Color(0xFF43A047), Color(0xFFFB8C00), Color(0xFF8E24AA),
        )
    }
}
```

Новый файл `EraserSettings.kt` (commonMain):

```kotlin
@Serializable
enum class EraserShape { CIRCLE, SQUARE }

@Serializable
data class EraserSettings(
    val shape: EraserShape = EraserShape.CIRCLE,
    /** Размер в нормализованных координатах [0..1] относительно ширины canvas. */
    val sizeNormalized: Float = DEFAULT_SIZE_NORMALIZED,
) {
    companion object {
        const val DEFAULT_SIZE_NORMALIZED = 0.04f
        const val MIN_SIZE_NORMALIZED = 0.01f
        const val MAX_SIZE_NORMALIZED = 0.20f
    }
}
```

`ColorAsLongSerializer` уже сохраняет полный ARGB (`Color.value.toULong()` → `Long`),
поэтому alpha-канал персистится без изменений сериализатора. `PenSettings.color` хранит
полностью собранный `Color(R,G,B,alpha=PenSettings.alpha)` на момент сериализации;
при загрузке `alpha` восстанавливается из этого же `Color` (`color.alpha`). Слайдер alpha
визуально работает поверх R/G/B пресета — на каждом изменении формируется итоговый
`color = preset.copy(alpha = alpha)`.

### 2. PdfDrawingState — расширение (точечное стирание + StrokeCap.Round)

`PdfDrawingState` остаётся per-page. Добавляются:

```kotlin
class PdfDrawingState {
    // существующие поля
    var currentPaths = mutableStateListOf<DrawingPath>()
    var currentPath = mutableStateOf(DrawingPath())
    var isDrawing = mutableStateOf(false)

    // существующие startDrawing/addPoint/finishDrawing/clearDrawing/undo
    // — без изменений сигнатур

    /**
     * Точечное стирание: для каждого штриха в currentPaths — удалить точки,
     * попавшие в зону (centerX, centerY, halfSize, shape) в нормализованных
     * координатах [0..1]. Получившиеся подштрихи (≥2 точки) сохранить;
     * пустые / одноточечные — отбросить.
     *
     * Возвращает true, если хотя бы один штрих был изменён.
     */
    fun erasePointsInZone(
        centerX: Float,
        centerY: Float,
        halfSizeNormalized: Float,
        shape: EraserShape,
    ): Boolean
}
```

Алгоритм `erasePointsInZone`:

1. Для каждого `DrawingPath` пройти по `points`.
2. Для каждой точки определить «попала ли в зону»: `circle` — `dx*dx + dy*dy ≤ r*r`;
   `square` — `|dx| ≤ halfSize && |dy| ≤ halfSize`. Координаты ластика и точек — в одной
   нормализованной системе [0..1] относительно canvas.
3. Сегменты подряд идущих не-стёртых точек становятся новыми подштрихами с тем же
   `color` / `strokeWidth`; первая точка каждого подштриха получает `isNewPath = true`,
   остальные — `false`.
4. Подштрихи с `points.size < 2` отбрасываются (EC-7).
5. Исходный штрих заменяется на список (0..N) подштрихов в `currentPaths`. Перестроение
   `mutableStateListOf` — атомарно (заменить за один проход через `clear()` + `addAll(new)`,
   либо собрать новый список и присвоить через `currentPaths.removeAll/addAll`).

`StrokeCap.Round + StrokeJoin.Round`: меняется только отрисовка в `DrawablePdfPage` —
`Stroke(width = ..., cap = StrokeCap.Round, join = StrokeJoin.Round)`.
Доменные модели не меняются.

### 3. ToolMode — единое состояние «активный инструмент»

Новый enum в commonMain:

```kotlin
enum class ToolMode { NONE, PEN, ERASER }
```

В `DetailsContent`:

```kotlin
var toolMode by remember { mutableStateOf(ToolMode.NONE) }
val isPenEnabled = toolMode == ToolMode.PEN
val isEraserEnabled = toolMode == ToolMode.ERASER
val isDrawingEnabled = toolMode != ToolMode.NONE  // объединяет AC-2 / AC-3 предыдущей фичи
```

Свойство «взаимоисключаемость» обеспечивается типом enum (одно значение в момент времени).
Переключение пера / ластика — `toolMode = if (toolMode == ToolMode.PEN) ToolMode.NONE else ToolMode.PEN`
(симметрично для ластика).

При смене `toolMode` LaunchedEffect вызывает `finishDrawing()` на всех `drawingStates`,
если был незавершённый штрих (EC-1), и завершает активную сессию стирания (EC-2).

### 4. DrawablePdfPage — обработка пера и ластика

Сигнатура расширяется:

```kotlin
@Composable
fun DrawablePdfPage(
    bitmap: ImageBitmap,
    pdfDrawingState: PdfDrawingState,
    toolMode: ToolMode,
    penSettings: PenSettings,
    eraserSettings: EraserSettings,
    modifier: Modifier = Modifier,
)
```

`pointerInput` — три ветви, ключ = `toolMode`:

- `ToolMode.NONE` — нет `pointerInput`, как раньше при `isDrawingEnabled = false`.
- `ToolMode.PEN` — `detectDragGestures` → `startDrawing` использует `penSettings.color.copy(alpha = penSettings.alpha)`
   и `penSettings.strokeWidth / w` как нормализованную толщину.
- `ToolMode.ERASER` — `detectDragGestures`:
  - `onDragStart` запоминает `eraserPos = (offset.x/w, offset.y/h)` в `mutableStateOf`,
    вызывает `pdfDrawingState.erasePointsInZone(...)` (первая точка тоже стирает).
  - `onDrag` обновляет `eraserPos`, вызывает `erasePointsInZone(...)` каждый кадр (AC-13).
  - `onDragEnd` сбрасывает `eraserPos = null`.

Индикатор ластика рисуется на том же `Canvas` (поверх штрихов), если `eraserPos != null`:

```kotlin
val pos = eraserPos ?: return@drawRect
val cx = pos.x * size.width
val cy = pos.y * size.height
val sizePx = eraserSettings.sizeNormalized * size.width
val color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
when (eraserSettings.shape) {
    EraserShape.CIRCLE -> drawCircle(color, radius = sizePx / 2, center = Offset(cx, cy))
    EraserShape.SQUARE -> drawRect(color, topLeft = Offset(cx - sizePx/2, cy - sizePx/2),
                                    size = Size(sizePx, sizePx))
}
```

Прежний параметр `isDrawingEnabled: Boolean` удаляется (источник истины — `toolMode`).
`DetailsContent` обновляется соответственно — это breaking change только внутри модуля
`common`, публичная поверхность за модулем не меняется.

### 5. PdfFloatingToolbar — раскрывающаяся панель настроек

Сигнатура:

```kotlin
@Composable
fun PdfFloatingToolbar(
    toolMode: ToolMode,
    onToolModeChange: (ToolMode) -> Unit,

    penSettings: PenSettings,
    onPenSettingsChange: (PenSettings) -> Unit,

    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,

    hasAnnotations: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,

    scale: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,

    modifier: Modifier = Modifier,
)
```

Структура:

- Существующая колонка кнопок (Edit / EditOff заменяется на Pen / Eraser; Save; ZoomIn / scale / ZoomOut)
  — сохраняет AC-1, AC-4..AC-13 предыдущей фичи.
- Под кнопкой инструмента раскрывается соответствующая секция настроек, **только когда этот
  инструмент активен**:
  - `ToolMode.PEN` → `PenSettingsPanel(penSettings, onPenSettingsChange)`
  - `ToolMode.ERASER` → `EraserSettingsPanel(eraserSettings, onEraserSettingsChange)`
  - `ToolMode.NONE` → ни одна секция не отрисована.

Раскрытие — без анимации в первой версии (плавный AnimatedVisibility — отдельная задача,
если PO решит).

#### PenSettingsPanel

Внутренний composable (commonMain, файл `PenSettingsPanel.kt`):

- `Slider("Толщина", penSettings.strokeWidth, MIN_STROKE_WIDTH..MAX_STROKE_WIDTH)`
- `Slider("Прозрачность", penSettings.alpha, 0f..1f)`
- `LazyRow` пресетов цвета `PenSettings.PRESET_COLORS`: каждый пресет — `Box` фиксированного
   dp-размера, `clip(CircleShape)`, фон = пресет, рамка = `MaterialTheme.colorScheme.outline`
   у активного, `onClick` → `onPenSettingsChange(penSettings.copy(color = preset.copy(alpha = penSettings.alpha)))`.

Все размеры — через dp-токены файла `PenSettingsPanel.kt` (private const val SLIDER_HEIGHT = ..., и т. д.).

#### EraserSettingsPanel

- `SegmentedButtons` (или `Row` из двух `FilterChip`) — «Круг» / «Квадрат»
- `Slider("Размер", eraserSettings.sizeNormalized, MIN_SIZE_NORMALIZED..MAX_SIZE_NORMALIZED)`

### 6. Persistence — расширение AnnotationData

`AnnotationData` (commonMain) расширяется:

```kotlin
@Serializable
data class AnnotationData(
    val pages: Map<String, List<DrawingPath>>,
    val scale: Int = 100,
    val tools: ToolsBundle? = null,   // new — nullable для обратной совместимости (AC-19)
)

@Serializable
data class ToolsBundle(
    val pen: PenSettings = PenSettings(),
    val eraser: EraserSettings = EraserSettings(),
)
```

`AnnotationBundle` (commonMain) расширяется:

```kotlin
data class AnnotationBundle(
    val pages: Map<Int, List<DrawingPath>> = emptyMap(),
    val scale: Int = 100,
    val pen: PenSettings = PenSettings(),
    val eraser: EraserSettings = EraserSettings(),
)
```

`AnnotationRepository.save` добавляет параметры:

```kotlin
suspend fun save(
    pdfPath: String,
    annotations: Map<Int, List<DrawingPath>>,
    scale: Int,
    pen: PenSettings,
    eraser: EraserSettings,
): Result<Unit>
```

JVM / Android реализации записывают `ToolsBundle(pen, eraser)` в поле `tools`. При `load`
если `tools == null` — возвращаются дефолты (`PenSettings()`, `EraserSettings()`),
обеспечивая AC-19. `Json { ignoreUnknownKeys = true }` уже выставлен — старый формат
без `tools` читается без ошибок; новый формат, прочитанный старым клиентом (если бы он
был), также не падает.

### 7. DetailsContent — wiring

```kotlin
var toolMode by remember { mutableStateOf(ToolMode.NONE) }
var penSettings by remember { mutableStateOf(PenSettings()) }
var eraserSettings by remember { mutableStateOf(EraserSettings()) }

// LaunchedEffect(filePath): bundle.pen / bundle.eraser → penSettings / eraserSettings
// LaunchedEffect(toolMode): finishDrawing() на всех drawingStates если переключаем инструмент

// DrawablePdfPage(toolMode = toolMode, penSettings = penSettings, eraserSettings = eraserSettings, ...)
// PdfFloatingToolbar(toolMode, onToolModeChange = { toolMode = it }, penSettings, onPenSettingsChange = { penSettings = it }, ...)
// onSave: annotationRepository.save(filePath, annotations, scale, penSettings, eraserSettings)
```

Persistence-механизм идентичен зум-полю `scale` из `pdf-toolbar-redesign` — никакого нового
storage слоя не появляется.

### Публичные сигнатуры (изменённые / новые)

```kotlin
// PenSettings.kt — new
@Serializable data class PenSettings(...)

// EraserSettings.kt — new
@Serializable enum class EraserShape { CIRCLE, SQUARE }
@Serializable data class EraserSettings(...)

// ToolMode.kt — new
enum class ToolMode { NONE, PEN, ERASER }

// PdfDrawingState.kt — new method
fun PdfDrawingState.erasePointsInZone(centerX: Float, centerY: Float, halfSizeNormalized: Float, shape: EraserShape): Boolean

// DrawablePdfPage.kt — signature change (внутри common)
fun DrawablePdfPage(bitmap, pdfDrawingState, toolMode: ToolMode, penSettings: PenSettings, eraserSettings: EraserSettings, modifier)

// PdfFloatingToolbar.kt — signature change (внутри common)
fun PdfFloatingToolbar(toolMode, onToolModeChange, penSettings, onPenSettingsChange, eraserSettings, onEraserSettingsChange, hasAnnotations, isSaving, onSave, scale, onZoomIn, onZoomOut, modifier)

// PenSettingsPanel.kt — new
@Composable fun PenSettingsPanel(settings: PenSettings, onChange: (PenSettings) -> Unit, modifier: Modifier = Modifier)

// EraserSettingsPanel.kt — new
@Composable fun EraserSettingsPanel(settings: EraserSettings, onChange: (EraserSettings) -> Unit, modifier: Modifier = Modifier)

// AnnotationRepository.kt — signature change
interface AnnotationRepository {
    suspend fun save(pdfPath: String, annotations: Map<Int, List<DrawingPath>>, scale: Int, pen: PenSettings, eraser: EraserSettings): Result<Unit>
    suspend fun load(pdfPath: String): Result<AnnotationBundle>  // bundle расширяется
}

// AnnotationData.kt — добавляется поле tools: ToolsBundle? = null
// ToolsBundle — new @Serializable data class
```

## Тест-план

| TC ID | Тип | Описание | Проверяет |
|-------|-----|----------|-----------|
| TC-1 | unit | `ToolMode` начальное = NONE; переключение PEN → клик PEN снова = NONE | AC-2, AC-4 |
| TC-2 | unit | Активация ластика при активном пере деактивирует перо (взаимоисключаемость) | AC-2, AC-3 |
| TC-3 | unit | `PdfDrawingState.erasePointsInZone` — стирание середины штриха разрезает его на 2 подштриха с правильным флагом `isNewPath = true` на первой точке каждого | EC-3 |
| TC-4 | unit | `erasePointsInZone` — стирание начала / конца штриха не разрезает, а укорачивает | EC-4 |
| TC-5 | unit | `erasePointsInZone` — зона полностью накрывает штрих → штрих удаляется из `currentPaths` | EC-5 |
| TC-6 | unit | `erasePointsInZone` — несколько штрихов под зоной обрабатываются независимо | EC-6 |
| TC-7 | unit | `erasePointsInZone` — подштрихи с < 2 точками отбрасываются | EC-7 |
| TC-8 | unit | `erasePointsInZone(shape = CIRCLE)` использует круговую метрику; `SQUARE` — квадратную | AC-10, AC-13 |
| TC-9 | unit | `erasePointsInZone` сохраняет `color` / `strokeWidth` родительского штриха в подштрихах | EC-3 |
| TC-10 | unit | `PenSettings` дефолты: color=Black, strokeWidth=10f, alpha=1f | AC-17 |
| TC-11 | unit | `EraserSettings` дефолты: shape=CIRCLE, sizeNormalized=DEFAULT_SIZE_NORMALIZED | AC-17 |
| TC-12 | unit | `EraserSettings.sizeNormalized` clamping: значения < MIN / > MAX отвергаются (через slider range) | EC-10, EC-11 |
| TC-13 | unit | `PenSettings` сериализуется/десериализуется через kotlinx.serialization (alpha сохраняется) | AC-15, AC-16 |
| TC-14 | unit | `EraserSettings` сериализуется/десериализуется через kotlinx.serialization | AC-15, AC-16 |
| TC-15 | unit | `AnnotationRepositoryJvm.save(...)` создаёт JSON с полем `tools.pen` и `tools.eraser` | AC-15 |
| TC-16 | unit | `AnnotationRepositoryJvm.load(...)` со старым JSON (без `tools`) возвращает `AnnotationBundle` с дефолтным pen / eraser | AC-19 |
| TC-17 | unit | `AnnotationRepositoryJvm.load(...)` с новым JSON возвращает `AnnotationBundle` с pen / eraser из файла | AC-16 |
| TC-18 | unit | `AnnotationRepositoryJvm.save(...)` при IOException возвращает `Result.failure` (поведение совместимо с предыдущей фичей) | EC-12 |
| TC-19 | unit | `AnnotationRepositoryAndroid.save(...)` создаёт JSON с `tools` | AC-15 |
| TC-20 | unit | Снапшот / композиционный тест `PenSettingsPanel`: слайдеры + LazyRow пресетов отрендерены, `onChange` зовётся при клике на пресет | AC-6, AC-7, AC-8 |
| TC-21 | unit | Снапшот `EraserSettingsPanel`: переключатель формы + слайдер размера; `onChange` зовётся при переключении формы | AC-10, AC-11 |
| TC-22 | unit | `PdfFloatingToolbar(toolMode = NONE)` — ни одна секция настроек не отрисована | AC-1 |
| TC-23 | unit | `PdfFloatingToolbar(toolMode = PEN)` — `PenSettingsPanel` отрисован, `EraserSettingsPanel` не отрисован | AC-2 |
| TC-24 | unit | `PdfFloatingToolbar(toolMode = ERASER)` — `EraserSettingsPanel` отрисован, `PenSettingsPanel` не отрисован | AC-3 |
| TC-25 | manual | Перо вкл → рисуется штрих с `StrokeCap.Round`+`StrokeJoin.Round` (визуально скруглённые концы и стыки) | AC-9 |
| TC-26 | manual | Ластик вкл → удержание пальца показывает индикатор формы и размера ластика | AC-12 |
| TC-27 | manual | Ластик вкл → проведение по штриху удаляет точки покадрово, штрих разрезается; конечный кадр показывает корректный набор подштрихов | AC-13, EC-3 |
| TC-28 | manual | Ластик вкл → стирание всех штрихов оставляет ластик активным, меню настроек не закрывается | AC-14 |
| TC-29 | manual | Сохранение → `<имя>.notepen.json` содержит блок `tools` с актуальными значениями | AC-15 |
| TC-30 | manual | Закрыть и снова открыть PDF → слайдеры пера и ластика, выбранный пресет цвета и форма ластика — те же | AC-16 |
| TC-31 | manual | Открыть PDF c аннотациями старого формата (без `tools`) → штрихи отображаются, инструменты — дефолтные | AC-19 |
| TC-32 | manual | Тёмная тема → слайдеры, фон панели, индикатор ластика контрастны и согласованы с `MaterialTheme.colorScheme` | AC-18, EC-15 |
| TC-33 | manual | Включить перо → начать рисовать → переключить на ластик не отпуская — незавершённый штрих фиксируется, новый жест уже стирает | EC-1, EC-2 |

## UI / UX

**Панель инструментов** (`PdfFloatingToolbar`) сохраняет вертикальную колонку из
`pdf-toolbar-redesign` и расширяется:

- Кнопка «Перо» (`IconButton`, `Icons.Default.Edit`) и кнопка «Ластик»
  (`IconButton`, `Icons.Default.Backspace` или эквивалент Material Symbols
   с понятной иконкой ластика — выбирается на этапе `@CodeWriter`).
  Активная иконка тонируется `MaterialTheme.colorScheme.primary`,
  неактивная — `onSurfaceVariant`. Иконка `Icons.Default.EditOff` больше не используется
  (источник истины — `toolMode`).
- Под колонкой кнопок — секция настроек (только при `toolMode != NONE`):
  - Фон секции = тот же `Surface(shape=RoundedCornerShape(16.dp), tonalElevation=3.dp,
    color=MaterialTheme.colorScheme.surfaceVariant)`, вертикальный отступ от колонки кнопок —
    `8.dp`.
  - Внутренний padding секции — `12.dp`.

**PenSettingsPanel**:

```
┌──────────────────────────┐
│ Толщина  [────●──── ]    │   slider, 1..60
│ Прозрач. [───●───── ]    │   slider, 0..1
│ ● ● ● ● ● ●              │   LazyRow из 6 пресетов, кружок 28.dp,
│                          │   активный — рамка outline 2.dp
└──────────────────────────┘
```

**EraserSettingsPanel**:

```
┌──────────────────────────┐
│ Форма [ ⚪ Круг | ⬛ Кв. ]│   2 FilterChip / SegmentedButtons
│ Размер [────●──── ]      │   slider, 0.01..0.20
└──────────────────────────┘
```

**Индикатор ластика под пальцем**: `MaterialTheme.colorScheme.outline` с alpha=0.35 — заливка;
`MaterialTheme.colorScheme.outline` без alpha — обводка 1.dp. Форма — круг или квадрат
по `EraserSettings.shape`. Размер — `eraserSettings.sizeNormalized * canvasWidthPx`.

Все размеры (28.dp, 12.dp, 2.dp и т. д.) объявляются как `private val ... = N.dp` в файлах
панели — никаких magic-чисел в теле composable (соответствует AC-18 и `forbidden_patterns`
проекта).

## Открытые вопросы

(нет — все шаги интейка получили ответы PO)
