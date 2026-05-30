package ru.kyamshanov.notepen.mainscreen.ui.peer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.LIQUID_GLASS_TOP_BAR_HEIGHT
import ru.kyamshanov.notepen.LiquidGlassTopBar
import ru.kyamshanov.notepen.blur.GlassBackdropProvider
import ru.kyamshanov.notepen.blur.glassSource
import ru.kyamshanov.notepen.currentWindowSizePx
import ru.kyamshanov.notepen.liquidGlassHero
import ru.kyamshanov.notepen.mainscreen.ui.component.RemoteEntryCard
import ru.kyamshanov.notepen.titlebar.LocalTitleBarInteraction
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

private val WIDE_SCREEN_THRESHOLD: Dp = 600.dp

@Composable
fun PeerCatalogContent(
    component: PeerCatalogComponentImpl,
    modifier: Modifier = Modifier,
) {
    val state by component.viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        component.viewModel.onErrorShown()
    }

    val windowWidth = currentWindowSizePx().width
    val isWide = with(LocalDensity.current) { windowWidth.toDp() >= WIDE_SCREEN_THRESHOLD }
    val titleBarInteraction = LocalTitleBarInteraction.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarTotal = statusBarTop + LIQUID_GLASS_TOP_BAR_HEIGHT

    val gridPadding =
        PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topBarTotal + 8.dp,
            bottom = 16.dp,
        )

    GlassBackdropProvider {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .liquidGlassHero()
                        .glassSource(),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                PeerCatalogBody(
                    state = state,
                    isWide = isWide,
                    contentPadding = gridPadding,
                    onOpen = { entry -> component.viewModel.openEntry(entry.documentId, entry.displayName) },
                )
            }

            LiquidGlassTopBar(
                modifier = Modifier.align(Alignment.TopCenter),
                title = {
                    val suffix = if (state.isDisconnected) " (не в сети)" else ""
                    Text("Библиотека | ${state.peerName}$suffix")
                },
                navigationIcon = {
                    IconButton(
                        onClick = component.onBack,
                        modifier = titleBarInteraction?.interactive(Modifier) ?: Modifier,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
    }
}

/**
 * The scrollable catalog body: an «Открыто на устройстве» section (the peer's
 * active tabs) above the «Библиотека» recents. Grid on wide screens, single
 * column otherwise.
 */
@Composable
private fun PeerCatalogBody(
    state: PeerCatalogUiState,
    isWide: Boolean,
    contentPadding: PaddingValues,
    onOpen: (ru.kyamshanov.notepen.mainscreen.ui.model.RemoteEntryUiModel) -> Unit,
) {
    if (state.entries.isEmpty() && state.openDocuments.isEmpty()) {
        Text(
            text = if (state.isDisconnected) "Пир отключился" else "Библиотека пуста",
            modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center).padding(16.dp),
            color = MaterialTheme.colorScheme.outline,
        )
        return
    }
    val hasOpen = state.openDocuments.isNotEmpty()
    if (isWide) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (hasOpen) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Открыто на устройстве") }
                gridItemsIndexed(state.openDocuments, key = { i, m -> "open_${i}_${m.documentId}" }) { _, e ->
                    RemoteEntryCard(model = e, onClick = { onOpen(e) })
                }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Библиотека") }
            }
            gridItemsIndexed(state.entries, key = { i, m -> "${i}_${m.documentId}" }) { _, e ->
                RemoteEntryCard(model = e, onClick = { onOpen(e) })
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            if (hasOpen) {
                item { SectionHeader("Открыто на устройстве") }
                itemsIndexed(state.openDocuments, key = { i, m -> "open_${i}_${m.documentId}" }) { _, e ->
                    RemoteEntryCard(model = e, onClick = { onOpen(e) }, modifier = Modifier.padding(vertical = 4.dp))
                }
                item { SectionHeader("Библиотека") }
            }
            itemsIndexed(state.entries, key = { i, m -> "${i}_${m.documentId}" }) { _, e ->
                RemoteEntryCard(model = e, onClick = { onOpen(e) }, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}
