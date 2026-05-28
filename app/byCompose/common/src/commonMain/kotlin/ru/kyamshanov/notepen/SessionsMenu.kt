package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.mainscreen.domain.model.generateUuid
import ru.kyamshanov.notepen.mainscreen.ui.viewmodel.currentTimeMillis
import ru.kyamshanov.notepen.session.NamedSession
import ru.kyamshanov.notepen.session.SessionData
import ru.kyamshanov.notepen.session.SessionRepository

/**
 * Меню управления сессиями редактора. Раскрывается из кнопки «Сессии» в левом
 * крае полосы вкладок (см. [ru.kyamshanov.notepen.tabs.TabBar]) и позволяет:
 * 1. **восстановить последнюю** сессию — автосохранённый (в т.ч. уцелевший после
 *    сбоя) рабочий стол; кнопка активна только когда автосейв существует;
 * 2. **сохранить текущую** под именем — текстовое поле + кнопка;
 * 3. управлять **списком именованных сессий** — у каждой строки действия
 *    «Восстановить» и «Удалить».
 *
 * Меню само владеет персистентностью ([sessionRepository]): читает список
 * именованных сессий, сохраняет и удаляет их, обновляя список после каждой
 * записи. Автосейв он не перечитывает — последнюю сессию получает готовой через
 * [lastSession]. Живой рабочий стол он не трогает — захват текущего
 * состояния и его восстановление делегируются вызывающему через [onCaptureCurrent]
 * и [onRestore], которые работают с активной `TabSession`.
 *
 * **Режим «только восстановление».** Когда [onCaptureCurrent] равен `null` (как на
 * библиотеке/главном экране, где нет открытого рабочего стола, который можно
 * сохранить), секция «Сохранить текущую…» и окружающий её разделитель не
 * отрисовываются — остаются только «восстановить последнюю» и список именованных
 * сессий.
 *
 * В отличие от модального диалога, [DropdownMenu] всплывает поверх редактора без
 * отдельного окна (на desktop модальный `Dialog` поднимал нативное окно и
 * подтормаживал на открытии) и закрывается тапом мимо — отдельной кнопки
 * «Закрыть» нет. Содержимое прокручивается самим меню, поэтому список сессий не
 * заворачивается в собственный скролл.
 *
 * @param expanded раскрыто ли меню; `false` — меню скрыто (контент не виден).
 * @param sessionRepository хранилище сессий: источник списка именованных сессий
 *   и операций сохранения/удаления.
 * @param lastSession рабочий стол на момент открытия редактора (в т.ч. уцелевший
 *   после сбоя автосейв), снятый ДО того, как живой автосейв начал перезаписывать
 *   файл; `null`, если автосейва не было. С ним работает кнопка «восстановить
 *   последнюю».
 * @param onCaptureCurrent снимок текущего рабочего стола для сохранения новой
 *   именованной сессии (обычно `tabSession.captureSession()`); `null` включает
 *   режим «только восстановление» — секция сохранения не показывается.
 * @param onRestore применяет выбранную сессию к живому рабочему столу; вызывается
 *   при восстановлении последней или именованной сессии, после чего меню
 *   закрывается.
 * @param onDismiss закрыть меню (тап мимо или после действия).
 */
@Composable
fun SessionsMenu(
    expanded: Boolean,
    sessionRepository: SessionRepository,
    lastSession: SessionData?,
    onRestore: (SessionData) -> Unit,
    onDismiss: () -> Unit,
    onCaptureCurrent: (() -> SessionData)? = null,
) {
    // Версия-триггер перечитывания списка: инкремент после сохранения/удаления
    // заставляет produceState перезапросить listNamed().
    var refreshKey by remember { mutableStateOf(0) }
    val namedSessions by produceState(initialValue = emptyList<NamedSession>(), refreshKey) {
        value = sessionRepository.listNamed()
    }

    LiquidGlassDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .width(SESSIONS_MENU_WIDTH)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(text = "Сессии", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            RestoreLastSessionButton(lastSession) { data ->
                onRestore(data)
                onDismiss()
            }

            // The save section is omitted in restore-only mode (onCaptureCurrent == null),
            // e.g. on the library screen, where there is no live workspace to save.
            if (onCaptureCurrent != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                SaveSessionSection(
                    sessionRepository = sessionRepository,
                    onCaptureCurrent = onCaptureCurrent,
                    onSaved = { refreshKey++ },
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            NamedSessionsSection(
                sessions = namedSessions,
                sessionRepository = sessionRepository,
                onRestore = { data ->
                    onRestore(data)
                    onDismiss()
                },
                onDeleted = { refreshKey++ },
            )
        }
    }
}

/** Width of the sessions dropdown content (the menu itself wraps to it). */
private val SESSIONS_MENU_WIDTH = 320.dp

/**
 * Restores the pre-session workspace (the crash survivor); disabled when none exists.
 * Rendered as a primary [Button] (terracotta on the warm-beige palette) so it reads
 * as the menu's main call-to-action and doesn't get lost in the tonal glass surface
 * — a `FilledTonalButton` here collapses into the menu's tint and is hard to spot.
 */
@Composable
private fun RestoreLastSessionButton(
    lastSession: SessionData?,
    onRestore: (SessionData) -> Unit,
) {
    Button(
        onClick = { lastSession?.let(onRestore) },
        enabled = lastSession != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Восстановить последнюю")
    }
}

/**
 * Saving UI for the current workspace. The name field is revealed **lazily** (only
 * after «Сохранить текущую…» is tapped): composing an `OutlinedTextField` is costly
 * on Compose Desktop and a [DropdownMenu] re-composes its content on every open, so
 * keeping the field out of the default content makes the menu open instantly.
 */
@Composable
private fun SaveSessionSection(
    sessionRepository: SessionRepository,
    onCaptureCurrent: () -> SessionData,
    onSaved: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var entering by remember { mutableStateOf(false) }
    if (!entering) {
        FilledTonalButton(onClick = { entering = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Сохранить текущую…")
        }
    } else {
        var newSessionName by remember { mutableStateOf("") }
        OutlinedTextField(
            value = newSessionName,
            onValueChange = { newSessionName = it },
            singleLine = true,
            label = { Text("Название сессии") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(
                onClick = {
                    val name = newSessionName.trim()
                    if (name.isEmpty()) return@FilledTonalButton
                    coroutineScope.launch {
                        sessionRepository.saveNamed(
                            NamedSession(
                                id = generateUuid(),
                                name = name,
                                savedAtEpochMs = currentTimeMillis(),
                                data = onCaptureCurrent(),
                            ),
                        )
                        newSessionName = ""
                        entering = false
                        onSaved()
                    }
                },
                enabled = newSessionName.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text("Сохранить")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { entering = false }) { Text("Отмена") }
        }
    }
}

/**
 * The list of saved named sessions, or a placeholder when none exist.
 *
 * No own [androidx.compose.foundation.verticalScroll]: the enclosing [DropdownMenu]
 * already scrolls its content, so the rows are emitted directly and the whole menu
 * (header + sections + this list) scrolls as one when the history is long.
 */
@Composable
private fun NamedSessionsSection(
    sessions: List<NamedSession>,
    sessionRepository: SessionRepository,
    onRestore: (SessionData) -> Unit,
    onDeleted: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    if (sessions.isEmpty()) {
        Text(
            text = "Нет сохранённых сессий",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    } else {
        Column {
            sessions.forEach { session ->
                NamedSessionRow(
                    session = session,
                    onRestore = { onRestore(session.data) },
                    onDelete = {
                        coroutineScope.launch {
                            sessionRepository.deleteNamed(session.id)
                            onDeleted()
                        }
                    },
                )
            }
        }
    }
}

/**
 * Одна строка списка именованных сессий: пользовательское имя слева, действия
 * «Восстановить» и «Удалить» справа.
 *
 * Время сохранения ([NamedSession.savedAtEpochMs]) намеренно не отображается:
 * модуль не зависит от форматтера дат (`kotlinx-datetime` не подключён), а
 * вводить ручную календарную арифметику ради «приятного» лейбла нежелательно.
 */
@Composable
private fun NamedSessionRow(
    session: NamedSession,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = session.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRestore) { Text("Восстановить") }
        TextButton(onClick = onDelete) { Text("Удалить") }
    }
}
