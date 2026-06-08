package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

@Composable
internal fun BookCurlOverlay(
    front: ImageBitmap?,
    back: ImageBitmap?,
    progress: Float,
    direction: Int,
    gripYFraction: Float,
    velocityX: Float,
    fingerYFraction: Float = gripYFraction,
    phase: BookCurlPhase,
    twoPageSpread: Boolean,
    pageWidthPx: Int,
    spineGapPx: Int = 0,
    style: PageTurnStyle = PageTurnStyle.Default,
    modifier: Modifier = Modifier,
) {
    if (front == null || progress <= 0.001f || !isBookCurlNativeRendererSupported()) return
    val density = LocalDensity.current
    // Загибаем только колонку страницы (по центру окна), а не всю ширину окна: на широком
    // мониторе у книги большие боковые поля, и загиб должен начинаться от края текста, а не
    // от края окна. В развороте корешок — по центру окна, свободный край — у края контента.
    val sheetGeometry =
        remember(front.width, pageWidthPx, direction, twoPageSpread, spineGapPx) {
            bookCurlSheetGeometry(front.width, pageWidthPx, direction, twoPageSpread, spineGapPx)
        }
    val sheetSource =
        remember(front, sheetGeometry) {
            SheetSource(
                image = cropBookCurlImage(image = front, sourceX = sheetGeometry.sourceX, width = sheetGeometry.width),
                offsetX = sheetGeometry.offsetX,
            )
        }
    val underPageSource =
        remember(back, pageWidthPx, direction, twoPageSpread, spineGapPx, sheetGeometry.offsetX) {
            back?.let { image ->
                val geometry = bookCurlUnderPageGeometry(image.width, pageWidthPx, direction, twoPageSpread, spineGapPx)
                SheetSource(
                    image = cropBookCurlImage(image = image, sourceX = geometry.sourceX, width = geometry.width),
                    offsetX = sheetGeometry.offsetX,
                )
            }
        }
    // Изнанка переворачиваемого листа = НАСТОЯЩАЯ следующая страница. В развороте это ПРОТИВОПОЛОЖНАЯ
    // половина следующего разворота (а не тот же столбец — иначе показывали бы страницу через одну).
    // Зеркалим один раз: после переворота вокруг корешка текст читается нормально.
    val backFaceImage =
        remember(back, front.width, pageWidthPx, direction, twoPageSpread, spineGapPx) {
            back?.let { image ->
                val backGeometry = bookCurlBackFaceGeometry(image.width, pageWidthPx, direction, twoPageSpread, spineGapPx)
                mirrorBookCurlImageHorizontally(
                    cropBookCurlImage(image = image, sourceX = backGeometry.sourceX, width = backGeometry.width),
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
        remember(progress, direction, gripYFraction, fingerYFraction, velocityX, phase, sheet.width, sheet.height) {
            BookCurlState(
                direction = direction,
                gripY = sheet.height * gripYFraction.coerceIn(0f, 1f),
                fingerY = sheet.height * fingerYFraction.coerceIn(0f, 1f),
                velocityX = velocityX,
                progress = progress.coerceIn(0f, 1f),
                phase = phase,
            )
        }
    val mesh =
        remember(state, profile, sheet.width, sheet.height, style) {
            BookCurlPhysics.mesh(
                state = state,
                widthPx = sheet.width.toFloat(),
                heightPx = sheet.height.toFloat(),
                profile = profile,
                style = style,
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
    // Плавное ПОЯВЛЕНИЕ оверлея: вместо резкой подмены живой страницы текстурой в один кадр (на старте
    // свайпа это читалось как рывок цвета) оверлей вплывает альфой на первых ~12 % прогресса. Любая
    // мелкая разница «текстура vs живой рендер» на стыке размывается кросс-фейдом.
    val appearAlpha =
        remember(progress) {
            val t = (progress / APPEAR_FADE_END).coerceIn(0f, 1f)
            t * t * (3f - 2f * t)
        }
    Canvas(modifier.fillMaxSize().graphicsLayer { alpha = appearAlpha }) {
        // Текстуры захватываются с супер-сэмплингом (×N) для чёткого текста на завитке. Геометрия и
        // текст-координаты живут в пикселях ТЕКСТУРЫ (×N), поэтому весь рисунок ужимаем в 1/N до
        // экранных пикселей. N выводим из отношения высоты текстуры к экранной высоте оверлея (на
        // Android текстура 1× ⇒ N=1, без масштаба).
        val texScale = (sheet.height.toFloat() / size.height.coerceAtLeast(1f)).roundToInt().coerceAtLeast(1)
        scale(scaleX = 1f / texScale, scaleY = 1f / texScale, pivot = Offset.Zero) {
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
                back = backFaceImage,
                mesh = mesh,
                buffers = buffers,
                offsetX = sheetSource.offsetX,
                paint = paint,
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
    pageWidth: Int,
    direction: Int,
    twoPageSpread: Boolean,
    spineGapPx: Int = 0,
): BookCurlSheetGeometry {
    val full = fullWidth.coerceAtLeast(1)
    val page = pageWidth.coerceIn(1, full)
    if (!twoPageSpread) {
        // Одиночная страница: центрированная колонка контента шириной pageWidth.
        val left = ((full - page) / 2).coerceIn(0, full - 1)
        return BookCurlSheetGeometry(sourceX = left, width = page.coerceAtMost(full - left), offsetX = left.toFloat())
    }
    // Разворот: корешок строго по центру окна (full/2), а колонки отстоят от него на ПОЛОВИНУ зазора
    // BOOK_SPREAD_GAP. Позицию колонки берём как center ± gap/2, НЕ как full-page: на широком мониторе
    // maxContentWidth добавляет боковые поля, и full-page заехало бы в правое поле (рваный лист). Так
    // лист точно совпадает с живой колонкой и не «прыгает» по горизонтали при отдаче статике.
    val center = full / 2
    val halfGap = (spineGapPx / 2).coerceAtLeast(0)
    val start =
        if (direction > 0) {
            (center + halfGap).coerceIn(0, full - 1)
        } else {
            (center - halfGap - page).coerceIn(0, full - 1)
        }
    return BookCurlSheetGeometry(
        sourceX = start,
        width = page.coerceAtMost(full - start).coerceAtLeast(1),
        offsetX = start.toFloat(),
    )
}

internal fun bookCurlUnderPageGeometry(
    fullWidth: Int,
    pageWidth: Int,
    direction: Int,
    twoPageSpread: Boolean,
    spineGapPx: Int = 0,
): BookCurlSheetGeometry = bookCurlSheetGeometry(fullWidth, pageWidth, direction, twoPageSpread, spineGapPx)

/**
 * Геометрия кропа ИЗНАНКИ переворачиваемого листа. Одиночная страница — тот же столбец. Разворот:
 * изнанка показывает противоположную половину следующего разворота (= настоящую следующую страницу),
 * то есть геометрию как у `-direction`. Исправляет показ «страницы через одну» в двухстраничном режиме.
 */
internal fun bookCurlBackFaceGeometry(
    fullWidth: Int,
    pageWidth: Int,
    direction: Int,
    twoPageSpread: Boolean,
    spineGapPx: Int = 0,
): BookCurlSheetGeometry =
    if (!twoPageSpread) {
        bookCurlSheetGeometry(fullWidth, pageWidth, direction, false, spineGapPx)
    } else {
        bookCurlSheetGeometry(fullWidth, pageWidth, -direction, true, spineGapPx)
    }

internal fun bookCurlTargetReveal(progress: Float): Float {
    val t = ((progress.coerceIn(0f, 1f) - 0.42f) / 0.48f).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Доля прогресса, на которой оверлей завитка ВПЛЫВАЕТ альфой 0→1 (плавная подмена живой страницы
 * текстурой на старте свайпа, без рывка цвета в один кадр). */
private const val APPEAR_FADE_END = 0.12f
