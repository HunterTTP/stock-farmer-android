package com.hunter.stockfarmer.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.audio.AudioChannel
import com.hunter.stockfarmer.audio.SoundCenter
import com.hunter.stockfarmer.audio.SoundSettings
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.hud.ToolRoute
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.PillButton
import com.hunter.stockfarmer.ui.theme.PillRole
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.glassCard

interface ScreenAudio {
    val settings: SoundSettings
    val trackName: String? get() = null

    fun setVolume(
        channel: AudioChannel,
        level: Double,
    )

    fun skipMusic() = Unit
}

class SoundCenterScreenAudio(
    private val center: SoundCenter,
) : ScreenAudio {
    override val settings: SoundSettings get() = center.settings
    override val trackName: String? get() = center.trackName

    override fun setVolume(
        channel: AudioChannel,
        level: Double,
    ) = center.setVolume(channel, level)

    override fun skipMusic() = center.skipMusic()
}

/** Supply the activity's audio facade here for immediate volume and soundtrack controls. */
val LocalScreenAudio = staticCompositionLocalOf<ScreenAudio?> { null }

private class PreferenceScreenAudio(
    context: Context,
) : ScreenAudio {
    private val preferences = context.applicationContext.getSharedPreferences("sound", Context.MODE_PRIVATE)
    override val settings: SoundSettings get() =
        SoundSettings.from(
            preferences.all.mapValues { (_, value) ->
                if (value is String) value.toDoubleOrNull() else value
            },
        )

    override fun setVolume(
        channel: AudioChannel,
        level: Double,
    ) {
        val clamped = settings.withLevel(channel, level)[channel]
        preferences.edit().putString(channel.preferenceKey, clamped.toString()).apply()
    }
}

private const val SOURCE_CODE_URL = "https://github.com/HunterTTP/stock-farmer-android"

/** The Settings page of the tools sheet: farm photo and links to the pages under it (iOS `SettingsScreen` main page). */
@Composable
fun SettingsScreen(
    session: GameSession,
    onOpen: (ToolRoute) -> Unit,
    onPhoto: () -> Unit,
    isRecording: Boolean,
    onClose: () -> Unit,
) {
    ScreenLayout("Settings", session, onClose) {
        ScreenLink("Screenshot Farm", onClick = onPhoto)
        ScreenLink("Timelapse") { onOpen(ToolRoute.Timelapse) }
        ScreenLink("Load Game") { onOpen(ToolRoute.LoadGame) }
        ScreenLink("Sound") { onOpen(ToolRoute.SoundSettings) }
        ScreenLink("Performance", badge = if (isRecording) "Recording" else null) { onOpen(ToolRoute.Performance) }
        ScreenLink("About") { onOpen(ToolRoute.About) }
    }
}

/** Where the game's code lives, and its changelog and privacy policy. */
@Composable
fun AboutScreen(
    session: GameSession,
    onOpen: (ToolRoute) -> Unit,
    onClose: () -> Unit,
) {
    val uri = LocalUriHandler.current
    ScreenLayout("About", session, onClose) {
        ScreenLink("Changelog") { onOpen(ToolRoute.Changelog) }
        ScreenLink("Source Code") { uri.openUri(SOURCE_CODE_URL) }
        ScreenLink("Privacy") { onOpen(ToolRoute.Privacy) }
    }
}

@Composable
private fun ScreenLink(
    title: String,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glassCard(14.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            title,
            Modifier.weight(1f),
            style = HudType.headline.copy(color = Theme.textPrimary),
        )
        badge?.let { BasicText(it, style = HudType.subheadline.copy(color = Theme.spend)) }
        BasicText(
            "›",
            style = HudType.headline.copy(fontWeight = FontWeight.Bold, color = Theme.textSecondary),
        )
    }
}

/** Volume sliders per channel, with the music switch and the song playing (iOS `SettingsScreen` sound page). */
@Composable
fun SoundSettingsScreen(
    session: GameSession,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val provided = LocalScreenAudio.current
    val audio = remember(context, provided) { provided ?: PreferenceScreenAudio(context) }
    val controls = remember(audio) { SoundControls(audio.settings) }
    var settings by remember(audio) { mutableStateOf(controls.settings) }
    val track by rememberScreenState(audio) { audio.trackName }

    fun applyControls() {
        val next = controls.settings
        AudioChannel.entries.filter { settings[it] != next[it] }.forEach { audio.setVolume(it, next[it]) }
        settings = next
    }
    ScreenLayout("Sound", session, onClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .glassCard(14.dp)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingSwitch("Music", controls.musicEnabled) {
                controls.toggleMusic(it)
                applyControls()
            }
            SettingSwitch("Sound effects", controls.effectsEnabled) {
                controls.toggleEffects(it)
                applyControls()
            }
            AudioChannel.entries.forEach { channel ->
                val title =
                    when (channel) {
                        AudioChannel.music -> "Music"
                        AudioChannel.`interface` -> "Menus"
                        AudioChannel.farm -> "Farm"
                        AudioChannel.animals -> "Animals"
                        AudioChannel.ambience -> "Ambience"
                    }
                VolumeRow(
                    title = title,
                    value = settings[channel].toFloat(),
                    onValueChange = {
                        controls.setLevel(channel, it.toDouble())
                        applyControls()
                    },
                )
            }
            track?.let {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        "Now playing: $it",
                        Modifier.weight(1f),
                        style = HudType.subheadline.copy(color = Theme.textSecondary),
                    )
                    PillButton(
                        title = "Next song",
                        onClick = audio::skipMusic,
                        role = PillRole.neutral,
                    )
                }
            }
        }
        PillButton(
            title = "Reset sound",
            onClick = {
                controls.reset()
                applyControls()
            },
            modifier = Modifier.fillMaxWidth(),
            role = PillRole.neutral,
        )
    }
}

@Composable
private fun VolumeRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                title,
                style = HudType.headline.copy(color = Theme.textPrimary),
            )
            BasicText(
                "${(value * 100).toInt()}%",
                style = HudType.subheadline.copy(color = Theme.textSecondary),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors =
                SliderDefaults.colors(
                    thumbColor = Theme.accent,
                    activeTrackColor = Theme.accent,
                    inactiveTrackColor = Theme.accent.copy(alpha = 0.24f),
                ),
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            title,
            Modifier.weight(1f),
            style = HudType.headline.copy(color = Theme.textPrimary),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Theme.onAccent,
                    checkedTrackColor = Theme.accent,
                    uncheckedThumbColor = Theme.textSecondary,
                    uncheckedTrackColor = Theme.glassFill,
                    uncheckedBorderColor = Theme.panelBorder,
                ),
        )
    }
}
