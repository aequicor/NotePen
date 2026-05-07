package ru.kyamshanov.notepen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import com.arkivanov.decompose.defaultComponentContext
import ru.kyamshanov.notepen.mainscreen.domain.usecase.AddToHistoryUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.CheckAvailabilityUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.OpenRecentFileUseCase
import ru.kyamshanov.notepen.mainscreen.infrastructure.FileAvailabilityCheckerAndroid
import ru.kyamshanov.notepen.mainscreen.infrastructure.FileHistoryRepositoryAndroid
import ru.kyamshanov.notepen.mainscreen.infrastructure.FolderRepositoryAndroid
import ru.kyamshanov.notepen.mainscreen.infrastructure.PdfThumbnailGeneratorAndroid
import ru.kyamshanov.notepen.mainscreen.infrastructure.ThumbnailRepositoryAndroid
import ru.kyamshanov.notepen.mainscreen.ui.screen.MainScreenComponent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val context = applicationContext
        val historyRepo = FileHistoryRepositoryAndroid(context)
        val folderRepo = FolderRepositoryAndroid(context)
        val availabilityChecker = FileAvailabilityCheckerAndroid(context)
        val thumbnailRepo = ThumbnailRepositoryAndroid(context)
        val thumbnailGenerator = PdfThumbnailGeneratorAndroid(context)

        val root = DefaultRootComponent(
            componentContext = defaultComponentContext(),
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
                    onOpenFilePicker = {},
                )
            },
        )

        setContent {
            // Remove when https://issuetracker.google.com/issues/364713509 is fixed
            LaunchedEffect(isSystemInDarkTheme()) {
                enableEdgeToEdge()
            }
            App(root)
        }
    }
}
