package ru.kyamshanov.notepen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.model.StoredToolPresets
import ru.kyamshanov.notepen.blur.GlassSurface

/**
 * Full-width top chrome for portrait mode.
 *
 * A back button and page counter on the left, then a single horizontal
 * [WheelStrip] holding tool toggles, the active tool's settings slots and
 * presets, and the system controls (see [unifiedToolWheelEntries]). Entries fade
 * towards the bar's edges and scroll when they overflow, so a phone needs no `⋮`.
 *
 * Tapping a settings slot opens its slider / color picker in a panel that
 * animates DOWN below the bar.
 */
@Composable
fun PortraitTopBar(
    currentPage: Int,
    totalPages: Int,
    onNavigateToPage: ((Int) -> Unit)? = null,
    toolMode: ToolMode,
    onToolModeChange: (ToolMode) -> Unit,
    penSettings: PenSettings,
    onPenSettingsChange: (PenSettings) -> Unit,
    markerSettings: MarkerSettings,
    onMarkerSettingsChange: (MarkerSettings) -> Unit,
    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,
    toolPresets: StoredToolPresets,
    onToolPresetsChange: (StoredToolPresets) -> Unit,
    onPresetApplied: ((id: String) -> Unit)? = null,
    hasAnnotations: Boolean,
    isExporting: Boolean,
    onExport: () -> Unit,
    scale: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    showThumbnails: Boolean,
    onToggleThumbnails: () -> Unit,
    showTocButton: Boolean,
    showToc: Boolean,
    onToggleToc: () -> Unit,
    readingModeEnabled: Boolean,
    readingModeAvailable: Boolean,
    onToggleReadingMode: () -> Unit,
    showPencilModeButton: Boolean,
    pencilModeEnabled: Boolean,
    onPencilModeChange: (Boolean) -> Unit,
    magnifierEnabled: Boolean,
    onMagnifierToggle: () -> Unit,
    showSyncButton: Boolean,
    syncTint: Color,
    onOpenSync: () -> Unit,
    onOpenShortcutsSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Фон активной темы ридера для стекла бара; `null` — цвет [MaterialTheme]. */
    readerBackground: Color? = null,
    /** Цвет контента активной темы ридера (кнопка назад, счётчик); `null` — цвет [MaterialTheme]. */
    readerContentColor: Color? = null,
) {
    var expandedIndex by remember(toolMode) { mutableStateOf<Int?>(null) }
    // Switching directly between two slots first collapses the open panel, then
    // opens the requested one — [pendingIndex] holds the slot awaiting that
    // delayed open.
    var pendingIndex by remember(toolMode) { mutableStateOf<Int?>(null) }
    var lastExpanded by remember { mutableStateOf(0) }
    LaunchedEffect(expandedIndex) { expandedIndex?.let { lastExpanded = it } }
    LaunchedEffect(expandedIndex, pendingIndex) {
        if (expandedIndex == null && pendingIndex != null) {
            delay(PORTRAIT_PANEL_ANIM_MS.toLong())
            expandedIndex = pendingIndex
            pendingIndex = null
        }
    }
    val onSlotToggle: (Int) -> Unit = { i ->
        when {
            expandedIndex == i -> {
                expandedIndex = null
                pendingIndex = null
            }
            expandedIndex == null -> expandedIndex = i
            else -> {
                pendingIndex = i
                expandedIndex = null
            }
        }
    }

    val toolActive = toolMode != ToolMode.NONE

    Column(modifier = modifier) {
        GlassSurface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
            shape = RectangleShape,
            tint = readerBackground ?: MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PORTRAIT_BAR_PADDING_H),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = readerContentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PortraitPageCounter(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    onNavigateToPage = onNavigateToPage,
                    contentColor = readerContentColor,
                    modifier = Modifier.padding(horizontal = PORTRAIT_BAR_PAGE_PADDING),
                )
                // Инструменты, настройки, пресеты и системные кнопки — в ОДНОМ
                // горизонтальном колесе (затухание к краям). Так на телефоне всё
                // помещается без ⋮-меню — лишнее прокручивается.
                val entries =
                    unifiedToolWheelEntries(
                        orientation = RailOrientation.HORIZONTAL,
                        toolMode = toolMode,
                        onToolModeChange = onToolModeChange,
                        penSettings = penSettings,
                        onPenSettingsChange = onPenSettingsChange,
                        markerSettings = markerSettings,
                        onMarkerSettingsChange = onMarkerSettingsChange,
                        eraserSettings = eraserSettings,
                        onEraserSettingsChange = onEraserSettingsChange,
                        toolPresets = toolPresets,
                        onToolPresetsChange = onToolPresetsChange,
                        onPresetApplied = onPresetApplied,
                        expandedIndex = expandedIndex,
                        onSlotToggle = onSlotToggle,
                        hasAnnotations = hasAnnotations,
                        isExporting = isExporting,
                        onExport = onExport,
                        scale = scale,
                        onZoomIn = onZoomIn,
                        onZoomOut = onZoomOut,
                        showThumbnails = showThumbnails,
                        onToggleThumbnails = onToggleThumbnails,
                        showTocButton = showTocButton,
                        showToc = showToc,
                        onToggleToc = onToggleToc,
                        readingModeEnabled = readingModeEnabled,
                        readingModeAvailable = readingModeAvailable,
                        onToggleReadingMode = onToggleReadingMode,
                        showPencilModeButton = showPencilModeButton,
                        pencilModeEnabled = pencilModeEnabled,
                        onPencilModeChange = onPencilModeChange,
                        magnifierEnabled = magnifierEnabled,
                        onMagnifierToggle = onMagnifierToggle,
                        showSyncButton = showSyncButton,
                        syncTint = syncTint,
                        onOpenSync = onOpenSync,
                        onOpenShortcutsSettings = onOpenShortcutsSettings,
                    )
                // `weight` снимаем в RowScope; [MaterialTheme] не создаёт layout-узел,
                // поэтому вес доходит до Row и сквозь обёртку перекраски. Ширину колеса
                // держим стабильной (вес), а его содержимое прижимаем к правому краю
                // через contentAlignment — иначе обёртка по содержимому анимировала бы
                // ширину и дёргала затухание крайних иконок при скролле PDF.
                val wheelModifier = Modifier.weight(1f)
                MaterialTheme(
                    colorScheme =
                        railSelectionColorScheme(
                            MaterialTheme.colorScheme,
                            readerContentColor,
                            readerBackground,
                        ),
                ) {
                    WheelStrip(
                        entries = entries,
                        orientation = RailOrientation.HORIZONTAL,
                        modifier = wheelModifier,
                        selectedKey = selectedToolWheelKey(toolMode),
                        contentAlignment = Alignment.CenterEnd,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = toolActive && expandedIndex != null,
            enter =
                expandVertically(animationSpec = tween(PORTRAIT_PANEL_ANIM_MS), expandFrom = Alignment.Top) +
                    fadeIn(animationSpec = tween(PORTRAIT_PANEL_ANIM_MS)),
            exit =
                shrinkVertically(animationSpec = tween(PORTRAIT_PANEL_ANIM_MS), shrinkTowards = Alignment.Top) +
                    fadeOut(animationSpec = tween(PORTRAIT_PANEL_ANIM_MS)),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = PORTRAIT_EXPANSION_TOP_PADDING),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(end = PORTRAIT_BAR_PADDING_H),
                horizontalArrangement = Arrangement.End,
            ) {
                GlassSurface(tint = MaterialTheme.colorScheme.secondaryContainer) {
                    ToolSettingsExpansionContent(
                        toolMode = toolMode,
                        penSettings = penSettings,
                        onPenSettingsChange = onPenSettingsChange,
                        markerSettings = markerSettings,
                        onMarkerSettingsChange = onMarkerSettingsChange,
                        eraserSettings = eraserSettings,
                        onEraserSettingsChange = onEraserSettingsChange,
                        orientation = RailOrientation.HORIZONTAL,
                        index = lastExpanded,
                    )
                }
            }
        }
    }
}

/**
 * Page counter widget for the portrait top bar. Shows "$currentPage / $totalPages".
 * When [onNavigateToPage] is provided, the current page number is tappable and
 * enters an inline edit mode on click.
 *
 * @param onNavigateToPage Called with a 0-based page index on confirmation.
 */
@Composable
private fun PortraitPageCounter(
    currentPage: Int,
    totalPages: Int,
    onNavigateToPage: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    contentColor: Color? = null,
) {
    val labelColor = contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (onNavigateToPage != null) {
            var editing by remember { mutableStateOf(false) }
            var fieldValue by remember { mutableStateOf(TextFieldValue(currentPage.toString())) }
            val focusRequester = remember { FocusRequester() }
            var everFocused by remember { mutableStateOf(false) }

            fun confirm() {
                val target = fieldValue.text.trim().toIntOrNull()
                if (target != null && target in 1..totalPages) onNavigateToPage(target - 1)
                editing = false
                everFocused = false
            }

            fun cancel() {
                editing = false
                everFocused = false
            }

            if (editing) {
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Box(modifier = Modifier.width(IntrinsicSize.Min)) {
                    Text(
                        text = "8".repeat(totalPages.toString().length),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.alpha(0f),
                    )
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { new ->
                            if (new.text.all { it.isDigit() } && new.text.length <= totalPages.toString().length) {
                                fieldValue = new
                            }
                        },
                        textStyle =
                            MaterialTheme.typography.labelLarge.copy(
                                color = labelColor,
                            ),
                        cursorBrush = SolidColor(labelColor),
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Go,
                            ),
                        keyboardActions = KeyboardActions(onGo = { confirm() }),
                        modifier =
                            Modifier
                                .matchParentSize()
                                .focusRequester(focusRequester)
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        everFocused = true
                                    } else if (everFocused) {
                                        cancel()
                                    }
                                }.onKeyEvent { event ->
                                    if (event.key == Key.Escape) {
                                        cancel()
                                        true
                                    } else {
                                        false
                                    }
                                },
                    )
                }
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "8".repeat(totalPages.toString().length),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.alpha(0f),
                    )
                    Text(
                        text = currentPage.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = labelColor,
                        modifier =
                            Modifier.clickable {
                                fieldValue =
                                    TextFieldValue(
                                        text = currentPage.toString(),
                                        selection = TextRange(0, currentPage.toString().length),
                                    )
                                editing = true
                            },
                    )
                }
            }
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "8".repeat(totalPages.toString().length),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.alpha(0f),
                )
                Text(
                    text = currentPage.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = labelColor,
                )
            }
        }
        Text(
            text = " / $totalPages",
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
        )
    }
}

/** Open/close duration of the expansion panel; the slot-switch delay matches it. */
private const val PORTRAIT_PANEL_ANIM_MS = 180

private val PORTRAIT_BAR_PADDING_H = 4.dp
private val PORTRAIT_BAR_PAGE_PADDING = 8.dp
private val PORTRAIT_EXPANSION_TOP_PADDING = 8.dp
