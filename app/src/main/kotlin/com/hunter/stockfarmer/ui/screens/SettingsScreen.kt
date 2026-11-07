package com.hunter.stockfarmer.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.hunter.stockfarmer.audio.AudioChannel
import com.hunter.stockfarmer.audio.SoundCenter
import com.hunter.stockfarmer.audio.SoundSettings
import com.hunter.stockfarmer.core.feedback.RepeatedTap
import com.hunter.stockfarmer.core.feedback.SoundCue
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.settings.PlaySettings
import com.hunter.stockfarmer.ui.hud.ToolRoute
import com.hunter.stockfarmer.ui.rememberPolledState
import com.hunter.stockfarmer.ui.theme.HudIcon
import com.hunter.stockfarmer.ui.theme.HudIcons
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.glassCard
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

interface ScreenAudio {
    val settings: SoundSettings
    val trackName: String? get() = null

    fun setVolume(
        channel: AudioChannel,
        level: Double,
    )

    fun skipMusic() = Unit

    fun playSample(channel: AudioChannel) = Unit
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

    override fun skipMusic() {
        center.skipMusic()
        center.play(SoundCue.toggleOn)
    }

    /** A short sound of its kind at the new loudness: a click for the master and the effects, a bird for the ambience. */
    override fun playSample(channel: AudioChannel) {
        when (channel) {
            AudioChannel.master, AudioChannel.effects -> SoundCue.selectItem
            AudioChannel.ambience -> SoundCue.birdChirp
            AudioChannel.music -> null
        }?.let { center.play(it) }
    }
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
        preferences.edit { putString(channel.preferenceKey, clamped.toString()) }
    }
}

private const val SOURCE_CODE_URL = "https://github.com/HunterTTP/stock-farmer-android"

/** The Settings page of the tools sheet: farm photo and links to the pages under it. */
@Composable
fun SettingsScreen(
    session: GameSession,
    onOpen: (ToolRoute) -> Unit,
    onPhoto: () -> Unit,
    isRecording: Boolean,
    onClose: () -> Unit,
) {
    SettingsPage("Settings", session, onClose) {
        FarmPhotoTile(onPhoto)
        ScreenLink("Timelapse", HudIcons.film) { onOpen(ToolRoute.Timelapse) }
        ScreenLink("Load Game", HudIcons.folderFill) { onOpen(ToolRoute.LoadGame) }
        ScreenLink("Sound", HudIcons.speakerWave2Fill) { onOpen(ToolRoute.SoundSettings) }
        ScreenLink("Graphics", HudIcons.sparkle) { onOpen(ToolRoute.GraphicsSettings) }
        ScreenLink("Gameplay", HudIcons.gamecontrollerFill) { onOpen(ToolRoute.GameplaySettings) }
        ScreenLink("Performance", HudIcons.speedometer, badge = if (isRecording) "Recording" else null) {
            onOpen(ToolRoute.Performance)
        }
        ScreenLink("About", HudIcons.infoCircle) { onOpen(ToolRoute.About) }
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
    val context = LocalContext.current
    val play = remember(context) { PlaySettings.of(context) }
    val version = remember(context) { appVersion(context) }
    val versionTaps = remember { RepeatedTap(required = VERSION_TAPS, window = VERSION_TAP_WINDOW) }
    SettingsPage("About", session, onClose) {
        ScreenLink("Changelog", HudIcons.listBulletRectangle) { onOpen(ToolRoute.Changelog) }
        ScreenLink("Source Code", HudIcons.codeBrackets, trailing = HudIcons.arrowUpRight) {
            uri.openUri(SOURCE_CODE_URL)
        }
        ScreenLink("Privacy", HudIcons.handRaised) { onOpen(ToolRoute.Privacy) }
        BasicText(
            if (play.isDeveloperTimeShown) "Version $version · dev" else "Version $version",
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable(interactionSource = null, indication = null) {
                    if (versionTaps.register(Instant.now())) play.setDeveloperTimeShown(!play.isDeveloperTimeShown)
                },
            style = HudType.footnote.copy(color = Theme.textSecondary, textAlign = TextAlign.Center),
        )
    }
}

/** Seven quick taps on the version line flip the time presets on the Time sheet, which the line confirms by ending in "dev". */
private const val VERSION_TAPS = 7
private val VERSION_TAP_WINDOW = Duration.ofSeconds(2)

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

/** The page body every settings screen shares: rows 12 apart, as on iOS. */
@Composable
internal fun SettingsPage(
    title: String,
    session: GameSession,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ScreenLayout(title, session, onClose) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** The camera tile: centered, 110 tall, wide enough for its caption. */
@Composable
private fun FarmPhotoTile(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .heightIn(min = 110.dp)
                .glassCard(18.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HudIcon(HudIcons.cameraFill, Theme.textPrimary, size = 34.dp)
            BasicText(
                "Screenshot Farm",
                style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold, color = Theme.textPrimary),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ScreenLink(
    title: String,
    icon: ImageVector,
    badge: String? = null,
    trailing: ImageVector = HudIcons.chevronRight,
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
        HudIcon(icon, Theme.textPrimary, size = 20.dp)
        BasicText(title, Modifier.weight(1f), style = HudType.headline.copy(color = Theme.textPrimary))
        badge?.let { BasicText(it, style = HudType.subheadline.copy(color = Theme.spend)) }
        HudIcon(trailing, Theme.textSecondary, size = 13.dp)
    }
}

/** Volume sliders per channel, with the music switch and the song playing. */
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
    val track by rememberPolledState(audio) { audio.trackName }

    fun applyControls() {
        val next = controls.settings
        AudioChannel.entries.filter { settings[it] != next[it] }.forEach { audio.setVolume(it, next[it]) }
        settings = next
    }
    SettingsPage("Sound", session, onClose) {
        Column(Modifier.fillMaxWidth().glassCard(14.dp)) {
            AudioChannel.entries.forEach { channel ->
                VolumeRow(
                    title = channel.title,
                    icon = channel.icon,
                    value = settings[channel].toFloat(),
                    onValueChange = {
                        controls.setLevel(channel, it.toDouble())
                        applyControls()
                    },
                    onRelease = { audio.playSample(channel) },
                )
                if (channel == AudioChannel.music && settings[channel] > 0) {
                    track?.let { SongRow(it, audio::skipMusic) }
                }
            }
        }
        ResetDefaultsButton("Sound") {
            controls.reset()
            applyControls()
        }
    }
}

/** The song playing, with a button to move on to the next. */
@Composable
private fun SongRow(
    song: String,
    onSkip: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            song,
            Modifier.weight(1f),
            style = HudType.subheadline.copy(color = Theme.textSecondary),
            maxLines = 1,
        )
        HudIcon(
            HudIcons.forwardFill,
            Theme.accent,
            Modifier.clickable(role = Role.Button, onClick = onSkip),
            size = 20.dp,
            contentDescription = "Skip Song",
        )
    }
}

private val AudioChannel.title: String
    get() =
        when (this) {
            AudioChannel.master -> "Master"
            AudioChannel.music -> "Music"
            AudioChannel.effects -> "Effects"
            AudioChannel.ambience -> "Ambience"
        }

private val AudioChannel.icon: ImageVector
    get() =
        when (this) {
            AudioChannel.master -> HudIcons.speakerWave3Fill
            AudioChannel.music -> HudIcons.musicNote
            AudioChannel.effects -> HudIcons.handTapFill
            AudioChannel.ambience -> HudIcons.cloudRainFill
        }

@Composable
private fun VolumeRow(
    title: String,
    icon: ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    onRelease: () -> Unit,
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
            SettingLabel(title, icon)
            BasicText(
                "${(value * 100).roundToInt()}%",
                style = HudType.subheadline.copy(color = Theme.textSecondary),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onRelease,
            colors = settingSliderColors,
        )
    }
}
