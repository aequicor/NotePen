package ru.kyamshanov.notepen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import ru.kyamshanov.notepen.mainscreen.ui.model.NavigationTarget
import ru.kyamshanov.notepen.mainscreen.ui.screen.MainContent
import ru.kyamshanov.notepen.mainscreen.ui.screen.MainScreenComponent

@Composable
fun RootContent(component: RootComponent, modifier: Modifier = Modifier) {
    Children(
        stack = component.stack,
        modifier = modifier,
        animation = stackAnimation(fade()),
    ) {
        when (val child = it.instance) {
            is RootComponent.Child.MainChild -> {
                // Safe cast: DefaultRootComponent always creates MainScreenComponent for MainChild.
                // RootContent lives in :common and may safely reference MainScreenComponent.
                val mainScreenComponent = child.component as? MainScreenComponent
                    ?: error("MainChild.component must be MainScreenComponent — check DefaultRootComponent factory")
                val state by mainScreenComponent.viewModel.state.collectAsState()

                LaunchedEffect(state.navigationTarget) {
                    when (val target = state.navigationTarget) {
                        is NavigationTarget.Editor -> {
                            mainScreenComponent.onOpenEditor(target.uri, target.lastPageIndex)
                            mainScreenComponent.viewModel.onNavigationHandled()
                        }
                        NavigationTarget.FilePicker -> {
                            val pickedPath = mainScreenComponent.onOpenFilePicker()
                            mainScreenComponent.viewModel.onIntent(
                                ru.kyamshanov.notepen.mainscreen.ui.MainScreenIntent.FilePickerResult(
                                    uri = pickedPath,
                                    displayName = pickedPath
                                        ?.substringAfterLast('/')
                                        ?.substringAfterLast('\\')
                                        ?: "",
                                    fileSize = null,
                                ),
                            )
                        }
                        null -> {}
                    }
                }

                MainContent(
                    state = state,
                    onIntent = { mainScreenComponent.viewModel.onIntent(it) },
                    modifier = modifier,
                )
            }
            is RootComponent.Child.DetailsChild -> DetailsContent(
                component = child.component,
                modifier = modifier,
            )
        }
    }
}
