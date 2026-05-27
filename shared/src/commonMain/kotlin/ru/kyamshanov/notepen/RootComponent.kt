package ru.kyamshanov.notepen

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value

interface RootComponent {
    val stack: Value<ChildStack<*, Child>>

    // It's possible to pop multiple screens at a time on iOS
    fun onBackClicked(toIndex: Int)

    /**
     * Pushes the editor onto the navigation stack for a PDF that was just
     * received from a peer over WebSocket. Used by the tablet side once
     * [ru.kyamshanov.notepen.sync.infrastructure.FileTransferReceiver]
     * finishes assembling the file.
     */
    fun openDetailsExternally(uri: String)

    // Defines all possible child components
    sealed class Child {
        class MainChild(
            val component: MainComponent,
        ) : Child()

        class DetailsChild(
            val component: DetailsComponent,
        ) : Child()

        class PeerCatalogChild(
            val component: PeerCatalogComponent,
        ) : Child()

        class FolderContentsChild(
            val component: FolderComponent,
        ) : Child()
    }
}
