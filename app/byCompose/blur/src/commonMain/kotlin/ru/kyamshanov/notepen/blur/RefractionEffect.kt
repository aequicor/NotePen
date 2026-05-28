package ru.kyamshanov.notepen.blur

import androidx.compose.ui.graphics.RenderEffect

/**
 * SkSL/AGSL displacement shader that bends the captured backdrop near the rounded edges
 * of a glass panel — straight lines crossing the rim curve through it, matching the
 * "liquid glass" lens look (Apple). Sampled content from *outside* the panel rim is
 * pulled inward across an edge band; the deep interior stays a 1:1 copy of the backdrop.
 *
 * The shader expects the `content` child to cover a rect that's larger than the visible
 * panel by [padPx] on every side — that pad zone is where the rim refraction samples
 * pixels from. Pixels outside the rounded shape are returned transparent so the caller
 * doesn't need an extra clip pass.
 *
 * Returns `null` on platforms / OS versions where runtime shaders aren't available
 * (Android < 13 in particular) — callers should fall back to a plain frosted-glass path.
 *
 * @param innerWidthPx width of the `content` source rect (panel width + 2 * pad).
 * @param innerHeightPx height of the `content` source rect (panel height + 2 * pad).
 * @param padPx margin on each side between the source rect and the visible shape.
 * @param shapeWidthPx visible shape width in pixels (the panel width).
 * @param shapeHeightPx visible shape height in pixels.
 * @param cornerRadiusPx rounded-rect corner radius in pixels (use `min(w,h)/2` for circle).
 * @param edgeBandPx width of the refraction band measured inward from the rim.
 * @param strengthPx maximum displacement at the rim, in pixels.
 */
expect fun refractionRenderEffect(
    innerWidthPx: Float,
    innerHeightPx: Float,
    padPx: Float,
    shapeWidthPx: Float,
    shapeHeightPx: Float,
    cornerRadiusPx: Float,
    edgeBandPx: Float,
    strengthPx: Float,
): RenderEffect?

/**
 * Shared SkSL source. Compatible with AGSL on Android (same syntax for the subset used).
 * Convention: the child shader is named `content` so both Skia (`childShaderNames`) and
 * Android (`shaderName` argument of `createRuntimeShaderEffect`) can reference it.
 */
internal const val REFRACTION_SKSL = """
uniform shader content;
uniform float2 uInner;       // content rect (shape + pad on each side)
uniform float2 uShapeOrigin; // shape top-left within the content rect (== padPx)
uniform float2 uShapeSize;   // visible shape size
uniform float uRadius;
uniform float uEdge;
uniform float uStrength;

float sdRoundedBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

half4 main(float2 coord) {
    // coord is in inner-rect space (0..uInner). The visible shape sits at
    // uShapeOrigin..uShapeOrigin+uShapeSize; pixels outside it are the pad zone
    // and must render as transparent so the panel keeps its rounded outline.
    float2 b = uShapeSize * 0.5;
    float2 p = coord - uShapeOrigin - b;
    float d = sdRoundedBox(p, b, uRadius);
    if (d >= 0.0) {
        return half4(0.0);
    }
    if (uEdge <= 0.0) {
        return content.eval(coord);
    }
    float t = clamp(1.0 + d / uEdge, 0.0, 1.0);
    if (t <= 0.0) {
        return content.eval(coord);
    }
    // Outward normal via central differences on the SDF.
    float h = 1.0;
    float dxp = sdRoundedBox(p + float2(h, 0.0), b, uRadius);
    float dxm = sdRoundedBox(p - float2(h, 0.0), b, uRadius);
    float dyp = sdRoundedBox(p + float2(0.0, h), b, uRadius);
    float dym = sdRoundedBox(p - float2(0.0, h), b, uRadius);
    float2 grad = float2(dxp - dxm, dyp - dym);
    float gl = length(grad);
    if (gl < 1e-4) {
        return content.eval(coord);
    }
    float2 n = grad / gl;
    // Corner-proximity mask: only the rounded corners should refract. Along a flat
    // edge the SDF normal is constant, so a uniform-strength lens reads as a stripe
    // parallel to the edge (very visible on long bars — titlebars, list cards). We
    // compute the distance from the current point to the *nearest corner centre*
    // (the corners of the inscribed rect at b - r); inside that disk we're in a
    // rounded corner, outside we're under a flat edge. smoothstep gives a soft
    // taper so the lens fades into the flat region instead of cutting off.
    float2 cornerCentreRel = abs(p) - (b - float2(uRadius));
    float distToCorner = length(max(cornerCentreRel, float2(0.0)));
    float cornerFactor = 1.0 - smoothstep(uRadius * 0.5, uRadius * 1.2, distToCorner);
    // Quadratic ease-in across the rim band, scaled by the corner mask — the centre
    // stays a 1:1 copy of the backdrop, the rounded corners show a hemispherical lens,
    // and the straight rim band degenerates to ~no displacement.
    float disp = uStrength * t * t * cornerFactor;
    // Sample from *outside* the rim along the outward normal. The pad zone provides
    // real backdrop pixels there; clamp keeps the worst-case sample within bounds so
    // the shader degrades to edge-stretch rather than transparency past the pad.
    float2 sampleCoord = clamp(coord + n * disp, float2(0.0), uInner);
    return content.eval(sampleCoord);
}
"""
