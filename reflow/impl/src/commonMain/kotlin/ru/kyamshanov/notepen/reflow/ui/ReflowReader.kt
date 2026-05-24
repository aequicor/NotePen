package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
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
 * Картинки ([ReflowBlock.Figure]) пока показываются плейсхолдером — извлечение
 * их пикселей в reflow ещё не реализовано.
 *
 * @param document документ для отображения
 * @param modifier модификатор корневого контейнера
 * @param highlights диапазоны-выделения по блокам
 * @param settings типографика и палитра
 * @param listState состояние прокрутки списка блоков (для перехода к нужному блоку)
 */
@Composable
public fun ReflowReader(
    document: ReflowDocument,
    modifier: Modifier = Modifier,
    highlights: List<TextAnchor> = emptyList(),
    settings: ReflowReaderSettings = ReflowReaderSettings(),
    listState: LazyListState = rememberLazyListState(),
) {
    val anchorsByBlock = remember(highlights) { highlights.groupBy { it.blockIndex } }
    Box(modifier = modifier.fillMaxSize().background(settings.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = settings.maxContentWidth)
                .fillMaxWidth(),
            contentPadding = PaddingValues(settings.contentPadding),
            verticalArrangement = Arrangement.spacedBy(settings.blockSpacing),
        ) {
            itemsIndexed(document.blocks) { index, block ->
                ReflowBlockView(block, anchorsByBlock[index].orEmpty(), settings)
            }
        }
    }
}

@Composable
private fun ReflowBlockView(block: ReflowBlock, anchors: List<TextAnchor>, settings: ReflowReaderSettings) {
    when (block) {
        is ReflowBlock.Heading -> BasicText(
            text = block.text.withHighlights(anchors, settings.highlightColor),
            style = settings.headingStyle(block.level),
        )

        is ReflowBlock.Paragraph -> BasicText(
            text = block.text.withHighlights(anchors, settings.highlightColor),
            style = settings.paragraphStyle(),
        )

        is ReflowBlock.Figure -> FigurePlaceholder(settings)
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

private fun ReflowReaderSettings.paragraphStyle(): TextStyle =
    TextStyle(
        color = textColor,
        fontSize = fontSize,
        lineHeight = (fontSize.value * lineHeightMultiplier).sp,
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

/** Накладывает фон-подсветку на диапазоны [anchors] внутри текста блока. */
private fun String.withHighlights(anchors: List<TextAnchor>, color: Color): AnnotatedString {
    if (anchors.isEmpty()) return AnnotatedString(this)
    val text = this
    return buildAnnotatedString {
        append(text)
        anchors.forEach { anchor ->
            val start = anchor.charStart.coerceIn(0, text.length)
            val end = anchor.charEnd.coerceIn(start, text.length)
            if (start < end) addStyle(SpanStyle(background = color), start, end)
        }
    }
}

private const val HEADING_SCALE_1 = 1.6f
private const val HEADING_SCALE_2 = 1.35f
private const val HEADING_SCALE_3 = 1.15f
private const val HEADING_LINE_HEIGHT_MULTIPLIER = 1.25f
private const val FIGURE_PLACEHOLDER_ALPHA = 0.06f
private const val FIGURE_LABEL_ALPHA = 0.5f
