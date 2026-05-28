package ru.kyamshanov.notepen.blur

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

private val refractionEffect: RuntimeEffect by lazy {
    RuntimeEffect.makeForShader(REFRACTION_SKSL)
}

actual fun isRefractionSupported(): Boolean = true

actual fun refractionRenderEffect(
    innerWidthPx: Float,
    innerHeightPx: Float,
    padPx: Float,
    shapeWidthPx: Float,
    shapeHeightPx: Float,
    cornerRadiusPx: Float,
    edgeBandPx: Float,
    strengthPx: Float,
    blurSigmaPx: Float,
): RenderEffect? {
    val measured =
        innerWidthPx > 0f && innerHeightPx > 0f && shapeWidthPx > 0f && shapeHeightPx > 0f
    if (!measured) return null
    val builder = RuntimeShaderBuilder(refractionEffect)
    builder.uniform("uInner", innerWidthPx, innerHeightPx)
    builder.uniform("uShapeOrigin", padPx, padPx)
    builder.uniform("uShapeSize", shapeWidthPx, shapeHeightPx)
    builder.uniform("uRadius", cornerRadiusPx)
    builder.uniform("uEdge", edgeBandPx)
    builder.uniform("uStrength", strengthPx)
    // Blur the layer's pre-effect content first, then sample it as `content` from the
    // runtime shader. With CLAMP we extend edge pixels into the displacement reach so
    // the rim never falls off into decal-transparent territory; the shape mask in the
    // shader still hides the pad ring.
    val blurFilter =
        if (blurSigmaPx > 0f) {
            ImageFilter.makeBlur(
                sigmaX = blurSigmaPx,
                sigmaY = blurSigmaPx,
                mode = FilterTileMode.CLAMP,
                input = null,
                crop = null,
            )
        } else {
            null
        }
    val filter =
        ImageFilter.makeRuntimeShader(
            runtimeShaderBuilder = builder,
            shaderName = "content",
            input = blurFilter,
        )
    return filter.asComposeRenderEffect()
}
