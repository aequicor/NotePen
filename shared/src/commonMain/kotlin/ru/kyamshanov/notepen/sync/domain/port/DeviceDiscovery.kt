package ru.kyamshanov.notepen.sync.domain.port

import kotlinx.coroutines.flow.Flow
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo

/**
 * Port for discovering peer NotePen servers on the local network (mDNS).
 *
 * Implementations must be main-safe; network I/O runs on an injected dispatcher.
 */
interface DeviceDiscovery {

    /**
     * Emits the current set of visible peers whenever it changes.
     *
     * Collection starts discovery; cancellation stops it.
     */
    val peers: Flow<List<DeviceInfo>>
}
