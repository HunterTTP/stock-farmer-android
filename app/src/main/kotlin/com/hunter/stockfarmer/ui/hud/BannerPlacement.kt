package com.hunter.stockfarmer.ui.hud

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.ui.theme.HudScale

/** Where the banners and messages over the farm appear: in the top third of a phone's screen, and in the middle of a tablet's. */
object BannerPlacement {
    const val TOP_THIRD_OFFSET = 0.1f

    /** The gap above a banner: a tenth of a phone's height, none on a tablet where it is centered. */
    fun topOffset(
        screenHeight: Dp,
        isTablet: Boolean,
    ): Dp = if (isTablet) 0.dp else screenHeight * TOP_THIRD_OFFSET

    fun alignment(isTablet: Boolean): Alignment = if (isTablet) Alignment.Center else Alignment.TopCenter
}

/** Places [content] as a banner over the farm (iOS `placedAsBanner`), with the 16 point side margin the banners have. */
@Composable
fun BannerBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= HudScale.TABLET_MIN_WIDTH_DP
    Box(
        modifier
            .fillMaxSize()
            .padding(top = BannerPlacement.topOffset(configuration.screenHeightDp.dp, isTablet))
            .padding(horizontal = 16.dp),
        contentAlignment = BannerPlacement.alignment(isTablet),
    ) { content() }
}
