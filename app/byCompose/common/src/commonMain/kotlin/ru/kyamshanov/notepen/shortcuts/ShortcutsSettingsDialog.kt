package ru.kyamshanov.notepen.shortcuts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import ru.kyamshanov.notepen.shortcuts.domain.model.ShortcutBinding
import ru.kyamshanov.notepen.shortcuts.domain.model.ShortcutsSettings

/**
 * Диалог настройки шорткатов. v1: два биндинга для лупы.
 *
 * Поле «Сочетание» — read-only TextField. Клик по «Записать» → следующая
 * нажатая комбинация (модификаторы + клавиша **или** кнопка пера)
 * сохраняется как новый биндинг. Кнопки пера обрабатываются как полноценные
 * элементы сочетания: можно сохранить «Pen1» в одиночку, «Ctrl + Pen2» или
 * «Ctrl + F». Кнопка «Сбросить» очищает биндинг (отключает).
 *
 * @param penButtons актуальный набор зажатых кнопок пера; нужен, чтобы при
 *  записи сочетания захватить кнопки пера в момент, когда пользователь их
 *  жмёт (KeyEvent через них не идёт).
 */
@Composable
fun ShortcutsSettingsDialog(
    settings: ShortcutsSettings,
    onChange: (ShortcutsSettings) -> Unit,
    onDismiss: () -> Unit,
    penButtons: StateFlow<Set<Int>>,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        },
        title = { Text("Шорткаты") },
        text = {
            Column {
                BindingField(
                    title = "Открыть лупу",
                    binding = settings.loupeOpen,
                    onChange = { onChange(settings.copy(loupeOpen = it)) },
                    penButtons = penButtons,
                )
                Spacer(Modifier.height(12.dp))
                BindingField(
                    title = "Закрыть лупу",
                    binding = settings.loupeClose,
                    onChange = { onChange(settings.copy(loupeClose = it)) },
                    penButtons = penButtons,
                )
            }
        },
    )
}

@Composable
private fun BindingField(
    title: String,
    binding: ShortcutBinding,
    onChange: (ShortcutBinding) -> Unit,
    penButtons: StateFlow<Set<Int>>,
) {
    var recording by remember { mutableStateOf(false) }
    // Снимок зажатых модификаторов — обновляется при любом keyEvent в
    // режиме записи. Нужно, чтобы при нажатии кнопки пера зафиксировать
    // «Ctrl + Pen1», а не «Pen1» в одиночку.
    var recCtrl by remember { mutableStateOf(false) }
    var recShift by remember { mutableStateOf(false) }
    var recAlt by remember { mutableStateOf(false) }
    var recMeta by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Live-состояние кнопок пера — нужно и для превью, и для логики записи.
    val penButtonsNow by penButtons.collectAsState()
    // baseline и peak копим в remember-state, чтобы LaunchedEffect не
    // пересоздавал их при каждой рекомпозиции.
    val baseline = remember(recording) { if (recording) penButtonsNow else emptySet() }
    val peakState = remember(recording) { mutableStateOf<Set<Int>>(emptySet()) }
    val extra = penButtonsNow - baseline
    if (extra.size > peakState.value.size) peakState.value = extra

    // Автосейв на отпускании кнопок пера: когда все «новые» кнопки
    // отпущены и peak не пустой — фиксируем биндинг
    // `модификаторы + peak`. Отпускание-триггер позволяет также записать
    // «Pen + F»: пока пользователь держит Pen, он может нажать F и
    // keyboard-листенер сохранит комбо до того, как сработает отпускание.
    LaunchedEffect(recording, extra.isEmpty(), peakState.value.isNotEmpty()) {
        if (!recording) return@LaunchedEffect
        if (extra.isEmpty() && peakState.value.isNotEmpty()) {
            val updated = ShortcutBinding(
                ctrl = recCtrl,
                shift = recShift,
                alt = recAlt,
                meta = recMeta,
                penButtons = peakState.value,
            )
            onChange(updated)
            recording = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        val display = when {
            recording -> {
                val preview = ShortcutBinding(
                    ctrl = recCtrl,
                    shift = recShift,
                    alt = recAlt,
                    meta = recMeta,
                    penButtons = peakState.value.ifEmpty { extra },
                )
                if (preview.isEmpty) {
                    "Нажмите сочетание клавиш или кнопку пера…"
                } else {
                    "Запись: ${formatBinding(preview)}"
                }
            }
            binding.isEmpty -> "Не задано"
            else -> formatBinding(binding)
        }
        OutlinedTextField(
            value = TextFieldValue(display),
            onValueChange = {},
            readOnly = true,
            label = { Text("Сочетание") },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { e ->
                    if (!recording) return@onPreviewKeyEvent false
                    // Обновляем снимок модификаторов на любом событии —
                    // полезно для пен-кнопочного captures.
                    recCtrl = e.isCtrlPressed
                    recShift = e.isShiftPressed
                    recAlt = e.isAltPressed
                    recMeta = e.isMetaPressed
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                    // Игнорируем «голые» нажатия модификаторов как основной
                    // клавиши сочетания.
                    if (isModifierKey(e.key.keyCode)) return@onPreviewKeyEvent true
                    val updated = ShortcutBinding(
                        ctrl = e.isCtrlPressed,
                        shift = e.isShiftPressed,
                        alt = e.isAltPressed,
                        meta = e.isMetaPressed,
                        penButtons = penButtons.value,
                        keyCode = e.key.keyCode,
                        keyName = keyDisplayName(e.key.keyCode),
                    )
                    onChange(updated)
                    recording = false
                    true
                },
        )

        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = {
                if (recording) {
                    recording = false
                } else {
                    recCtrl = false; recShift = false; recAlt = false; recMeta = false
                    recording = true
                    focusRequester.requestFocus()
                }
            }) {
                Text(if (recording) "Отмена" else "Записать")
            }
            TextButton(onClick = {
                recording = false
                onChange(ShortcutBinding.Disabled)
            }) {
                Text("Сбросить")
            }
        }
    }
}

private fun formatBinding(binding: ShortcutBinding): String {
    val parts = mutableListOf<String>()
    if (binding.ctrl) parts += "Ctrl"
    if (binding.shift) parts += "Shift"
    if (binding.alt) parts += "Alt"
    if (binding.meta) parts += "Meta"
    binding.penButtons.sorted().forEach { parts += "Pen$it" }
    if (binding.keyName.isNotEmpty()) parts += binding.keyName
    return parts.joinToString(" + ")
}
