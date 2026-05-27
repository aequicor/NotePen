package ru.kyamshanov.notepen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.blur.GlassSurface
import ru.kyamshanov.notepen.book.TocEntry

/**
 * Вертикальный сайдбар оглавления документа: список разделов, отсортированный по
 * порядку чтения. Каждый пункт сдвинут вправо пропорционально [TocEntry.level]
 * (1 — верхний уровень, без отступа), по тапу вызывает [onEntryClick] с
 * 0-based индексом страницы PDF, на которую нужно перейти.
 *
 * Структурно повторяет [PageThumbnailsSidebar]: тот же [Surface] со скруглением
 * правого края и [LazyColumn] внутри. Для документов без оглавления
 * (обычные PDF, комиксы) показывает заглушку.
 *
 * @param entries записи оглавления в порядке чтения; пусто — показывается заглушка
 * @param onEntryClick вызывается с [TocEntry.pageIndex] выбранного раздела
 */
@Composable
fun TocSidebar(
    entries: List<TocEntry>,
    onEntryClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    GlassSurface(
        modifier =
            modifier
                .fillMaxHeight()
                .width(TOC_SIDEBAR_WIDTH),
        tint = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topEnd = TOC_SIDEBAR_CORNER, bottomEnd = TOC_SIDEBAR_CORNER),
    ) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
            Text(
                text = "Содержание",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
            if (entries.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 24.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = "Оглавление недоступно",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(items = entries) { entry ->
                        TocEntryRow(entry = entry, onClick = { onEntryClick(entry.pageIndex) })
                    }
                }
            }
        }
    }
}

/** Одна строка оглавления: отступ по уровню, заголовок и номер страницы. */
@Composable
private fun TocEntryRow(
    entry: TocEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val indent = (TOC_INDENT_PER_LEVEL * (entry.level - 1).coerceAtLeast(0))
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 12.dp + indent, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.title,
            style =
                if (entry.level <= 1) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodySmall
                },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${entry.pageIndex + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TOC_PAGE_NUMBER_ALPHA),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

internal val TOC_SIDEBAR_WIDTH = 240.dp
private val TOC_SIDEBAR_CORNER = 18.dp
private val TOC_INDENT_PER_LEVEL = 14.dp
private const val TOC_PAGE_NUMBER_ALPHA = 0.7f
