import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.jetbrains.compose.resources.painterResource
import notepen.app.bycompose.desktop.generated.resources.Res
import notepen.app.bycompose.desktop.generated.resources.app_icon
import kotlinx.coroutines.Dispatchers
import ru.kyamshanov.notepen.App
import ru.kyamshanov.notepen.DefaultRootComponent
import ru.kyamshanov.notepen.RootComponent
import ru.kyamshanov.notepen.mainscreen.domain.usecase.AddToHistoryUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.CheckAvailabilityUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.OpenRecentFileUseCase
import ru.kyamshanov.notepen.mainscreen.infrastructure.FileAvailabilityCheckerDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.FileHistoryRepositoryDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.FolderRepositoryDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.PdfThumbnailGeneratorDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.ThumbnailRepositoryDesktop
import ru.kyamshanov.notepen.mainscreen.platform.FilePicker
import ru.kyamshanov.notepen.mainscreen.ui.screen.MainScreenComponent
import ru.kyamshanov.notepen.pdf.infrastructure.JvmPdfDocumentLoader
import ru.kyamshanov.notepen.pdf.infrastructure.JvmPdfPageRenderer


fun main() {
    val lifecycle = LifecycleRegistry()

    val pdfDocumentLoader = JvmPdfDocumentLoader(Dispatchers.IO)
    val pdfPageRenderer = JvmPdfPageRenderer(Dispatchers.IO)

    val historyRepo = FileHistoryRepositoryDesktop()
    val folderRepo = FolderRepositoryDesktop()
    val availabilityChecker = FileAvailabilityCheckerDesktop()
    val thumbnailRepo = ThumbnailRepositoryDesktop()
    val thumbnailGenerator = PdfThumbnailGeneratorDesktop()

    // Always create the root component outside Compose on the UI thread
    val root: RootComponent =
        runOnUiThread {
            DefaultRootComponent(
                componentContext = DefaultComponentContext(lifecycle = lifecycle),
                historyRepository = historyRepo,
                mainComponentFactory = { componentContext, onOpenEditor ->
                    MainScreenComponent(
                        componentContext = componentContext,
                        historyRepository = historyRepo,
                        folderRepository = folderRepo,
                        addToHistory = AddToHistoryUseCase(historyRepo),
                        checkAvailability = CheckAvailabilityUseCase(availabilityChecker, historyRepo),
                        openRecentFileUseCase = OpenRecentFileUseCase(availabilityChecker),
                        thumbnailRepository = thumbnailRepo,
                        thumbnailGenerator = thumbnailGenerator,
                        onOpenEditor = onOpenEditor,
                        onOpenFilePicker = { FilePicker().pickPdfFile() },
                    )
                },
            )
        }

    application {
        val windowState = rememberWindowState(
            placement = WindowPlacement.Maximized,
        )

        LifecycleController(lifecycle, windowState)

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "NotePen",
            icon = painterResource(Res.drawable.app_icon) //for generate Res class use `gradle :app:byCompose:desktop:generateComposeResClass`
        ) {
            App(
                rootComponent = root,
                pdfDocumentLoader = pdfDocumentLoader,
                pdfPageRenderer = pdfPageRenderer,
            )
        }
    }
}
