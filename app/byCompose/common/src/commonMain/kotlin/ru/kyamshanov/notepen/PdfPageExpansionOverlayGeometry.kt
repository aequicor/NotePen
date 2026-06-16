package ru.kyamshanov.notepen

internal fun pageExpansionLeftButtonX(
    leftEdgeX: Float,
    viewportWidth: Float,
    buttonSize: Float,
    padding: Float,
): Float =
    clampButtonXToViewport(
        preferredX = leftEdgeX - buttonSize - padding,
        viewportWidth = viewportWidth,
        buttonSize = buttonSize,
    )

internal fun pageExpansionRightButtonX(
    rightEdgeX: Float,
    viewportWidth: Float,
    buttonSize: Float,
    padding: Float,
): Float =
    clampButtonXToViewport(
        preferredX = rightEdgeX + padding,
        viewportWidth = viewportWidth,
        buttonSize = buttonSize,
    )

private fun clampButtonXToViewport(
    preferredX: Float,
    viewportWidth: Float,
    buttonSize: Float,
): Float {
    val maxX = (viewportWidth - buttonSize).coerceAtLeast(0f)
    return preferredX.coerceIn(0f, maxX)
}
