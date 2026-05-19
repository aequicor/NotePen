package ru.kyamshanov.notepen.shortcuts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import ru.kyamshanov.notepen.shortcuts.domain.model.ShortcutsSettings
import ru.kyamshanov.notepen.shortcuts.domain.port.ShortcutsRepository

/**
 * Android v1: настройки шорткатов на десктоп-функционал (лупа с пером)
 * неактуальны. Возвращаем in-memory NoOp; персистенция отсутствует.
 */
actual fun defaultShortcutsRepository(): ShortcutsRepository = InMemoryShortcutsRepository

private object InMemoryShortcutsRepository : ShortcutsRepository {
    private val state = MutableStateFlow(ShortcutsSettings())

    override suspend fun load(): ShortcutsSettings = state.value

    override suspend fun save(settings: ShortcutsSettings) {
        state.update { settings }
    }
}
