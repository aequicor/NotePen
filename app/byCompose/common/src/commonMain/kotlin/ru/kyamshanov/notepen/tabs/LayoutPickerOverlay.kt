package ru.kyamshanov.notepen.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Human-readable title for [template], shown under its thumbnail in the picker. */
private fun LayoutTemplate.title(): String = when (this) {
    LayoutTemplate.FULL -> "Одна панель"
    LayoutTemplate.COLUMNS_2 -> "Две колонки"
    LayoutTemplate.ROWS_2 -> "Две строки"
    LayoutTemplate.COLUMNS_3 -> "Три колонки"
    LayoutTemplate.LEFT_PLUS_STACK -> "Большая + две"
    LayoutTemplate.GRID_2X2 -> "Сетка 2×2"
}

/**
 * Preset picker overlay (à la Windows Snap Layouts): shows a thumbnail per
 * available [templates], highlighting the slot the new panel will occupy.
 * Tapping a thumbnail invokes [onPick]; tapping outside invokes [onDismiss].
 */
@Composable
fun LayoutPickerOverlay(
    templates: List<LayoutTemplate>,
    onPick: (LayoutTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    if (templates.isEmpty()) return
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = "Куда поместить новую панель",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    templates.forEach { template ->
                        TemplateOption(template, onPick)
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateOption(template: LayoutTemplate, onPick: (LayoutTemplate) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onPick(template) }
            .padding(8.dp),
    ) {
        TemplateThumb(template)
        Spacer(Modifier.height(8.dp))
        Text(
            text = template.title(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Static miniature of [template]; the last slot (where the new panel lands) is highlighted. */
@Composable
private fun TemplateThumb(template: LayoutTemplate) {
    val gap = 3.dp
    Box(
        Modifier
            .size(width = 84.dp, height = 60.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(gap),
    ) {
        when (template) {
            LayoutTemplate.FULL -> Slot(true, Modifier.fillMaxSize())
            LayoutTemplate.COLUMNS_2 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Slot(false, Modifier.fillMaxHeight().weight(1f))
                Slot(true, Modifier.fillMaxHeight().weight(1f))
            }
            LayoutTemplate.ROWS_2 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Slot(false, Modifier.fillMaxWidth().weight(1f))
                Slot(true, Modifier.fillMaxWidth().weight(1f))
            }
            LayoutTemplate.COLUMNS_3 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Slot(false, Modifier.fillMaxHeight().weight(1f))
                Slot(false, Modifier.fillMaxHeight().weight(1f))
                Slot(true, Modifier.fillMaxHeight().weight(1f))
            }
            LayoutTemplate.LEFT_PLUS_STACK -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Slot(false, Modifier.fillMaxHeight().weight(1f))
                Column(Modifier.fillMaxHeight().weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                    Slot(false, Modifier.fillMaxWidth().weight(1f))
                    Slot(true, Modifier.fillMaxWidth().weight(1f))
                }
            }
            LayoutTemplate.GRID_2X2 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Slot(false, Modifier.fillMaxHeight().weight(1f))
                    Slot(false, Modifier.fillMaxHeight().weight(1f))
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Slot(false, Modifier.fillMaxHeight().weight(1f))
                    Slot(true, Modifier.fillMaxHeight().weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Slot(highlighted: Boolean, modifier: Modifier) {
    val color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    Box(modifier.clip(RoundedCornerShape(2.dp)).background(color))
}
