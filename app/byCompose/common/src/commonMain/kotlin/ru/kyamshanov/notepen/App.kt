package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import ru.kyamshanov.notepen.sync.HostScreen
import ru.kyamshanov.notepen.sync.HostViewModel
import ru.kyamshanov.notepen.sync.SyncScreen
import ru.kyamshanov.notepen.sync.SyncViewModel

@Composable
fun App(
    rootComponent: RootComponent,
    pdfDocumentLoader: PdfDocumentLoader,
    pdfPageRenderer: PdfPageRenderer,
    hostViewModel: HostViewModel? = null,
    syncViewModel: SyncViewModel? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    var showSyncPanel by remember { mutableStateOf(false) }

    ComposableAppTheme {
        Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                RootContent(
                    component = rootComponent,
                    pdfDocumentLoader = pdfDocumentLoader,
                    pdfPageRenderer = pdfPageRenderer,
                    modifier = Modifier.fillMaxSize(),
                )

                if (hostViewModel != null && syncViewModel != null) {
                    FloatingActionButton(
                        onClick = { showSyncPanel = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 88.dp, end = 16.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Синхронизация",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }

        if (showSyncPanel && hostViewModel != null && syncViewModel != null) {
            Dialog(onDismissRequest = { showSyncPanel = false }) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 6.dp,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        androidx.compose.foundation.layout.Column {
                            HostScreen(viewModel = hostViewModel)
                            HorizontalDivider()
                            SyncScreen(viewModel = syncViewModel)
                        }
                    }
                }
            }
        }
    }
}
