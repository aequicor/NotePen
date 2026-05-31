package ru.kyamshanov.notepen.mainscreen.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.LIQUID_GLASS_TOP_BAR_HEIGHT
import ru.kyamshanov.notepen.LiquidGlassCard
import ru.kyamshanov.notepen.LiquidGlassTopBar
import ru.kyamshanov.notepen.blur.GlassBackdropProvider
import ru.kyamshanov.notepen.blur.glassSource
import ru.kyamshanov.notepen.liquidGlassHero
import ru.kyamshanov.notepen.mainscreen.ui.model.LibraryContentItemUiModel
import ru.kyamshanov.notepen.titlebar.LocalTitleBarInteraction

private val BOOK_CARD_HEIGHT = 132.dp
private val BOOK_ICON_BOX = 36.dp

/** Sub-экран общей папки «Библиотека»: сетка книг + back. */
@Composable
fun LibraryFolderContent(
    component: LibraryFolderContentsComponentImpl,
    modifier: Modifier = Modifier,
) {
    val state by component.viewModel.state.collectAsState()
    val titleBarInteraction = LocalTitleBarInteraction.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarTotal = statusBarTop + LIQUID_GLASS_TOP_BAR_HEIGHT
    GlassBackdropProvider {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .liquidGlassHero()
                        .glassSource(),
            )
            LibraryFolderBody(
                state = state,
                topBarTotal = topBarTotal,
                onOpenItem = { component.viewModel.openItem(it) },
            )
            LiquidGlassTopBar(
                modifier = Modifier.align(Alignment.TopCenter),
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(
                        onClick = component.onBack,
                        modifier = titleBarInteraction?.interactive(Modifier) ?: Modifier,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        }
    }
}

@Composable
private fun LibraryFolderBody(
    state: LibraryFolderContentsUiState,
    topBarTotal: androidx.compose.ui.unit.Dp,
    onOpenItem: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading ->
                Text(
                    text = "Загрузка…",
                    modifier = Modifier.align(Alignment.Center).padding(top = topBarTotal),
                    color = MaterialTheme.colorScheme.outline,
                )
            state.items.isEmpty() ->
                EmptyLibraryHint(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp)
                            .padding(top = topBarTotal),
                )
            else -> LibraryGrid(items = state.items, topBarTotal = topBarTotal, onOpenItem = onOpenItem)
        }
    }
}

@Composable
private fun LibraryGrid(
    items: List<LibraryContentItemUiModel>,
    topBarTotal: androidx.compose.ui.unit.Dp,
    onOpenItem: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding =
            PaddingValues(start = 16.dp, end = 16.dp, top = topBarTotal + 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.id }) { item ->
            LibraryBookCard(model = item, onClick = { onOpenItem(item.id) })
        }
    }
}

@Composable
private fun EmptyLibraryHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Перетащите сюда книги — они станут доступны на других устройствах",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LibraryBookCard(
    model: LibraryContentItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassCard(
        modifier = modifier.fillMaxWidth().height(BOOK_CARD_HEIGHT),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(BOOK_ICON_BOX)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
        }
    }
}
