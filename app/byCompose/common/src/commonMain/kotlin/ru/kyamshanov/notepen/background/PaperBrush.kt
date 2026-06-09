package ru.kyamshanov.notepen.background

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Сторона процедурной плитки бумаги в пикселях. Степень двойки → дешёвый tiling. */
private const val TILE_SIZE = 256

/** Плотность зёрен на плитку (масштабируется от площади). Подобрано «на глаз» для лёгкой фактуры. */
private const val GRAIN_DENSITY = 0.09f

/**
 * Возвращает плиточную (повторяющуюся) [Brush] для стиля фона [styleId], либо `null`
 * для [PaperBackgrounds.PLAIN] / неизвестного id (вызывающий тогда красит как раньше).
 *
 * Кисть строится в app-слое и передаётся ВНИЗ параметром в `PdfPagesViewer` /
 * `DrawablePdfPage` / `ReflowReader` — impl-модули не знают про каталог и ресурсы.
 *
 * [isDark] выбирает светлый/тёмный базовый тон (тёмная тема ридера/приложения), чтобы
 * бумага оставалась читаемой. Плитка кэшируется по `(id, isDark)`.
 */
@Composable
public fun rememberPaperBrush(
    styleId: String,
    isDark: Boolean,
): Brush? {
    val style = PaperBackgrounds.byId(styleId) ?: return null
    val tile = remember(style.id, isDark) { generatePaperTile(style, isDark) }
    return remember(tile) { ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)) }
}

/**
 * Детерминированно рисует одну бесшовную-«на глаз» плитку бумаги: базовый тон + мелкие
 * полупрозрачные зёрна со стабильным сидом (один и тот же вид при каждой рекомпозиции).
 *
 * Bootstrap до реальной арт-графики: зёрна около краёв дают едва заметный шов (для
 * фона приемлемо). Настоящие PNG-плитки будут полностью бесшовными — см. KDoc
 * [PaperBackgrounds].
 */
private fun generatePaperTile(
    style: PaperBackgrounds.Style,
    isDark: Boolean,
): ImageBitmap {
    val bitmap = ImageBitmap(TILE_SIZE, TILE_SIZE)
    val canvas = Canvas(bitmap)
    val base = if (isDark) style.baseDark else style.baseLight
    val grainCount = (TILE_SIZE * TILE_SIZE * GRAIN_DENSITY).toInt()
    // Тёмная тема: зерно чуть светлее фона (а не темнее), иначе фактура «проваливается».
    val grainBoost = if (isDark) 1.4f else 1f
    var seed = style.id.hashCode().toLong() xor if (isDark) DARK_SEED_SALT else LIGHT_SEED_SALT

    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        canvas,
        Size(TILE_SIZE.toFloat(), TILE_SIZE.toFloat()),
    ) {
        drawRect(color = base)
        repeat(grainCount) {
            seed = nextSeed(seed)
            val x = unit(seed) * TILE_SIZE
            seed = nextSeed(seed)
            val y = unit(seed) * TILE_SIZE
            seed = nextSeed(seed)
            val radius = GRAIN_MIN_RADIUS + unit(seed) * GRAIN_RADIUS_SPREAD
            seed = nextSeed(seed)
            val alpha = (style.grainAlpha * grainBoost * unit(seed)).coerceIn(0f, 1f)
            drawCircle(color = style.grain.copy(alpha = alpha), radius = radius, center = Offset(x, y))
        }
    }
    return bitmap
}

private const val GRAIN_MIN_RADIUS = 0.4f
private const val GRAIN_RADIUS_SPREAD = 1.1f
private val LIGHT_SEED_SALT = 0x9E3779B97F4A7C15uL.toLong()
private val DARK_SEED_SALT = 0x632BE59BD9B4E019uL.toLong()

/** xorshift64 — детерминированный PRNG (без `Math.random()`, стабильный сид). */
private fun nextSeed(seed: Long): Long {
    var s = if (seed == 0L) 1L else seed
    s = s xor (s shl 13)
    s = s xor (s ushr 7)
    s = s xor (s shl 17)
    return s
}

/** Бит-сид → `Float` в `[0, 1)`. */
private fun unit(seed: Long): Float = ((seed ushr 11).toDouble() / (1L shl 53).toDouble()).toFloat()
