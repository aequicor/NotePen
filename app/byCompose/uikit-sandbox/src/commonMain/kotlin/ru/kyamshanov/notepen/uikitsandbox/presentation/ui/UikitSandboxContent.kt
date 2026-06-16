package ru.kyamshanov.notepen.uikitsandbox.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.flow.collectLatest
import ru.kyamshanov.notepen.AppTheme
import ru.kyamshanov.notepen.CustomButton
import ru.kyamshanov.notepen.uikitsandbox.UikitSandboxComponent

/**
 * Shared UI исходной sandbox-фичи.
 *
 * Компонент демонстрирует, как UI работает только с [UikitSandboxComponent]:
 * подписывается на модель, собирает events и вызывает семантические callbacks.
 *
 * @param component публичный component sandbox-фичи.
 * @param onMessage callback для одноразовых сообщений.
 * @param modifier модификатор корневого layout.
 */
@Composable
internal fun UikitSandboxContent(
    component: UikitSandboxComponent,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model by component.model.subscribeAsState()

    LaunchedEffect(component) {
        component.events.collectLatest { event ->
            when (event) {
                is UikitSandboxComponent.Event.ShowMessage -> onMessage(event.message)
            }
        }
    }

    Column(
        modifier = modifier.padding(AppTheme.spacing.screenEdge),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SandboxToolbar(
            isLoading = model.isLoading,
            selectedFilter = model.selectedFilter,
            onFilterSelected = component::onFilterSelected,
            onRefreshClicked = component::onRefreshClicked,
        )
        model.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(
                items = model.widgets,
                key = UikitSandboxComponent.Widget::id,
            ) { widget ->
                SandboxWidgetRow(
                    widget = widget,
                    onPinnedChanged = component::onPinnedChanged,
                    onWidgetClicked = component::onWidgetClicked,
                )
            }
        }
    }
}

@Composable
private fun SandboxToolbar(
    isLoading: Boolean,
    selectedFilter: UikitSandboxComponent.Filter,
    onFilterSelected: (UikitSandboxComponent.Filter) -> Unit,
    onRefreshClicked: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "UIKit sandbox",
                style = MaterialTheme.typography.titleLarge,
            )
            CustomButton(onClick = onRefreshClicked) {
                Text("Refresh")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UikitSandboxComponent.Filter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(filter.name) },
                )
            }
        }
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SandboxWidgetRow(
    widget: UikitSandboxComponent.Widget,
    onPinnedChanged: (String, Boolean) -> Unit,
    onWidgetClicked: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = { onWidgetClicked(widget.id) },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = widget.title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    AssistChip(
                        onClick = { },
                        label = { Text(widget.statusLabel) },
                    )
                }
                Text(
                    text = widget.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = widget.isPinned,
                onCheckedChange = { onPinnedChanged(widget.id, it) },
            )
        }
    }
}
