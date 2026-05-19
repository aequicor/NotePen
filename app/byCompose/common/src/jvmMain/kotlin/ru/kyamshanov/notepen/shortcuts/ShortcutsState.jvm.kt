package ru.kyamshanov.notepen.shortcuts

import ru.kyamshanov.notepen.shortcuts.data.JvmShortcutsRepository
import ru.kyamshanov.notepen.shortcuts.domain.port.ShortcutsRepository

actual fun defaultShortcutsRepository(): ShortcutsRepository = JvmShortcutsRepository()
