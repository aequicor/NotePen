package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import ru.kyamshanov.notepen.reflow.api.BuiltinReaderPresets
import ru.kyamshanov.notepen.reflow.api.ProgressFormat
import ru.kyamshanov.notepen.reflow.api.ReaderAlign
import ru.kyamshanov.notepen.reflow.api.ReaderFontFamily
import ru.kyamshanov.notepen.reflow.api.ReaderPreset
import ru.kyamshanov.notepen.reflow.api.ReaderSettings
import ru.kyamshanov.notepen.reflow.api.ReaderSettingsReducer
import ru.kyamshanov.notepen.reflow.api.ReaderTheme
import ru.kyamshanov.notepen.reflow.api.StoredReaderSettings
import ru.kyamshanov.notepen.wheelItem
import kotlin.math.roundToInt

/**
 * Нижний «airbar» ридера по центру: пресеты на верхнем уровне, тонкие ползунки —
 * за кнопкой «Настроить». Плавающая «таблетка» поверх текста, окрашенная под
 * текущую тему ([background]/[textColor]), чтобы не спорить с чтением.
 *
 * Все изменения настроек уходят наружу через [onStoredChange] (чистые переходы
 * [ReaderSettingsReducer]); сам компонент состояния настроек не держит — только
 * локальный флаг «развёрнут/свёрнут».
 *
 * @param stored текущее персистентное состояние (настройки + пользовательские пресеты)
 * @param onStoredChange применить новое состояние
 * @param newPresetIdProvider источник id для нового кастомного пресета при первой
 *   правке встроенного — поднят наружу, чтобы переходы редьюсера оставались чистыми
 *   (модуль `reflow/impl` не зависит от генератора UUID)
 * @param background фон-основа таблетки (из темы ридера)
 * @param textColor цвет текста/контролов (из темы ридера)
 * @param progressLabel готовая строка индикатора прогресса, либо `null`
 * @param autoHideMs автоскрытие через N мс простоя (0 — не скрывать)
 * @param onRequestHide попросить скрыть весь airbar (тап-скрытие/автоскрытие)
 */
@Composable
internal fun ReaderAirbar(
    stored: StoredReaderSettings,
    onStoredChange: (StoredReaderSettings) -> Unit,
    newPresetIdProvider: () -> String,
    background: Color,
    textColor: Color,
    progressLabel: String?,
    autoHideMs: Long,
    onRequestHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Маркер активности «таблетки»: любое касание контрола сбрасывает таймер
    // автоскрытия. Меняем значение — перезапускаем эффект delay ниже.
    var interactionTick by remember { mutableStateOf(0) }
    val touched = { interactionTick++ }

    // Автоскрытие: только когда панель свёрнута (развёрнутую трогает пользователь).
    if (autoHideMs > 0L && !expanded) {
        LaunchedEffect(autoHideMs, interactionTick) {
            delay(autoHideMs)
            onRequestHide()
        }
    }

    val current = stored.current

    fun emit(next: ReaderSettings) {
        touched()
        onStoredChange(
            ReaderSettingsReducer.editActive(
                stored = stored,
                settings = next,
                newPresetId = newPresetIdProvider(),
                defaultName = MY_PRESET_NAME,
            ),
        )
    }

    // Таблетка консьюмит тапы, чтобы тап по ней не сворачивал airbar (тап по
    // тексту ридера обрабатывается родителем).
    Column(
        modifier =
            modifier
                .padding(horizontal = 12.dp, vertical = AIRBAR_BOTTOM_PADDING)
                .wrapContentWidth()
                .pointerInput(Unit) { detectTapGestures { touched() } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (expanded) {
            TuneSheet(
                settings = current,
                onChange = ::emit,
                background = background,
                textColor = textColor,
            )
            Spacer(Modifier.height(8.dp))
        }
        CollapsedPill(
            stored = stored,
            background = background,
            textColor = textColor,
            progressLabel = progressLabel,
            expanded = expanded,
            onToggleExpanded = {
                touched()
                expanded = !expanded
            },
            onApplyPreset = { preset ->
                touched()
                onStoredChange(ReaderSettingsReducer.applyPreset(stored, preset))
            },
            onDeletePreset = { id ->
                touched()
                onStoredChange(ReaderSettingsReducer.deletePreset(stored, id))
            },
        )
    }
}

/** Базовое имя кастомного пресета, создаваемого первой ручной правкой. */
private const val MY_PRESET_NAME = "Моё"

/**
 * Элемент колеса пресетов: либо пресет (встроенный/кастомный), либо тюнер
 * «⚙ Настроить», открывающий [TuneSheet]. Вынесено отдельным типом, чтобы порядок
 * элементов (кастомные первыми) строился чистой функцией [readerWheelElements] и
 * покрывался unit-тестом без Compose.
 */
internal sealed interface ReaderWheelElement {
    /** Стабильный ключ для `LazyRow` (см. [androidx.compose.foundation.lazy.LazyListScope.items]). */
    val key: String

    /**
     * Пресет ридера. [deletable] — только для кастомных: их можно удалить долгим
     * тапом; встроенные неудаляемы.
     */
    data class Preset(
        val preset: ReaderPreset,
        val deletable: Boolean,
    ) : ReaderWheelElement {
        override val key: String get() = preset.id
    }

    /** Тюнер: тап раскрывает/сворачивает панель тонких настроек. */
    data object Tuner : ReaderWheelElement {
        override val key: String get() = "reader-wheel:tuner"
    }
}

/**
 * Строит порядок элементов колеса: сначала кастомные пресеты
 * ([StoredReaderSettings.userPresets], они же удаляемы), затем встроенные
 * ([BuiltinReaderPresets]), в конце — тюнер. Чистая функция (без Compose) —
 * проверяется в `ReaderWheelTest`.
 */
internal fun readerWheelElements(stored: StoredReaderSettings): List<ReaderWheelElement> =
    buildList {
        stored.userPresets.forEach { add(ReaderWheelElement.Preset(it, deletable = true)) }
        BuiltinReaderPresets.all.forEach { add(ReaderWheelElement.Preset(it, deletable = false)) }
        add(ReaderWheelElement.Tuner)
    }

/**
 * Свёрнутая «таблетка»: лидирующий прогресс + одно колесо-«барабан» из пресетов и
 * тюнера. Кастомные пресеты идут первыми и удаляются долгим тапом; тюнер —
 * последний элемент колеса (тап раскрывает [TuneSheet]).
 */
@Composable
private fun CollapsedPill(
    stored: StoredReaderSettings,
    background: Color,
    textColor: Color,
    progressLabel: String?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onApplyPreset: (ReaderPreset) -> Unit,
    onDeletePreset: (String) -> Unit,
) {
    val elements = readerWheelElements(stored)
    val listState = rememberLazyListState()
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(AIRBAR_RADIUS))
                .background(background.copy(alpha = AIRBAR_ALPHA))
                .border(1.dp, textColor.copy(alpha = 0.08f), RoundedCornerShape(AIRBAR_RADIUS))
                .widthIn(max = AIRBAR_MAX_WIDTH)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (progressLabel != null) {
            BasicText(
                text = progressLabel,
                modifier = Modifier.padding(start = 4.dp, end = 2.dp),
                style = TextStyle(color = textColor.copy(alpha = 0.7f), fontSize = 12.sp),
            )
        }
        LazyRow(
            modifier = Modifier.weight(1f, fill = false),
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            itemsIndexed(elements, key = { _, e -> e.key }) { index, element ->
                Box(Modifier.wheelItem(listState, index)) {
                    when (element) {
                        is ReaderWheelElement.Preset ->
                            PresetWheelChip(
                                preset = element.preset,
                                selected = stored.activePresetId == element.preset.id,
                                deletable = element.deletable,
                                textColor = textColor,
                                background = background,
                                onApply = { onApplyPreset(element.preset) },
                                onDelete = { onDeletePreset(element.preset.id) },
                            )

                        ReaderWheelElement.Tuner ->
                            Chip(
                                label = TUNER_LABEL,
                                selected = expanded,
                                textColor = textColor,
                                onClick = onToggleExpanded,
                            )
                    }
                }
            }
        }
    }
}

/**
 * Чип пресета в колесе. Кастомные ([deletable]) открывают долгим тапом лёгкий
 * поповер «Удалить» (без material3 — собственное меню в стиле airbar).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetWheelChip(
    preset: ReaderPreset,
    selected: Boolean,
    deletable: Boolean,
    textColor: Color,
    background: Color,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(AIRBAR_CHIP_RADIUS))
                    .background(textColor.copy(alpha = if (selected) 0.18f else 0.06f))
                    .combinedClickable(
                        onClick = onApply,
                        onLongClick = if (deletable) ({ menuOpen = true }) else null,
                    ).padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = preset.name,
                maxLines = 1,
                style =
                    TextStyle(
                        color = textColor.copy(alpha = if (selected) 1f else 0.65f),
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
            )
        }
        if (menuOpen) {
            DeletePopover(
                background = background,
                textColor = textColor,
                onDismiss = { menuOpen = false },
                onConfirm = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

/**
 * Лёгкий поповер удаления поверх чипа: одна строка «Удалить» на фоне-таблетке.
 * Реализован на foundation ([Popup]) вместо material3 `DropdownMenu`, чтобы не
 * тянуть material3 в `reflow/impl`.
 */
@Composable
private fun DeletePopover(
    background: Color,
    textColor: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, -POPOVER_OFFSET_PX),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(AIRBAR_CHIP_RADIUS))
                    .background(background.copy(alpha = AIRBAR_SHEET_ALPHA))
                    .border(1.dp, textColor.copy(alpha = 0.12f), RoundedCornerShape(AIRBAR_CHIP_RADIUS))
                    .clickable(onClick = onConfirm)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = DELETE_LABEL,
                maxLines = 1,
                style = TextStyle(color = textColor.copy(alpha = 0.9f), fontSize = 13.sp),
            )
        }
    }
}

/** Развёрнутая панель тонких настроек: пять групп, вертикальная прокрутка. */
@Composable
private fun TuneSheet(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    background: Color,
    textColor: Color,
) {
    Column(
        modifier =
            Modifier
                .clip(RoundedCornerShape(AIRBAR_RADIUS))
                .background(background.copy(alpha = AIRBAR_SHEET_ALPHA))
                .border(1.dp, textColor.copy(alpha = 0.08f), RoundedCornerShape(AIRBAR_RADIUS))
                .widthIn(max = AIRBAR_MAX_WIDTH)
                .heightIn(max = AIRBAR_SHEET_MAX_HEIGHT)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Group("Типографика", textColor) {
            LabeledChoice(
                label = "Шрифт",
                options = ReaderFontFamily.entries,
                selected = settings.fontFamily,
                labelOf = ::fontFamilyName,
                textColor = textColor,
                onSelect = { onChange(settings.copy(fontFamily = it)) },
            )
            LabeledSlider(
                label = "Кегль",
                value = settings.fontSizeSp,
                range = ReaderSettings.MIN_FONT_SP..ReaderSettings.MAX_FONT_SP,
                valueText = "${settings.fontSizeSp.roundToInt()} sp",
                textColor = textColor,
                onChange = { onChange(settings.copy(fontSizeSp = it)) },
            )
        }
        Group("Воздух", textColor) {
            LabeledSlider(
                label = "Межстрочный",
                value = settings.lineHeight,
                range = ReaderSettings.MIN_LINE_HEIGHT..ReaderSettings.MAX_LINE_HEIGHT,
                valueText = formatOneDecimal(settings.lineHeight),
                textColor = textColor,
                onChange = { onChange(settings.copy(lineHeight = it)) },
            )
            LabeledSlider(
                label = "Ширина строки",
                value = settings.columnChars.toFloat(),
                range = ReaderSettings.MIN_COLUMN_CHARS.toFloat()..ReaderSettings.MAX_COLUMN_CHARS.toFloat(),
                valueText = "${settings.columnChars} зн.",
                textColor = textColor,
                onChange = { onChange(settings.copy(columnChars = it.roundToInt())) },
            )
            LabeledSlider(
                label = "Поля",
                value = settings.marginDp,
                range = ReaderSettings.MIN_MARGIN_DP..ReaderSettings.MAX_MARGIN_DP,
                valueText = "${settings.marginDp.roundToInt()}",
                textColor = textColor,
                onChange = { onChange(settings.copy(marginDp = it)) },
            )
            LabeledChoice(
                label = "Выравнивание",
                options = ReaderAlign.entries,
                selected = settings.align,
                labelOf = ::alignName,
                textColor = textColor,
                onSelect = { onChange(settings.copy(align = it)) },
            )
            ToggleRow(
                label = "Переносы слов",
                checked = settings.hyphenation,
                textColor = textColor,
                onChange = { onChange(settings.copy(hyphenation = it)) },
            )
        }
        Group("Цвет", textColor) {
            LabeledChoice(
                label = "Тема",
                options = ReaderTheme.entries,
                selected = settings.theme,
                labelOf = ::themeName,
                textColor = textColor,
                onSelect = { onChange(settings.copy(theme = it)) },
            )
            LabeledSlider(
                label = "Теплота фона",
                value = settings.backgroundWarmth,
                range = 0f..1f,
                valueText = percentText(settings.backgroundWarmth),
                textColor = textColor,
                onChange = { onChange(settings.copy(backgroundWarmth = it)) },
            )
            LabeledSlider(
                label = "Яркость",
                value = settings.brightness,
                range = ReaderSettings.MIN_BRIGHTNESS..1f,
                valueText = percentText(settings.brightness),
                textColor = textColor,
                onChange = { onChange(settings.copy(brightness = it)) },
            )
            ToggleRow(
                label = "Теплеть после заката",
                checked = settings.sunsetWarm,
                textColor = textColor,
                onChange = { onChange(settings.copy(sunsetWarm = it)) },
            )
        }
        Group("Поведение", textColor) {
            LabeledChoice(
                label = "Режим",
                options = listOf(false, true),
                selected = settings.paged,
                labelOf = { if (it) "Страницы" else "Скролл" },
                textColor = textColor,
                onSelect = { onChange(settings.copy(paged = it)) },
            )
            LabeledSlider(
                label = "Автоскрытие панели",
                value = settings.autoHideSec.toFloat(),
                range = 0f..ReaderSettings.MAX_AUTO_HIDE_SEC.toFloat(),
                valueText = if (settings.autoHideSec == 0) "выкл" else "${settings.autoHideSec} с",
                textColor = textColor,
                onChange = { onChange(settings.copy(autoHideSec = it.roundToInt())) },
            )
            LabeledChoice(
                label = "Прогресс",
                options = ProgressFormat.entries,
                selected = settings.progress,
                labelOf = ::progressName,
                textColor = textColor,
                onSelect = { onChange(settings.copy(progress = it)) },
            )
            ToggleRow(
                label = "Забота о глазах (20-20-20)",
                checked = settings.ergonomics,
                textColor = textColor,
                onChange = { onChange(settings.copy(ergonomics = it)) },
            )
        }
        Group("Доступность", textColor) {
            LabeledSlider(
                label = "Межбуквенный",
                value = settings.letterSpacingSp,
                range = ReaderSettings.MIN_LETTER_SPACING_SP..ReaderSettings.MAX_LETTER_SPACING_SP,
                valueText = formatOneDecimal(settings.letterSpacingSp),
                textColor = textColor,
                onChange = { onChange(settings.copy(letterSpacingSp = it)) },
            )
            LabeledSlider(
                label = "Межсловный",
                value = settings.wordSpacingSp,
                range = ReaderSettings.MIN_WORD_SPACING_SP..ReaderSettings.MAX_WORD_SPACING_SP,
                valueText = formatOneDecimal(settings.wordSpacingSp),
                textColor = textColor,
                onChange = { onChange(settings.copy(wordSpacingSp = it)) },
            )
            ToggleRow(
                label = "Подсветка строки",
                checked = settings.readingRuler,
                textColor = textColor,
                onChange = { onChange(settings.copy(readingRuler = it)) },
            )
            ToggleRow(
                label = "Bionic (жирные начала слов)",
                checked = settings.bionic,
                textColor = textColor,
                onChange = { onChange(settings.copy(bionic = it)) },
            )
        }
    }
}

@Composable
private fun Group(
    title: String,
    textColor: Color,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(
            text = title.uppercase(),
            style =
                TextStyle(
                    color = textColor.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                ),
        )
        content()
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    textColor: Color,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(label, style = TextStyle(color = textColor.copy(alpha = 0.85f), fontSize = 13.sp))
            BasicText(valueText, style = TextStyle(color = textColor.copy(alpha = 0.55f), fontSize = 13.sp))
        }
        ReaderSlider(value = value, range = range, tint = textColor, onChange = onChange)
    }
}

/** Минималистичный ползунок на foundation: трек + заполнение + кружок-«ползунок». */
@Composable
private fun ReaderSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    tint: Color,
    onChange: (Float) -> Unit,
) {
    var trackWidth by remember { mutableStateOf(1) }
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    val thumbHalfPx = with(LocalDensity.current) { (SLIDER_THUMB / 2).toPx() }

    val setFromX: (Float) -> Unit = { x ->
        val frac = (x / trackWidth).coerceIn(0f, 1f)
        onChange(range.start + frac * span)
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(SLIDER_HEIGHT)
                .onSizeChanged { trackWidth = it.width.coerceAtLeast(1) }
                .pointerInput(Unit) { detectTapGestures { setFromX(it.x) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        setFromX(change.position.x)
                        change.consume()
                    }
                },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(SLIDER_TRACK)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f)),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(SLIDER_TRACK)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.5f)),
        )
        Box(
            Modifier
                .offsetX { (fraction * trackWidth - thumbHalfPx).roundToInt() }
                .size(SLIDER_THUMB)
                .clip(CircleShape)
                .background(tint),
        )
    }
}

/** Горизонтальный сдвиг по X в пикселях (тонкий помощник для «ползунка»). */
private fun Modifier.offsetX(x: () -> Int): Modifier =
    this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(x(), 0)
            }
        },
    )

@Composable
private fun <T> LabeledChoice(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    textColor: Color,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(label, style = TextStyle(color = textColor.copy(alpha = 0.85f), fontSize = 13.sp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                Chip(
                    label = labelOf(option),
                    selected = option == selected,
                    textColor = textColor,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    textColor: Color,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onChange(!checked) }
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(label, style = TextStyle(color = textColor.copy(alpha = 0.85f), fontSize = 13.sp))
        ReaderSwitch(checked = checked, tint = textColor)
    }
}

/** Компактный переключатель на foundation: дорожка + кружок, без Material. */
@Composable
private fun ReaderSwitch(
    checked: Boolean,
    tint: Color,
) {
    Box(
        modifier =
            Modifier
                .width(SWITCH_WIDTH)
                .height(SWITCH_HEIGHT)
                .clip(CircleShape)
                .background(tint.copy(alpha = if (checked) 0.45f else 0.14f))
                .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(SWITCH_THUMB)
                .clip(CircleShape)
                .background(if (checked) tint else tint.copy(alpha = 0.6f)),
        )
    }
}

/** Чип-кнопка: ярче и полужирнее в выбранном состоянии. */
@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    textColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(AIRBAR_CHIP_RADIUS))
                .background(textColor.copy(alpha = if (selected) 0.18f else 0.06f))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            maxLines = 1,
            style =
                TextStyle(
                    color = textColor.copy(alpha = if (selected) 1f else 0.65f),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
        )
    }
}

private fun fontFamilyName(family: ReaderFontFamily): String =
    when (family) {
        ReaderFontFamily.SYSTEM_SERIF -> "Сериф"
        ReaderFontFamily.SYSTEM_SANS -> "Гротеск"
        ReaderFontFamily.SYSTEM_MONO -> "Моно"
        ReaderFontFamily.CHARTER -> "Charter"
        ReaderFontFamily.SOURCE_SERIF -> "Source Serif"
        ReaderFontFamily.LORA -> "Lora"
        ReaderFontFamily.LITERATA -> "Literata"
        ReaderFontFamily.INTER -> "Inter"
        ReaderFontFamily.IBM_PLEX_SANS -> "IBM Plex"
        ReaderFontFamily.OPEN_DYSLEXIC -> "Дислексия"
    }

private fun themeName(theme: ReaderTheme): String =
    when (theme) {
        ReaderTheme.PAPER -> "Бумага"
        ReaderTheme.SEPIA -> "Сепия"
        ReaderTheme.GRAY -> "Серый"
        ReaderTheme.NIGHT -> "Ночь"
        ReaderTheme.BRIGHT -> "Яркий"
    }

private fun alignName(align: ReaderAlign): String =
    when (align) {
        ReaderAlign.START -> "По левому"
        ReaderAlign.JUSTIFY -> "По ширине"
    }

private fun progressName(format: ProgressFormat): String =
    when (format) {
        ProgressFormat.NONE -> "Нет"
        ProgressFormat.PERCENT -> "Процент"
        ProgressFormat.CHAPTER -> "Глава"
        ProgressFormat.TIME_LEFT -> "Время"
    }

private fun percentText(value: Float): String = "${(value * 100).roundToInt()}%"

private fun formatOneDecimal(value: Float): String {
    val scaled = (value * 10).roundToInt()
    return "${scaled / 10}.${scaled % 10}"
}

private val AIRBAR_RADIUS = 22.dp
private val AIRBAR_CHIP_RADIUS = 16.dp
private val AIRBAR_MAX_WIDTH = 560.dp
private val AIRBAR_SHEET_MAX_HEIGHT = 380.dp
private val AIRBAR_BOTTOM_PADDING = 16.dp
private const val AIRBAR_ALPHA = 0.92f
private const val AIRBAR_SHEET_ALPHA = 0.96f

/** Подпись тюнера в колесе — открывает/сворачивает панель тонких настроек. */
private const val TUNER_LABEL = "Настроить"

/** Подпись действия удаления кастомного пресета в поповере. */
private const val DELETE_LABEL = "Удалить"

/** На сколько px приподнять поповер «Удалить» над чипом (airbar внизу — растём вверх). */
private const val POPOVER_OFFSET_PX = 96

private val SLIDER_HEIGHT = 28.dp
private val SLIDER_TRACK = 4.dp
private val SLIDER_THUMB = 18.dp

private val SWITCH_WIDTH = 40.dp
private val SWITCH_HEIGHT = 24.dp
private val SWITCH_THUMB = 18.dp
