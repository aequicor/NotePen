package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class BookCurlRendererDesktopTest {
    @Test
    fun writesPreviewFramesForManualInspection() {
        listOf(0.15f, 0.45f, 0.8f).forEach { progress ->
            renderCurlFrame(progress = progress).writePng(File(PREVIEW_DIR, "curl-${(progress * 100).toInt()}.png"))
            renderOverlayFrame(progress = progress).writePng(File(PREVIEW_DIR, "overlay-${(progress * 100).toInt()}.png"))
        }
    }

    @Test
    fun earlyCurlDoesNotDimWholePage() {
        val frame = renderCurlFrame(progress = 0.22f)
        val pixels = frame.toPixelMap()

        val topLeft = pixels.averageLuminance(xRange = 0 until 34, yRange = 0 until 34)
        val bottomLeft = pixels.averageLuminance(xRange = 0 until 34, yRange = 326 until 360)

        assertTrue(
            topLeft > MIN_UNAFFECTED_CORNER_LUMINANCE,
            "top-left corner must not be globally dimmed: $topLeft",
        )
        assertTrue(
            bottomLeft > MIN_UNAFFECTED_CORNER_LUMINANCE,
            "bottom-left corner must not be globally dimmed: $bottomLeft",
        )
    }

    @Test
    fun activeCurlProducesLocalizedShadowAndKeepsPageBright() {
        // Берём прогресс, где загиб уже активен (фаза подъёма заканчивается на ~45%).
        val frame = renderCurlFrame(progress = 0.72f)
        val pixels = frame.toPixelMap()

        val wholePage = pixels.averageLuminance(xRange = 0 until PAGE_WIDTH, yRange = 0 until PAGE_HEIGHT)
        val darkestBand = pixels.minColumnLuminance(yRange = 135 until 250)

        assertTrue(
            wholePage > MIN_WHOLE_PAGE_LUMINANCE,
            "curl frame must stay paper-bright, not fullscreen-dark: $wholePage",
        )
        assertTrue(
            wholePage - darkestBand > MIN_LOCAL_SHADOW_DELTA,
            "curl must produce a localized darker band (fold/shadow): whole=$wholePage darkest=$darkestBand",
        )
    }

    @Test
    fun overlayDoesNotReplacePageBeforeCommitWindow() {
        val frame = renderOverlayFrame(progress = 0.32f)
        val pixels = frame.toPixelMap()

        val targetSignal = pixels.averageBlueDominance(xRange = 190 until 250, yRange = 48 until 160)

        assertTrue(
            targetSignal < MAX_EARLY_TARGET_BLUE_DOMINANCE,
            "target page must not be visibly cross-faded before commit window: $targetSignal",
        )
    }

    @Test
    fun overlayRevealsTargetBeforeFinalScroll() {
        val frame = renderOverlayFrame(progress = 0.92f)
        val pixels = frame.toPixelMap()

        val targetSignal = pixels.averageBlueDominance(xRange = 190 until 250, yRange = 48 until 160)

        assertTrue(
            targetSignal > MIN_LATE_TARGET_BLUE_DOMINANCE,
            "target page must be visible before pager scroll commits: $targetSignal",
        )
    }

    @Test
    fun curledPaperSurfaceIsVisibleEvenWhenTextureIsTransparent() {
        val frame = ImageBitmap(PAGE_WIDTH, PAGE_HEIGHT)
        val mesh = mesh(progress = 0.58f)
        val buffers = buffers()

        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(frame),
            size = Size(PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat()),
        ) {
            drawRect(Color(0xFF5C6670))
            drawBookCurlNative(
                front = ImageBitmap(PAGE_WIDTH, PAGE_HEIGHT),
                back = null,
                mesh = mesh,
                buffers = buffers,
                offsetX = 0f,
                paint =
                    BookCurlPaint(
                        paperTint = Color(0xFFFFF8EA),
                        shadow = Color.Black,
                        highlight = Color.White,
                    ),
            )
        }

        val pixels = frame.toPixelMap()
        val visiblePaperPixels =
            pixels.countPixelsBrighterThan(
                xRange = 0 until PAGE_WIDTH,
                yRange = 0 until PAGE_HEIGHT,
                threshold = MIN_VISIBLE_PAPER_SURFACE_LUMINANCE,
            )

        assertTrue(
            visiblePaperPixels > MIN_VISIBLE_PAPER_SURFACE_PIXELS,
            "curl renderer must draw the paper surface, not only its shadow: pixels=$visiblePaperPixels",
        )
    }

    private fun renderCurlFrame(progress: Float): ImageBitmap {
        val frame = ImageBitmap(PAGE_WIDTH, PAGE_HEIGHT)
        val front = pageTexture()
        val back = mirrorBookCurlImageHorizontally(targetPageTexture())
        val mesh = mesh(progress)
        val buffers = buffers()

        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(frame),
            size = Size(PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat()),
        ) {
            drawRect(Color.White)
            drawBookCurlNative(
                front = front,
                back = back,
                mesh = mesh,
                buffers = buffers,
                offsetX = 0f,
                paint =
                    BookCurlPaint(
                        paperTint = Color(0xFFFFF8EA),
                        shadow = Color.Black,
                        highlight = Color.White,
                    ),
            )
        }
        return frame
    }

    private fun renderOverlayFrame(progress: Float): ImageBitmap {
        val frame = ImageBitmap(PAGE_WIDTH, PAGE_HEIGHT)
        val front = pageTexture()
        val back = targetPageTexture()
        val mesh = mesh(progress)
        val buffers = buffers()

        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(frame),
            size = Size(PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat()),
        ) {
            drawImage(front)
            val reveal = bookCurlTargetReveal(progress)
            if (reveal > 0f) {
                drawImage(
                    image = back,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(back.width, back.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(PAGE_WIDTH, PAGE_HEIGHT),
                    alpha = reveal,
                )
            }
            drawBookCurlNative(
                front = front,
                back = null,
                mesh = mesh,
                buffers = buffers,
                offsetX = 0f,
                paint =
                    BookCurlPaint(
                        paperTint = Color(0xFFFFF8EA),
                        shadow = Color.Black,
                        highlight = Color.White,
                    ),
            )
        }
        return frame
    }

    private fun mesh(progress: Float): BookCurlMesh {
        val state =
            BookCurlState(
                direction = 1,
                gripY = PAGE_HEIGHT * 0.62f,
                fingerY = PAGE_HEIGHT * 0.62f,
                velocityX = -1800f,
                progress = progress,
                phase = BookCurlPhase.Dragging,
            )
        return BookCurlPhysics.mesh(
            state = state,
            widthPx = PAGE_WIDTH.toFloat(),
            heightPx = PAGE_HEIGHT.toFloat(),
            profile = BookCurlProfile.Low,
        )
    }

    private fun buffers(): BookCurlMeshBuffers =
        bookCurlMeshBuffers(
            columns = BookCurlProfile.Low.columns,
            rows = BookCurlProfile.Low.rows,
            widthPx = PAGE_WIDTH.toFloat(),
            heightPx = PAGE_HEIGHT.toFloat(),
        )

    private fun pageTexture(): ImageBitmap {
        val bitmap = ImageBitmap(PAGE_WIDTH, PAGE_HEIGHT)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap),
            size = Size(PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat()),
        ) {
            drawRect(Color(0xFFFFFCF3))
            repeat(18) { index ->
                val y = 46f + index * 14f
                drawLine(
                    color = Color(0xFF8E8A80),
                    start = Offset(42f, y),
                    end = Offset(220f - (index % 4) * 18f, y),
                    strokeWidth = 2f,
                )
            }
        }
        return bitmap
    }

    private fun targetPageTexture(): ImageBitmap {
        val bitmap = ImageBitmap(PAGE_WIDTH, PAGE_HEIGHT)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap),
            size = Size(PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat()),
        ) {
            drawRect(Color(0xFFE8F1FF))
            repeat(14) { index ->
                val y = 58f + index * 16f
                drawLine(
                    color = Color(0xFF2F6FCC),
                    start = Offset(58f, y),
                    end = Offset(222f - (index % 3) * 22f, y),
                    strokeWidth = 2.4f,
                )
            }
        }
        return bitmap
    }
}

private fun androidx.compose.ui.graphics.PixelMap.averageLuminance(
    xRange: IntRange,
    yRange: IntRange,
): Float {
    var sum = 0f
    var count = 0
    for (y in yRange) {
        for (x in xRange) {
            val color = this[x, y]
            sum += color.red * 0.2126f + color.green * 0.7152f + color.blue * 0.0722f
            count += 1
        }
    }
    return sum / count.coerceAtLeast(1)
}

private fun androidx.compose.ui.graphics.PixelMap.minColumnLuminance(yRange: IntRange): Float {
    var min = 1f
    var x = 0
    while (x < width) {
        var sum = 0f
        var count = 0
        for (y in yRange) {
            val color = this[x, y]
            sum += color.red * 0.2126f + color.green * 0.7152f + color.blue * 0.0722f
            count += 1
        }
        val avg = sum / count.coerceAtLeast(1)
        if (avg < min) min = avg
        x += 4
    }
    return min
}

private fun androidx.compose.ui.graphics.PixelMap.averageBlueDominance(
    xRange: IntRange,
    yRange: IntRange,
): Float {
    var sum = 0f
    var count = 0
    for (y in yRange) {
        for (x in xRange) {
            val color = this[x, y]
            sum += (color.blue - maxOf(color.red, color.green)).coerceAtLeast(0f)
            count += 1
        }
    }
    return sum / count.coerceAtLeast(1)
}

private fun androidx.compose.ui.graphics.PixelMap.countPixelsBrighterThan(
    xRange: IntRange,
    yRange: IntRange,
    threshold: Float,
): Int {
    var count = 0
    for (y in yRange) {
        for (x in xRange) {
            val color = this[x, y]
            val luminance = color.red * 0.2126f + color.green * 0.7152f + color.blue * 0.0722f
            if (luminance > threshold) count += 1
        }
    }
    return count
}

private fun ImageBitmap.writePng(file: File) {
    file.parentFile.mkdirs()
    val image = Image.makeFromBitmap(asSkiaBitmap())
    val data = image.encodeToData(EncodedImageFormat.PNG, 100) ?: fail("Could not encode book curl preview PNG")
    try {
        file.writeBytes(data.bytes)
    } finally {
        data.close()
        image.close()
    }
}

private const val PAGE_WIDTH = 260
private const val PAGE_HEIGHT = 360
private const val PREVIEW_DIR = "build/bookcurl-preview"
private const val MIN_UNAFFECTED_CORNER_LUMINANCE = 0.88f
private const val MIN_WHOLE_PAGE_LUMINANCE = 0.78f
private const val MIN_LOCAL_SHADOW_DELTA = 0.01f
private const val MAX_EARLY_TARGET_BLUE_DOMINANCE = 0.018f
private const val MIN_LATE_TARGET_BLUE_DOMINANCE = 0.035f
private const val MIN_VISIBLE_PAPER_SURFACE_LUMINANCE = 0.85f
private const val MIN_VISIBLE_PAPER_SURFACE_PIXELS = 900
