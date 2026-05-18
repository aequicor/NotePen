package ru.kyamshanov.notepen.mainscreen.ui.peer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import ru.kyamshanov.notepen.mainscreen.ui.component.RemoteEntryCard
import ru.kyamshanov.notepen.mainscreen.ui.component.RemoteFolderCard

private val WIDE_SCREEN_THRESHOLD: Dp = 600.dp

@OptIn(ExperimentalMaterial3Api::class)
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

    val windowWidth = LocalWindowInfo.current.containerSize.width
    val isWide = with(LocalDensity.current) { windowWidth.toDp() >= WIDE_SCREEN_THRESHOLD }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val subtitle = if (state.isDisconnected) " (не в сети)" else ""
                    Text(state.peerName + subtitle)
                },
                navigationIcon = {
                    IconButton(onClick = component.onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            if (state.entries.isEmpty() && state.folders.isEmpty()) {
                Text(
                    text = if (state.isDisconnected) "Пир отключился" else "Каталог пуст",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    color = MaterialTheme.colorScheme.outline,
                )
            } else if (isWide) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    if (state.folders.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Папки") }
                        gridItems(state.folders, key = { "folder_${it.folderId}" }) { folder ->
                            RemoteFolderCard(model = folder)
                        }
                    }
                    if (state.entries.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Недавние файлы") }
                        // documentId не уникален: на хосте могут быть два файла с
                        // одинаковым именем по разным путям. Ключ — индекс+id.
                        gridItemsIndexed(state.entries, key = { idx, it -> "${idx}_${it.documentId}" }) { _, entry ->
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
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    if (state.folders.isNotEmpty()) {
                        item { SectionHeader("Папки") }
                        items(state.folders, key = { "folder_${it.folderId}" }) { folder ->
                            RemoteFolderCard(
                                model = folder,
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
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

