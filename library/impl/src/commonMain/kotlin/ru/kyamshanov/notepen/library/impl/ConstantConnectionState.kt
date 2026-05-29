package ru.kyamshanov.notepen.library.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.kyamshanov.notepen.library.api.LibraryConnectionState

/**
 * Reusable connection-state holders for backends whose connectivity never changes after connect
 * (e.g. a local folder is always reachable).
 */
internal object ConstantConnectionState {
    /** A [StateFlow] that is permanently [LibraryConnectionState.Connected]. */
    val connected: StateFlow<LibraryConnectionState> =
        MutableStateFlow<LibraryConnectionState>(LibraryConnectionState.Connected).asStateFlow()
}
