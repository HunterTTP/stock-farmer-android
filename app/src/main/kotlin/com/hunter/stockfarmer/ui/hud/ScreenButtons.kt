package com.hunter.stockfarmer.ui.hud

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.hudPanel

/**
 * The full screens the HUD can open over the farm. On iOS these live in the tools sheet; until the sheet navigation lands they
 * are reached from the strip below the status panels.
 */
enum class HudScreen(
    val label: String,
) {
    Quests("Quests"),
    Stocks("Stocks"),
    Career("Career"),
    Farms("Farms"),
    Timelapse("Timelapse"),
    Performance("Performance"),
    Changelog("Changelog"),
    Privacy("Privacy"),
    Settings("Settings"),
}

/** The strip of glass buttons that open the [HudScreen]s, the farm photo and Storage. */
@Composable
internal fun ScreenButtons(
    onStorage: () -> Unit,
    onPhoto: () -> Unit,
    onScreen: (HudScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ScreenButton("Storage", onStorage)
        ScreenButton("Photo", onPhoto)
        HudScreen.entries.forEach { ScreenButton(it.label) { onScreen(it) } }
    }
}

@Composable
private fun ScreenButton(
    label: String,
    onClick: () -> Unit,
) {
    BasicText(
        label,
        Modifier
            .hudPanel(12.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        maxLines = 1,
        style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold, color = Theme.textPrimary),
    )
}
