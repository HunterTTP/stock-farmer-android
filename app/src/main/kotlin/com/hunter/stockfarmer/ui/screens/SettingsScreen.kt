package com.hunter.stockfarmer.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.audio.AudioChannel
import com.hunter.stockfarmer.audio.SoundCenter
import com.hunter.stockfarmer.audio.SoundSettings
import com.hunter.stockfarmer.core.model.ChangelogDay
import com.hunter.stockfarmer.game.GameSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

private data class InformationText(
    val privacy: String,
    val changelog: List<ChangelogDay>,
    val error: String? = null,
)

@Composable
fun SettingsScreen(
    session: GameSession,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val provided = LocalScreenAudio.current
    val audio = remember(context, provided) { provided ?: PreferenceScreenAudio(context) }
    val controls = remember(audio) { SoundControls(audio.settings) }
    var settings by remember(audio) { mutableStateOf(controls.settings) }
    val track by rememberScreenState(audio) { audio.trackName }
    val information by produceState<InformationText?>(null, context.assets) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    val privacy =
                        context.assets
                            .open("information/Privacy.md")
                            .bufferedReader()
                            .use { it.readText() }
                    val changelog =
                        context.assets
                            .open("information/Changelog.md")
                            .bufferedReader()
                            .use { it.readText() }
                    InformationText(privacy, ScreenInformation.changelog(changelog))
                }.getOrElse { InformationText("", emptyList(), "Couldn't load app information") }
            }
    }

    fun applyControls() {
        val next = controls.settings
        AudioChannel.entries.filter { settings[it] != next[it] }.forEach { audio.setVolume(it, next[it]) }
        settings = next
    }
    ScreenLayout("Settings", session, onClose) {
        ScreenCard {
            Text("Sound", style = MaterialTheme.typography.titleMedium)
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
                Figure(title, "${(settings[channel] * 100).toInt()}%")
                Slider(value = settings[channel].toFloat(), onValueChange = {
                    controls.setLevel(channel, it.toDouble())
                    applyControls()
                })
            }
            Button(onClick = {
                controls.reset()
                applyControls()
            }) { Text("Reset sound") }
            track?.let {
                Text("Now playing: $it")
                Button(onClick = audio::skipMusic) { Text("Next song") }
            }
        }
        ScreenCard {
            Text("Privacy", style = MaterialTheme.typography.titleMedium)
            Text(information?.privacy ?: "Loading…")
        }
        ScreenCard {
            Text("Changelog", style = MaterialTheme.typography.titleMedium)
            information?.error?.let { Text(it) }
            information?.changelog?.forEach { day ->
                Text(day.date, style = MaterialTheme.typography.titleSmall, color = FarmPurple)
                day.lines.forEach { Text("• $it") }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f))
        Switch(checked, onCheckedChange)
    }
}
