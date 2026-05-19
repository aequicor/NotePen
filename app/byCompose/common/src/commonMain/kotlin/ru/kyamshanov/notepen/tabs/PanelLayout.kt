package ru.kyamshanov.notepen.tabs

/**
 * Orientation of a [PanelLayout.Split]: [HORIZONTAL] puts the two
 * panels side-by-side (left | right), [VERTICAL] stacks them
 * (top / bottom).
 */
enum class PanelOrientation { HORIZONTAL, VERTICAL }

/**
 * Which slot of a [PanelLayout] a UI event targets:
 * - [PRIMARY] = the only panel in a [PanelLayout.Single], or the left /
 *   top panel of a [PanelLayout.Split];
 * - [SECONDARY] = the right / bottom panel of a [PanelLayout.Split].
 *
 * [SECONDARY] is only valid for [PanelLayout.Split].
 */
enum class PanelSide { PRIMARY, SECONDARY }

/**
 * Layout of the editor workspace.
 *
 * `Single(tabs)` — one panel covering the whole workspace.
 * `Split(orientation, ratio, left, right)` — two panels with a
 * draggable divider; [ratio] is the fraction of the cross axis taken
 * by [left] (so 0.5 means equal halves).
 *
 * The hierarchy is intentionally recursive (each side is a
 * [PanelLayout]) but the [PanelLayout.Split] invariant enforces *one
 * level only* — both children must be [PanelLayout.Single]. Going
 * deeper is rejected at construction time, matching the spec's
 * "maximum one split level" requirement.
 */
sealed interface PanelLayout {

    /** Single-panel layout. */
    data class Single(val tabs: OpenDocuments) : PanelLayout

    /** Two-panel layout. See [PanelLayout] for invariants. */
    data class Split(
        val orientation: PanelOrientation,
        val ratio: Float,
        val left: Single,
        val right: Single,
    ) : PanelLayout {
        init {
            require(ratio in MIN_RATIO..MAX_RATIO) {
                "ratio $ratio out of range [$MIN_RATIO..$MAX_RATIO]"
            }
        }
    }

    companion object {
        /** Lower bound for [Split.ratio]: a panel is never thinner than 20% of the cross axis. */
        const val MIN_RATIO: Float = 0.2f

        /** Upper bound for [Split.ratio]: a panel never takes more than 80% of the cross axis. */
        const val MAX_RATIO: Float = 0.8f

        /** Default ratio for a fresh split — equal halves. */
        const val DEFAULT_RATIO: Float = 0.5f
    }
}

/** Returns the [OpenDocuments] of [side] in [this]. `null` when [side] is [PanelSide.SECONDARY] of a [PanelLayout.Single]. */
internal fun PanelLayout.tabsOf(side: PanelSide): OpenDocuments? = when (this) {
    is PanelLayout.Single -> if (side == PanelSide.PRIMARY) tabs else null
    is PanelLayout.Split -> when (side) {
        PanelSide.PRIMARY -> left.tabs
        PanelSide.SECONDARY -> right.tabs
    }
}

/**
 * Returns a new [PanelLayout] obtained by replacing the [OpenDocuments]
 * of [side] via [transform]. When [transform] returns `null` the
 * affected panel collapses:
 * - in a [PanelLayout.Single], the result is `null` (the whole layout
 *   becomes empty — caller decides what to do, typically "pop the
 *   editor");
 * - in a [PanelLayout.Split], the surviving side becomes the new
 *   [PanelLayout.Single].
 */
internal fun PanelLayout.transformTabs(
    side: PanelSide,
    transform: (OpenDocuments) -> OpenDocuments?,
): PanelLayout? = when (this) {
    is PanelLayout.Single -> {
        if (side == PanelSide.PRIMARY) {
            transform(tabs)?.let { PanelLayout.Single(it) }
        } else {
            this
        }
    }
    is PanelLayout.Split -> when (side) {
        PanelSide.PRIMARY -> {
            val newLeft = transform(left.tabs)
            if (newLeft == null) right else copy(left = PanelLayout.Single(newLeft))
        }
        PanelSide.SECONDARY -> {
            val newRight = transform(right.tabs)
            if (newRight == null) left else copy(right = PanelLayout.Single(newRight))
        }
    }
}

/**
 * Splits a [PanelLayout.Single] into a [PanelLayout.Split] with the
 * existing tabs on the [PanelSide.PRIMARY] side and a fresh
 * single-tab [OpenDocuments] on [PanelSide.SECONDARY]. Throws on a
 * layout that is already split (the spec disallows deeper than one
 * level).
 */
internal fun PanelLayout.openInSplit(
    orientation: PanelOrientation,
    secondaryTabs: OpenDocuments,
    ratio: Float = PanelLayout.DEFAULT_RATIO,
): PanelLayout.Split {
    require(this is PanelLayout.Single) { "openInSplit requires a Single layout, got $this" }
    return PanelLayout.Split(
        orientation = orientation,
        ratio = ratio,
        left = this,
        right = PanelLayout.Single(secondaryTabs),
    )
}

/**
 * Returns a [PanelLayout.Split] copy with a clamped [PanelLayout.Split.ratio].
 * On a [PanelLayout.Single] the receiver is returned unchanged.
 */
internal fun PanelLayout.setRatio(ratio: Float): PanelLayout = when (this) {
    is PanelLayout.Single -> this
    is PanelLayout.Split -> copy(
        ratio = ratio.coerceIn(PanelLayout.MIN_RATIO, PanelLayout.MAX_RATIO),
    )
}
