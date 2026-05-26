package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.reflow.api.BuiltinReaderPresets
import ru.kyamshanov.notepen.reflow.api.PageTransition
import ru.kyamshanov.notepen.reflow.api.ProgressFormat
import ru.kyamshanov.notepen.reflow.api.ReaderAlign
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.SourceSpan
import ru.kyamshanov.notepen.reflow.api.StoredReaderSettings
import ru.kyamshanov.notepen.reflow.api.TextAnchor

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
 *   страничном режиме меняет индекс текущей страницы (её показывает [AnimatedContent]);
 *   в скролл-режиме — прокрутка на дельту экранов ([listState]). Нужен, чтобы
 *   аппаратные клавиши (стрелки/громкость/Space), приходящие в общий key-sink выше по
 *   дереву, листали ридер. Тап-зоны по краям и горизонтальный свайп вызывают тот же
 *   обработчик локально.
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
    listState: LazyListState = rememberLazyListState(),
    renderPage: (suspend (pageIndex: Int) -> ImageBitmap?)? = null,
    onPageDeltaReady: (((Int) -> Unit)?) -> Unit = {},
) {
    val settings = remember(stored.current) { stored.current.toRenderSettings() }
    val anchorsByBlock = remember(highlights) { highlights.groupBy { it.blockIndex } }

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

    // Высота вьюпорта ридера в px — шаг «листнуть страницу» в скролл-режиме (paged
    // меряет страницу сам). Меряем корневой Box, а не внутренний LazyColumn, чтобы
    // дельта не зависела от contentPadding/резерва под airbar.
    var viewportHeightPx by remember { mutableStateOf(0) }

    // Активный обработчик «листнуть на ±N»: им пользуется и горизонтальный свайп
    // (локально), и аппаратные клавиши (через [onPageDeltaReady] выше по дереву).
    // Заполняется активным режимом (paged — пейджером, scroll — прокруткой) и
    // обнуляется, пока контент не готов либо при выходе из композиции.
    var pageDelta by remember(document) { mutableStateOf<((Int) -> Unit)?>(null) }
    val latestOnPageDeltaReady by rememberUpdatedState(onPageDeltaReady)
    val setPageDelta: (((Int) -> Unit)?) -> Unit = { handler ->
        pageDelta = handler
        latestOnPageDeltaReady(handler)
    }
    DisposableEffect(Unit) { onDispose { latestOnPageDeltaReady(null) } }

    // Скролл-режим: «страница» = дельта экранов (с лёгким нахлёстом, чтобы строка
    // на стыке не терялась). animateScrollBy сам клампится на краях списка.
    if (!settings.paged) {
        val scrollPageDelta: (Int) -> Unit =
            remember(listState, scope) {
                { delta ->
                    val step = viewportHeightPx * PAGE_SCROLL_OVERLAP_FRACTION
                    if (step > 0f) scope.launch { listState.animateScrollBy(delta * step) }
                }
            }
        DisposableEffect(scrollPageDelta) {
            setPageDelta(scrollPageDelta)
            onDispose { setPageDelta(null) }
        }
    }

    // Свайп влево → следующая страница (+1), вправо → предыдущая (-1) — как
    // перелистывание печатной книги. Отдельный pointerInput: detectHorizontalDrag
    // забирает указатель только после горизонтального slop, поэтому вертикальный
    // скролл пейджера/списка и тап-по-тексту (соседний pointerInput) не глотаются.
    val latestPageDelta by rememberUpdatedState(pageDelta)
    val swipeThresholdPx = with(LocalDensity.current) { SWIPE_DISTANCE_THRESHOLD_DP.toPx() }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(effectiveBackground)
                .onSizeChanged { viewportHeightPx = it.height }
                .pointerInput(barVisible, settings.tapToTurn) {
                    // Тап-зоны: лево — назад, право — вперёд, центр — показать/скрыть
                    // панель (стандарт e-readers). При tapToTurn=false тап везде лишь
                    // тогглит панель — защита от случайных перелистываний.
                    detectTapGestures { offset ->
                        when (tapAction(offset.x, size.width, settings.tapToTurn)) {
                            TapAction.PREV -> latestPageDelta?.invoke(-1)
                            TapAction.NEXT -> latestPageDelta?.invoke(1)
                            TapAction.TOGGLE_BAR -> onBarVisibleChange(!barVisible)
                        }
                    }
                }.pointerInput(swipeThresholdPx) {
                    var dragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragX = 0f },
                        onDragEnd = {
                            if (dragX <= -swipeThresholdPx) {
                                latestPageDelta?.invoke(1)
                            } else if (dragX >= swipeThresholdPx) {
                                latestPageDelta?.invoke(-1)
                            }
                            dragX = 0f
                        },
                        onDragCancel = { dragX = 0f },
                    ) { change, dragAmount ->
                        dragX += dragAmount
                        change.consume()
                    }
                },
    ) {
        if (settings.paged) {
            PagedReflowContent(
                document = document,
                anchorsByBlock = anchorsByBlock,
                settings = settings,
                renderPage = renderPage,
                onVisibleBlockChange = { pagedFirstBlock = it },
                onPageDeltaReady = setPageDelta,
            )
        } else {
            ScrollReflowContent(
                document = document,
                anchorsByBlock = anchorsByBlock,
                settings = settings,
                listState = listState,
                renderPage = renderPage,
            )
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
            ReaderAirbar(
                stored = stored,
                onStoredChange = onStoredChange,
                newPresetIdProvider = newPresetIdProvider,
                background = effectiveBackground,
                textColor = settings.textColor,
                progressLabel = progressLabel,
                autoHideMs = settings.autoHideMs,
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

/** Скролл-режим: одна прокручиваемая колонка с ритм-паузами. */
@Composable
private fun BoxScope.ScrollReflowContent(
    document: ReflowDocument,
    anchorsByBlock: Map<Int, List<TextAnchor>>,
    settings: ReflowReaderSettings,
    listState: LazyListState,
    renderPage: (suspend (pageIndex: Int) -> ImageBitmap?)?,
) {
    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = settings.maxContentWidth)
                .fillMaxWidth(),
        contentPadding =
            PaddingValues(
                start = settings.contentPadding,
                top = settings.contentPadding,
                end = settings.contentPadding,
                bottom = settings.contentPadding + READER_BAR_RESERVE,
            ),
        verticalArrangement = Arrangement.spacedBy(settings.blockSpacing),
    ) {
        itemsIndexed(document.blocks) { index, block ->
            Column {
                ReflowBlockView(block, anchorsByBlock[index].orEmpty(), settings, renderPage)
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
 * Страничный режим: измеряет высоты блоков при текущей ширине колонки, раскладывает
 * их по страницам ([ReaderPagination]) и показывает текущую через [AnimatedContent]
 * с переходом из [ReflowReaderSettings.pageTransition] (горизонтальный слайд /
 * затухание / без анимации; системное «уменьшить движение» форсит мгновенный). Пока
 * высоты не измерены — невидимый проход рендерит блоки и снимает их высоты.
 */
@Composable
private fun PagedReflowContent(
    document: ReflowDocument,
    anchorsByBlock: Map<Int, List<TextAnchor>>,
    settings: ReflowReaderSettings,
    renderPage: (suspend (pageIndex: Int) -> ImageBitmap?)?,
    onVisibleBlockChange: (Int) -> Unit,
    onPageDeltaReady: (((Int) -> Unit)?) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // Полезная высота страницы = вьюпорт минус резерв под верхний счётчик страниц
        // и нижний airbar настроек (оба всплывают по тапу). Резервируем всегда, чтобы
        // пагинация не «прыгала» при показе/скрытии хрома и текст на него не наезжал.
        val pageHeightPx =
            with(density) {
                (maxHeight - READER_TOP_RESERVE - READER_BAR_RESERVE).toPx().coerceAtLeast(1f)
            }
        val spacingPx = with(density) { settings.blockSpacing.toPx() }
        val heights = remember(document) { mutableStateMapOf<Int, Int>() }
        val measured = document.blocks.isEmpty() || heights.size >= document.blocks.size

        if (!measured) {
            Box(Modifier.fillMaxSize().clipToBounds().alpha(0f)) {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = settings.maxContentWidth)
                            .fillMaxWidth()
                            .padding(horizontal = settings.contentPadding),
                ) {
                    document.blocks.forEachIndexed { index, block ->
                        Box(Modifier.fillMaxWidth().onSizeChanged { heights[index] = it.height }) {
                            ReflowBlockView(block, anchorsByBlock[index].orEmpty(), settings, renderPage)
                        }
                    }
                }
            }
            return@BoxWithConstraints
        }

        val pages =
            remember(document, pageHeightPx, spacingPx, heights.size) {
                ReaderPagination.paginate(
                    blockHeightsPx = document.blocks.indices.map { heights[it]?.toFloat() ?: 0f },
                    pageHeightPx = pageHeightPx,
                    spacingPx = spacingPx,
                )
            }
        if (pages.isEmpty()) return@BoxWithConstraints

        val lastPage = pages.lastIndex
        // Индекс текущей страницы — единый источник для свайпа, тап-зон и аппаратных
        // клавиш. Сбрасывается на 0 при смене документа; при ре-пагинации (смена
        // кегля/полей) клампится, чтобы не указывать за конец списка страниц.
        var currentPage by remember(document) { mutableStateOf(0) }
        LaunchedEffect(lastPage) {
            if (currentPage > lastPage) currentPage = lastPage
        }
        LaunchedEffect(currentPage, pages) {
            onVisibleBlockChange(pages.getOrNull(currentPage)?.firstOrNull() ?: 0)
        }

        // «Листнуть на ±N» = смена currentPage в границах списка страниц. Публикуем
        // наружу, пока контент жив (им листают свайп/тап-зоны/клавиши выше по дереву).
        val pageDeltaHandler: (Int) -> Unit =
            remember(lastPage) { { delta -> currentPage = (currentPage + delta).coerceIn(0, lastPage) } }
        DisposableEffect(pageDeltaHandler) {
            onPageDeltaReady(pageDeltaHandler)
            onDispose { onPageDeltaReady(null) }
        }

        // Системное «уменьшить движение» форсит мгновенный переход, не меняя настройку.
        val transition =
            if (isReducedMotionEnabled()) PageTransition.NONE else settings.pageTransition
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = { pageTransitionSpec(transition, initialState, targetState) },
            modifier = Modifier.fillMaxSize(),
            label = "reader-page",
        ) { pageIndex ->
            // Box занимает область между верхним счётчиком и нижним airbar (padding) и
            // клипает содержимое — текст не наезжает на хром, даже если блок выше
            // страницы. fillMaxSize+padding даёт колонке СВОБОДНЫЕ ограничения (min=0),
            // поэтому widthIn реально сужает ширину до maxContentWidth (AnimatedContent
            // сам отдаёт контенту fixed-fill, и widthIn на колонке напрямую не работал —
            // текст тянулся на весь экран). Колонка центрирована — комфортная длина
            // строки, как в скролл-режиме.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = READER_TOP_RESERVE, bottom = READER_BAR_RESERVE)
                        .clipToBounds(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier =
                        Modifier
                            .widthIn(max = settings.maxContentWidth)
                            .fillMaxWidth()
                            .padding(horizontal = settings.contentPadding),
                    verticalArrangement = Arrangement.spacedBy(settings.blockSpacing),
                ) {
                    pages.getOrNull(pageIndex)?.forEach { blockIndex ->
                        ReflowBlockView(
                            document.blocks[blockIndex],
                            anchorsByBlock[blockIndex].orEmpty(),
                            settings,
                            renderPage,
                        )
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
) {
    when (block) {
        is ReflowBlock.Heading ->
            BasicText(
                text = styledText(block.text, block.source, anchors, settings),
                style = settings.headingStyle(block.level),
            )

        is ReflowBlock.Paragraph ->
            BasicText(
                text = styledText(block.text, block.source, anchors, settings),
                style = settings.paragraphStyle(),
            )

        is ReflowBlock.ListItem ->
            BasicText(
                text = styledText(block.text, block.source, anchors, settings),
                style = settings.paragraphStyle(),
                modifier = Modifier.padding(start = settings.contentPadding),
            )

        is ReflowBlock.Blockquote -> BlockquoteView(block, anchors, settings)

        is ReflowBlock.Table -> TableView(block, settings)

        is ReflowBlock.Figure -> FigureView(block, settings, renderPage)

        ReflowBlock.Divider -> DividerView(settings)
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
) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier =
                Modifier
                    .width(BLOCKQUOTE_BAR_WIDTH)
                    .fillMaxHeight()
                    .background(settings.textColor.copy(alpha = BLOCKQUOTE_BAR_ALPHA)),
        )
        BasicText(
            text = styledText(block.text, block.source, anchors, settings),
            style = settings.paragraphStyle().copy(fontStyle = FontStyle.Italic),
            modifier = Modifier.padding(start = settings.contentPadding),
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
 * Рендерит [ReflowBlock.Table] сеткой: каждая строка — [Row] ячеек равной
 * ширины, ячейки с тонкой рамкой и общей высотой строки ([IntrinsicSize.Min]).
 */
@Composable
private fun TableView(
    table: ReflowBlock.Table,
    settings: ReflowReaderSettings,
) {
    val borderColor = settings.textColor.copy(alpha = TABLE_BORDER_ALPHA)
    Column(modifier = Modifier.fillMaxWidth()) {
        table.rows.forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.cells.forEach { cell ->
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(TABLE_BORDER_WIDTH, borderColor)
                                .background(
                                    if (rowIndex == 0) settings.textColor.copy(alpha = TABLE_HEADER_ALPHA) else Color.Transparent,
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
    if (renderPage == null) {
        FigurePlaceholder(settings)
        return
    }
    val pageBitmap by androidx.compose.runtime.produceState<ImageBitmap?>(initialValue = null, figure.pageIndex) {
        value = renderPage(figure.pageIndex)
    }
    val bitmap = pageBitmap
    if (bitmap == null) {
        FigurePlaceholder(settings)
        return
    }
    val bounds = figure.bounds
    val srcLeft = (bounds.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
    val srcTop = (bounds.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
    val srcWidth = (bounds.width * bitmap.width).toInt().coerceIn(1, bitmap.width - srcLeft)
    val srcHeight = (bounds.height * bitmap.height).toInt().coerceIn(1, bitmap.height - srcTop)
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(srcWidth.toFloat() / srcHeight.toFloat())) {
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
private fun FigurePlaceholder(settings: ReflowReaderSettings) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(settings.textColor.copy(alpha = FIGURE_PLACEHOLDER_ALPHA)),
    ) {
        BasicText(
            text = "[ изображение ]",
            modifier = Modifier.padding(settings.contentPadding),
            style = settings.paragraphStyle().copy(color = settings.textColor.copy(alpha = FIGURE_LABEL_ALPHA)),
        )
    }
}

private fun ReflowReaderSettings.paragraphStyle(): TextStyle =
    TextStyle(
        color = textColor,
        fontFamily = resolveReaderFontFamily(fontFamily),
        fontSize = fontSize,
        lineHeight = (fontSize.value * lineHeightMultiplier).sp,
        letterSpacing = letterSpacing,
        textAlign = if (align == ReaderAlign.JUSTIFY) TextAlign.Justify else TextAlign.Start,
        hyphens = if (hyphenation) Hyphens.Auto else Hyphens.None,
    )

private fun ReflowReaderSettings.headingStyle(level: Int): TextStyle {
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
    )
}

/**
 * Собирает оформленный текст блока: полужирные/моноширинные фрагменты по провенансу
 * [source], межсловный трекинг, bionic-выделение начал слов и фон-подсветку
 * [anchors]. Подсветка накладывается последней, поэтому перекрывает фон inline-кода.
 */
private fun styledText(
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

/**
 * Спецификация перехода [AnimatedContent] между страницами под выбранный [transition]:
 * SLIDE — книжный горизонтальный слайд (вперёд, [toPage] >= [fromPage], уезжает влево)
 * с лёгким затуханием; FADE — только перекрёстное затухание; NONE — мгновенно.
 */
private fun pageTransitionSpec(
    transition: PageTransition,
    fromPage: Int,
    toPage: Int,
): ContentTransform =
    when (transition) {
        PageTransition.NONE -> EnterTransition.None togetherWith ExitTransition.None
        PageTransition.FADE ->
            fadeIn(tween(PAGE_ANIM_MS)) togetherWith fadeOut(tween(PAGE_ANIM_MS))
        PageTransition.SLIDE -> {
            val dir = if (toPage >= fromPage) 1 else -1
            (slideInHorizontally(tween(PAGE_ANIM_MS)) { w -> dir * w } + fadeIn(tween(PAGE_ANIM_MS))) togetherWith
                (slideOutHorizontally(tween(PAGE_ANIM_MS)) { w -> -dir * w } + fadeOut(tween(PAGE_ANIM_MS)))
        }
    }

// Свайп засчитывается как перелистывание после ~64dp горизонтального смещения —
// заметно больше touch-slop, чтобы случайные диагональные движения при скролле не
// листали страницу, но без необходимости «бросать» через весь экран.
private val SWIPE_DISTANCE_THRESHOLD_DP = 64.dp

// В скролл-режиме «страница» прокручивает ~90% вьюпорта: нахлёст в одну-две строки
// сохраняет контекст на стыке (как Page Down в читалках), а не прыгает ровно на экран.
private const val PAGE_SCROLL_OVERLAP_FRACTION = 0.9f

// Доли ширины под тап-зоны перелистывания: лево — назад, право — вперёд, центр
// (остаток) — показать/скрыть панель. Центр щедрый, т.к. в нём живёт тоггл панели.
private const val TAP_ZONE_PREV_FRACTION = 0.3f
private const val TAP_ZONE_NEXT_FRACTION = 0.3f

// Длительность анимации перехода между страницами (slide/fade), мс.
private const val PAGE_ANIM_MS = 280

private const val HEADING_SCALE_1 = 1.6f
private const val HEADING_SCALE_2 = 1.35f
private const val HEADING_SCALE_3 = 1.15f
private const val HEADING_LINE_HEIGHT_MULTIPLIER = 1.25f
private const val FIGURE_PLACEHOLDER_ALPHA = 0.06f
private const val FIGURE_LABEL_ALPHA = 0.5f

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

// Вертикальные резервы страничного режима под всплывающий хром: снизу — airbar
// настроек, сверху — счётчик страниц (на Android ещё и под статус-бар). Сознательно
// щедрые, как поля книги; точная высота хрома платформозависима, поэтому берём с запасом.
private val READER_BAR_RESERVE = 96.dp
private val READER_TOP_RESERVE = 88.dp

private val TABLE_BORDER_WIDTH = 1.dp
private val TABLE_CELL_PADDING = 8.dp
private const val TABLE_BORDER_ALPHA = 0.22f
private const val TABLE_HEADER_ALPHA = 0.06f

private val BLOCKQUOTE_BAR_WIDTH = 3.dp
private const val BLOCKQUOTE_BAR_ALPHA = 0.3f
private const val DIVIDER_LINE_FRACTION = 0.2f
private const val DIVIDER_LINE_ALPHA = 0.25f
