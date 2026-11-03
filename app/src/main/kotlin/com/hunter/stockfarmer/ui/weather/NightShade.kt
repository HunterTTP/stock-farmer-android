package com.hunter.stockfarmer.ui.weather

/** The colour the whole farm is multiplied by at night, a deep blue; daylight multiplies by white, which changes nothing. */
object NightShade {
    const val DEEPEST_RED = 0.42
    const val DEEPEST_GREEN = 0.49
    const val DEEPEST_BLUE = 0.72

    /** One channel of the tint at [darkness], from 1 by day (0) down to [deepest] at full night (1). */
    fun channel(
        deepest: Double,
        darkness: Double,
    ) = 1 + (deepest - 1) * darkness
}
