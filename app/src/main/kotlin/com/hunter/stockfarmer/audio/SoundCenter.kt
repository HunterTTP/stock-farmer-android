package com.hunter.stockfarmer.audio

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
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
import kotlin.math.ceil

/** Activity-owned facade. All calls, including event batches from the game, belong on the main thread. */
@MainThread
class SoundCenter(
    context: Context,
) : DefaultLifecycleObserver,
    AutoCloseable {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("sound", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val effects = SoundEffectsPlayer(SoundPoolOutput(app.assets), ::seconds)
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
    private val endDuck = Runnable { music.endDuck() }

    val isActive: Boolean get() = active && !closed

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
        effects.onMusicDuck = ::duckMusic
        music.setGain(settings.mixedGain(AudioChannel.music))
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
        if (channel == AudioChannel.music || channel == AudioChannel.master) {
            music.setGain(settings.mixedGain(AudioChannel.music))
            focus.setActive(active && settings.isAudible(AudioChannel.music))
        }
        refreshRain()
    }

    /** A fanfare has started and will play for [duration] seconds: the music steps down under it. */
    private fun duckMusic(duration: Double) {
        if (closed || !music.duck(duration, seconds())) return
        handler.removeCallbacks(endDuck)
        handler.postDelayed(endDuck, ceil(duration * 1000).toLong())
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
        ambience.update(easedRain, snowing, settings.mixedGain(AudioChannel.ambience))
    }

    fun resume() {
        if (closed) return
        active = true
        effects.resume()
        focus.setActive(settings.isAudible(AudioChannel.music))
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

    private fun seconds(): Double = SystemClock.elapsedRealtime() / 1000.0

    override fun close() {
        if (closed) return
        pause()
        closed = true
        handler.removeCallbacks(endDuck)
        focus.close()
        effects.close()
        music.close()
        ambience.close()
    }
}
