---
genre: feature-spec
title: PDF Viewer UX — исправления состояния, resize и адаптивность
topic: feature
module: common
status: CLOSED
updated: 2026-05-09
---

# PDF Viewer UX improvements

> Статус: DRAFT (до CONFIRM). После CONFIRM файл замораживается.
> Модуль: common
> Базируется на: `vault/features/common/pdf-eraser-tool-settings/spec.md` (CLOSED).
> Не нарушает AC-1..AC-19 закрытой фичи.

## Почему

После завершения `pdf-eraser-tool-settings` обнаружено пять связанных проблем:

1. **Масштаб не восстанавливается визуально.** Корень: `rememberSaveable { localWindowInfo.containerSize }` в `DetailsContent.kt:51` фиксирует размер окна при первой композиции. После поворота экрана (Android) сохранённое значение становится устаревшим → `targetWidthPx` вычисляется с неверным `screenWidthPx` → страницы рендерятся в тех же пикселях при любом `scale`.

2. **Текущая страница не сохраняется.** `lazyListState.firstVisibleItemIndex` нигде не персистируется; переоткрытие всегда начинает с первой страницы.

3. **Аннотации смещаются при resize.** Тот же `rememberSaveable` фиксирует `windowSizeInPx`; при изменении размера окна (desktop) или повороте (Android) размеры страниц и оверлей DrawablePdfPage рассинхронизируются.

4. **Нет индикатора текущей страницы.** Пользователь не знает, на какой из N страниц находится.

5. **Перекрытие панелей на Compact-экране.** `PdfFloatingToolbar` (слева) и `ToolSettingsFloatingPanel` (внизу по центру) перекрываются на мобильных устройствах при активном инструменте.

## Критерии приёмки

| ID | Дано | Когда | Тогда |
|----|------|-------|-------|
| AC-1 | PDF открыт, масштаб изменён до 150 % и сохранён | Пользователь закрывает и снова открывает PDF | Страницы отображаются с тем же визуальным масштабом; тумблер показывает 150 % |
| AC-2 | PDF открыт, пользователь прокрутил до страницы N и нажал «Сохранить» | Пользователь переоткрывает PDF | LazyColumn прокручивается к той же странице N (`firstVisibleItemIndex = N − 1`) |
| AC-3 | DetailsContent открыт | Размер окна (desktop) изменяется или происходит поворот (Android) | Размеры страниц и DrawablePdfPage пересчитываются немедленно; аннотации не смещаются |
| AC-4 | DetailsContent открыт, в PDF ≥ 1 страница | Экран отображается | В верхней части экрана виден полупрозрачный glass-индикатор «Страница X / N»; X обновляется при прокрутке без задержки |
| AC-5 | Compact-ширина (< 600 dp), `toolMode == PEN` или `ERASER` | `ToolSettingsFloatingPanel` видима | `PdfFloatingToolbar` анимированно скрыт; на Medium/Expanded оба элемента видны одновременно |
| AC-6 | `ToolSettingsFloatingPanel` на Compact-экране | Контент панели шире доступного экрана | Содержимое панели прокручивается горизонтально; вертикальная прокрутка PDF не блокируется |

## Граничные случаи

| ID | Серьёзность | Сценарий | Ожидаемое поведение |
|----|-------------|----------|---------------------|
| EC-1 | High | Сохранённый `currentPage ≥ pages.size` (PDF с меньшим числом страниц) | `scrollToItem` вызывается с `index.coerceIn(0, pages.size − 1)`; краша нет |
| EC-2 | High | `pages.isEmpty()` при загрузке | Airbar не отображается; `scrollToItem` не вызывается |
| EC-3 | Medium | Быстрый resize окна (несколько recomposition подряд) | `windowSizeInPx` обновляется на каждый recomposition; страницы перерисовываются; нет debounce-артефактов |
| EC-4 | Medium | Поворот Android при открытом DetailsContent | `containerSize` из `LocalWindowInfo` применяется в ту же recomposition после поворота |
| EC-5 | Medium | toolMode NONE → PEN на Compact при уже закрытом toolbar | `AnimatedVisibility(visible = false)` → toolbar скрывается анимированно |
| EC-6 | Low | Старый файл аннотаций без поля `currentPage` | Десериализуется с default = 0; обратная совместимость сохранена |
| EC-7 | Low | PDF открыт впервые (нет файла аннотаций) | `currentPage = 0`; `scrollToItem` не вызывается; airbar показывает «Страница 1 / N» |

## Как это работает

### 1. Исправление `windowSizeInPx`

```kotlin
// DetailsContent.kt:51 — было:
val windowSizeInPx = rememberSaveable { localWindowInfo.containerSize }
// стало:
val windowSizeInPx = localWindowInfo.containerSize
```

`LocalWindowInfo` — `CompositionLocal`; чтение без `remember` гарантирует реактивное обновление при каждом изменении размера. Все downstream-вычисления (`targetWidthPx`, `maxTargetWidthPx`, ширина/высота `Box`) пересчитываются автоматически. Это устраняет одновременно проблему resize (AC-3) и визуального невосстановления масштаба (AC-1).

### 2. Persistence `currentPage`

**`AnnotationData`** — добавляется поле с default для backward-compat (EC-6):

```kotlin
@Serializable
data class AnnotationData(
    val pages: Map<String, List<DrawingPath>>,
    val scale: Int = 100,
    val tools: ToolsBundle? = null,
    val currentPage: Int = 0,          // new
)
```

**`AnnotationBundle`** — добавляется поле:

```kotlin
data class AnnotationBundle(
    val pages: Map<Int, List<DrawingPath>> = emptyMap(),
    val scale: Int = 100,
    val pen: PenSettings = PenSettings(),
    val eraser: EraserSettings = EraserSettings(),
    val currentPage: Int = 0,          // new
)
```

**`AnnotationRepository.save`** — расширяется сигнатура:

```kotlin
suspend fun save(
    pdfPath: String,
    annotations: Map<Int, List<DrawingPath>>,
    scale: Int,
    pen: PenSettings = PenSettings(),
    eraser: EraserSettings = EraserSettings(),
    currentPage: Int = 0,              // new, default для backward-compat
): Result<Unit>
```

**JVM / Android реализации**: `data.currentPage` → передаётся в `AnnotationData`; при `load` → копируется в `AnnotationBundle.currentPage`.

**`DetailsContent` wiring**:
- `onSave` передаёт `currentPage = lazyListState.firstVisibleItemIndex`.
- `LaunchedEffect(filePath)` после load:
  ```kotlin
  if (pages.isNotEmpty() && bundle.currentPage > 0) {
      lazyListState.scrollToItem(bundle.currentPage.coerceIn(0, pages.size - 1))
  }
  ```

### 3. `PageIndicatorAirbar`

Новый composable `PageIndicatorAirbar.kt` (commonMain):

```kotlin
@Composable
fun PageIndicatorAirbar(
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
)
```

Glass-стиль, совпадающий с `ToolSettingsFloatingPanel`:
- `color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)`
- `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))`
- `tonalElevation = 6.dp`, `shadowElevation = 4.dp`
- `shape = RoundedCornerShape(12.dp)`

Текст: `"Страница $currentPage / $totalPages"`, `typography.labelLarge`.

В `DetailsContent`:

```kotlin
val firstVisiblePage by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
// ...
if (pages.isNotEmpty()) {
    PageIndicatorAirbar(
        currentPage = firstVisiblePage + 1,
        totalPages = pages.size,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 8.dp),
    )
}
```

`derivedStateOf` предотвращает лишние recomposition DetailsContent при прокрутке — перекомпозиция затронет только `PageIndicatorAirbar`.

### 4. Адаптивность на Compact

**`DetailsContent`** — вычисление ширины класса и условная видимость:

```kotlin
val isCompact = with(LocalDensity.current) {
    localWindowInfo.containerSize.width.toDp() < 600.dp
}

AnimatedVisibility(
    visible = !isCompact || toolMode == ToolMode.NONE,
    enter = fadeIn() + slideInHorizontally { -it },
    exit  = fadeOut() + slideOutHorizontally { -it },
    modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(16.dp),
) {
    PdfFloatingToolbar(...)
}
```

**`ToolSettingsFloatingPanel`** — добавляется `.horizontalScroll(rememberScrollState())` к `modifier` внутренних `Row` в `PenSettingsRow` и `EraserSettingsRow`. Изменение не затрагивает внешнее API composable.

### Публичные сигнатуры (изменённые / новые)

```kotlin
// AnnotationRepository.kt
interface AnnotationRepository {
    suspend fun save(
        pdfPath: String,
        annotations: Map<Int, List<DrawingPath>>,
        scale: Int,
        pen: PenSettings = PenSettings(),
        eraser: EraserSettings = EraserSettings(),
        currentPage: Int = 0,
    ): Result<Unit>
    suspend fun load(pdfPath: String): Result<AnnotationBundle>
}

// AnnotationData.kt — поле currentPage: Int = 0
// AnnotationBundle — поле currentPage: Int = 0

// PageIndicatorAirbar.kt (new)
@Composable
fun PageIndicatorAirbar(currentPage: Int, totalPages: Int, modifier: Modifier = Modifier)
```

## Тест-план

| TC ID | Тип | Описание | Проверяет |
|-------|-----|----------|-----------|
| TC-1 | unit | `AnnotationRepositoryJvm.save(currentPage = 5)` → JSON содержит `"currentPage": 5` | AC-2 (save) |
| TC-2 | unit | `AnnotationRepositoryJvm.load(JSON с currentPage=5)` → `bundle.currentPage == 5` | AC-2 (load) |
| TC-3 | unit | `AnnotationRepositoryJvm.load(старый JSON без currentPage)` → `bundle.currentPage == 0` | EC-6 |
| TC-4 | unit | `AnnotationRepositoryAndroid.save(currentPage = 3)` → JSON содержит `"currentPage": 3` | AC-2 (save, Android) |
| TC-5 | manual | Resize окна на JVM-desktop → размеры страниц пересчитываются, аннотации не смещаются | AC-3, EC-3 |
| TC-6 | manual | Поворот Android-устройства → страницы пересчитываются корректно | AC-3, EC-4 |
| TC-7 | manual | Масштаб 150 %, Save, закрыть, открыть → масштаб 150 % | AC-1 |
| TC-8 | manual | Прокрутить до страницы 5, Save, закрыть, открыть → экран открывается на странице 5 | AC-2 |
| TC-9 | manual | Airbar видим, прокрутить → номер страницы обновляется в реальном времени | AC-4 |
| TC-10 | manual | Compact-экран, включить перо → PdfFloatingToolbar скрывается анимированно | AC-5, EC-5 |
| TC-11 | manual | Compact-экран, ToolSettingsFloatingPanel — горизонтальная прокрутка работает | AC-6 |
| TC-12 | manual | Medium/Expanded-экран, включить перо → оба элемента одновременно видны | AC-5 |
| TC-13 | manual | PDF без аннотаций → airbar виден, scroll не происходит, scale = 100 % | EC-7 |
| TC-14 | manual | `currentPage = 999` в JSON, PDF с 3 страницами → scroll к странице 3 (coerceIn) | EC-1 |

## UI / UX

**PageIndicatorAirbar** — плавающая стеклянная пилюля у верхнего края, по центру горизонтально.
Стиль идентичен `ToolSettingsFloatingPanel` (surface 0.85α, outlineVariant border, tonalElevation 6.dp).
Не перекрывает кнопку «Назад» (TopStart, 16.dp padding).

**AnimatedVisibility для PdfFloatingToolbar** — `slideInHorizontally { -it } + fadeIn` /
`slideOutHorizontally { -it } + fadeOut`. Ширина порога 600 dp соответствует
`WindowWidthSizeClass.Compact` из Material 3.

**HorizontalScroll в ToolSettingsFloatingPanel** — всегда включён в Row пера и ластика;
на широких экранах контент умещается без прокрутки, на узких — прокручивается.

## Открытые вопросы

(нет — требования полностью оговорены PO)
