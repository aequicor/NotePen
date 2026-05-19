package ru.kyamshanov.notepen.shortcuts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import ru.kyamshanov.notepen.shortcuts.domain.model.ShortcutsSettings
import ru.kyamshanov.notepen.shortcuts.domain.port.ShortcutsRepository

/**
 * Создаёт платформенно-специфичный [ShortcutsRepository]. На JVM —
 * `JvmShortcutsRepository`, на других платформах — in-memory NoOp.
 */
expect fun defaultShortcutsRepository(): ShortcutsRepository

/**
 * Поднимает [ShortcutsSettings] как Compose-state, привязанный к [repository].
 *
 * При первой композиции — асинхронно загружает настройки. На каждое
 * последующее изменение — пишет в репозиторий (тоже асинхронно, не
 * блокирует UI).
 */
@Composable
fun rememberShortcutsSettings(
    repository: ShortcutsRepository = remember { defaultShortcutsRepository() },
): MutableState<ShortcutsSettings> {
    val state = remember { mutableStateOf(ShortcutsSettings()) }

    LaunchedEffect(repository) {
        state.value = repository.load()
        // Сохранение на изменениях. Первое значение из snapshotFlow — то, что
        // мы только что загрузили; пропускаем его, чтобы не писать сразу.
        snapshotFlow { state.value }
            .drop(1)
            .collect { current -> repository.save(current) }
    }

    return state
}
