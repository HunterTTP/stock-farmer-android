package com.hunter.stockfarmer.ui.hud

/** The gap between the top safe area (status bar or display cutout) and the money and level panels. */
object HudTopSpacing {
    /** The least gap under the safe area, in dp at phone scale. */
    const val MIN_GAP_DP = 8f

    /** The least distance from the screen's top edge to the panels when the safe area reports little or nothing, in dp at phone scale. */
    const val MIN_FROM_EDGE_DP = 28f

    /**
     * The padding to add above the panels, in dp, once the top safe area of [insetTopDp] is already applied.
     * It is never less than [MIN_GAP_DP], and tops the panels up to [MIN_FROM_EDGE_DP] from the edge when a device
     * hides the status bar or reports a zero inset. Both grow with the HUD [scale]; the inset is already real dp and is not scaled.
     */
    fun extraTopPaddingDp(
        insetTopDp: Float,
        scale: Float,
    ): Float = maxOf(MIN_GAP_DP * scale, MIN_FROM_EDGE_DP * scale - insetTopDp.coerceAtLeast(0f))
}
