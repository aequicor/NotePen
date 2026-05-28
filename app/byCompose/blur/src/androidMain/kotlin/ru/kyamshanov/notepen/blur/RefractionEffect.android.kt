package ru.kyamshanov.notepen.blur

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

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
    val measured = innerWidthPx > 0f && innerHeightPx > 0f && shapeWidthPx > 0f && shapeHeightPx > 0f
    val supported = measured && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    return if (supported) {
        buildRefractionEffect(
            innerWidthPx = innerWidthPx,
            innerHeightPx = innerHeightPx,
            padPx = padPx,
            shapeWidthPx = shapeWidthPx,
            shapeHeightPx = shapeHeightPx,
            cornerRadiusPx = cornerRadiusPx,
            edgeBandPx = edgeBandPx,
            strengthPx = strengthPx,
            blurSigmaPx = blurSigmaPx,
        )
    } else {
        null
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun buildRefractionEffect(
    innerWidthPx: Float,
    innerHeightPx: Float,
    padPx: Float,
    shapeWidthPx: Float,
    shapeHeightPx: Float,
    cornerRadiusPx: Float,
    edgeBandPx: Float,
    strengthPx: Float,
    blurSigmaPx: Float,
): RenderEffect {
    val shader = RuntimeShader(REFRACTION_SKSL)
    shader.setFloatUniform("uInner", innerWidthPx, innerHeightPx)
    shader.setFloatUniform("uShapeOrigin", padPx, padPx)
    shader.setFloatUniform("uShapeSize", shapeWidthPx, shapeHeightPx)
    shader.setFloatUniform("uRadius", cornerRadiusPx)
    shader.setFloatUniform("uEdge", edgeBandPx)
    shader.setFloatUniform("uStrength", strengthPx)
    // "content" matches the SkSL `uniform shader content;` declaration. The platform
    // substitutes the input filter (or the layer's content when no input is given) as
    // that shader at composition.
    val refraction = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
    val composed =
        if (blurSigmaPx > 0f) {
            val blur =
                android.graphics.RenderEffect.createBlurEffect(
                    blurSigmaPx,
                    blurSigmaPx,
                    Shader.TileMode.CLAMP,
                )
            // Chain order: inner (blur) runs first, outer (refraction) consumes its
            // output — so the runtime shader samples a pre-blurred backdrop.
            android.graphics.RenderEffect.createChainEffect(refraction, blur)
        } else {
            refraction
        }
    return composed.asComposeRenderEffect()
}
