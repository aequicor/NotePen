package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.annotation.domain.model.PageNote
import ru.kyamshanov.notepen.blur.glassSource
import ru.kyamshanov.notepen.reflow.NoteAnchor
import ru.kyamshanov.notepen.reflow.api.BuiltinReaderPresets
import ru.kyamshanov.notepen.reflow.api.PageTransition
import ru.kyamshanov.notepen.reflow.api.ProgressFormat
import ru.kyamshanov.notepen.reflow.api.ReaderAlign
import ru.kyamshanov.notepen.reflow.api.ReaderSettings
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.SourceSpan
import ru.kyamshanov.notepen.reflow.api.StoredReaderSettings
import ru.kyamshanov.notepen.reflow.api.TextAnchor
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.TimeSource

private val readerLogger = KotlinLogging.logger {}

/**
 * Общий на весь ридер кэш растров страниц для врезок-картинок ([FigureView]),
 * по индексу страницы. Нужен, чтобы при свежей композиции фигуры (виртуализация
 * `LazyColumn`, ре-пагинация, перелистывание) растр был доступен **синхронно** и
 * блок не мигал плейсхолдером: иначе высота картинки скачет placeholder↔растр,
 * запускает ре-пагинацию и пейджер начинает дёргаться между страницами
 * («то фото, то текст»). Заполняется в [FigureView], сбрасывается со сменой
 * документа (ремембер в [ReflowReader]).
 */
internal val LocalFigureBitmapCache: ProvidableCompositionLocal<SnapshotStateMap<Int, ImageBitmap>> =
    staticCompositionLocalOf { mutableStateMapOf() }

/**
 * Reflow-ридер: рендерит [ReflowDocument] колонкой ограниченной ширины с
 * типографикой из [stored]. Все настройки оформления приходят сериализуемыми
 * [StoredReaderSettings]; ридер разворачивает их в Compose-модель
 * ([ReflowReaderSettings]) и применяет — шрифт, кегль, воздух, цвет/тема,
 * яркость, выравнивание, переносы, интервалы, bionic, подсветку строки,
 * страничный/скролл-режим и индикатор прогресса.
 *
 * Настройки живут в нижнем «airbar» по центру ([ReaderAirbar]); по тапу по
 * тексту он скрывается/возвращается ([barVisible]/[onBarVisibleChange]) — этим
 * же механизмом вызывающий слой связывает видимость airbar с фокусом панели.
 *
 * Выделения [highlights] подсвечиваются прямо в потоке текста, поэтому «текут»
 * вместе с переверстанным текстом. Картинки ([ReflowBlock.Figure]) рендерятся
 * кропом исходной страницы через [renderPage], иначе — плейсхолдер.
 *
 * @param document документ для отображения
 * @param stored настройки + пользовательские пресеты
 * @param onStoredChange применить новое состояние настроек
 * @param barVisible показывать ли нижний airbar
 * @param onBarVisibleChange запрос смены видимости airbar (тап/автоскрытие)
 * @param newPresetIdProvider источник стабильного id для нового кастомного пресета
 *   (форк при первой правке встроенного) — поднят наружу, т.к. reflow/impl не
 *   генерирует UUID; редьюсер остаётся детерминированным
 * @param modifier модификатор корневого контейнера
 * @param highlights диапазоны-выделения по блокам
 * @param listState состояние прокрутки (скролл-режим)
 * @param renderPage растеризатор страницы для врезок-картинок; `null` — плейсхолдер
 * @param onPageDeltaReady публикует наружу императивный обработчик «листнуть на ±N
 *   страниц» (или `null`, пока контент не готов / при выходе из композиции). В
 *   страничном режиме прокручивает [HorizontalPager] до нужной страницы; в скролл-режиме
 *   — прокрутка на дельту экранов ([listState]). Нужен, чтобы аппаратные клавиши
 *   (стрелки/громкость/Space) и тап-зоны по краям листали ридер.
 * @param initialAnchor якорь, с которого открыть документ (последняя сохранённая позиция
 *   чтения). Читается один раз при заходе ридера в композицию для текущего [document];
 *   далее живёт во внутреннем стейте и публикуется через [onReadingAnchorChange]. Phase A
 *   использует только `blockIndex`; `charStart` зарезервирован под Phase B.
 * @param onReadingAnchorChange колбэк изменения якоря чтения — для персиста на диск
 *   (writer перевёрстки, режим страница/скролл, ре-пагинация). Сюда летят и «логические»
 *   обновления (paged.snapshotFlow, scroll.firstVisibleItem), и первичная инициализация.
 * @param topInset статический отступ сверху, резервируемый под плавающую панель редактора
 *   (верхний бар / чип «Страница N/M»), чтобы H1 и первые строки не уходили под хром.
 *   По умолчанию `0.dp` — standalone-вызовы (тесты) не затрагиваются.
 * @param startInset статический отступ слева, резервируемый под боковой tool-rail на
 *   планшете, чтобы первые символы строк не перекрывались. По умолчанию `0.dp`.
 *   Резерв статический (не анимируется с видимостью хрома): иначе каждое скрытие/показ
 *   панели вызывало бы ре-пагинацию из-за смены высоты вьюпорта.
 *
 * Выделение текста для создания подсветок доступно в режиме чтения всегда и охватывает
 * несколько абзацев (сквозной жест на контейнере, см. [reflowSelectionDrag]); на
 * отпускании отдаёт анкеры через [LocalReflowSelection]. От состояния маркера
 * ([ReflowSelection.immediate]) зависит лишь жест запуска: активен — тянем сразу, иначе —
 * после долгого нажатия. Пока тянем — список не скроллится. Перелистывание — тап-зонами
 * по краям и клавишами (горизонтального свайпа нет: жест drag принадлежит выделению).
 */
@Composable
public fun ReflowReader(
    document: ReflowDocument,
    stored: StoredReaderSettings,
    onStoredChange: (StoredReaderSettings) -> Unit,
    barVisible: Boolean,
    onBarVisibleChange: (Boolean) -> Unit,
    newPresetIdProvider: () -> String,
    modifier: Modifier = Modifier,
    highlights: List<TextAnchor> = emptyList(),
    notes: List<NoteAnchor> = emptyList(),
    onNoteTap: (PageNote) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    renderPage: (suspend (pageIndex: Int) -> ImageBitmap?)? = null,
    onPageDeltaReady: (((Int) -> Unit)?) -> Unit = {},
    navigateToBlock: MutableState<Int?> = remember { mutableStateOf(null) },
    onFirstBlockChange: (Int) -> Unit = {},
    initialAnchor: TextAnchor = TextAnchor.START,
    onReadingAnchorChange: (TextAnchor) -> Unit = {},
    topInset: Dp = 0.dp,
    startInset: Dp = 0.dp,
) {
    // Высота вьюпорта ридера в px — нужна и для зажима вертикальных полей (ниже), и для
    // шага «листнуть страницу» в скролл-режиме. Меряем корневой Box (см. onSizeChanged).
    var viewportHeightPx by remember { mutableStateOf(0) }

    val baseSettings = remember(stored.current) { stored.current.toRenderSettings() }
    val density = LocalDensity.current
    // Зажимаем вертикальные поля под фактическую высоту вьюпорта: пользователь (или
    // перенесённое с большего экрана значение) может задать поля, в сумме превышающие
    // высоту страницы — тогда без зажима пагинация схлопывается в микространицы (OOM).
    // Делаем это в единой точке, чтобы и раскладка, и визуальные отступы совпадали.
    val settings =
        remember(baseSettings, viewportHeightPx, density) {
            if (viewportHeightPx <= 0) {
                baseSettings
            } else {
                val viewportDp = with(density) { viewportHeightPx.toDp() }
                val (top, bottom) =
                    clampVerticalMargins(baseSettings.topMargin, baseSettings.bottomMargin, viewportDp)
                if (top == baseSettings.topMargin && bottom == baseSettings.bottomMargin) {
                    baseSettings
                } else {
                    baseSettings.copy(topMargin = top, bottomMargin = bottom)
                }
            }
        }
    val anchorsByBlock = remember(highlights) { highlights.groupBy { it.blockIndex } }
    // Заметки, сгруппированные по блоку — для тап-бейджа на полях у строки заметки.
    // Диапазон-подсветка заметки приходит «бесплатно» через [highlights] (вызывающий слой
    // подмешивает note-анкеры в общий список), поэтому здесь рисуем только бейдж.
    val notesByBlock = remember(notes) { notes.groupBy { it.anchor.blockIndex } }
    val latestOnNoteTap = rememberUpdatedState(onNoteTap)

    // Сессия чтения тикает всегда: нужна и для эргономики, и для контекстного
    // предложения «режим долгого чтения» через ~45 минут.
    var elapsedMs by remember(document) { mutableStateOf(0L) }
    LaunchedEffect(document) {
        while (true) {
            delay(ERGO_TICK_MS)
            elapsedMs += ERGO_TICK_MS
        }
    }

    var breakPrompt by remember(document) { mutableStateOf(false) }
    if (settings.ergonomicsEnabled) {
        LaunchedEffect(document) {
            while (true) {
                delay(BREAK_INTERVAL_MS)
                breakPrompt = true
                delay(BREAK_SHOWN_MS)
                breakPrompt = false
            }
        }
    }
    val nightDim =
        if (settings.ergonomicsEnabled) {
            ReadingErgonomics.dimAlpha(elapsedMs, NIGHT_AFTER_MS, NIGHT_RAMP_MS, NIGHT_MAX_DIM)
        } else {
            0f
        }
    val brightnessDim = (1f - settings.brightness).coerceIn(0f, 1f)

    // «Теплеть после заката»: без геопозиции — приближение по вечерним часам.
    // Час берём от тика сессии, чтобы переоценивать без отдельного таймера.
    val sunsetWarmth =
        if (settings.sunsetWarm) {
            val hour = remember(elapsedMs) { currentLocalHour() }
            if (hour >= SUNSET_START_HOUR || hour < SUNRISE_HOUR) SUNSET_EXTRA_WARMTH else 0f
        } else {
            0f
        }
    val effectiveBackground =
        if (sunsetWarmth > 0f) warmShift(settings.background, sunsetWarmth) else settings.background

    // Первый видимый блок: из прокрутки (скролл) либо из текущей страницы (paged).
    var pagedFirstBlock by remember(document) { mutableStateOf(0) }
    val firstVisibleBlock = if (settings.paged) pagedFirstBlock else listState.firstVisibleItemIndex
    val latestOnFirstBlockChange = rememberUpdatedState(onFirstBlockChange)
    LaunchedEffect(firstVisibleBlock) { latestOnFirstBlockChange.value(firstVisibleBlock) }
    // Якорь чтения, общий для обоих режимов: сохраняет место при переключении
    // страница<->скролл и при ре-пагинации (смена шрифта/полей/ориентации) — номер
    // страницы выводится из него, а не наоборот. Paged пишет его через
    // onVisibleAnchorChange; скролл отслеживает первый видимый элемент (эффекты ниже).
    // Seed — последняя сохранённая позиция (initialAnchor); по изменениям публикуется
    // наружу через onReadingAnchorChange (см. эффект ниже).
    var readingAnchor by remember(document) { mutableStateOf(initialAnchor) }
    val latestOnReadingAnchorChange = rememberUpdatedState(onReadingAnchorChange)
    LaunchedEffect(readingAnchor) { latestOnReadingAnchorChange.value(readingAnchor) }
    val progressLabel =
        remember(settings.progress, firstVisibleBlock, document) {
            progressLabel(settings.progress, firstVisibleBlock, document)
        }

    // Контекстное предложение долгого чтения — мягко, один раз за сессию, и
    // только если пользователь ещё не на этом пресете.
    var longPromptDismissed by remember(document) { mutableStateOf(false) }
    val showLongPrompt =
        !longPromptDismissed &&
            elapsedMs >= LONG_READING_AFTER_MS &&
            stored.activePresetId != BuiltinReaderPresets.longReading.id

    val scope = rememberCoroutineScope()

    // Кэш растров врезок-картинок (см. [LocalFigureBitmapCache]). Сбрасывается со
    // сменой документа, чтобы индексы страниц не пересекались между документами.
    val figureBitmapCache = remember(document) { mutableStateMapOf<Int, ImageBitmap>() }

    // Активный обработчик «листнуть на ±N»: им пользуются тап-зоны (локально, через
    // [latestPageDelta]) и аппаратные клавиши (через [onPageDeltaReady] выше по дереву).
    // Заполняется активным режимом (paged — индексом страницы, scroll — прокруткой) и
    // обнуляется, пока контент не готов либо при выходе из композиции.
    var pageDelta by remember(document) { mutableStateOf<((Int) -> Unit)?>(null) }
    val latestOnPageDeltaReady by rememberUpdatedState(onPageDeltaReady)
    val setPageDelta: (((Int) -> Unit)?) -> Unit = { handler ->
        pageDelta = handler
        latestOnPageDeltaReady(handler)
    }
    DisposableEffect(Unit) { onDispose { latestOnPageDeltaReady(null) } }
    val latestPageDelta by rememberUpdatedState(pageDelta)

    // Скролл-режим: «страница» = дельта экранов (с лёгким нахлёстом, чтобы строка
    // на стыке не терялась). animateScrollBy сам клампится на краях списка.
    if (!settings.paged) {
        val bottomMarginPx = with(LocalDensity.current) { settings.bottomMargin.toPx() }
        val scrollPageDelta: (Int) -> Unit =
            remember(listState, scope, bottomMarginPx) {
                { delta ->
                    // Видимая высота вьюпорта = корневой Box минус нижнее поле (на него ужат
                    // LazyColumn ниже), поэтому шаг считаем от неё — иначе перелистывание
                    // перепрыгивало бы строку вместо нахлёста.
                    val step = (viewportHeightPx - bottomMarginPx) * PAGE_SCROLL_OVERLAP_FRACTION
                    if (step > 0f) scope.launch { listState.animateScrollBy(delta * step) }
                }
            }
        DisposableEffect(scrollPageDelta) {
            setPageDelta(scrollPageDelta)
            onDispose { setPageDelta(null) }
        }
        val scrollBlockTarget by navigateToBlock
        LaunchedEffect(scrollBlockTarget) {
            val block = scrollBlockTarget ?: return@LaunchedEffect
            listState.animateScrollToItem(block)
            navigateToBlock.value = null
        }
    }

    // Мост позиции страница<->скролл: при переключении в скролл встаём на якорный блок;
    // пока скроллим — двигаем якорь, чтобы возврат в страницы попал на то же место. Самый
    // первый композит пропускаем, чтобы не сбить уже восстановленную позицию списка.
    var positionBridgeReady by remember(document) { mutableStateOf(false) }
    LaunchedEffect(settings.paged) {
        if (!positionBridgeReady) {
            positionBridgeReady = true
        } else if (!settings.paged) {
            listState.scrollToItem(readingAnchor.blockIndex.coerceAtLeast(0))
        }
    }
    LaunchedEffect(settings.paged, listState) {
        if (!settings.paged) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .collect { readingAnchor = TextAnchor.ofBlock(it) }
        }
    }

    // Выделение текста для создания подсветок. Доступно в режиме чтения всегда; на
    // отпускании всегда отдаёт анкеры наружу ([ReflowSelection.onCreate]). От состояния
    // маркера ([ReflowSelection.immediate]) зависит лишь жест запуска — см. ниже.
    val selection = LocalReflowSelection.current
    val selectionState = remember(document) { ReflowSelectionState() }
    val latestSelection by rememberUpdatedState(selection)
    // Синхронизируем «немедленный режим» в состояние: от него зависит блокировка скролла
    // контента (а не только активное выделение) — см. ReflowSelectionState.scrollLocked.
    SideEffect { selectionState.immediate = selection.immediate }
    // Подтверждение выделения (не-маркерный режим): после отпускания выделение не
    // фиксируется сразу, а ждёт выбора «Копировать»/«Выделить» через плавающую панель.
    // confirming держит выделение «живым» и показывает панель действий + ручки-курсоры.
    var confirming by remember(document) { mutableStateOf(false) }
    val clipboardWriter = rememberClipboardWriter()
    // Краткое подтверждение «Скопировано» (как лёгкий тост) — гасится по таймеру.
    var copiedToast by remember(document) { mutableStateOf(false) }
    val dismissConfirm: () -> Unit = {
        confirming = false
        selectionState.clear()
    }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                // Фон рисуем ДО padding'а — во всю площадь ридера, включая полоску под
                // плавающим хромом. Иначе резерв оставался незакрашенным и сквозь него
                // просвечивал фон родителя (без тёплого сдвига заката) — это и были «полосы»
                // сверху и слева на стыке с контентом.
                .background(effectiveBackground)
                // Резерв под плавающий хром редактора (верхний бар/чип, боковой tool-rail).
                // После background/перед onSizeChanged: вьюпорт ридера (LazyColumn/пейджер)
                // ужимается под резерв и тап-зоны считаются от нового размера, а фон под
                // хромом остаётся закрашенным. Пользовательские topMargin/contentPadding
                // остаются внутри и складываются с этим резервом.
                .padding(top = topInset, start = startInset)
                .onSizeChanged { viewportHeightPx = it.height }
                .pointerInput(barVisible, settings.tapToTurn) {
                    // Тап-зоны: лево — назад, право — вперёд, центр — показать/скрыть панель
                    // (стандарт e-readers). Тап (не drag) совместим с выделением: селекшн-жест
                    // ниже потребляет только движения, оставляя нам DOWN/тап.
                    detectTapGestures { offset ->
                        when (tapAction(offset.x, size.width, settings.tapToTurn)) {
                            TapAction.PREV -> latestPageDelta?.invoke(-1)
                            TapAction.NEXT -> latestPageDelta?.invoke(1)
                            TapAction.TOGGLE_BAR -> onBarVisibleChange(!barVisible)
                        }
                    }
                }.pointerInput(selection.immediate, settings.paged) {
                    // В страничном режиме без активного маркера HorizontalPager обрабатывает
                    // свайп нативно — внешний обработчик не нужен и только создал бы конкуренцию жестов.
                    if (settings.paged && !selection.immediate) return@pointerInput
                    if (!selection.immediate) {
                        // Маркер неактивен: выделение — только после долгого нажатия,
                        // до него drag не потребляется → detectHorizontalDragGestures
                        // в Main-проходе свободно видит нетронутые события.
                        var dx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dx = 0f },
                            onDragCancel = { dx = 0f },
                            onDragEnd = {
                                if (abs(dx) > size.width * SWIPE_TURN_FRACTION) {
                                    latestPageDelta?.invoke(if (dx < 0) 1 else -1)
                                }
                            },
                            onHorizontalDrag = { change, amount ->
                                dx += amount
                                change.consume()
                            },
                        )
                    } else {
                        // Маркер активен: выделение в Main-проходе (дочерний → родитель)
                        // потребляет drag до detectHorizontalDragGestures. Initial-проход
                        // (родитель → дочерний) видит события до их потребления.
                        // Обнаружив свайп на отпускании, сбрасываем незафиксированное
                        // выделение: selectionState.clear() обнуляет anchor до того, как
                        // onEnd() → anchorsForSelection() проверит их в Main-проходе.
                        val outerScope = this
                        awaitEachGesture {
                            var dx = 0f
                            var dy = 0f
                            val down =
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var tracking = true
                            var released = false
                            while (tracking) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                when {
                                    change == null -> tracking = false // палец потерян — без перелистывания
                                    change.pressed && change.previousPressed -> {
                                        dx += change.position.x - change.previousPosition.x
                                        dy += change.position.y - change.previousPosition.y
                                    }
                                    !change.pressed -> {
                                        released = true
                                        tracking = false
                                    }
                                }
                            }
                            // Листаем только на чистом отпускании и при «достаточно горизонтальном»
                            // свайпе (не вертикальном): clear() гасит незафиксированное выделение
                            // до того, как Main-проход создаст подсветку.
                            if (released &&
                                abs(dx) > abs(dy) * 2f &&
                                abs(dx) > outerScope.size.width * SWIPE_TURN_FRACTION
                            ) {
                                selectionState.clear()
                                latestPageDelta?.invoke(if (dx < 0) 1 else -1)
                            }
                        }
                    }
                },
    ) {
        CompositionLocalProvider(
            LocalReflowSelectionState provides selectionState,
            LocalFigureBitmapCache provides figureBitmapCache,
        ) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        // Capture the reader page background into the glass backdrop too, so
                        // panels sample a solid page (like PDF mode) instead of transparent gaps
                        // between text lines — otherwise the reader panels read as effect-less.
                        .glassSource()
                        .background(effectiveBackground)
                        .onGloballyPositioned { selectionState.containerCoordinates = it }
                        .reflowSelectionDrag(
                            immediate = selection.immediate,
                            state = selectionState,
                            // Новый жест выделения закрывает предыдущую панель подтверждения.
                            onBegin = { confirming = false },
                            onRelease = {
                                val anchors = selectionState.anchorsForSelection()
                                when {
                                    // Маркер активен — намерение однозначно: ставим подсветку сразу.
                                    selection.immediate -> {
                                        if (anchors.isNotEmpty()) latestSelection.onCreate(anchors)
                                        selectionState.clear()
                                    }
                                    // Иначе ждём выбор «Копировать»/«Выделить»: выделение не гасим.
                                    anchors.isNotEmpty() -> confirming = true
                                    else -> selectionState.clear()
                                }
                            },
                        ),
            ) {
                if (settings.paged) {
                    PagedReflowContent(
                        document = document,
                        anchorsByBlock = anchorsByBlock,
                        notesByBlock = notesByBlock,
                        onNoteTap = { latestOnNoteTap.value(it) },
                        settings = settings,
                        renderPage = renderPage,
                        initialAnchor = readingAnchor,
                        onVisibleAnchorChange = {
                            pagedFirstBlock = it.blockIndex
                            readingAnchor = it
                        },
                        onPageDeltaReady = setPageDelta,
                        navigateToBlock = navigateToBlock,
                    )
                } else {
                    ScrollReflowContent(
                        document = document,
                        anchorsByBlock = anchorsByBlock,
                        notesByBlock = notesByBlock,
                        onNoteTap = { latestOnNoteTap.value(it) },
                        settings = settings,
                        listState = listState,
                        renderPage = renderPage,
                    )
                }
            }
        }

        // Ручки-курсоры показываем уже во время самого жеста (живое превью концов
        // выделения), а не только после отпускания. На этой фазе они неинтерактивны
        // (фокус ведёт палец) и без собственного pointerInput — поэтому не конкурируют
        // с идущим drag. После отпускания их сменяет интерактивный SelectionConfirmOverlay
        // в тех же координатах (matchParentSize контейнера → endRect ложится один в один).
        if (selectionState.isActive && !confirming) {
            LiveSelectionHandles(
                state = selectionState,
                settings = settings,
                modifier = Modifier.matchParentSize(),
            )
        }

        // Оверлей подтверждения выделения: ручки-курсоры на концах + плавающая панель
        // «Копировать/Выделить». Тот же размер/координаты, что и контейнер контента
        // (matchParentSize), поэтому endRect() в системе контейнера ложится один в один.
        if (confirming) {
            SelectionConfirmOverlay(
                state = selectionState,
                settings = settings,
                onCopy = {
                    clipboardWriter(selectedText(document, selectionState.anchorsForSelection()))
                    copiedToast = true
                    dismissConfirm()
                },
                onHighlight = {
                    val anchors = selectionState.anchorsForSelection()
                    if (anchors.isNotEmpty()) latestSelection.onCreate(anchors)
                    dismissConfirm()
                },
                onDismiss = dismissConfirm,
                modifier = Modifier.matchParentSize(),
            )
        }

        if (copiedToast) {
            LaunchedEffect(copiedToast) {
                delay(COPIED_TOAST_MS)
                copiedToast = false
            }
            CopiedToast(settings, Modifier.align(Alignment.BottomCenter))
        }

        // Затемнение «внутренней яркости» и тёплый ночной тинт — оверлеем поверх
        // текста, но под панелью/линейкой, чтобы контролы оставались читаемыми.
        if (brightnessDim > 0f || nightDim > 0f) {
            Box(
                Modifier.matchParentSize().drawBehind {
                    if (brightnessDim > 0f) drawRect(color = Color.Black.copy(alpha = brightnessDim))
                    if (nightDim > 0f) drawRect(color = NIGHT_TINT.copy(alpha = nightDim))
                },
            )
        }

        if (settings.readingRuler) {
            ReadingRuler(settings, Modifier.align(Alignment.Center))
        }

        if (barVisible) {
            // Максимум ползунков верхнего/нижнего поля — доля высоты вьюпорта (до
            // измерения откатываемся к абсолютному потолку), чтобы на большом
            // планшете текст можно было сдвинуть к центру.
            val density = LocalDensity.current
            val maxVerticalMarginDp =
                if (viewportHeightPx <= 0) {
                    ReaderSettings.MAX_VERTICAL_MARGIN_DP
                } else {
                    val viewportDp = with(density) { viewportHeightPx.toDp().value }
                    (viewportDp * ReaderSettings.MAX_VERTICAL_MARGIN_FRACTION)
                        .coerceAtMost(ReaderSettings.MAX_VERTICAL_MARGIN_DP)
                }
            ReaderAirbar(
                stored = stored,
                onStoredChange = onStoredChange,
                newPresetIdProvider = newPresetIdProvider,
                background = effectiveBackground,
                textColor = settings.textColor,
                progressLabel = progressLabel,
                autoHideMs = settings.autoHideMs,
                maxVerticalMarginDp = maxVerticalMarginDp,
                onRequestHide = { onBarVisibleChange(false) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (showLongPrompt) {
            LongReadingPrompt(
                settings = settings,
                onApply = {
                    longPromptDismissed = true
                    onStoredChange(
                        ru.kyamshanov.notepen.reflow.api.ReaderSettingsReducer
                            .applyPreset(stored, BuiltinReaderPresets.longReading),
                    )
                },
                onDismiss = { longPromptDismissed = true },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        if (breakPrompt) {
            BreakReminderBar(settings, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/**
 * Жест сквозного выделения на контейнере контента. Позиции жеста приходят в координатах
 * контейнера — той же системе, в которой [state] хит-тестит блоки, поэтому хит-тест идёт
 * без дополнительных переводов.
 *
 * Стартовый детектор зависит от [immediate]:
 * - маркер активен — выделение анкерится на самом касании (DOWN) и тянется **без слопа**
 *   ([awaitFirstDown] + [drag]); так оно начинается сразу, и даже короткий drag ниже
 *   порога слопа выделяет — в отличие от штатного `detectDragGestures`, который сначала
 *   ждёт превышения слопа и потому для коротких/быстрых жестов вовсе не стартует;
 * - маркер выключен — после долгого нажатия ([detectDragGesturesAfterLongPress]), чтобы
 *   обычное чтение не «цепляло» выделение.
 *
 * В обоих случаях движение двигает конец выделения; в начале жеста вызывается [onBegin]
 * (сбросить открытую панель подтверждения предыдущего выделения), на отпускании —
 * [onRelease]. Сбрасывает ли [onRelease] выделение — решает он сам: в немедленном
 * (маркер) режиме подсветка ставится сразу и выделение гасится; иначе выделение
 * остаётся для подтверждения «Копировать/Выделить». Отмена/перехват жеста — всегда сброс.
 */
private fun Modifier.reflowSelectionDrag(
    immediate: Boolean,
    state: ReflowSelectionState,
    onBegin: () -> Unit,
    onRelease: () -> Unit,
): Modifier =
    pointerInput(immediate, state) {
        val onStart: (Offset) -> Unit = { pos ->
            onBegin()
            state.moveTo(pos, anchoring = true)
        }
        val onMove: (PointerInputChange) -> Unit = { change ->
            state.moveTo(change.position, anchoring = false)
            change.consume()
        }
        val onEnd: () -> Unit = { onRelease() }
        val onCancel: () -> Unit = { state.clear() }
        if (immediate) {
            // Без слопа: анкер ставим прямо на DOWN, далее ведём конец до отпускания.
            // Структурно повторяем detectDragGesturesAfterLongPress, но без ожидания
            // долгого нажатия — поэтому выделение начинается мгновенно.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                onStart(down.position)
                // НЕ потребляем сам DOWN: иначе родительский detectTapGestures (он требует
                // непотреблённого нажатия) перестал бы переключать панель ридера по тапу.
                // Реальный drag всё равно «выигрывает» — движения потребляются в onMove,
                // что снимает тап у родителя; чистый тап вернёт completed=false → onCancel.
                val completed = drag(down.id) { change -> onMove(change) }
                if (completed) onEnd() else onCancel()
            }
        } else {
            detectDragGesturesAfterLongPress(
                onDragStart = onStart,
                onDragEnd = onEnd,
                onDragCancel = onCancel,
                onDrag = { change, _ -> onMove(change) },
            )
        }
    }

/** Скролл-режим: одна прокручиваемая колонка с ритм-паузами. */
@Composable
private fun BoxScope.ScrollReflowContent(
    document: ReflowDocument,
    anchorsByBlock: Map<Int, List<TextAnchor>>,
    notesByBlock: Map<Int, List<NoteAnchor>>,
    onNoteTap: (PageNote) -> Unit,
    settings: ReflowReaderSettings,
    listState: LazyListState,
    renderPage: (suspend (pageIndex: Int) -> ImageBitmap?)?,
) {
    LazyColumn(
        state = listState,
        // При активном маркере (немедленный режим) или идущем выделении список не скроллим —
        // жест принадлежит выделению (см. ReflowSelectionState.scrollLocked).
        userScrollEnabled = !LocalReflowSelectionState.current.scrollLocked,
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = settings.maxContentWidth)
                .fillMaxWidth(),
        // Вертикальные поля задаёт пользователь (topMargin/bottomMargin); боковые — contentPadding.
        // Нижнее поле постоянно, поэтому всплывающая панель не двигает текст, а лишь накрывает
        // его понизу (как в Apple Books) и сама прячется.
        contentPadding =
            PaddingValues(
                start = settings.contentPadding,
                top = settings.topMargin,
                end = settings.contentPadding,
                bottom = settings.bottomMargin,
            ),
        verticalArrangement = Arrangement.spacedBy(settings.blockSpacing),
    ) {
        itemsIndexed(document.blocks) { index, block ->
            Column {
                ReflowBlockView(
                    block,
                    anchorsByBlock[index].orEmpty(),
                    settings,
                    renderPage,
                    blockIndex = index,
                    notes = notesByBlock[index].orEmpty(),
                    onNoteTap = onNoteTap,
                )
                if (settings.ergonomicsEnabled &&
                    index < document.blocks.lastIndex &&
                    ReadingErgonomics.isRhythmBreak(index, RHYTHM_EVERY_BLOCKS)
                ) {
                    RhythmPause(settings)
                }
            }
        }
    }
}

/**
 * Phase B precision (read-side): уточняет номер страницы внутри блока, растянутого на
 * несколько окон. [basePage] приходит из [ReaderPagination.pageForAnchor] — это первая
 * страница, открывающая `anchor.blockIndex`. Если `anchor.charStart > 0`, переводим его
 * в номер строки ([BlockHeightCalculator.lineForCharStart]) и затем в Y внутри блока
 * через закэшированные `lineBottoms`. Дальше [ReaderPagination.pageWithinBlockForY]
 * выбирает последнее окно внутри блока с `firstBlockOffsetPx ≤ Y`.
 *
 * Откат к [basePage]: отсутствие блока / неделимый блок / `lineBottoms` ещё не готов /
 * line за пределами замеренного — во всех случаях лучше встать на начало блока, чем
 * на чужую страницу.
 *
 * Top-level (а не приватный метод [PagedReflowContent]): композайбл уже большой, плюс
 * у такого вынесения нулевой ремембер-state и можно потенциально гонять на
 * Dispatchers.Default (см. [LaunchedEffect(windows)]).
 */
private fun refinePageForCharStart(
    windows: List<ReaderPagination.PageWindow>,
    basePage: Int,
    anchor: TextAnchor,
    blocks: List<ReflowBlock>,
    lineBottoms: Map<Int, List<Float>>,
    contentWidthPx: Int,
    settings: ReflowReaderSettings,
    textMeasurer: TextMeasurer,
    density: Density,
): Int {
    val targetY =
        anchorLineTopY(
            anchor = anchor,
            blocks = blocks,
            lineBottoms = lineBottoms,
            contentWidthPx = contentWidthPx,
            settings = settings,
            textMeasurer = textMeasurer,
            density = density,
        ) ?: return basePage
    return ReaderPagination.pageWithinBlockForY(windows, basePage, anchor.blockIndex, targetY)
}

/**
 * Y верха строки якоря в системе координат блока — `lineBottoms[line-1]` (низ
 * предыдущей строки = верх нашей). `null` означает «уточнение неприменимо»: charStart=0
 * (начало блока), блок отсутствует/не-делимый (lineForCharStart=0), lineBottoms ещё
 * не готов, или строка вне замеренного диапазона.
 */
private fun anchorLineTopY(
    anchor: TextAnchor,
    blocks: List<ReflowBlock>,
    lineBottoms: Map<Int, List<Float>>,
    contentWidthPx: Int,
    settings: ReflowReaderSettings,
    textMeasurer: TextMeasurer,
    density: Density,
): Float? {
    val block = blocks.getOrNull(anchor.blockIndex)
    if (anchor.charStart <= 0 || block == null) return null
    val line =
        BlockHeightCalculator.lineForCharStart(
            block = block,
            contentWidthPx = contentWidthPx,
            settings = settings,
            textMeasurer = textMeasurer,
            density = density,
            charStart = anchor.charStart,
        )
    return if (line <= 0) null else lineBottoms[anchor.blockIndex].orEmpty().getOrNull(line - 1)
}

/**
 * Страничный режим: измеряет высоты блоков при текущей ширине колонки, раскладывает
 * их по страницам ([ReaderPagination]) и отображает через [HorizontalPager].
 * Пейджер: padding + clipToBounds вынесены на контейнер, а не на каждую страницу —
 * при перелистывании страницы движутся в едином клип-боксе и двойное поле не возникает.
 * Пока высоты не измерены — невидимый проход рендерит блоки и снимает их высоты.
 */
@Composable
private fun PagedReflowContent(
    document: ReflowDocument,
    anchorsByBlock: Map<Int, List<TextAnchor>>,
    notesByBlock: Map<Int, List<NoteAnchor>>,
    onNoteTap: (PageNote) -> Unit,
    settings: ReflowReaderSettings,
    renderPage: (suspend (pageIndex: Int) -> ImageBitmap?)?,
    initialAnchor: TextAnchor,
    onVisibleAnchorChange: (TextAnchor) -> Unit,
    onPageDeltaReady: (((Int) -> Unit)?) -> Unit,
    navigateToBlock: MutableState<Int?>,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // Полезная высота страницы = вьюпорт минус резерв под верхний счётчик страниц
        // и нижний airbar настроек (оба всплывают по тапу). Резервируем всегда, чтобы
        // пагинация не «прыгала» при показе/скрытии хрома и текст на него не наезжал.
        val pageHeightPx =
            with(density) {
                // roundToPx() согласуется с тем, как Compose сам округляет Dp→px при
                // вёрстке — без него дробный пиксель в pageHeightPx мог чуть
                // расходиться с реальной высотой клип-бокса.
                (maxHeight - settings.topMargin - settings.bottomMargin).roundToPx().toFloat().coerceAtLeast(1f)
            }
        val spacingPx =
            with(density) {
                // roundToPx() вместо toPx(): Arrangement.spacedBy внутри тоже округляет
                // до целых пикселей; дробный spacingPx в пагинаторе накапливался по
                // нескольким зазорам и блок последнего абзаца вылезал за clipToBounds.
                settings.blockSpacing.roundToPx().toFloat()
            }
        // Сигнатура измерения: всё, что меняет высоту блока при вёрстке. Если она изменилась
        // (другой кегль/шрифт/межстрочный/ширина колонки/поля/выравнивание/переносы/трекинг/
        // bionic) — высоты переснимаем заново. Иначе пагинация считает по устаревшим высотам:
        // после увеличения кегля блоки рендерятся выше, чем думала раскладка, низ страницы
        // обрезается clipToBounds — и кусок текста пропадает между страницами.
        val measureKey =
            listOf(
                settings.fontFamily,
                settings.fontSize,
                settings.lineHeightMultiplier,
                settings.columnChars,
                settings.contentPadding,
                settings.align,
                settings.hyphenation,
                settings.letterSpacing,
                settings.wordSpacing,
                settings.bionic,
                // Ширина вьюпорта влияет на перенос строк и, значит, на высоты блоков.
                // Без неё смена ориентации / split-screen не сбрасывает замер.
                maxWidth,
            )
        // Phase 0 telemetry: маркеры для трассировки открытия reader-mode.
        // openMark — момент первой композиции страничного контента для документа;
        // measurePassMark — момент старта невидимого прохода обмера (сбрасывается
        // и при смене типографики/ширины, т.е. при ре-пагинации).
        val openMark = remember(document) { TimeSource.Monotonic.markNow() }
        val measurePassMark = remember(document, measureKey) { TimeSource.Monotonic.markNow() }
        val firstPageLogged = remember(document) { mutableStateOf(false) }

        // Ширина контентной колонки в пикселях. Используется и для бэк-обмера
        // через TextMeasurer, и как ключ дискового layout-кэша (см. LaunchedEffect
        // ниже). Считается из BoxWithConstraints.maxWidth с учётом
        // maxContentWidth и горизонтального padding'а — той же формулой, что
        // диктует фактический рендер LazyColumn внутри pager'а.
        val contentWidthPx =
            with(density) {
                (maxWidth.coerceAtMost(settings.maxContentWidth) - settings.contentPadding * 2)
                    .roundToPx()
                    .coerceAtLeast(1)
            }
        // figureHeights — стейт-мап (а не remember-вычисление), потому что значение
        // хранится в layout-кэше: после bg-measure / cache-load populate в
        // LaunchedEffect ниже. Если бы значения пересчитывались по текущему
        // contentWidthPx «налету», transient-колебание maxWidth на 1-2 px
        // приводило бы к иному round(...) и второй re-paginate с visual shift
        // после first-page. Из кэша поднимаем ровно те значения, что были при
        // записи.
        val figureHeights = remember(document, measureKey) { mutableStateMapOf<Int, Int>() }
        val textBlockCount = remember(document) { document.blocks.count { it !is ReflowBlock.Figure } }

        // Высоты и линейные срезы блоков, измеряемые на фоне через TextMeasurer
        // (детерминированно по (text, style, constraints)). Старый «невидимый
        // Compose-проход» удалён: он синхронно блокировал main-поток на 13-17 с
        // и был источником ANR + drift'а при возврате на читанную страницу.
        val heights = remember(document, measureKey) { mutableStateMapOf<Int, Int>() }
        val lineBottoms = remember(document, measureKey) { mutableStateMapOf<Int, List<Float>>() }
        val measured = textBlockCount == 0 || heights.size >= textBlockCount

        // TextMeasurer обмера блоков. cacheSize=0 — внутренний LRU синхронизован
        // mutex'ом и сериализует параллельные measure; для 7000 уникальных блоков
        // он бесполезен, а память жрёт.
        val textMeasurer = rememberTextMeasurer(cacheSize = 0)
        // Дисковый layout-cache: тиже cr3cache в KOReader. Считаем раскладку один
        // раз на сочетание (содержимое × ширина × типографика), при повторном
        // открытии — мгновенно поднимаем готовые heights/lineBottoms.
        val layoutCache = LocalReflowLayoutCache.current
        val docFingerprint = remember(document) { fingerprintDocument(document) }
        LaunchedEffect(document, measureKey, contentWidthPx, layoutCache) {
            if (textBlockCount == 0 || contentWidthPx <= 0) return@LaunchedEffect
            val cacheKey =
                LayoutCacheKey(
                    docFingerprint = docFingerprint,
                    contentWidthPx = contentWidthPx,
                    fontFamilyId = settings.fontFamily.toString(),
                    fontSizeSp = settings.fontSize.value,
                    lineHeightMultiplier = settings.lineHeightMultiplier,
                    letterSpacingSp = settings.letterSpacing.value,
                    wordSpacingSp = settings.wordSpacing.value,
                    hyphenation = settings.hyphenation,
                    align = settings.align.name,
                    bionic = settings.bionic,
                    columnChars = settings.columnChars,
                    contentPaddingDp = settings.contentPadding.value,
                )
            val cached = layoutCache.read(cacheKey)
            if (cached != null) {
                // Все три карты пишем АТОМАРНО в один snapshot — иначе первая
                // рекомпозиция увидит heights:populated, figureHeights:empty
                // → blockLayouts с figureHeights=0 → paginate с неверным
                // page-count → re-paginate после второго putAll → visual shift.
                figureHeights.putAll(cached.figureHeights)
                heights.putAll(cached.textHeights)
                lineBottoms.putAll(cached.textLineBottoms)
                readerLogger.info {
                    "PdfReflow: layout-cache populated text=${cached.textHeights.size} " +
                        "figures=${cached.figureHeights.size} " +
                        "since-open=${openMark.elapsedNow().inWholeMilliseconds}ms"
                }
                return@LaunchedEffect
            }
            // На cache MISS вычисляем figureHeights аналитически (round(width / aspect)
            // — детерминированно для заданной contentWidthPx) и складываем рядом с
            // bg-measure-результатом текстовых блоков. Пишем всё в кэш одним блоком.
            val newFigureHeights = HashMap<Int, Int>()
            document.blocks.forEachIndexed { index, block ->
                if (block is ReflowBlock.Figure) {
                    val ratio = block.aspectRatio.takeIf { it > 0f } ?: 1f
                    newFigureHeights[index] = (contentWidthPx / ratio).roundToInt().coerceAtLeast(1)
                }
            }
            readerLogger.info {
                "PdfReflow: bg-measure-start blocks=${document.blocks.size} " +
                    "text=$textBlockCount widthPx=$contentWidthPx " +
                    "since-open=${openMark.elapsedNow().inWholeMilliseconds}ms"
            }
            val mark = TimeSource.Monotonic.markNow()
            val (newHeights, newLineBottoms) =
                withContext(Dispatchers.Default) {
                    val h = HashMap<Int, Int>(textBlockCount)
                    val lb = HashMap<Int, List<Float>>(textBlockCount)
                    document.blocks.forEachIndexed { index, block ->
                        if (block is ReflowBlock.Figure) return@forEachIndexed
                        val m =
                            BlockHeightCalculator.measure(
                                block = block,
                                index = index,
                                contentWidthPx = contentWidthPx,
                                settings = settings,
                                textMeasurer = textMeasurer,
                                density = density,
                                figureHeights = newFigureHeights,
                            )
                        h[index] = m.heightPx
                        if (m.lineBottoms.isNotEmpty()) lb[index] = m.lineBottoms
                        // Cooperative cancellation: бэк-обмер 7000 блоков на mid-range
                        // Huawei ≈ 1-2 секунды; даём шанс отменить, если пользователь
                        // вышел из reader-mode / переключил типографику.
                        if (index and 0x3F == 0) currentCoroutineContext().ensureActive()
                    }
                    h to lb
                }
            figureHeights.putAll(newFigureHeights)
            heights.putAll(newHeights)
            lineBottoms.putAll(newLineBottoms)
            readerLogger.info {
                "PdfReflow: bg-measure-done blocks=$textBlockCount " +
                    "took=${mark.elapsedNow().inWholeMilliseconds}ms " +
                    "since-open=${openMark.elapsedNow().inWholeMilliseconds}ms"
            }
            // Записываем в кэш ПОСЛЕ обновления state (страница уже отрисовалась)
            // — пользователь не ждёт I/O записи. Ошибки/cancellation залогируются
            // и проглотятся внутри cache.write.
            layoutCache.write(
                cacheKey,
                CachedLayout(
                    textHeights = newHeights,
                    textLineBottoms = newLineBottoms,
                    figureHeights = newFigureHeights,
                ),
            )
        }
        LaunchedEffect(measured, document, measureKey) {
            if (measured) {
                readerLogger.info {
                    "PdfReflow: measured blocks=${document.blocks.size} " +
                        "measure=${measurePassMark.elapsedNow().inWholeMilliseconds}ms " +
                        "since-open=${openMark.elapsedNow().inWholeMilliseconds}ms"
                }
            }
        }

        if (!measured) {
            // Лоадер вместо «невидимого Compose-прохода»: пока [BlockHeightCalculator]
            // считает высоты на фоновом диспетчере, main-поток свободен. Жесты
            // потребляются (consume) — иначе тап-зоны родителя сработали бы на
            // экране, где ещё ничего не отрисовано.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() }
                        },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "Готовим читалку…",
                    style =
                        settings
                            .paragraphStyle()
                            .copy(color = settings.textColor.copy(alpha = FIGURE_LABEL_ALPHA)),
                )
            }
            return@BoxWithConstraints
        }

        // Метрики блоков для построчной раскладки: высота + (для делимых текстовых блоков)
        // границы строк. Заголовки неделимы и не дают разрыв сразу за собой (orphan-control);
        // картинки/таблицы/разделители неделимы, но разрыв после них допустим. List.equals
        // сравнивает поэлементно, поэтому remember пересчитает окна при любом изменении высоты
        // или числа строк (в т.ч. когда FigureView вырастает из placeholder после загрузки).
        val blockLayouts =
            document.blocks.mapIndexed { index, block ->
                val splittable =
                    block is ReflowBlock.Paragraph ||
                        block is ReflowBlock.ListItem ||
                        block is ReflowBlock.Blockquote
                // Figure: высота строго из figureHeights (round(contentWidthPx /
                // aspectRatio)). Остальные блоки — из onSizeChanged-обмера.
                val heightPx =
                    if (block is ReflowBlock.Figure) {
                        figureHeights[index]?.toFloat() ?: 0f
                    } else {
                        (heights[index] ?: 0).toFloat()
                    }
                ReaderPagination.BlockLayout(
                    heightPx = heightPx,
                    lineBottomsPx = if (splittable) lineBottoms[index].orEmpty() else emptyList(),
                    breakAfter = block !is ReflowBlock.Heading,
                )
            }
        val windows =
            remember(pageHeightPx, spacingPx, blockLayouts) {
                val paginateMark = TimeSource.Monotonic.markNow()
                val computed = ReaderPagination.pageWindows(blockLayouts, pageHeightPx, spacingPx)
                readerLogger.info {
                    "PdfReflow: paginate windows=${computed.size} blocks=${blockLayouts.size} " +
                        "took=${paginateMark.elapsedNow().inWholeMilliseconds}ms " +
                        "since-open=${openMark.elapsedNow().inWholeMilliseconds}ms"
                }
                computed
            }
        if (windows.isEmpty()) return@BoxWithConstraints

        val lastPage = windows.lastIndex
        val scope = rememberCoroutineScope()
        // Якорь чтения — TextAnchor начала текущей страницы. Durable: переживает
        // ре-пагинацию (смена кегля/полей/ориентации) и переключение страница<->скролл.
        // Номер страницы выводим из него после раскладки — сам номер нестабилен при
        // переверстке. Phase B: учитываем charStart для блока, растянутого на много
        // страниц (мапим строку → смещение px → нужное окно).
        var anchor by remember(document) { mutableStateOf(initialAnchor) }
        val initialPage =
            remember(document) {
                refinePageForCharStart(
                    windows = windows,
                    basePage = ReaderPagination.pageForAnchor(windows, anchor),
                    anchor = anchor,
                    blocks = document.blocks,
                    lineBottoms = lineBottoms,
                    contentWidthPx = contentWidthPx,
                    settings = settings,
                    textMeasurer = textMeasurer,
                    density = density,
                )
            }
        val pagerState = rememberPagerState(initialPage = initialPage) { windows.size }
        // Свежие значения для трекера якоря ниже: он НЕ перезапускается на ре-пагинации
        // (иначе перечитал бы устаревший индекс страницы в новой раскладке и затёр якорь —
        // F-10: место чтения терялось при смене ориентации). См. LaunchedEffect(pagerState).
        val latestWindows = rememberUpdatedState(windows)
        val latestContentWidthPx = rememberUpdatedState(contentWidthPx)
        val latestSettings = rememberUpdatedState(settings)
        // Ре-пагинация: встаём на страницу, открывающуюся якорным блоком (не на тот же
        // номер) — место чтения сохраняется при смене кегля/полей/ориентации. Тяжёлый measure
        // (до ~50 ms для блока-главы EPUB) уносим на Default, чтобы не блокировать main.
        // Якорь захватываем в локальную val ДО suspend: трекер ниже сдвинет `anchor` уже
        // после скролла на восстановленную страницу, и читать «живой» `anchor` тут нельзя.
        LaunchedEffect(windows) {
            val restoreAnchor = anchor
            val base = ReaderPagination.pageForAnchor(windows, restoreAnchor)
            val newPage =
                withContext(Dispatchers.Default) {
                    refinePageForCharStart(
                        windows = windows,
                        basePage = base,
                        anchor = restoreAnchor,
                        blocks = document.blocks,
                        lineBottoms = lineBottoms,
                        contentWidthPx = contentWidthPx,
                        settings = settings,
                        textMeasurer = textMeasurer,
                        density = density,
                    )
                }
            if (newPage != pagerState.currentPage) pagerState.scrollToPage(newPage)
        }
        val pagedBlockTarget by navigateToBlock
        LaunchedEffect(pagedBlockTarget) {
            val block = pagedBlockTarget ?: return@LaunchedEffect
            val target =
                windows.indexOfLast { it.firstBlock <= block }.takeIf { it >= 0 } ?: return@LaunchedEffect
            pagerState.animateScrollToPage(target)
            navigateToBlock.value = null
        }
        // Первый блок текущей страницы = якорь; публикуем наружу (прогресс + смена режима).
        // Phase B: вычисляем charStart по firstBlockOffsetPx → строка → начало строки в
        // тексте блока (один measure firstBlock на смену страницы; Default для патологии).
        // Ключ — только pagerState (+document), НЕ windows: трекер живёт сквозь ре-пагинацию
        // и реагирует лишь на реальные смены страницы (свайп/тап/клавиша/восстановление
        // выше), а не на рестарт из-за смены раскладки. windows/ширину/настройки читаем
        // «вживую» через rememberUpdatedState, чтобы маппинг был по актуальной раскладке.
        LaunchedEffect(pagerState, document) {
            snapshotFlow { pagerState.currentPage }.collect { pageIndex ->
                val window = latestWindows.value.getOrNull(pageIndex)
                val first = window?.firstBlock ?: 0
                val charStart =
                    if (window != null && window.firstBlockOffsetPx > 0) {
                        val block = document.blocks.getOrNull(first)
                        if (block != null) {
                            withContext(Dispatchers.Default) {
                                BlockHeightCalculator.charStartAtOffsetPx(
                                    block = block,
                                    contentWidthPx = latestContentWidthPx.value,
                                    settings = latestSettings.value,
                                    textMeasurer = textMeasurer,
                                    density = density,
                                    offsetPx = window.firstBlockOffsetPx.toFloat(),
                                )
                            }
                        } else {
                            0
                        }
                    } else {
                        0
                    }
                val next = TextAnchor(blockIndex = first, charStart = charStart, charEnd = charStart)
                anchor = next
                onVisibleAnchorChange(next)
            }
        }

        // «Листнуть на ±N»: тап-зоны, клавиши и Initial-свайп (при активном маркере) идут
        // через programmatic scroll. NONE и «уменьшить движение» → мгновенно, иначе анимация.
        val animatePager = !isReducedMotionEnabled() && settings.pageTransition != PageTransition.NONE
        val pageDeltaHandler: (Int) -> Unit =
            remember(scope, pagerState, lastPage, animatePager) {
                { delta ->
                    val target = (pagerState.currentPage + delta).coerceIn(0, lastPage)
                    scope.launch {
                        if (animatePager) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
                    }
                }
            }
        DisposableEffect(pageDeltaHandler) {
            onPageDeltaReady(pageDeltaHandler)
            onDispose { onPageDeltaReady(null) }
        }

        val scrollLocked = LocalReflowSelectionState.current.scrollLocked
        HorizontalPager(
            state = pagerState,
            // Соседние страницы не держим скомпонованными: при построчном переносе один блок
            // попадает на две страницы, а двойная регистрация координат ломала бы хит-тест
            // выделения. На свайпе сосед всё равно скомпонуется (выделение тогда заблокировано).
            beyondViewportPageCount = 0,
            userScrollEnabled = !scrollLocked,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = settings.topMargin, bottom = settings.bottomMargin)
                    .clipToBounds(),
        ) { pageIndex ->
            if (!firstPageLogged.value) {
                SideEffect {
                    if (!firstPageLogged.value) {
                        firstPageLogged.value = true
                        readerLogger.info {
                            "PdfReflow: first-page-composed page=$pageIndex windows=${windows.size} " +
                                "since-open=${openMark.elapsedNow().inWholeMilliseconds}ms"
                        }
                    }
                }
            }
            val pageWindow = windows[pageIndex]
            // Высота окна обрезана по границе строки: нижняя строка не режется пополам, а внизу
            // остаётся зазор меньше строки (поле, как в книге), а не целый незаполненный абзац.
            val windowHeightDp = with(density) { pageWindow.heightPx.toDp() }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                // Страница — окно в непрерывную колонку: LazyColumn без пользовательского скролла,
                // спозиционированный на стартовую строку окна. Тот же рендер блоков, что и в
                // скролл-режиме (виртуализация, выделение, провенанс-спаны работают без изменений).
                val pageList = rememberLazyListState(pageWindow.firstBlock, pageWindow.firstBlockOffsetPx)
                // Держим страницу на её строке при ре-пагинации (смена кегля/полей).
                LaunchedEffect(pageWindow) {
                    pageList.scrollToItem(pageWindow.firstBlock, pageWindow.firstBlockOffsetPx)
                }
                LazyColumn(
                    state = pageList,
                    userScrollEnabled = false,
                    modifier =
                        Modifier
                            .widthIn(max = settings.maxContentWidth)
                            .fillMaxWidth()
                            .height(windowHeightDp)
                            .clipToBounds(),
                    contentPadding = PaddingValues(horizontal = settings.contentPadding),
                    verticalArrangement = Arrangement.spacedBy(settings.blockSpacing),
                ) {
                    itemsIndexed(document.blocks) { index, block ->
                        // onSizeChanged больше не используется: высоты блоков заданы
                        // детерминированно (BlockHeightCalculator на фоне +
                        // figureHeights аналитически), а ре-замер на render-фазе
                        // — главный источник layout drift'а при возврате на читанную
                        // страницу (defect b). Если TextMeasurer-обмер расходится
                        // с BasicText-рендером, корень — в TextStyle (см.
                        // DeterministicLineHeight / readerPlatformTextStyle), не
                        // в auto-correction после рендера.
                        Box(modifier = Modifier.fillMaxWidth()) {
                            ReflowBlockView(
                                block,
                                anchorsByBlock[index].orEmpty(),
                                settings,
                                renderPage,
                                blockIndex = index,
                                notes = notesByBlock[index].orEmpty(),
                                onNoteTap = onNoteTap,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReflowBlockView(
    block: ReflowBlock,
    anchors: List<TextAnchor>,
    settings: ReflowReaderSettings,
    renderPage: (suspend (pageIndex: Int) -> ImageBitmap?)?,
    blockIndex: Int,
    onLines: ((List<Float>) -> Unit)? = null,
    notes: List<NoteAnchor> = emptyList(),
    onNoteTap: (PageNote) -> Unit = {},
) {
    when (block) {
        is ReflowBlock.Heading ->
            // Заголовок неделим (по строкам не рвём) — onLines не передаём.
            SelectableReflowText(
                content = BlockContent(block.text, block.source),
                anchors = anchors,
                style = settings.headingStyle(block.level),
                settings = settings,
                blockIndex = blockIndex,
                notes = notes,
                onNoteTap = onNoteTap,
            )

        is ReflowBlock.Paragraph ->
            SelectableReflowText(
                content = BlockContent(block.text, block.source),
                anchors = anchors,
                style = settings.paragraphStyle(),
                settings = settings,
                blockIndex = blockIndex,
                onLines = onLines,
                notes = notes,
                onNoteTap = onNoteTap,
            )

        is ReflowBlock.ListItem ->
            // Уровень вложенности → дополнительный indent: каждый вложенный уровень
            // добавляет ещё один contentPadding слева. Самый верхний уровень (level=0)
            // — один contentPadding (как и было). Phase A: визуальный indent;
            // bullet/numbering glyph остаётся частью block.text. Отступ — параметром
            // SelectableReflowText (на самом BasicText), без внешнего Box: иначе
            // публикуемые координаты не совпадали бы с началом текста и хит-тест
            // выделения промахивался по строке.
            SelectableReflowText(
                content = BlockContent(block.text, block.source),
                anchors = anchors,
                style = settings.paragraphStyle(),
                settings = settings,
                blockIndex = blockIndex,
                onLines = onLines,
                contentPadding = PaddingValues(start = settings.contentPadding * (block.level + 1)),
                notes = notes,
                onNoteTap = onNoteTap,
            )

        is ReflowBlock.Blockquote ->
            BlockquoteView(block, anchors, settings, blockIndex, onLines, notes, onNoteTap)

        is ReflowBlock.Table -> TableView(block, settings)

        is ReflowBlock.Figure -> FigureView(block, settings, renderPage)

        ReflowBlock.Divider -> DividerView(settings)

        is ReflowBlock.Code ->
            // Code-блок: моноширинный шрифт, переносы строк сохранены.
            // Используем стандартный SelectableReflowText со специальным
            // monospace-стилем; selection/anchors работают как у Paragraph.
            SelectableReflowText(
                content = BlockContent(block.text, block.source),
                anchors = anchors,
                style = settings.codeStyle(),
                settings = settings,
                blockIndex = blockIndex,
                onLines = onLines,
                notes = notes,
                onNoteTap = onNoteTap,
            )

        is ReflowBlock.Footnote ->
            // Footnote: меньший шрифт + dim alpha. Marker (¹/*) пока не рендерится
            // отдельно — он встроен в text если PDF содержал его inline.
            SelectableReflowText(
                content = BlockContent(block.text, block.source),
                anchors = anchors,
                style = settings.footnoteStyle(),
                settings = settings,
                blockIndex = blockIndex,
                onLines = onLines,
                notes = notes,
                onNoteTap = onNoteTap,
            )
    }
}

/**
 * Текст блока и его исходные провенанс-спаны — одна цельная единица «что рисуем».
 * Сгруппированы, чтобы не плодить параметры [SelectableReflowText].
 *
 * @property text видимый текст блока
 * @property source провенанс-спаны (жирность/моноширинность/исходные области)
 */
private data class BlockContent(
    val text: String,
    val source: List<SourceSpan>,
)

/**
 * Текстовый блок ридера, участвующий в сквозном выделении (см. [ReflowSelectionState]).
 * Сам жест выделения живёт на корневом контейнере; блок лишь публикует свою раскладку
 * ([reportLayout]) и собственные координаты ([reportCoordinates]) — из них хит-тест
 * лениво считает границы в системе контейнера, чтобы попасть в нужный блок и символ — и
 * читает свой текущий диапазон выделения ([selectionAnchorFor]) для превью прямо в тексте.
 * На выходе из композиции снимает регистрацию ([forget]). Без активного выделения это
 * обычный [BasicText].
 */
@Composable
private fun SelectableReflowText(
    content: BlockContent,
    anchors: List<TextAnchor>,
    style: TextStyle,
    settings: ReflowReaderSettings,
    blockIndex: Int,
    onLines: ((List<Float>) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(),
    notes: List<NoteAnchor> = emptyList(),
    onNoteTap: (PageNote) -> Unit = {},
) {
    val selectionState = LocalReflowSelectionState.current
    DisposableEffect(selectionState, blockIndex) {
        onDispose { selectionState.forget(blockIndex) }
    }
    val liveAnchor = selectionState.selectionAnchorFor(blockIndex)
    val liveAnchors = if (liveAnchor != null) anchors + liveAnchor else anchors

    // Раскладка текущего блока — нужна, чтобы поставить бейдж заметки напротив строки её
    // анкера (getCursorRect(charStart).top). Захватываем из того же onTextLayout, что
    // публикует раскладку в selectionState.
    var textLayout by remember(blockIndex) { mutableStateOf<TextLayoutResult?>(null) }

    Box {
        BasicText(
            text = styledText(content.text, content.source, liveAnchors, settings),
            style = style,
            onTextLayout = { layout ->
                textLayout = layout
                selectionState.reportLayout(blockIndex, layout)
                // Нижние границы строк (для построчной раскладки страниц): снимаем только когда
                // нужно (проход обмера в PagedReflowContent передаёт onLines).
                onLines?.invoke(List(layout.lineCount) { layout.getLineBottom(it) })
            },
            // Отступ блока (indent списка / втяжка цитаты) — на самом BasicText и ДО
            // onGloballyPositioned, чтобы публикуемые координаты совпадали с началом текста.
            // Раньше отступ давал внешний Box: его координаты включали padding, и хит-тест
            // выделения мог промахнуться по строке (defect «выделяется не та строка»).
            modifier =
                Modifier
                    .padding(contentPadding)
                    .onGloballyPositioned { coordinates ->
                        selectionState.reportCoordinates(blockIndex, coordinates)
                    },
        )

        // Тап-бейджи заметок: цветной кружок на левом поле напротив строки анкера.
        // Подсветка диапазона заметки приходит через общий список [anchors] (вызывающий
        // слой подмешивает note-анкеры), поэтому здесь рисуем только бейдж. Накладывается
        // поверх (x=0) и не сдвигает раскладку текста.
        val layout = textLayout
        if (layout != null) {
            notes.forEach { na ->
                val charStart = na.anchor.charStart.coerceIn(0, content.text.length)
                val top = layout.getCursorRect(charStart).top
                Box(
                    modifier =
                        Modifier
                            .offset { IntOffset(0, top.roundToInt()) }
                            .size(NOTE_MARKER_SIZE)
                            // colorArgb — упакованный ARGB; Color(Int) трактует биты как ARGB
                            // (Color(Long) трактовал бы как RGBA — другой порядок).
                            .background(color = Color(na.note.colorArgb.toInt()), shape = CircleShape)
                            .clickable { onNoteTap(na.note) },
                )
            }
        }
    }
}

/**
 * Цитата: тонкая вертикальная линейка слева + втянутый курсивный текст
 * приглушённого цвета. Подсветка [anchors] течёт вместе с текстом, как в абзаце.
 */
@Composable
private fun BlockquoteView(
    block: ReflowBlock.Blockquote,
    anchors: List<TextAnchor>,
    settings: ReflowReaderSettings,
    blockIndex: Int,
    onLines: ((List<Float>) -> Unit)? = null,
    notes: List<NoteAnchor> = emptyList(),
    onNoteTap: (PageNote) -> Unit = {},
) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier =
                Modifier
                    .width(BLOCKQUOTE_BAR_WIDTH)
                    .fillMaxHeight()
                    .background(settings.textColor.copy(alpha = BLOCKQUOTE_BAR_ALPHA)),
        )
        // Втяжка — параметром SelectableReflowText (на самом BasicText), без внешнего
        // Box: координаты текста публикуются от его реального начала, и хит-тест
        // выделения не промахивается по строке.
        SelectableReflowText(
            content = BlockContent(block.text, block.source),
            anchors = anchors,
            style = settings.paragraphStyle().copy(fontStyle = FontStyle.Italic),
            settings = settings,
            blockIndex = blockIndex,
            onLines = onLines,
            contentPadding = PaddingValues(start = settings.contentPadding),
            notes = notes,
            onNoteTap = onNoteTap,
        )
    }
}

/**
 * Оверлей подтверждения выделения: editor-подобные ручки-курсоры на концах выделения
 * (которые можно перетаскивать, доводя диапазон) + плавающая панель «Копировать/Выделить».
 *
 * Координаты концов берём из [ReflowSelectionState.endRect] — они в системе контейнера,
 * та же, что и у этого оверлея (matchParentSize контейнера). Тап мимо ручек и панели
 * закрывает подтверждение без фиксации ([onDismiss]); тап по панели — действие.
 *
 * @param onCopy «Копировать» — копирует выделенный текст в буфер
 * @param onHighlight «Выделить» — создаёт подсветку (как при активном маркере)
 * @param onDismiss тап мимо / отмена — закрыть без фиксации
 */
@Composable
private fun SelectionConfirmOverlay(
    state: ReflowSelectionState,
    settings: ReflowReaderSettings,
    onCopy: () -> Unit,
    onHighlight: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val anchorRect = state.endRect(SelectionEnd.ANCHOR)
    val focusRect = state.endRect(SelectionEnd.FOCUS)
    Box(
        modifier =
            modifier.pointerInput(Unit) {
                // Тап мимо ручек/панели закрывает подтверждение. Ручки и панель —
                // отдельные дочерние pointerInput'ы, их события сюда не доходят.
                detectTapGestures { onDismiss() }
            },
    ) {
        // Ручки-курсоры: только после фиксации (здесь), не во время первичного drag —
        // иначе они закрывали бы текст под пальцем.
        anchorRect?.let { SelectionHandle(it, density, state, SelectionEnd.ANCHOR, settings) }
        focusRect?.let { SelectionHandle(it, density, state, SelectionEnd.FOCUS, settings) }

        // Панель действий — над верхним из концов выделения, по горизонтали у его начала.
        val topRect =
            when {
                anchorRect == null -> focusRect
                focusRect == null -> anchorRect
                anchorRect.top <= focusRect.top -> anchorRect
                else -> focusRect
            }
        if (topRect != null) {
            SelectionActionBar(
                anchorRect = topRect,
                density = density,
                settings = settings,
                onCopy = onCopy,
                onHighlight = onHighlight,
            )
        }
    }
}

/**
 * Перетаскиваемая ручка-курсор на конце выделения [end]. Рисуется кружком под нижней
 * границей курсор-прямоугольника [rect] (координаты контейнера); drag двигает этот
 * конец через [ReflowSelectionState.moveEnd], доводя диапазон, как в текстовом редакторе.
 */
@Composable
private fun BoxScope.SelectionHandle(
    rect: Rect,
    density: Density,
    state: ReflowSelectionState,
    end: SelectionEnd,
    settings: ReflowReaderSettings,
) {
    val radiusPx = with(density) { SELECTION_HANDLE_RADIUS.toPx() }
    // Центр ручки в px (контейнерная система): по горизонтали — у конца, по вертикали —
    // чуть ниже базовой линии курсора. Для drag ведём собственную позицию пальца.
    var dragPos by remember(rect.left, rect.bottom) { mutableStateOf(Offset(rect.left, rect.bottom)) }
    Box(
        modifier =
            Modifier
                .offset {
                    IntOffset(
                        (dragPos.x - radiusPx).roundToInt(),
                        dragPos.y.roundToInt(),
                    )
                }.size(SELECTION_HANDLE_RADIUS * 2)
                .pointerInput(end, state) {
                    detectDragGestures(
                        onDragStart = { dragPos = Offset(rect.left, rect.bottom) },
                        onDrag = { change, amount ->
                            change.consume()
                            dragPos += amount
                            // Целимся в середину строки чуть выше точки касания ручки,
                            // чтобы хит-тест попадал в текст, а не в зазор под строкой.
                            state.moveTo(Offset(dragPos.x, dragPos.y - radiusPx), anchoring = false, end = end)
                        },
                    )
                }.drawBehind {
                    drawCircle(
                        color = settings.highlightColor.copy(alpha = SELECTION_HANDLE_ALPHA),
                        radius = radiusPx,
                        center = Offset(radiusPx, radiusPx),
                    )
                },
    )
}

/**
 * Неинтерактивные ручки-курсоры на концах выделения во время самого жеста — живое
 * превью ещё до фиксации. В отличие от [SelectionHandle], без собственного pointerInput
 * (фокус ведёт палец), поэтому не перехватывают идущий drag. Координаты концов берём из
 * [ReflowSelectionState.endRect] — в системе контейнера, как и у [SelectionConfirmOverlay].
 */
@Composable
private fun BoxScope.LiveSelectionHandles(
    state: ReflowSelectionState,
    settings: ReflowReaderSettings,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { SELECTION_HANDLE_RADIUS.toPx() }
    Box(modifier) {
        state.endRect(SelectionEnd.ANCHOR)?.let { LiveSelectionHandle(it, radiusPx, settings) }
        state.endRect(SelectionEnd.FOCUS)?.let { LiveSelectionHandle(it, radiusPx, settings) }
    }
}

/** Один неинтерактивный кружок-ручка под нижней границей курсора [rect] (см. [LiveSelectionHandles]). */
@Composable
private fun BoxScope.LiveSelectionHandle(
    rect: Rect,
    radiusPx: Float,
    settings: ReflowReaderSettings,
) {
    Box(
        modifier =
            Modifier
                .offset { IntOffset((rect.left - radiusPx).roundToInt(), rect.bottom.roundToInt()) }
                .size(SELECTION_HANDLE_RADIUS * 2)
                .drawBehind {
                    drawCircle(
                        color = settings.highlightColor.copy(alpha = SELECTION_HANDLE_ALPHA),
                        radius = radiusPx,
                        center = Offset(radiusPx, radiusPx),
                    )
                },
    )
}

/**
 * Плавающая панель «Копировать»/«Выделить» над верхним концом выделения [anchorRect]
 * (координаты контейнера). Если места сверху мало — опускается под строку.
 */
@Composable
private fun BoxScope.SelectionActionBar(
    anchorRect: Rect,
    density: Density,
    settings: ReflowReaderSettings,
    onCopy: () -> Unit,
    onHighlight: () -> Unit,
) {
    val gapPx = with(density) { SELECTION_BAR_GAP.toPx() }
    var barHeightPx by remember { mutableStateOf(0) }
    Row(
        modifier =
            Modifier
                .offset {
                    // По вертикали — над строкой; если не помещается (y<0), под строкой.
                    val above = anchorRect.top - gapPx - barHeightPx
                    val y = if (above >= 0f) above else anchorRect.bottom + gapPx
                    IntOffset(anchorRect.left.roundToInt(), y.roundToInt())
                }.onSizeChanged { barHeightPx = it.height }
                .clip(RoundedCornerShape(SELECTION_BAR_CORNER))
                .background(settings.textColor.copy(alpha = SELECTION_BAR_BG_ALPHA))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionActionButton("Копировать", settings, onCopy)
        SelectionActionButton("Выделить", settings, onHighlight)
    }
}

/** Одна кнопка панели выделения: текст на инвертированном фоне панели. */
@Composable
private fun SelectionActionButton(
    label: String,
    settings: ReflowReaderSettings,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(SELECTION_BAR_CORNER))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = settings.background, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
    }
}

/** Лёгкий «тост» подтверждения копирования внизу — гасится таймером в [ReflowReader]. */
@Composable
private fun CopiedToast(
    settings: ReflowReaderSettings,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .padding(bottom = 32.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(settings.textColor.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        BasicText(
            text = "Скопировано",
            style = TextStyle(color = settings.background, fontSize = 13.sp),
        )
    }
}

/** Тематический разделитель: тонкая короткая линия по центру с воздухом. */
@Composable
private fun DividerView(settings: ReflowReaderSettings) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = settings.blockSpacing),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(DIVIDER_LINE_FRACTION)
                    .height(1.dp)
                    .background(settings.textColor.copy(alpha = DIVIDER_LINE_ALPHA)),
        )
    }
}

/**
 * Рендерит [ReflowBlock.Table] сеткой: каждая строка — [Row] ячеек, ширина
 * колонок пропорциональна их содержимому ([tableColumnWeights]), а не делится
 * поровну — иначе узкие колонки заставляли бы текст рваться по символам
 * («piano» → «pia»/«no»). Ячейки с тонкой рамкой и общей высотой строки
 * ([IntrinsicSize.Min]). Базис ширин общий с [BlockHeightCalculator.measureTable].
 */
@Composable
private fun TableView(
    table: ReflowBlock.Table,
    settings: ReflowReaderSettings,
) {
    val borderColor = settings.textColor.copy(alpha = TABLE_BORDER_ALPHA)
    val weights = tableColumnWeights(table)
    Column(modifier = Modifier.fillMaxWidth()) {
        table.rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.cells.forEachIndexed { col, cell ->
                    Box(
                        modifier =
                            Modifier
                                .weight(weights.getOrElse(col) { 1f })
                                .fillMaxHeight()
                                .border(TABLE_BORDER_WIDTH, borderColor)
                                .background(
                                    if (row.isHeader) {
                                        settings.textColor.copy(alpha = TABLE_HEADER_ALPHA)
                                    } else {
                                        Color.Transparent
                                    },
                                ).padding(TABLE_CELL_PADDING),
                    ) {
                        BasicText(
                            text = styledText(cell.text, cell.source, emptyList(), settings),
                            style = settings.paragraphStyle(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Рисует врезку-картинку как кроп исходной страницы: лениво растеризует страницу
 * через [renderPage] и рисует её подобласть по [ReflowBlock.Figure.bounds].
 */
@Composable
private fun FigureView(
    figure: ReflowBlock.Figure,
    settings: ReflowReaderSettings,
    renderPage: (suspend (pageIndex: Int) -> ImageBitmap?)?,
) {
    // Истинный aspect берётся из figure.aspectRatio (поле PDF-уровня) — он
    // детерминирован и одинаков для placeholder и Canvas, поэтому подгрузка
    // растра не меняет высоту блока и не дёргает пагинацию.
    val ratio = figure.aspectRatio.takeIf { it > 0f } ?: 1f
    if (renderPage == null) {
        FigurePlaceholder(settings, ratio)
        return
    }
    // Засеиваем начальным значением из общего кэша: при ре-композиции фигуры
    // (виртуализация/ре-пагинация/перелистывание) растр доступен сразу, без кадра
    // с плейсхолдером — иначе скачок высоты блока зацикливал бы пагинацию.
    val cache = LocalFigureBitmapCache.current
    val pageBitmap by produceState<ImageBitmap?>(cache[figure.pageIndex], figure.pageIndex) {
        value = cache[figure.pageIndex] ?: renderPage(figure.pageIndex)?.also { cache[figure.pageIndex] = it }
    }
    val bitmap = pageBitmap
    if (bitmap == null) {
        FigurePlaceholder(settings, ratio)
        return
    }
    val bounds = figure.bounds
    val srcLeft = (bounds.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
    val srcTop = (bounds.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
    val srcWidth = (bounds.width * bitmap.width).toInt().coerceIn(1, bitmap.width - srcLeft)
    val srcHeight = (bounds.height * bitmap.height).toInt().coerceIn(1, bitmap.height - srcTop)
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(ratio)) {
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(srcLeft, srcTop),
            srcSize = IntSize(srcWidth, srcHeight),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}

/** Reading ruler: тонкая горизонтальная подсветка строки по центру вьюпорта. */
@Composable
private fun ReadingRuler(
    settings: ReflowReaderSettings,
    modifier: Modifier = Modifier,
) {
    val bandHeight = (settings.fontSize.value * settings.lineHeightMultiplier * RULER_BAND_LINES).dp
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(bandHeight)
                .background(settings.textColor.copy(alpha = RULER_BAND_ALPHA)),
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(settings.textColor.copy(alpha = RULER_LINE_ALPHA)),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(settings.textColor.copy(alpha = RULER_LINE_ALPHA)),
        )
    }
}

/** Мягкое предложение перейти в режим долгого чтения после длительной сессии. */
@Composable
private fun LongReadingPrompt(
    settings: ReflowReaderSettings,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(settings.textColor.copy(alpha = 0.9f))
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(
            text = "Читаете уже долго. Включить режим долгого чтения?",
            style = TextStyle(color = settings.background, fontSize = 13.sp),
        )
        PromptButton("Включить", filled = true, settings = settings, onClick = onApply)
        PromptButton("Позже", filled = false, settings = settings, onClick = onDismiss)
    }
}

@Composable
private fun PromptButton(
    label: String,
    filled: Boolean,
    settings: ReflowReaderSettings,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(settings.background.copy(alpha = if (filled) 1f else 0.18f))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        BasicText(
            text = label,
            style =
                TextStyle(
                    color = if (filled) settings.textColor else settings.background,
                    fontSize = 13.sp,
                    fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal,
                ),
        )
    }
}

/** Тонкая «дышащая» полоска снизу: напоминание по правилу 20-20-20 (не модалка). */
@Composable
private fun BreakReminderBar(
    settings: ReflowReaderSettings,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "break-breath")
    val breath by transition.animateFloat(
        initialValue = BREAK_BREATH_MIN,
        targetValue = BREAK_BREATH_MAX,
        animationSpec = infiniteRepeatable(tween(BREAK_BREATH_MS), RepeatMode.Reverse),
        label = "break-breath",
    )
    Box(modifier = modifier.fillMaxWidth().background(settings.textColor.copy(alpha = breath))) {
        BasicText(
            text = "Посмотрите вдаль ~20 секунд",
            modifier = Modifier.align(Alignment.Center).padding(vertical = 10.dp),
            style = TextStyle(color = settings.background, fontSize = 14.sp),
        )
    }
}

/** Лёгкая визуальная пауза-«вдох» между блоками: отступ + короткая тонкая линия по центру. */
@Composable
private fun RhythmPause(settings: ReflowReaderSettings) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = settings.blockSpacing * 2),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(RHYTHM_LINE_FRACTION)
                    .height(1.dp)
                    .background(settings.textColor.copy(alpha = RHYTHM_LINE_ALPHA)),
        )
    }
}

@Composable
private fun FigurePlaceholder(
    settings: ReflowReaderSettings,
    aspectRatio: Float,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .background(settings.textColor.copy(alpha = FIGURE_PLACEHOLDER_ALPHA)),
    ) {
        BasicText(
            text = "[ изображение ]",
            modifier = Modifier.padding(settings.contentPadding),
            style = settings.paragraphStyle().copy(color = settings.textColor.copy(alpha = FIGURE_LABEL_ALPHA)),
        )
    }
}

// Явный LineHeightStyle фиксирует высоту строки одинаково между TextMeasurer
// (фоновый обмер блока, см. BlockHeightCalculator) и BasicText (рендер).
// Trim.Both убирает «лишний воздух» сверху первой строки и снизу последней (как
// делает традиционная книжная типографика), оставляя только межстрочный
// интервал внутри абзаца. Это и предотвращает разрежённый вид (Trim.None
// добавляет ~20-30% к высоте каждого блока), и сводит к нулю drift между
// измерением и рендером — иначе верхняя строка следующей страницы «срезалась»
// после переноса по границе блока. PlatformTextStyle(includeFontPadding=false)
// на Android дополнительно убирает Android-специфичный fontPadding (см.
// [readerPlatformTextStyle]).
@Suppress("DEPRECATION")
private val DeterministicLineHeight =
    LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Proportional,
        trim = LineHeightStyle.Trim.Both,
    )

internal fun ReflowReaderSettings.paragraphStyle(): TextStyle =
    TextStyle(
        color = textColor,
        fontFamily = resolveReaderFontFamily(fontFamily),
        fontSize = fontSize,
        lineHeight = (fontSize.value * lineHeightMultiplier).sp,
        letterSpacing = letterSpacing,
        textAlign = if (align == ReaderAlign.JUSTIFY) TextAlign.Justify else TextAlign.Start,
        hyphens = if (hyphenation) Hyphens.Auto else Hyphens.None,
        lineHeightStyle = DeterministicLineHeight,
        platformStyle = readerPlatformTextStyle(),
    )

/**
 * Стиль рендера блока кода [ReflowBlock.Code]: моноширинный шрифт, тот же кегль и
 * line-height, что и у paragraph (чтобы соседство code → text не прыгало),
 * выравнивание по левому краю (justify ломал бы код), без переноса слов.
 */

/**
 * Стиль footnote: меньший кегль (0.85×) и приглушённый цвет (75% alpha).
 */
internal fun ReflowReaderSettings.footnoteStyle(): TextStyle {
    val size = fontSize.value * FOOTNOTE_FONT_SCALE
    return TextStyle(
        color = textColor.copy(alpha = FOOTNOTE_TEXT_ALPHA),
        fontFamily = resolveReaderFontFamily(fontFamily),
        fontSize = size.sp,
        lineHeight = (size * lineHeightMultiplier).sp,
        letterSpacing = letterSpacing,
        textAlign = if (align == ReaderAlign.JUSTIFY) TextAlign.Justify else TextAlign.Start,
        hyphens = if (hyphenation) Hyphens.Auto else Hyphens.None,
        lineHeightStyle = DeterministicLineHeight,
        platformStyle = readerPlatformTextStyle(),
    )
}

internal fun ReflowReaderSettings.codeStyle(): TextStyle =
    TextStyle(
        color = textColor,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize,
        lineHeight = (fontSize.value * lineHeightMultiplier).sp,
        letterSpacing = letterSpacing,
        textAlign = TextAlign.Start,
        hyphens = Hyphens.None,
        lineHeightStyle = DeterministicLineHeight,
        platformStyle = readerPlatformTextStyle(),
    )

internal fun ReflowReaderSettings.headingStyle(level: Int): TextStyle {
    val scale =
        when (level) {
            1 -> HEADING_SCALE_1
            2 -> HEADING_SCALE_2
            else -> HEADING_SCALE_3
        }
    val size = fontSize.value * scale
    return TextStyle(
        color = textColor,
        fontFamily = resolveReaderFontFamily(fontFamily),
        fontSize = size.sp,
        lineHeight = (size * HEADING_LINE_HEIGHT_MULTIPLIER).sp,
        fontWeight = FontWeight.SemiBold,
        lineHeightStyle = DeterministicLineHeight,
        platformStyle = readerPlatformTextStyle(),
    )
}

/**
 * Собирает оформленный текст блока: полужирные/моноширинные фрагменты по провенансу
 * [source], межсловный трекинг, bionic-выделение начал слов и фон-подсветку
 * [anchors]. Подсветка накладывается последней, поэтому перекрывает фон inline-кода.
 */
internal fun styledText(
    text: String,
    source: List<SourceSpan>,
    anchors: List<TextAnchor>,
    settings: ReflowReaderSettings,
): AnnotatedString {
    val needsWordSpacing = settings.wordSpacing.value > 0f && text.contains(' ')
    if (source.isEmpty() && anchors.isEmpty() && !settings.bionic && !needsWordSpacing) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        append(text)
        source.forEach { span ->
            val start = span.charStart.coerceIn(0, text.length)
            val end = span.charEnd.coerceIn(start, text.length)
            if (start >= end) return@forEach
            if (span.bold) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            if (span.monospace) {
                addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = settings.codeBackground), start, end)
            }
        }
        if (needsWordSpacing) {
            var i = text.indexOf(' ')
            while (i >= 0) {
                addStyle(SpanStyle(letterSpacing = settings.wordSpacing), i, i + 1)
                i = text.indexOf(' ', i + 1)
            }
        }
        if (settings.bionic) {
            ReaderBionic.boldRanges(text).forEach { range ->
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), range.first, range.last + 1)
            }
        }
        anchors.forEach { anchor ->
            val start = anchor.charStart.coerceIn(0, text.length)
            val end = anchor.charEnd.coerceIn(start, text.length)
            if (start < end) addStyle(SpanStyle(background = settings.highlightColor), start, end)
        }
    }
}

/** Готовая строка индикатора прогресса под выбранный формат, либо `null`. */
private fun progressLabel(
    format: ProgressFormat,
    firstVisibleBlock: Int,
    document: ReflowDocument,
): String? =
    when (format) {
        ProgressFormat.NONE -> null
        ProgressFormat.PERCENT -> "${ReaderProgress.percent(firstVisibleBlock, document.blocks.size)}%"
        ProgressFormat.CHAPTER -> currentChapterTitle(document, firstVisibleBlock)
        ProgressFormat.TIME_LEFT -> {
            val remaining = document.blocks.drop(firstVisibleBlock).sumOf { blockTextLength(it) }
            "~${ReaderProgress.minutesLeft(remaining)} мин"
        }
    }

/** Заголовок ближайшего предшествующего раздела (для индикатора «Глава»). */
private fun currentChapterTitle(
    document: ReflowDocument,
    firstVisibleBlock: Int,
): String? {
    val upTo = firstVisibleBlock.coerceIn(0, document.blocks.lastIndex.coerceAtLeast(0))
    for (i in upTo downTo 0) {
        val block = document.blocks.getOrNull(i)
        if (block is ReflowBlock.Heading) {
            return block.text.take(CHAPTER_TITLE_MAX_CHARS)
        }
    }
    return null
}

/** Длина текста блока в символах (для оценки времени чтения). */
private fun blockTextLength(block: ReflowBlock): Int =
    when (block) {
        is ReflowBlock.Heading -> block.text.length
        is ReflowBlock.Paragraph -> block.text.length
        is ReflowBlock.ListItem -> block.text.length
        is ReflowBlock.Blockquote -> block.text.length
        is ReflowBlock.Code -> block.text.length
        is ReflowBlock.Footnote -> block.text.length
        is ReflowBlock.Table -> block.rows.sumOf { row -> row.cells.sumOf { it.text.length } }
        is ReflowBlock.Figure -> 0
        ReflowBlock.Divider -> 0
    }

/** Реакция на тап по зоне ридера (см. [tapAction]). */
internal enum class TapAction { PREV, NEXT, TOGGLE_BAR }

/**
 * Действие тапа по горизонтальной позиции [x] при ширине [width]: лево —
 * предыдущая страница, право — следующая, центр — показать/скрыть панель (стандарт
 * e-readers). Чистая функция без Compose — проверяется юнит-тестом, как
 * [readerWheelElements]. При [tapToTurn]=`false` или невалидной ширине любой тап
 * тогглит панель (защита от случайных перелистываний).
 */
internal fun tapAction(
    x: Float,
    width: Int,
    tapToTurn: Boolean,
): TapAction {
    if (!tapToTurn || width <= 0) return TapAction.TOGGLE_BAR
    val w = width.toFloat()
    return when {
        x < w * TAP_ZONE_PREV_FRACTION -> TapAction.PREV
        x > w * (1f - TAP_ZONE_NEXT_FRACTION) -> TapAction.NEXT
        else -> TapAction.TOGGLE_BAR
    }
}

// В скролл-режиме «страница» прокручивает ~90% вьюпорта: нахлёст в одну-две строки
// сохраняет контекст на стыке (как Page Down в читалках), а не прыгает ровно на экран.
private const val PAGE_SCROLL_OVERLAP_FRACTION = 0.9f

// Доли ширины под тап-зоны перелистывания: лево — назад, право — вперёд, центр
// (остаток) — показать/скрыть панель. Центр щедрый, т.к. в нём живёт тоггл панели.
private const val TAP_ZONE_PREV_FRACTION = 0.3f
private const val TAP_ZONE_NEXT_FRACTION = 0.3f

// Доля ширины, на которую нужно увести горизонтальный свайп, чтобы листнуть страницу
// (защита от случайных микро-сдвигов).
private const val SWIPE_TURN_FRACTION = 0.18f

private const val HEADING_SCALE_1 = 1.6f
private const val HEADING_SCALE_2 = 1.35f
private const val HEADING_SCALE_3 = 1.15f
private const val HEADING_LINE_HEIGHT_MULTIPLIER = 1.25f
private const val FIGURE_PLACEHOLDER_ALPHA = 0.06f
private const val FIGURE_LABEL_ALPHA = 0.5f

/** Размер шрифта footnote относительно body-кегля. */
private const val FOOTNOTE_FONT_SCALE = 0.85f

/** Прозрачность текста сноски — обычно ниже, чем у body, для визуального отделения. */
private const val FOOTNOTE_TEXT_ALPHA = 0.75f

private const val ERGO_TICK_MS = 30_000L
private const val BREAK_INTERVAL_MS = 25 * 60 * 1000L
private const val BREAK_SHOWN_MS = 20_000L
private const val NIGHT_AFTER_MS = 20 * 60 * 1000L
private const val NIGHT_RAMP_MS = 10 * 60 * 1000L
private const val NIGHT_MAX_DIM = 0.12f
private const val LONG_READING_AFTER_MS = 45 * 60 * 1000L
private const val SUNSET_START_HOUR = 18
private const val SUNRISE_HOUR = 6
private const val SUNSET_EXTRA_WARMTH = 0.3f
private const val RHYTHM_EVERY_BLOCKS = 10
private const val BREAK_BREATH_MIN = 0.5f
private const val BREAK_BREATH_MAX = 0.9f
private const val BREAK_BREATH_MS = 1600
private const val RHYTHM_LINE_FRACTION = 0.18f
private const val RHYTHM_LINE_ALPHA = 0.25f
private const val CHAPTER_TITLE_MAX_CHARS = 28
private const val RULER_BAND_LINES = 1.7f
private const val RULER_BAND_ALPHA = 0.06f
private const val RULER_LINE_ALPHA = 0.18f
private val NIGHT_TINT = Color(0xFFFF7A1A)

// Подтверждение выделения: ручки-курсоры + панель действий.
private val SELECTION_HANDLE_RADIUS = 7.dp
private const val SELECTION_HANDLE_ALPHA = 0.95f
private val SELECTION_BAR_GAP = 8.dp
private val SELECTION_BAR_CORNER = 10.dp
private const val SELECTION_BAR_BG_ALPHA = 0.92f
private const val COPIED_TOAST_MS = 1500L

// Тап-бейдж заметки на левом поле напротив строки её анкера.
private val NOTE_MARKER_SIZE = 10.dp

internal val TABLE_BORDER_WIDTH = 1.dp
internal val TABLE_CELL_PADDING = 8.dp
private const val TABLE_BORDER_ALPHA = 0.22f
private const val TABLE_HEADER_ALPHA = 0.06f

/**
 * Минимальный «вес» колонки в [tableColumnWeights]: даже пустая/однобуквенная
 * колонка не схлопывается в нечитаемую полоску. Подобран так, чтобы узкие
 * колонки оставались кликабельными, но не отъедали ширину у содержательных.
 */
private const val TABLE_MIN_COL_WEIGHT = 3f

/**
 * Доля ширины колонки от самой длинной ячейки в ней — по числу символов её
 * [ReflowBlock.TableCell.text] (с минимумом [TABLE_MIN_COL_WEIGHT]).
 *
 * Это ЕДИНЫЙ источник пропорций колонок для таблицы: им пользуются и
 * [TableView] при рендере (`Modifier.weight(weights[col])`), и
 * [BlockHeightCalculator.measureTable] при обмере высоты (та же ширина ячейки
 * → тот же перенос строк → та же высота). Если render и measure разойдутся в
 * базисе ширин, измеренная высота неделимой таблицы поедет относительно
 * нарисованной и сломается пагинация — поэтому функция одна на оба пути.
 *
 * Вес — это число символов (а не доли, суммирующиеся в 1): Compose `weight`
 * раздаёт ширину пропорционально, а measure нормирует на сумму сам. Длина
 * считается по самой широкой строке во всех рядах для данной колонки, чтобы
 * колонка вмещала свою крупнейшую ячейку.
 *
 * @return список длиной `maxOf { row.cells.size }`; индекс — номер колонки.
 */
internal fun tableColumnWeights(table: ReflowBlock.Table): List<Float> {
    val columnCount = table.rows.maxOfOrNull { it.cells.size } ?: 0
    if (columnCount == 0) return emptyList()
    return List(columnCount) { col ->
        val maxLen =
            table.rows.maxOfOrNull { row -> row.cells.getOrNull(col)?.text?.length ?: 0 } ?: 0
        maxLen.toFloat().coerceAtLeast(TABLE_MIN_COL_WEIGHT)
    }
}

internal val BLOCKQUOTE_BAR_WIDTH = 3.dp
private const val BLOCKQUOTE_BAR_ALPHA = 0.3f
private const val DIVIDER_LINE_FRACTION = 0.2f
private const val DIVIDER_LINE_ALPHA = 0.25f
