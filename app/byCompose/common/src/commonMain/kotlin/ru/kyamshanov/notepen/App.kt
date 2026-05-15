package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer

@Composable
fun App(
    rootComponent: RootComponent,
    pdfDocumentLoader: PdfDocumentLoader,
    pdfPageRenderer: PdfPageRenderer,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    ComposableAppTheme {
        Surface {
            RootContent(
                component = rootComponent,
                pdfDocumentLoader = pdfDocumentLoader,
                pdfPageRenderer = pdfPageRenderer,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
