package ru.kyamshanov.notepen.background

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ru.kyamshanov.notepen.BooleanToggle

/**
 * Лист настроек фона документа («текстурированная бумага»): выбор стиля бумаги +
 * переключатель «заменять белый фон PDF».
 *
 * Общий для редактора и режима чтения — открывается из тулбара редактора и из
 * панели настроек ридера, но всегда пишет ОДНО per-document состояние (см.
 * `PdfDocumentState.backgroundStyle` / `replaceWhiteBackground`). Превью стилей
 * строит [rememberPaperBrush] из того же каталога [PaperBackgrounds], что и сам фон.
 */
@Composable
public fun DocumentBackgroundSettingsSheet(
    currentStyle: String,
    replaceWhiteBackground: Boolean,
    isDark: Boolean,
    onStyleChange: (String) -> Unit,
    onReplaceWhiteChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = SHEET_ELEVATION,
            shadowElevation = SHEET_ELEVATION,
            modifier = Modifier.width(SHEET_WIDTH),
        ) {
            Column(
                modifier = Modifier.padding(SHEET_PADDING),
                verticalArrangement = Arrangement.spacedBy(SHEET_SECTION_GAP),
            ) {
                Text("Фон документа", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP)) {
                    PaperSwatch(
                        label = "Обычный",
                        brush = null,
                        selected = !PaperBackgrounds.isTextured(currentStyle),
                        onClick = { onStyleChange(PaperBackgrounds.PLAIN) },
                    )
                    PaperBackgrounds.styles.forEach { style ->
                        PaperSwatch(
                            label = style.displayName,
                            brush = rememberPaperBrush(style.id, isDark),
                            selected = currentStyle == style.id,
                            onClick = { onStyleChange(style.id) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text("Заменять белый фон PDF", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Подменяет белый фон страницы бумагой. Сканы и цветные страницы оставьте без замены.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(SWATCH_GAP))
                    BooleanToggle(enabled = replaceWhiteBackground, onToggle = onReplaceWhiteChange)
                }
            }
        }
    }
}

/** Квадрат-превью стиля бумаги: рисует плитку (или нейтральный фон для «Обычный»). */
@Composable
private fun PaperSwatch(
    label: String,
    brush: androidx.compose.ui.graphics.Brush?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(SWATCH_CORNER)
    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SWATCH_LABEL_GAP),
    ) {
        Spacer(
            modifier =
                Modifier
                    .size(SWATCH_SIZE)
                    .clip(shape)
                    .then(
                        if (brush != null) {
                            Modifier.drawBehind { drawRect(brush = brush) }
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        },
                    )
                    .border(if (selected) SWATCH_BORDER_SELECTED else SWATCH_BORDER, borderColor, shape)
                    .clickable(onClick = onClick),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color =
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val SHEET_WIDTH = 360.dp
private val SHEET_PADDING = 20.dp
private val SHEET_ELEVATION = 6.dp
private val SHEET_SECTION_GAP = 18.dp
private val SWATCH_SIZE = 56.dp
private val SWATCH_GAP = 12.dp
private val SWATCH_LABEL_GAP = 6.dp
private val SWATCH_CORNER = 10.dp
private val SWATCH_BORDER = 1.dp
private val SWATCH_BORDER_SELECTED = 2.dp
