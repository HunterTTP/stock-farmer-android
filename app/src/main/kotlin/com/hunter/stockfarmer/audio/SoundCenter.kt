package com.hunter.stockfarmer.audio

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.feedback.SoundCue
import com.hunter.stockfarmer.core.feedback.SoundPlacement
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PetSpecies
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.Season

/** Activity-owned facade. All calls, including event batches from the game, belong on the main thread. */
@MainThread
class SoundCenter(
    context: Context,
) : DefaultLifecycleObserver,
    AutoCloseable {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("sound", Context.MODE_PRIVATE)
    private val effects = SoundEffectsPlayer(SoundPoolOutput(app.assets), { SystemClock.elapsedRealtime() / 1000.0 })
    private val musicOutput = MediaPlayerOutput(app.assets)
    private val music =
        MusicPlayer(
            musicOutput,
            MusicPlayer.playlists { path ->
                app.assets
                    .list(path.substringBeforeLast('/'))
                    .orEmpty()
                    .contains(path.substringAfterLast('/'))
            },
        )
    private val ambience = AmbientPlayer(MediaPlayerOutput(app.assets, looping = true))
    private val focus =
        MusicAudioFocus(
            app.getSystemService(AudioManager::class.java),
            { musicOutput.isPlaying },
            { music.setOtherAudioPlaying(!it) },
        )
    private var active = false
    private var closed = false
    private var easedRain = 0.0
    private var snowing = false

    var settings: SoundSettings =
        SoundSettings.from(
            preferences.all.mapValues { (_, value) ->
                if (value is String) value.toDoubleOrNull() else value
            },
        )
        private set

    val trackName: String? get() = music.trackName
    var onTrackChange: ((String?) -> Unit)?
        get() = music.onTrackChange
        set(value) {
            music.onTrackChange = value
        }

    init {
        effects.settings = settings
        music.setLevel(settings[AudioChannel.music])
        music.setOtherAudioPlaying(true)
    }

    fun react(
        events: List<GameEvent>,
        landscapeTool: LandscapeTool,
        petSpecies: (GridPoint) -> PetSpecies?,
        placement: (GridPoint) -> SoundPlacement,
    ) {
        if (!closed) effects.react(events, landscapeTool, petSpecies, placement)
    }

    fun play(
        cue: SoundCue,
        placement: SoundPlacement = SoundPlacement.centered,
    ): Boolean = !closed && effects.play(cue, placement)

    fun stop(cue: SoundCue) {
        if (!closed) effects.stop(cue)
    }

    fun setVolume(
        channel: AudioChannel,
        level: Double,
    ) {
        if (closed) return
        settings = settings.withLevel(channel, level)
        preferences.edit().putString(channel.preferenceKey, settings[channel].toString()).apply()
        effects.settings = settings
        if (channel == AudioChannel.music) {
            music.setLevel(settings[channel])
            focus.setActive(active && settings[channel] > 0)
        }
        refreshRain()
    }

    fun updateMusic(
        season: Season,
        minuteOfDay: Double,
        chimeForCurrentNight: Boolean,
    ) {
        if (closed) return
        music.updateNight(minuteOfDay, chimeForCurrentNight)
        music.setSeason(season)
    }

    fun skipMusic() {
        if (!closed) music.skip()
    }

    fun updateRain(
        easedAmount: Double,
        isSnowing: Boolean,
    ) {
        if (closed) return
        easedRain = easedAmount
        snowing = isSnowing
        refreshRain()
    }

    private fun refreshRain() {
        ambience.update(easedRain, snowing, settings[AudioChannel.ambience])
    }

    fun resume() {
        if (closed) return
        active = true
        effects.resume()
        focus.setActive(settings[AudioChannel.music] > 0)
        music.setActive(true)
        ambience.setActive(true)
    }

    fun pause() {
        active = false
        effects.suspend()
        music.setActive(false)
        ambience.setActive(false)
        focus.setActive(false)
    }

    override fun onResume(owner: LifecycleOwner) = resume()

    override fun onPause(owner: LifecycleOwner) = pause()

    override fun onDestroy(owner: LifecycleOwner) {
        close()
        owner.lifecycle.removeObserver(this)
    }

    override fun close() {
        if (closed) return
        pause()
        closed = true
        focus.close()
        effects.close()
        music.close()
        ambience.close()
    }
}
