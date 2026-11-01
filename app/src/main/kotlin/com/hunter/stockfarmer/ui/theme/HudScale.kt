package com.hunter.stockfarmer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

/** How much larger than on a phone the controls floating over the farm are drawn: the sizes in them are written for a phone. */
val LocalHudScale = staticCompositionLocalOf { 1f }

object HudScale {
    const val TABLET_SCALE = 1.3f

    /** Android's "medium" window width starts at 600dp; treat that as the regular size class. */
    const val TABLET_MIN_WIDTH_DP = 600

    fun forWidth(widthDp: Int): Float = if (widthDp >= TABLET_MIN_WIDTH_DP) TABLET_SCALE else 1f

    fun isTablet(scale: Float): Boolean = scale > 1f
}

/** Draws the controls over the farm larger on a tablet, where a phone's sizes look small and crowded. */
@Composable
fun ScalesHudForTablets(content: @Composable () -> Unit) {
    val width = LocalConfiguration.current.screenWidthDp
    CompositionLocalProvider(LocalHudScale provides HudScale.forWidth(width), content = content)
}
