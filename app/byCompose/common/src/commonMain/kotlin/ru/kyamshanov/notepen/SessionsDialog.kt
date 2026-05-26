package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.mainscreen.domain.model.generateUuid
import ru.kyamshanov.notepen.mainscreen.ui.viewmodel.currentTimeMillis
import ru.kyamshanov.notepen.session.NamedSession
import ru.kyamshanov.notepen.session.SessionData
import ru.kyamshanov.notepen.session.SessionRepository

/**
 * Диалог управления сессиями редактора. Открывается из колеса настроек (см.
 * кнопку «Сессии» в [systemControlEntries]) и позволяет пользователю:
 * 1. **восстановить последнюю** сессию — автосохранённый (в т.ч. уцелевший после
 *    сбоя) рабочий стол; кнопка активна только когда автосейв существует;
 * 2. **сохранить текущую** под именем — текстовое поле + кнопка;
 * 3. управлять **списком именованных сессий** — у каждой строки действия
 *    «Восстановить» и «Удалить».
 *
 * Диалог сам владеет персистентностью ([sessionRepository]): читает список
 * именованных сессий, сохраняет и удаляет их, обновляя список после каждой
 * записи. Автосейв он не перечитывает — последнюю сессию получает готовой через
 * [lastSession]. Живой рабочий стол он не трогает — захват текущего
 * состояния и его восстановление делегируются вызывающему через [onCaptureCurrent]
 * и [onRestore], которые работают с активной `TabSession`.
 *
 * @param sessionRepository хранилище сессий: источник списка именованных сессий
 *   и операций сохранения/удаления.
 * @param lastSession рабочий стол на момент открытия редактора (в т.ч. уцелевший
 *   после сбоя автосейв), снятый ДО того, как живой автосейв начал перезаписывать
 *   файл; `null`, если автосейва не было. С ним работает кнопка «восстановить
 *   последнюю».
 * @param onCaptureCurrent снимок текущего рабочего стола для сохранения новой
 *   именованной сессии (обычно `tabSession.captureSession()`).
 * @param onRestore применяет выбранную сессию к живому рабочему столу; вызывается
 *   при восстановлении последней или именованной сессии, после чего диалог
 *   закрывается.
 * @param onDismiss закрыть диалог без действия.
 */
@Composable
fun SessionsDialog(
    sessionRepository: SessionRepository,
    lastSession: SessionData?,
    onCaptureCurrent: () -> SessionData,
    onRestore: (SessionData) -> Unit,
    onDismiss: () -> Unit,
) {
    // Версия-триггер перечитывания списка: инкремент после сохранения/удаления
    // заставляет produceState перезапросить listNamed().
    var refreshKey by remember { mutableStateOf(0) }
    val namedSessions by produceState(initialValue = emptyList<NamedSession>(), refreshKey) {
        value = sessionRepository.listNamed()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier =
                Modifier
                    .widthIn(min = 320.dp, max = 480.dp)
                    .heightIn(min = 200.dp, max = 720.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Text(text = "Сессии", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                RestoreLastSessionButton(lastSession) { data ->
                    onRestore(data)
                    onDismiss()
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                SaveSessionSection(
                    sessionRepository = sessionRepository,
                    onCaptureCurrent = onCaptureCurrent,
                    onSaved = { refreshKey++ },
                )

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

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
            }
        }
    }
}

/** Restores the pre-session workspace (the crash survivor); disabled when none exists. */
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

/** Name field + button that saves the current workspace as a named session. */
@Composable
private fun SaveSessionSection(
    sessionRepository: SessionRepository,
    onCaptureCurrent: () -> SessionData,
    onSaved: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var newSessionName by remember { mutableStateOf("") }
    OutlinedTextField(
        value = newSessionName,
        onValueChange = { newSessionName = it },
        singleLine = true,
        label = { Text("Название сессии") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            val name = newSessionName.trim()
            if (name.isEmpty()) return@Button
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
                onSaved()
            }
        },
        enabled = newSessionName.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Сохранить текущую")
    }
}

/** The list of saved named sessions, or a placeholder when none exist. */
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
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
