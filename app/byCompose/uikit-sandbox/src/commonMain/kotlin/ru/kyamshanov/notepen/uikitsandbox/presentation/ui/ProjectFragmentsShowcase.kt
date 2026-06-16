package ru.kyamshanov.notepen.uikitsandbox.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.mainscreen.ui.component.EmptyState
import ru.kyamshanov.notepen.mainscreen.ui.component.FolderCard
import ru.kyamshanov.notepen.mainscreen.ui.component.LibraryShelf
import ru.kyamshanov.notepen.mainscreen.ui.component.PeerCard
import ru.kyamshanov.notepen.mainscreen.ui.component.RecentFileCard
import ru.kyamshanov.notepen.mainscreen.ui.component.RemoteEntryCard
import ru.kyamshanov.notepen.mainscreen.ui.component.RemoteFolderCard

/**
 * Секция фрагментов основного экрана NotePen.
 *
 * Показывает карточки документов, папок, пиров, удалённых записей, shelf и empty-state
 * на фиктивных данных, чтобы sandbox не зависел от реального storage.
 *
 * @param strings локализованные demo-строки.
 * @param onMessage callback для snackbar-сообщений при клике на фрагменты.
 */
@Composable
internal fun ProjectFragmentsShowcase(
    strings: SandboxStrings,
    onMessage: (String) -> Unit,
) {
    SectionBlock(title = strings.projectTitle, icon = Icons.Default.Folder) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                RecentFileCard(
                    model = sampleRecentFile(strings),
                    onClick = { onMessage(strings.openDocumentMessage) },
                    folders = listOf(sampleFolder(strings)),
                    onAddToFolder = { onMessage(strings.movedMessage) },
                    onDelete = { onMessage(strings.deletedMessage) },
                    modifier = Modifier.width(220.dp),
                )
            }
            item {
                Column(
                    modifier = Modifier.width(260.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FolderCard(
                        model = sampleFolder(strings),
                        onClick = { onMessage(strings.openFolderMessage) },
                        onDelete = { onMessage(strings.deletedMessage) },
                    )
                    PeerCard(
                        model = samplePeer(strings),
                        onClick = { onMessage(strings.peerMessage) },
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier.width(280.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RemoteEntryCard(
                        model = sampleRemoteEntry(strings),
                        onClick = { onMessage(strings.remoteMessage) },
                    )
                    RemoteFolderCard(
                        model = sampleRemoteFolder(strings),
                        onClick = { onMessage(strings.openFolderMessage) },
                    )
                }
            }
        }
        LibraryShelf(
            items = sampleShelf(strings),
            onItemClick = { onMessage(strings.openDocumentMessage) },
            onDropInternalUri = { onMessage(strings.dropMessage) },
            onDropExternalFiles = { onMessage(strings.dropMessage) },
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_PANEL_ALPHA)),
        ) {
            EmptyState(
                onOpenFile = { onMessage(strings.primaryActionMessage) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
