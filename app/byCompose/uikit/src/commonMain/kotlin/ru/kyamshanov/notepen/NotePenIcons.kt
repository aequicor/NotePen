package ru.kyamshanov.notepen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Domain-specific glyphs for NotePen's drawing tools. They replace the ambiguous
 * Material defaults (a mop for the eraser, an underline pen for the marker, a plain
 * search loupe for the writing magnifier) with shapes users recognise from physical
 * stationery. Path data is adapted from the open-source Material Design Icons set
 * (Pictogrammers, Apache-2.0).
 *
 * All glyphs are tinted by the surrounding `Icon`. [ColorSwatch] is meant to be
 * tinted with the *currently selected* color so the slot doubles as a colour preview.
 */
public object NotePenIcons {
    /** Paintbrush — the freehand pen / "кисть" tool. */
    public val Brush: ImageVector by lazy {
        filled(
            "M20.71,4.63L19.37,3.29C19,2.9 18.35,2.9 17.96,3.29L9,12.25L11.75,15L20.71," +
                "6.04C21.1,5.65 21.1,5 20.71,4.63M7,14A3,3 0 0,0 4,17C4,18.31 2.66,19 1.5," +
                "19C2.81,20.66 4.97,21.5 7,21.5A4.5,4.5 0 0,0 11.5,17A3,3 0 0,0 8.5,14H7Z",
        )
    }

    /**
     * Chisel-tip highlighter drawn diagonally with the bold stroke it leaves
     * underneath, so it reads as "marker that paints a line" — the "маркер" tool.
     */
    public val Highlighter: ImageVector by lazy {
        filled(
            // Barrel of the marker.
            "M9.5,2H14.5A1.5,1.5 0 0,1 16,3.5V11.5H8V3.5A1.5,1.5 0 0,1 9.5,2Z" +
                // Chisel felt tip below the barrel.
                "M8,12H16L14,16.5H10L8,12Z" +
                // Bold stroke left on the page.
                "M4,18.25H20A1.75,1.75 0 0,1 20,21.75H4A1.75,1.75 0 0,1 4,18.25Z",
        )
    }

    /** Rubber eraser — the "ластик" tool. */
    public val Eraser: ImageVector by lazy {
        filled(
            "M16.24,3.56L21.19,8.5C21.97,9.29 21.97,10.55 21.19,11.34L12,20.53C10.44," +
                "22.09 7.91,22.09 6.34,20.53L2.81,17C2.03,16.21 2.03,14.95 2.81,14.16L13.66," +
                "3.31C14.44,2.53 15.7,2.53 16.49,3.31M4.22,15.58L7.76,19.11C8.54,19.89 9.8," +
                "19.89 10.59,19.11L14.12,15.58L9.17,10.63L4.22,15.58Z",
        )
    }

    /** Half-filled disc — the de-facto symbol for "прозрачность" / opacity. */
    public val Opacity: ImageVector by lazy {
        filled("M12,2A10,10 0 0,1 22,12A10,10 0 0,1 12,22A10,10 0 0,1 2,12A10,10 0 0,1 12,2M12,4V20A8,8 0 0,0 12,4Z")
    }

    /** Solid disc, intended to be tinted with the active colour as a live swatch. */
    public val ColorSwatch: ImageVector by lazy {
        filled("M12,2A10,10 0 1,0 12,22A10,10 0 1,0 12,2Z")
    }

    /**
     * A loupe over text lines — the "лупа для письма" magnifier. Unlike a bare
     * search glass, the lines inside read as "magnify what you write", keeping it
     * visually distinct from the +/− zoom controls.
     */
    public val WritingLoupe: ImageVector by lazy {
        stroked(
            "M10,3.5A6.5,6.5 0 1,0 10,16.5A6.5,6.5 0 1,0 10,3.5" +
                "M15,15L20.5,20.5" +
                "M7,8L13,8" +
                "M7,11L11,11",
        )
    }

    /**
     * Two page-columns separated by a dashed gutter — the "разделить развороты"
     * toggle (FEATURE #4). Reads as "one sheet split into a left and a right
     * page", distinct from the thumbnails grid and the rotate glyph.
     */
    public val SplitSpreads: ImageVector by lazy {
        stroked(
            // Outer page frame.
            "M4,4H20V20H4Z" +
                // Dashed central gutter (the split line).
                "M12,5V8" +
                "M12,11V14" +
                "M12,17V19",
        )
    }

    /**
     * Two facing page-rectangles standing SIDE BY SIDE with a solid spine between
     * them — the "две страницы" / book-spread toggle (FEATURE #5, two-up view on
     * wide screens). Deliberately distinct from both the reflow reading-mode glyph
     * (an open MenuBook, «Режим чтения») and the FEATURE #4 split glyph
     * ([SplitSpreads] — ONE frame cut by a dashed gutter): this one reads as "two
     * separate sheets shown next to each other", not "one sheet split" and not
     * "flowing text". No visual confusion between the three controls.
     */
    public val BookSpread: ImageVector by lazy {
        stroked(
            // Left page rectangle.
            "M3,5H10V19H3Z" +
                // Right page rectangle (mirrored, side by side).
                "M14,5H21V19H14Z" +
                // Solid spine between the two pages.
                "M12,4V20",
        )
    }

    private fun filled(pathData: String): ImageVector =
        ImageVector.Builder(
            name = "NotePen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()

    private fun stroked(pathData: String): ImageVector =
        ImageVector.Builder(
            name = "NotePen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
}
