package ru.kyamshanov.notepen.mainscreen.ui.peer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.EditorBackHandler
import ru.kyamshanov.notepen.LIQUID_GLASS_TOP_BAR_HEIGHT
import ru.kyamshanov.notepen.LiquidGlassTopBar
import ru.kyamshanov.notepen.blur.GlassBackdropProvider
import ru.kyamshanov.notepen.blur.glassSource
import ru.kyamshanov.notepen.liquidGlassHero
import ru.kyamshanov.notepen.mainscreen.ui.component.RemoteEntryCard
import ru.kyamshanov.notepen.mainscreen.ui.component.RemoteFolderCard
import ru.kyamshanov.notepen.titlebar.LocalTitleBarInteraction
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

private val WIDE_SCREEN_THRESHOLD: Dp = 600.dp

@Composable
fun PeerCatalogContent(
    component: PeerCatalogComponentImpl,
    modifier: Modifier = Modifier,
) {
    val state by component.viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    EditorBackHandler(enabled = state.currentFolderId != null) {
        component.viewModel.exitFolder()
    }

    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        component.viewModel.onErrorShown()
    }

    val windowWidth = LocalWindowInfo.current.containerSize.width
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
                if (state.entries.isEmpty() && state.folders.isEmpty()) {
                    Text(
                        text = if (state.isDisconnected) "Пир отключился" else "Каталог пуст",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else if (isWide) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 280.dp),
                        contentPadding = gridPadding,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.folders.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Папки") }
                            gridItems(state.folders, key = { "folder_${it.folderId}" }) { folder ->
                                RemoteFolderCard(
                                    model = folder,
                                    onClick = { component.viewModel.openFolder(folder.folderId) },
                                )
                            }
                        }
                        if (state.entries.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Недавние файлы") }
                            gridItemsIndexed(
                                state.entries,
                                key = { idx, it -> "${idx}_${it.documentId}" },
                            ) { _, entry ->
                                RemoteEntryCard(
                                    model = entry,
                                    onClick = {
                                        component.viewModel.openEntry(entry.documentId, entry.displayName)
                                    },
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = gridPadding,
                    ) {
                        if (state.folders.isNotEmpty()) {
                            item { SectionHeader("Папки") }
                            items(state.folders, key = { "folder_${it.folderId}" }) { folder ->
                                RemoteFolderCard(
                                    model = folder,
                                    onClick = { component.viewModel.openFolder(folder.folderId) },
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }
                        if (state.entries.isNotEmpty()) {
                            item { SectionHeader("Недавние файлы") }
                            itemsIndexed(state.entries, key = { idx, it -> "${idx}_${it.documentId}" }) { _, entry ->
                                RemoteEntryCard(
                                    model = entry,
                                    onClick = {
                                        component.viewModel.openEntry(entry.documentId, entry.displayName)
                                    },
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            LiquidGlassTopBar(
                modifier = Modifier.align(Alignment.TopCenter),
                title = {
                    val suffix =
                        when {
                            state.currentFolderName != null -> " / ${state.currentFolderName}"
                            state.isDisconnected -> " (не в сети)"
                            else -> ""
                        }
                    Text(state.peerName + suffix)
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (!component.viewModel.exitFolder()) component.onBack() },
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

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
