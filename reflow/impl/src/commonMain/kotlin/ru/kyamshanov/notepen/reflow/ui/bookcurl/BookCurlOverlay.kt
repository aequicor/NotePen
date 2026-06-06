package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun BookCurlOverlay(
    front: ImageBitmap?,
    back: ImageBitmap?,
    progress: Float,
    direction: Int,
    gripYFraction: Float,
    velocityX: Float,
    phase: BookCurlPhase,
    twoPageSpread: Boolean,
    modifier: Modifier = Modifier,
) {
    if (front == null || progress <= 0.001f || !isBookCurlNativeRendererSupported()) return
    val density = LocalDensity.current
    val sheetGeometry =
        remember(front.width, direction, twoPageSpread) {
            bookCurlSheetGeometry(front.width, direction, twoPageSpread)
        }
    val sheetSource =
        remember(front, sheetGeometry, twoPageSpread) {
            if (twoPageSpread) {
                SheetSource(
                    image = cropBookCurlImage(image = front, sourceX = sheetGeometry.sourceX, width = sheetGeometry.width),
                    offsetX = sheetGeometry.offsetX,
                )
            } else {
                SheetSource(image = front, offsetX = sheetGeometry.offsetX)
            }
        }
    val underPageSource =
        remember(back, direction, twoPageSpread, sheetSource.image.width, sheetSource.offsetX) {
            back?.let { image ->
                val geometry = bookCurlUnderPageGeometry(image.width, direction, twoPageSpread)
                SheetSource(
                    image = cropBookCurlImage(image = image, sourceX = geometry.sourceX, width = geometry.width),
                    offsetX = sheetSource.offsetX,
                )
            }
        }
    val sheet = sheetSource.image
    val profile =
        remember(sheet.width, sheet.height, density.density) {
            BookCurlPhysics.autoProfile(sheet.width.toFloat(), sheet.height.toFloat(), density.density)
        }
    val buffers =
        remember(sheet.width, sheet.height, profile) {
            bookCurlMeshBuffers(
                columns = profile.columns,
                rows = profile.rows,
                widthPx = sheet.width.toFloat(),
                heightPx = sheet.height.toFloat(),
            )
        }
    val state =
        remember(progress, direction, gripYFraction, velocityX, phase, sheet.width, sheet.height) {
            BookCurlState(
                direction = direction,
                gripY = sheet.height * gripYFraction.coerceIn(0f, 1f),
                fingerX = sheet.width * (1f - progress.coerceIn(0f, 1f)),
                fingerY = sheet.height * gripYFraction.coerceIn(0f, 1f),
                velocityX = velocityX,
                velocityY = 0f,
                progress = progress.coerceIn(0f, 1f),
                phase = phase,
            )
        }
    val mesh =
        remember(state, profile, sheet.width, sheet.height) {
            BookCurlPhysics.mesh(
                state = state,
                widthPx = sheet.width.toFloat(),
                heightPx = sheet.height.toFloat(),
                elapsedSeconds = progress * 0.42f + abs(velocityX) / 8000f,
                profile = profile,
            )
        }
    val paint =
        remember {
            BookCurlPaint(
                paperTint = Color(0xFFFFF8EA),
                shadow = Color.Black,
                highlight = Color.White,
            )
        }
    Canvas(modifier.fillMaxSize()) {
        val underPage = underPageSource
        if (underPage != null) {
            drawImage(
                image = underPage.image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(underPage.image.width, underPage.image.height),
                dstOffset = IntOffset(underPage.offsetX.roundToInt(), 0),
                dstSize = IntSize(underPage.image.width, underPage.image.height),
            )
        }
        drawBookCurlNative(
            front = sheet,
            back = null,
            mesh = mesh,
            buffers = buffers,
            offsetX = sheetSource.offsetX,
            paint = paint,
        )
        val readableAlpha = bookCurlReadableOverlayAlpha(progress)
        if (readableAlpha > 0f) {
            drawImage(
                image = front,
                srcOffset = IntOffset(sheetGeometry.sourceX, 0),
                srcSize = IntSize(sheetGeometry.width, front.height),
                dstOffset = IntOffset(sheetGeometry.offsetX.roundToInt(), 0),
                dstSize = IntSize(sheetGeometry.width, front.height),
                alpha = readableAlpha,
            )
        }
    }
}

private data class SheetSource(
    val image: ImageBitmap,
    val offsetX: Float,
)

internal data class BookCurlSheetGeometry(
    val sourceX: Int,
    val width: Int,
    val offsetX: Float,
)

internal fun bookCurlSheetGeometry(
    fullWidth: Int,
    direction: Int,
    twoPageSpread: Boolean,
): BookCurlSheetGeometry {
    if (!twoPageSpread) return BookCurlSheetGeometry(sourceX = 0, width = fullWidth.coerceAtLeast(1), offsetX = 0f)
    val halfWidth = (fullWidth / 2).coerceAtLeast(1)
    return if (direction > 0) {
        BookCurlSheetGeometry(
            sourceX = halfWidth,
            width = (fullWidth - halfWidth).coerceAtLeast(1),
            offsetX = halfWidth.toFloat(),
        )
    } else {
        BookCurlSheetGeometry(sourceX = 0, width = halfWidth, offsetX = 0f)
    }
}

internal fun bookCurlUnderPageGeometry(
    fullWidth: Int,
    direction: Int,
    twoPageSpread: Boolean,
): BookCurlSheetGeometry {
    if (!twoPageSpread) return BookCurlSheetGeometry(sourceX = 0, width = fullWidth.coerceAtLeast(1), offsetX = 0f)
    val halfWidth = (fullWidth / 2).coerceAtLeast(1)
    return if (direction > 0) {
        BookCurlSheetGeometry(
            sourceX = halfWidth,
            width = (fullWidth - halfWidth).coerceAtLeast(1),
            offsetX = halfWidth.toFloat(),
        )
    } else {
        BookCurlSheetGeometry(sourceX = 0, width = halfWidth, offsetX = 0f)
    }
}

internal fun bookCurlTargetReveal(progress: Float): Float {
    val t = ((progress.coerceIn(0f, 1f) - 0.42f) / 0.48f).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun bookCurlReadableOverlayAlpha(progress: Float): Float {
    val t = ((progress.coerceIn(0f, 1f) - 0.58f) / 0.42f).coerceIn(0f, 1f)
    val smooth = t * t * (3f - 2f * t)
    return 0.92f * (1f - smooth)
}
