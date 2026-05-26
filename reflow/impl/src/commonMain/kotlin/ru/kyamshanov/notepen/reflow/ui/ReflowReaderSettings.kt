package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kyamshanov.notepen.reflow.api.PageTransition
import ru.kyamshanov.notepen.reflow.api.ProgressFormat
import ru.kyamshanov.notepen.reflow.api.ReaderAlign
import ru.kyamshanov.notepen.reflow.api.ReaderFontFamily
import ru.kyamshanov.notepen.reflow.api.ReaderSettings
import ru.kyamshanov.notepen.reflow.api.ReaderTheme

/**
 * Compose-типизированная модель отображения reflow-ридера, в которую слой UI
 * разворачивает сериализуемые [ReaderSettings] (см. [toRenderSettings]). Цвета,
 * размеры и шрифт здесь уже резолвлены из темы/пресета — рендеру остаётся только
 * применить их.
 *
 * @property fontFamily семейство шрифта основного текста
 * @property fontSize кегль основного текста (в `sp`)
 * @property lineHeightMultiplier межстрочный интервал как множитель кегля
 * @property columnChars целевая длина строки в символах (ширину колонки рендер
 *   считает из неё и кегля)
 * @property contentPadding поля колонки от краёв
 * @property blockSpacing вертикальный зазор между блоками (производный от кегля)
 * @property align выравнивание абзацев
 * @property hyphenation переносы слов
 * @property letterSpacing межбуквенный интервал
 * @property wordSpacing межсловный интервал (рендерится как доп. трекинг пробелов)
 * @property theme базовая палитра (для подсветки выбора в панели)
 * @property background цвет фона (тема + статичная теплота)
 * @property textColor цвет текста
 * @property backgroundWarmth теплота фона `0..1` (уже учтена в [background]; хранится для UI)
 * @property brightness внутренняя яркость `0..1` (рендер затемняет оверлеем при `< 1`)
 * @property sunsetWarm плавное потепление после захода солнца
 * @property highlightColor цвет подсветки выделений
 * @property codeBackground фон inline-кода
 * @property paged страничный режим вместо скролла
 * @property pageTransition стиль перехода между страницами (только в страничном режиме)
 * @property tapToTurn перелистывание тапом по краям (тап-зоны лево/право)
 * @property autoHideMs автоскрытие панелей через N мс (0 — не скрывать)
 * @property progress формат индикатора прогресса
 * @property readingRuler подсветка текущей строки
 * @property bionic выделение первых букв слов
 * @property ergonomicsEnabled забота о глазах по времени сессии (20-20-20, затемнение, ритм)
 */
public data class ReflowReaderSettings(
    public val fontFamily: ReaderFontFamily = ReaderFontFamily.SYSTEM_SERIF,
    public val fontSize: TextUnit = 19.sp,
    public val lineHeightMultiplier: Float = 1.6f,
    public val columnChars: Int = 66,
    public val contentPadding: Dp = 24.dp,
    public val blockSpacing: Dp = 14.dp,
    public val align: ReaderAlign = ReaderAlign.START,
    public val hyphenation: Boolean = false,
    public val letterSpacing: TextUnit = 0.sp,
    public val wordSpacing: TextUnit = 0.sp,
    public val theme: ReaderTheme = ReaderTheme.PAPER,
    public val background: Color = PAPER_BACKGROUND,
    public val textColor: Color = PAPER_TEXT,
    public val backgroundWarmth: Float = 0f,
    public val brightness: Float = 1f,
    public val sunsetWarm: Boolean = false,
    public val highlightColor: Color = Color(0x59FFD24D),
    public val codeBackground: Color = Color(0x14000000),
    public val paged: Boolean = false,
    public val pageTransition: PageTransition = PageTransition.SLIDE,
    public val tapToTurn: Boolean = true,
    public val autoHideMs: Long = 0L,
    public val progress: ProgressFormat = ProgressFormat.PERCENT,
    public val readingRuler: Boolean = false,
    public val bionic: Boolean = false,
    public val ergonomicsEnabled: Boolean = true,
)

/** Палитра одной темы: уже подобранные сочетающиеся цвета. */
private data class ReaderPalette(
    val background: Color,
    val text: Color,
    val code: Color,
    val highlight: Color,
)

private fun paletteOf(theme: ReaderTheme): ReaderPalette =
    when (theme) {
        ReaderTheme.PAPER -> ReaderPalette(Color(0xFFFAF6EF), Color(0xFF2A2A2A), Color(0x14000000), Color(0x59FFD24D))
        ReaderTheme.SEPIA -> ReaderPalette(Color(0xFFF4ECD8), Color(0xFF433422), Color(0x14000000), Color(0x59E0A84D))
        ReaderTheme.GRAY -> ReaderPalette(Color(0xFFE9E9E6), Color(0xFF33332F), Color(0x12000000), Color(0x59FFD24D))
        ReaderTheme.NIGHT -> ReaderPalette(Color(0xFF16161A), Color(0xFFC9C7C0), Color(0x1AFFFFFF), Color(0x4DFFC255))
        ReaderTheme.BRIGHT -> ReaderPalette(Color(0xFFFFFFFF), Color(0xFF111111), Color(0x14000000), Color(0x66FFD24D))
    }

/**
 * Сдвигает цвет в тёплую сторону (больше красного, меньше синего) на долю
 * [amount] `0..1`. Шаг намеренно мягкий — теплота должна успокаивать, а не красить.
 */
internal fun warmShift(
    color: Color,
    amount: Float,
): Color {
    val a = amount.coerceIn(0f, 1f)
    if (a == 0f) return color
    val shift = a * MAX_WARM_SHIFT
    return Color(
        red = (color.red + shift).coerceAtMost(1f),
        green = color.green,
        blue = (color.blue - shift).coerceAtLeast(0f),
        alpha = color.alpha,
    )
}

/**
 * Разворачивает сериализуемые [ReaderSettings] в Compose-модель отображения:
 * резолвит палитру темы, применяет статичную теплоту к фону и переводит примитивы
 * в Compose-единицы. Числовые поля предварительно зажимаются [ReaderSettings.coerced].
 */
public fun ReaderSettings.toRenderSettings(): ReflowReaderSettings {
    val s = coerced()
    val palette = paletteOf(s.theme)
    return ReflowReaderSettings(
        fontFamily = s.fontFamily,
        fontSize = s.fontSizeSp.sp,
        lineHeightMultiplier = s.lineHeight,
        columnChars = s.columnChars,
        contentPadding = s.marginDp.dp,
        blockSpacing = (s.fontSizeSp * BLOCK_SPACING_RATIO).dp,
        align = s.align,
        hyphenation = s.hyphenation,
        letterSpacing = s.letterSpacingSp.sp,
        wordSpacing = s.wordSpacingSp.sp,
        theme = s.theme,
        background = warmShift(palette.background, s.backgroundWarmth),
        textColor = palette.text,
        backgroundWarmth = s.backgroundWarmth,
        brightness = s.brightness,
        sunsetWarm = s.sunsetWarm,
        highlightColor = palette.highlight,
        codeBackground = palette.code,
        paged = s.paged,
        pageTransition = s.pageTransition,
        tapToTurn = s.tapToTurn,
        autoHideMs = s.autoHideSec * 1000L,
        progress = s.progress,
        readingRuler = s.readingRuler,
        bionic = s.bionic,
        ergonomicsEnabled = s.ergonomics,
    )
}

/**
 * Максимальная ширина текстовой колонки, выведенная из целевой длины строки в
 * символах и кегля (комфортная зона — 50–90 знаков). Рендер ограничивает ею
 * колонку, поэтому при крупном шрифте/узкой колонке текст не растягивается на
 * весь экран.
 */
public val ReflowReaderSettings.maxContentWidth: Dp
    get() = columnWidthValue(columnChars, fontSize.value).dp

/**
 * Чистый расчёт ширины колонки (в тех же единицах, что и кегль) под [columnChars]
 * знаков при кегле [fontSizeValue]: средний шаг глифа пропорционального шрифта
 * ≈ [CHAR_ADVANCE_RATIO] кегля. Вынесено для unit-тестов.
 */
internal fun columnWidthValue(
    columnChars: Int,
    fontSizeValue: Float,
): Float = columnChars * CHAR_ADVANCE_RATIO * fontSizeValue

/** Цвет фона темы PAPER — дефолт data class (совпадает с [paletteOf]). */
internal val PAPER_BACKGROUND: Color = Color(0xFFFAF6EF)

/** Цвет текста темы PAPER — дефолт data class. */
internal val PAPER_TEXT: Color = Color(0xFF2A2A2A)

private const val MAX_WARM_SHIFT = 0.10f
private const val BLOCK_SPACING_RATIO = 0.73f
private const val CHAR_ADVANCE_RATIO = 0.5f
