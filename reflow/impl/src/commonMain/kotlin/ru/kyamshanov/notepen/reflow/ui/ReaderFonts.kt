package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.ui.text.font.FontFamily
import ru.kyamshanov.notepen.reflow.api.ReaderFontFamily

/**
 * Резолвит [ReaderFontFamily] в Compose [FontFamily].
 *
 * Системные семейства отдаются напрямую логическими [FontFamily.Serif] /
 * [FontFamily.SansSerif] / [FontFamily.Monospace].
 *
 * Бандловые семейства (Charter, Source Serif, Lora, Literata, Inter, IBM Plex
 * Sans, OpenDyslexic) подключаются дроп-ином бинарей шрифтов — до тех пор они
 * честно откатываются к ближайшему системному семейству, поэтому проект собирается
 * без вложенных .ttf.
 *
 * **Как подключить настоящий шрифт:**
 * 1. Положите `<family>.ttf`/`.otf` в
 *    `reflow/impl/src/commonMain/composeResources/font/` (только OFL/свободные
 *    лицензии; Iowan Old Style несвободен — заменён на Literata).
 * 2. Добавьте ветку, возвращающую
 *    `FontFamily(org.jetbrains.compose.resources.Font(Res.font.<family>))`
 *    (Res генерируется плагином compose-resources).
 */
internal fun resolveReaderFontFamily(family: ReaderFontFamily): FontFamily =
    when (family) {
        ReaderFontFamily.SYSTEM_SERIF -> FontFamily.Serif
        ReaderFontFamily.SYSTEM_SANS -> FontFamily.SansSerif
        ReaderFontFamily.SYSTEM_MONO -> FontFamily.Monospace
        ReaderFontFamily.CHARTER,
        ReaderFontFamily.SOURCE_SERIF,
        ReaderFontFamily.LORA,
        ReaderFontFamily.LITERATA,
        -> FontFamily.Serif
        ReaderFontFamily.INTER,
        ReaderFontFamily.IBM_PLEX_SANS,
        ReaderFontFamily.OPEN_DYSLEXIC,
        -> FontFamily.SansSerif
    }
