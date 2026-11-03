package com.hunter.stockfarmer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.settings.PlaySettings
import com.hunter.stockfarmer.ui.theme.HudIcon
import com.hunter.stockfarmer.ui.theme.HudIcons
import com.hunter.stockfarmer.ui.theme.HudMaterialTheme
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.glassCard
import kotlin.math.roundToInt

/** Shadows, grid lines and the money and level panels. */
@Composable
fun GraphicsSettingsScreen(
    session: GameSession,
    onClose: () -> Unit,
) {
    val settings = PlaySettings.of(LocalContext.current)
    SettingsPage("Graphics", session, onClose) {
        Column(Modifier.fillMaxWidth().glassCard(14.dp)) {
            SettingToggle(
                "Dynamic Shadows",
                HudIcons.sunMaxFill,
                settings.isDynamicShadowsEnabled,
                settings::setDynamicShadows,
            )
            if (settings.isDynamicShadowsEnabled) {
                Slider(
                    value = settings.shadowIntensity,
                    onValueChange = settings::setShadowIntensity,
                    modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp),
                    colors = settingSliderColors,
                )
            }
            SettingToggle("Grid Lines", HudIcons.grid, settings.isGridShown, settings::setGridShown)
            SettingToggle("Show Money", HudIcons.dollarsignCircle, settings.isMoneyShown, settings::setMoneyShown)
            SettingToggle("Show Level", HudIcons.starFill, settings.isLevelShown, settings::setLevelShown)
        }
        ResetDefaultsButton("Graphics", settings::resetGraphicsDefaults)
    }
}

/** How far up and to the right of the finger a dragged tool starts working. */
@Composable
fun GameplaySettingsScreen(
    session: GameSession,
    onClose: () -> Unit,
) {
    val settings = PlaySettings.of(LocalContext.current)
    val reach = settings.toolReach
    SettingsPage("Gameplay", session, onClose) {
        Column(
            Modifier.fillMaxWidth().glassCard(14.dp).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SettingLabel("Tool Offset", HudIcons.arrowUpRight, Modifier.weight(1f))
                BasicText("${reach.roundToInt()} pt", style = HudType.subheadline.copy(color = Theme.textSecondary))
                BasicText(
                    "Reset",
                    Modifier.clickable(
                        enabled = reach != PlaySettings.DEFAULT_TOOL_REACH,
                        role = Role.Button,
                        onClick = settings::resetGameplayDefaults,
                    ),
                    style =
                        HudType.subheadline.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (reach == PlaySettings.DEFAULT_TOOL_REACH) Theme.textSecondary else Theme.accent,
                        ),
                )
            }
            Slider(
                value = reach,
                onValueChange = settings::setToolReach,
                valueRange = 0f..PlaySettings.MAX_TOOL_REACH,
                steps = PlaySettings.MAX_TOOL_REACH.toInt() - 1,
                colors = settingSliderColors,
            )
            BasicText(
                "How far up and to the right of the dragged circle's center a tool works; 0 is right under it",
                style = HudType.caption.copy(color = Theme.textSecondary),
            )
        }
        ResetDefaultsButton("Gameplay", settings::resetGameplayDefaults)
    }
}

internal val settingSliderColors
    @Composable get() =
        SliderDefaults.colors(
            thumbColor = Theme.accent,
            activeTrackColor = Theme.accent,
            inactiveTrackColor = Theme.accent.copy(alpha = 0.24f),
        )

/** A setting's name with its icon. */
@Composable
internal fun SettingLabel(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        HudIcon(icon, Theme.textPrimary, size = 20.dp)
        BasicText(title, style = HudType.body.copy(color = Theme.textPrimary))
    }
}

@Composable
private fun SettingToggle(
    title: String,
    icon: ImageVector,
    isOn: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingLabel(title, icon, Modifier.weight(1f))
        Switch(
            checked = isOn,
            onCheckedChange = onChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Theme.onAccent,
                    checkedTrackColor = Theme.accent,
                    uncheckedThumbColor = Theme.textSecondary,
                    uncheckedTrackColor = Theme.glassFill,
                ),
        )
    }
}

/** The page's "Reset to Defaults" button, which asks before it puts the settings back. */
@Composable
internal fun ResetDefaultsButton(
    pageTitle: String,
    onReset: () -> Unit,
) {
    var confirmsReset by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .glassCard(14.dp)
            .clickable(role = Role.Button) { confirmsReset = true }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            "Reset to Defaults",
            style = HudType.headline.copy(color = Theme.onNeutral, textAlign = TextAlign.Center),
        )
    }
    if (confirmsReset) {
        HudMaterialTheme {
            AlertDialog(
                onDismissRequest = { confirmsReset = false },
                title = {
                    Text(
                        "Reset $pageTitle to defaults?",
                        style = HudType.title3.copy(fontWeight = FontWeight.Bold),
                        color = Theme.textPrimary,
                    )
                },
                text = {
                    Text(
                        "Your ${pageTitle.lowercase()} settings go back to how the game first came.",
                        style = HudType.subheadline,
                        color = Theme.textSecondary,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmsReset = false
                            onReset()
                        },
                    ) { Text("Reset", style = HudType.headline, color = Theme.danger) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmsReset = false }) {
                        Text("Cancel", style = HudType.subheadline, color = Theme.textSecondary)
                    }
                },
            )
        }
    }
}
