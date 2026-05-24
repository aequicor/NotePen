package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.SourceSpan
import ru.kyamshanov.notepen.reflow.api.TextAnchor

/**
 * Reflow-ридер: рендерит [ReflowDocument] в одну прокручиваемую колонку
 * ограниченной ширины с типографикой [settings].
 *
 * Выделения [highlights] подсвечиваются прямо в потоке текста (фон через
 * [SpanStyle]) — поэтому они «текут» вместе с переверстанным текстом, в отличие
 * от привязанных к координатам страницы штрихов. Обычно [highlights] получают
 * из `StrokeTextMapper.anchorsFor` по рукописным аннотациям документа.
 *
 * Картинки ([ReflowBlock.Figure]) рендерятся как кроп исходной страницы, если
 * передан [renderPage]; иначе — плейсхолдер.
 *
 * @param document документ для отображения
 * @param modifier модификатор корневого контейнера
 * @param highlights диапазоны-выделения по блокам
 * @param settings типографика и палитра
 * @param listState состояние прокрутки списка блоков (для перехода к нужному блоку)
 * @param renderPage растеризатор страницы в [ImageBitmap] по нулевому индексу
 *   (для отрисовки врезок-картинок); `null` — показывать плейсхолдер
 */
@Composable
public fun ReflowReader(
    document: ReflowDocument,
    modifier: Modifier = Modifier,
    highlights: List<TextAnchor> = emptyList(),
    settings: ReflowReaderSettings = ReflowReaderSettings(),
    listState: LazyListState = rememberLazyListState(),
    renderPage: (suspend (pageIndex: Int) -> ImageBitmap?)? = null,
) {
    val anchorsByBlock = remember(highlights) { highlights.groupBy { it.blockIndex } }

    // Выключка по ширине — переключаемая в нижней панели ридера настройка;
    // стартует из [settings], дальше живёт как состояние ридера (без сброса при
    // смене документа, чтобы выбор пользователя сохранялся).
    var justify by remember { mutableStateOf(settings.justify) }

    // Сессия чтения: тиканье времени → напоминание 20-20-20 и адаптивное тёплое
    // затемнение на долгой сессии. Сбрасывается при смене документа.
    var elapsedMs by remember(document) { mutableStateOf(0L) }
    var breakPrompt by remember(document) { mutableStateOf(false) }
    if (settings.ergonomicsEnabled) {
        LaunchedEffect(document) {
            while (true) {
                delay(ERGO_TICK_MS)
                elapsedMs += ERGO_TICK_MS
            }
        }
        LaunchedEffect(document) {
            while (true) {
                delay(BREAK_INTERVAL_MS)
                breakPrompt = true
                delay(BREAK_SHOWN_MS)
                breakPrompt = false
            }
        }
    }
    val nightDim = if (settings.ergonomicsEnabled) {
        ReadingErgonomics.dimAlpha(elapsedMs, NIGHT_AFTER_MS, NIGHT_RAMP_MS, NIGHT_MAX_DIM)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(settings.background)
            .drawWithContent {
                drawContent()
                if (nightDim > 0f) drawRect(color = NIGHT_TINT.copy(alpha = nightDim))
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = settings.maxContentWidth)
                .fillMaxWidth(),
            // нижний отступ — чтобы последний блок не прятался под панель настроек
            contentPadding = PaddingValues(
                start = settings.contentPadding,
                top = settings.contentPadding,
                end = settings.contentPadding,
                bottom = settings.contentPadding + READER_BAR_HEIGHT,
            ),
            verticalArrangement = Arrangement.spacedBy(settings.blockSpacing),
        ) {
            itemsIndexed(document.blocks) { index, block ->
                Column {
                    ReflowBlockView(block, anchorsByBlock[index].orEmpty(), settings, justify, renderPage)
                    if (settings.ergonomicsEnabled &&
                        index < document.blocks.lastIndex &&
                        ReadingErgonomics.isRhythmBreak(index, RHYTHM_EVERY_BLOCKS)
                    ) {
                        RhythmPause(settings)
                    }
                }
            }
        }
        ReaderSettingsBar(
            justify = justify,
            onToggleJustify = { justify = !justify },
            settings = settings,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        if (breakPrompt) {
            BreakReminderBar(settings, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ReflowBlockView(
    block: ReflowBlock,
    anchors: List<TextAnchor>,
    settings: ReflowReaderSettings,
    justify: Boolean,
    renderPage: (suspend (pageIndex: Int) -> ImageBitmap?)?,
) {
    when (block) {
        is ReflowBlock.Heading -> BasicText(
            text = styledText(block.text, block.source, anchors, settings),
            style = settings.headingStyle(block.level),
        )

        is ReflowBlock.Paragraph -> BasicText(
            text = styledText(block.text, block.source, anchors, settings),
            style = settings.paragraphStyle(justify),
        )

        is ReflowBlock.ListItem -> BasicText(
            text = styledText(block.text, block.source, anchors, settings),
            style = settings.paragraphStyle(justify),
            modifier = Modifier.padding(start = settings.contentPadding),
        )

        is ReflowBlock.Table -> TableView(block, settings)

        is ReflowBlock.Figure -> FigureView(block, settings, renderPage)
    }
}

/**
 * Рендерит [ReflowBlock.Table] сеткой: каждая строка — [Row] ячеек равной
 * ширины, ячейки с тонкой рамкой и общей высотой строки ([IntrinsicSize.Min]).
 * Полужирность заголовка приходит из провенанса ячеек (см. [styledText]).
 */
@Composable
private fun TableView(table: ReflowBlock.Table, settings: ReflowReaderSettings) {
    val borderColor = settings.textColor.copy(alpha = TABLE_BORDER_ALPHA)
    Column(modifier = Modifier.fillMaxWidth()) {
        table.rows.forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.cells.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(TABLE_BORDER_WIDTH, borderColor)
                            .background(
                                if (rowIndex == 0) settings.textColor.copy(alpha = TABLE_HEADER_ALPHA) else Color.Transparent,
                            )
                            .padding(TABLE_CELL_PADDING),
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
 * через [renderPage] (только когда блок попал во вьюпорт LazyColumn) и рисует её
 * подобласть по [ReflowBlock.Figure.bounds]. Пока грузится / без [renderPage] —
 * плейсхолдер.
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
    val pageBitmap by produceState<ImageBitmap?>(initialValue = null, figure.pageIndex) {
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

/** Тонкая «дышащая» полоска снизу: напоминание по правилу 20-20-20 (не модалка). */
@Composable
private fun BreakReminderBar(settings: ReflowReaderSettings, modifier: Modifier = Modifier) {
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

/**
 * Нижняя панель ридера: набор переключателей оформления, не мешающий чтению
 * (полупрозрачная «бумага»). Пока несёт выключку по ширине; сюда же лягут
 * будущие настройки (кегль, тема и т. п.).
 */
@Composable
private fun ReaderSettingsBar(
    justify: Boolean,
    onToggleJustify: () -> Unit,
    settings: ReflowReaderSettings,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(READER_BAR_HEIGHT)
            .background(settings.background.copy(alpha = READER_BAR_ALPHA))
            .padding(horizontal = settings.contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        ReaderToggleChip(
            label = "По ширине",
            checked = justify,
            onClick = onToggleJustify,
            settings = settings,
        )
    }
}

/** Компактный чип-переключатель: ярче и полужирнее во включённом состоянии. */
@Composable
private fun ReaderToggleChip(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    settings: ReflowReaderSettings,
) {
    val backgroundAlpha = if (checked) READER_CHIP_ON_ALPHA else READER_CHIP_OFF_ALPHA
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(READER_CHIP_RADIUS))
            .background(settings.textColor.copy(alpha = backgroundAlpha))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = settings.textColor.copy(alpha = if (checked) 1f else READER_CHIP_OFF_TEXT_ALPHA),
                fontSize = 14.sp,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            ),
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
            modifier = Modifier
                .fillMaxWidth(RHYTHM_LINE_FRACTION)
                .height(1.dp)
                .background(settings.textColor.copy(alpha = RHYTHM_LINE_ALPHA)),
        )
    }
}

@Composable
private fun FigurePlaceholder(settings: ReflowReaderSettings) {
    Box(
        modifier = Modifier
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

private fun ReflowReaderSettings.paragraphStyle(justify: Boolean = false): TextStyle =
    TextStyle(
        color = textColor,
        fontSize = fontSize,
        lineHeight = (fontSize.value * lineHeightMultiplier).sp,
        textAlign = if (justify) TextAlign.Justify else TextAlign.Unspecified,
    )

private fun ReflowReaderSettings.headingStyle(level: Int): TextStyle {
    val scale = when (level) {
        1 -> HEADING_SCALE_1
        2 -> HEADING_SCALE_2
        else -> HEADING_SCALE_3
    }
    val size = fontSize.value * scale
    return TextStyle(
        color = textColor,
        fontSize = size.sp,
        lineHeight = (size * HEADING_LINE_HEIGHT_MULTIPLIER).sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * Собирает оформленный текст блока: полужирные/моноширинные фрагменты по
 * провенансу [source] и фон-подсветку по диапазонам [anchors]. Подсветка
 * накладывается последней, поэтому перекрывает фон inline-кода.
 */
private fun styledText(
    text: String,
    source: List<SourceSpan>,
    anchors: List<TextAnchor>,
    settings: ReflowReaderSettings,
): AnnotatedString {
    if (source.isEmpty() && anchors.isEmpty()) return AnnotatedString(text)
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
        anchors.forEach { anchor ->
            val start = anchor.charStart.coerceIn(0, text.length)
            val end = anchor.charEnd.coerceIn(start, text.length)
            if (start < end) addStyle(SpanStyle(background = settings.highlightColor), start, end)
        }
    }
}

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
private const val RHYTHM_EVERY_BLOCKS = 10
private const val BREAK_BREATH_MIN = 0.5f
private const val BREAK_BREATH_MAX = 0.9f
private const val BREAK_BREATH_MS = 1600
private const val RHYTHM_LINE_FRACTION = 0.18f
private const val RHYTHM_LINE_ALPHA = 0.25f
private val NIGHT_TINT = Color(0xFFFF7A1A)

private val READER_BAR_HEIGHT = 52.dp
private const val READER_BAR_ALPHA = 0.92f
private val READER_CHIP_RADIUS = 18.dp
private const val READER_CHIP_ON_ALPHA = 0.16f
private const val READER_CHIP_OFF_ALPHA = 0.06f
private const val READER_CHIP_OFF_TEXT_ALPHA = 0.6f

private val TABLE_BORDER_WIDTH = 1.dp
private val TABLE_CELL_PADDING = 8.dp
private const val TABLE_BORDER_ALPHA = 0.22f
private const val TABLE_HEADER_ALPHA = 0.06f
