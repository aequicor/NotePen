package ru.kyamshanov.notepen.blur

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

private val refractionEffect: RuntimeEffect by lazy {
    RuntimeEffect.makeForShader(REFRACTION_SKSL)
}

actual fun refractionRenderEffect(
    innerWidthPx: Float,
    innerHeightPx: Float,
    padPx: Float,
    shapeWidthPx: Float,
    shapeHeightPx: Float,
    cornerRadiusPx: Float,
    edgeBandPx: Float,
    strengthPx: Float,
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
    // Single child shader named "content"; null input → the layer's own content is
    // substituted by Skia at composition time. This is what lets the renderEffect see
    // the GraphicsLayer's pre-effect drawing (i.e. the drawn backdrop + tint) as the
    // sampling source for the displacement.
    val filter =
        ImageFilter.makeRuntimeShader(
            runtimeShaderBuilder = builder,
            shaderName = "content",
            input = null,
        )
    return filter.asComposeRenderEffect()
}
