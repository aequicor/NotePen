package ru.kyamshanov.notepen.blur

import android.graphics.RuntimeShader
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
): RenderEffect {
    val shader = RuntimeShader(REFRACTION_SKSL)
    shader.setFloatUniform("uInner", innerWidthPx, innerHeightPx)
    shader.setFloatUniform("uShapeOrigin", padPx, padPx)
    shader.setFloatUniform("uShapeSize", shapeWidthPx, shapeHeightPx)
    shader.setFloatUniform("uRadius", cornerRadiusPx)
    shader.setFloatUniform("uEdge", edgeBandPx)
    shader.setFloatUniform("uStrength", strengthPx)
    // "content" matches the SkSL `uniform shader content;` declaration. The platform
    // substitutes the layer's pre-effect content as that shader at composition.
    val androidEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
    return androidEffect.asComposeRenderEffect()
}
