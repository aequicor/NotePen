package ru.kyamshanov.notepen.reflow.api

import kotlinx.serialization.Serializable

/**
 * Семейство шрифта основного текста ридера.
 *
 * Куратированный, намеренно короткий список (паралич выбора вреднее, чем
 * отсутствие любимого шрифта): несколько системных «логических» семейств,
 * горстка засечных/гротесковых для бандла и отдельный шрифт для дислексии.
 *
 * Семейства с [bundled] `= true` подтягиваются из ресурсов приложения
 * (`composeResources/font`); если бинарь шрифта не вложен — слой отображения
 * откатывается к ближайшему системному семейству, поэтому сборка не ломается.
 *
 * @property bundled требует вложенного бинаря шрифта (иначе — системный фолбэк)
 * @property dyslexic шрифт, оптимизированный для дислексии
 */
@Serializable
public enum class ReaderFontFamily(
    public val bundled: Boolean,
    public val dyslexic: Boolean = false,
) {
    /** Системный засечный (Serif) — дефолт: засечки ведут глаз по строке. */
    SYSTEM_SERIF(bundled = false),

    /** Системный гротеск (Sans-serif). */
    SYSTEM_SANS(bundled = false),

    /** Системный моноширинный. */
    SYSTEM_MONO(bundled = false),

    /** Charter — экранно-дружелюбный засечный (бандл). */
    CHARTER(bundled = true),

    /** Source Serif — нейтральный засечный Adobe/Google (бандл, OFL). */
    SOURCE_SERIF(bundled = true),

    /** Lora — каллиграфичный засечный (бандл, OFL). */
    LORA(bundled = true),

    /** Literata — книжный засечный (бандл, OFL); свободная замена Iowan Old Style. */
    LITERATA(bundled = true),

    /** Inter — гуманистический гротеск (бандл, OFL). */
    INTER(bundled = true),

    /** IBM Plex Sans — гротеск (бандл, OFL). */
    IBM_PLEX_SANS(bundled = true),

    /** OpenDyslexic — шрифт с утяжелённым низом для дислексии (бандл, OFL). */
    OPEN_DYSLEXIC(bundled = true, dyslexic = true),
}

/** Базовая палитра темы ридера; конкретные цвета резолвит слой отображения. */
@Serializable
public enum class ReaderTheme {
    /** Тёплая «бумага»: не чисто-белый фон, мягкий тёмно-серый текст. */
    PAPER,

    /** Сепия: ещё теплее, для долгого вечернего чтения. */
    SEPIA,

    /** Приглушённо-серый светлый: ниже яркость без перехода в тёмную тему. */
    GRAY,

    /** Тёмная тема: тёмный фон, светлый неяркий текст. */
    NIGHT,

    /** Максимальный контраст: белый фон, почти-чёрный текст — для солнца. */
    BRIGHT,
}

/** Горизонтальное выравнивание абзацев. */
@Serializable
public enum class ReaderAlign {
    /** По левому краю — комфортнее: ровные межсловные пробелы. */
    START,

    /** Выключка по ширине — ровный правый край, но «реки» в узких колонках. */
    JUSTIFY,
}

/** Формат индикатора прогресса чтения. */
@Serializable
public enum class ProgressFormat {
    /** Не показывать прогресс. */
    NONE,

    /** Процент пройденного. */
    PERCENT,

    /** Позиция по главам (TOC). */
    CHAPTER,

    /** Оценка времени до конца главы. */
    TIME_LEFT,
}

/**
 * Стиль перехода между страницами в страничном режиме ([ReaderSettings.paged]).
 * В скролл-режиме не применяется. При системном «уменьшить движение» слой
 * отображения форсит [NONE] независимо от выбора.
 */
@Serializable
public enum class PageTransition {
    /** Книжный горизонтальный слайд: вперёд страница уезжает влево. */
    SLIDE,

    /** Мягкое перекрёстное затухание без движения. */
    FADE,

    /** Без анимации: мгновенная смена страницы. */
    NONE,
}

/**
 * Полный набор пользовательских настроек ридера — сериализуемый, на примитивах
 * (без Compose-типов), чтобы его можно было персистить и переносить между
 * платформами. Слой отображения (`reflow/impl`) маппит это в свою Compose-модель.
 *
 * Значения по умолчанию = пресет «Комфорт».
 *
 * @property fontFamily семейство шрифта основного текста
 * @property fontSizeSp кегль основного текста в sp ([MIN_FONT_SP]..[MAX_FONT_SP])
 * @property lineHeight межстрочный как множитель кегля ([MIN_LINE_HEIGHT]..[MAX_LINE_HEIGHT])
 * @property columnChars целевая длина строки в символах ([MIN_COLUMN_CHARS]..[MAX_COLUMN_CHARS])
 * @property marginDp поля колонки в dp ([MIN_MARGIN_DP]..[MAX_MARGIN_DP])
 * @property align выравнивание абзацев
 * @property hyphenation переносы слов
 * @property letterSpacingSp межбуквенный интервал в sp ([MIN_LETTER_SPACING_SP]..[MAX_LETTER_SPACING_SP])
 * @property wordSpacingSp межсловный интервал в sp ([MIN_WORD_SPACING_SP]..[MAX_WORD_SPACING_SP])
 * @property theme базовая палитра
 * @property backgroundWarmth теплота фона `0..1` (0 — как в теме, 1 — теплее всего)
 * @property brightness внутренняя яркость `[MIN_BRIGHTNESS]..1` (1 — без затемнения)
 * @property sunsetWarm плавно теплеть после захода солнца (по локальному времени)
 * @property paged страничный режим (по умолчанию) вместо непрерывного скролла
 * @property pageTransition стиль перехода между страницами (только в страничном режиме)
 * @property tapToTurn перелистывание тапом по краям (тап-зоны лево/право); при `false`
 *   тап в любом месте лишь показывает/прячет панель — защита от случайных перелистываний
 * @property autoHideSec автоскрытие панелей через N секунд (0 — не скрывать)
 * @property progress формат индикатора прогресса
 * @property readingRuler подсветка текущей строки (reading ruler)
 * @property bionic выделение первых букв слов (bionic reading)
 * @property ergonomics забота о глазах по времени сессии (20-20-20, затемнение, ритм)
 * @property keepScreenOn не гасить экран во время чтения (always-on display; Android)
 */
@Serializable
public data class ReaderSettings(
    public val fontFamily: ReaderFontFamily = ReaderFontFamily.SYSTEM_SERIF,
    public val fontSizeSp: Float = 19f,
    public val lineHeight: Float = 1.6f,
    public val columnChars: Int = 66,
    public val marginDp: Float = 24f,
    public val align: ReaderAlign = ReaderAlign.START,
    public val hyphenation: Boolean = false,
    public val letterSpacingSp: Float = 0f,
    public val wordSpacingSp: Float = 0f,
    public val theme: ReaderTheme = ReaderTheme.PAPER,
    public val backgroundWarmth: Float = 0f,
    public val brightness: Float = 1f,
    public val sunsetWarm: Boolean = false,
    public val paged: Boolean = true,
    public val pageTransition: PageTransition = PageTransition.SLIDE,
    public val tapToTurn: Boolean = true,
    public val autoHideSec: Int = 0,
    public val progress: ProgressFormat = ProgressFormat.PERCENT,
    public val readingRuler: Boolean = false,
    public val bionic: Boolean = false,
    public val ergonomics: Boolean = true,
    public val keepScreenOn: Boolean = true,
) {
    /** Возвращает копию с зажатыми в допустимые диапазоны числовыми полями. */
    public fun coerced(): ReaderSettings =
        copy(
            fontSizeSp = fontSizeSp.coerceIn(MIN_FONT_SP, MAX_FONT_SP),
            lineHeight = lineHeight.coerceIn(MIN_LINE_HEIGHT, MAX_LINE_HEIGHT),
            columnChars = columnChars.coerceIn(MIN_COLUMN_CHARS, MAX_COLUMN_CHARS),
            marginDp = marginDp.coerceIn(MIN_MARGIN_DP, MAX_MARGIN_DP),
            letterSpacingSp = letterSpacingSp.coerceIn(MIN_LETTER_SPACING_SP, MAX_LETTER_SPACING_SP),
            wordSpacingSp = wordSpacingSp.coerceIn(MIN_WORD_SPACING_SP, MAX_WORD_SPACING_SP),
            brightness = brightness.coerceIn(MIN_BRIGHTNESS, 1f),
            backgroundWarmth = backgroundWarmth.coerceIn(0f, 1f),
            autoHideSec = autoHideSec.coerceIn(0, MAX_AUTO_HIDE_SEC),
        )

    /** Допустимые диапазоны ползунков — единый источник правды для UI и клампинга. */
    public companion object {
        /** Минимальный кегль, sp. */
        public const val MIN_FONT_SP: Float = 14f

        /** Максимальный кегль, sp. */
        public const val MAX_FONT_SP: Float = 24f

        /** Минимальный межстрочный множитель. */
        public const val MIN_LINE_HEIGHT: Float = 1.4f

        /** Максимальный межстрочный множитель. */
        public const val MAX_LINE_HEIGHT: Float = 2.0f

        /** Минимальная длина строки в символах. */
        public const val MIN_COLUMN_CHARS: Int = 50

        /** Максимальная длина строки в символах. */
        public const val MAX_COLUMN_CHARS: Int = 90

        /** Минимальные поля, dp. */
        public const val MIN_MARGIN_DP: Float = 8f

        /** Максимальные поля, dp. */
        public const val MAX_MARGIN_DP: Float = 48f

        /** Минимальный межбуквенный интервал, sp. */
        public const val MIN_LETTER_SPACING_SP: Float = 0f

        /** Максимальный межбуквенный интервал, sp. */
        public const val MAX_LETTER_SPACING_SP: Float = 2f

        /** Минимальный межсловный интервал, sp. */
        public const val MIN_WORD_SPACING_SP: Float = 0f

        /** Максимальный межсловный интервал, sp. */
        public const val MAX_WORD_SPACING_SP: Float = 8f

        /** Минимальная внутренняя яркость (сильнее затемнять нельзя — текст пропадёт). */
        public const val MIN_BRIGHTNESS: Float = 0.4f

        /** Максимальное автоскрытие панелей, секунд. */
        public const val MAX_AUTO_HIDE_SEC: Int = 30
    }
}

/**
 * Именованный пресет ридера — снимок [ReaderSettings], применяемый одним тапом.
 *
 * @property id стабильный идентификатор; встроенные пресеты используют
 *   префикс `"builtin:reader:"` (см. [isBuiltinReaderPresetId])
 * @property name отображаемое имя
 * @property settings полный снимок настроек
 */
@Serializable
public data class ReaderPreset(
    public val id: String,
    public val name: String,
    public val settings: ReaderSettings,
)

/** `true`, если [id] принадлежит встроенному (неудаляемому) пресету ридера. */
public fun isBuiltinReaderPresetId(id: String): Boolean = id.startsWith(BUILTIN_READER_PRESET_PREFIX)

internal const val BUILTIN_READER_PRESET_PREFIX: String = "builtin:reader:"

/**
 * Встроенные пресеты ридера. Главный принцип — пресеты на верхнем уровне,
 * тонкие ползунки спрятаны за «Настроить»: большинство выберет пресет и больше
 * туда не полезет. Пятый пресет «Солнце на улице» — для чтения вне дома.
 *
 * Живут в коде (не сериализуются), поэтому могут эволюционировать между версиями.
 */
public object BuiltinReaderPresets {
    /** Сбалансированный дефолт. */
    public val comfort: ReaderPreset =
        ReaderPreset(
            id = "${BUILTIN_READER_PRESET_PREFIX}comfort",
            name = "Комфорт",
            settings = ReaderSettings(),
        )

    /** Долгое чтение: крупнее, воздушнее, чуть теплее и приглушённее, эргономика. */
    public val longReading: ReaderPreset =
        ReaderPreset(
            id = "${BUILTIN_READER_PRESET_PREFIX}long",
            name = "Долгое чтение",
            settings =
                ReaderSettings(
                    fontSizeSp = 21f,
                    lineHeight = 1.8f,
                    columnChars = 62,
                    marginDp = 32f,
                    backgroundWarmth = 0.3f,
                    brightness = 0.92f,
                    ergonomics = true,
                ),
        )

    /** Компактно: больше текста на экране (мельче, плотнее, шире колонка). */
    public val compact: ReaderPreset =
        ReaderPreset(
            id = "${BUILTIN_READER_PRESET_PREFIX}compact",
            name = "Компактно",
            settings =
                ReaderSettings(
                    fontSizeSp = 16f,
                    lineHeight = 1.45f,
                    columnChars = 80,
                    marginDp = 16f,
                ),
        )

    /** Ночь: тёмная тема, тёплый сдвиг и пониженная яркость. */
    public val night: ReaderPreset =
        ReaderPreset(
            id = "${BUILTIN_READER_PRESET_PREFIX}night",
            name = "Ночь",
            settings =
                ReaderSettings(
                    theme = ReaderTheme.NIGHT,
                    fontSizeSp = 19f,
                    lineHeight = 1.7f,
                    backgroundWarmth = 0.2f,
                    brightness = 0.8f,
                ),
        )

    /** Солнце на улице: максимальный контраст и чуть крупнее. */
    public val sunlight: ReaderPreset =
        ReaderPreset(
            id = "${BUILTIN_READER_PRESET_PREFIX}sun",
            name = "Солнце на улице",
            settings =
                ReaderSettings(
                    theme = ReaderTheme.BRIGHT,
                    fontSizeSp = 21f,
                    lineHeight = 1.6f,
                    backgroundWarmth = 0f,
                    brightness = 1f,
                ),
        )

    /** Все встроенные пресеты в порядке отображения. */
    public val all: List<ReaderPreset> = listOf(comfort, longReading, compact, night, sunlight)
}

/**
 * Персистентное состояние ридера (глобальное, на все документы).
 *
 * @property current последние применённые настройки — восстанавливаются при старте
 * @property userPresets пользовательские пресеты (показываются первыми в колесе);
 *   встроенные пресеты живут в коде ([BuiltinReaderPresets]) и не сериализуются,
 *   поэтому могут эволюционировать между версиями
 * @property activePresetId id выбранного пресета для подсветки в панели — может
 *   указывать как на встроенный, так и на кастомный пресет из [userPresets]; `null` —
 *   активного пресета нет (например, после удаления активного кастома)
 */
@Serializable
public data class StoredReaderSettings(
    public val current: ReaderSettings = ReaderSettings(),
    public val userPresets: List<ReaderPreset> = emptyList(),
    public val activePresetId: String? = null,
)
