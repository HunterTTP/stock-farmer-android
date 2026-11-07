package com.hunter.stockfarmer.audio

import com.hunter.stockfarmer.core.catalog.SeasonalSoundtrack
import com.hunter.stockfarmer.core.feedback.MusicLoudness
import com.hunter.stockfarmer.core.feedback.SoundMix
import com.hunter.stockfarmer.core.model.Playlist
import com.hunter.stockfarmer.core.rules.NightPulse
import com.hunter.stockfarmer.core.rules.Season
import java.net.URI

/** Callbacks and commands run on the same thread. Loading a new track cancels the previous one. */
interface MusicOutput : AutoCloseable {
    fun load(
        path: String,
        ready: () -> Unit,
        completed: () -> Unit,
        failed: () -> Unit,
    )

    fun start()

    fun pause()

    fun stop()

    fun setVolume(
        level: Double,
        fadeSeconds: Double = 0.0,
    )
}

/** Seasonal playlist positions survive pauses and season changes. */
class MusicPlayer(
    private val output: MusicOutput,
    playlists: Map<Season, Playlist>,
) : AutoCloseable {
    private val playlists = playlists.toMutableMap()
    private var season = Season.spring
    private var active = false
    private var otherAudio = false
    private var hushed = false
    private var loaded = false
    private var ready = false
    private var closed = false
    private var generation = 0
    private var gain = 1.0

    /** When the fanfare that turned the music down ends, in the caller's seconds, while one is playing. */
    private var duckEndsAt: Double? = null

    var trackName: String? = null
        private set
    var onTrackChange: ((String?) -> Unit)? = null

    private val canPlay: Boolean get() = active && gain > 0 && !otherAudio && !closed
    private val volume: Double get() {
        if (hushed) return 0.0
        val duck = if (duckEndsAt == null) 1.0 else SoundMix.musicDuckGain
        return gain * MusicLoudness.gain(trackName.orEmpty()) * duck
    }

    fun setSeason(requested: Season) {
        val next = if (playlists[requested]?.current == null) Season.spring else requested
        if (next == season || closed) return
        season = next
        discardTrack()
        resume()
    }

    /**
     * The share of full volume the Music and Master sliders leave: the soundtrack follows at once, stops when it can't be
     * heard, and starts again when turned up.
     */
    fun setGain(value: Double) {
        gain = if (value.isFinite()) value.coerceIn(0.0, 1.0) else 0.0
        output.setVolume(volume)
        if (canPlay) resume() else output.pause()
    }

    /**
     * Turns the soundtrack down while a fanfare plays, from [now] for [seconds]; a fanfare that starts during another keeps
     * it down until the later one ends. Returns whether the duck now ends later, so the caller calls [endDuck] then.
     */
    fun duck(
        seconds: Double,
        now: Double,
    ): Boolean {
        val endsAt = now + seconds
        if (closed || !endsAt.isFinite() || endsAt <= (duckEndsAt ?: Double.NEGATIVE_INFINITY)) return false
        val wasDucked = duckEndsAt != null
        duckEndsAt = endsAt
        if (!wasDucked) output.setVolume(volume, SoundMix.duckFade)
        return true
    }

    /** The fanfare is over: the soundtrack comes back up. */
    fun endDuck() {
        if (duckEndsAt == null || closed) return
        duckEndsAt = null
        output.setVolume(volume, SoundMix.duckFade)
    }

    fun setActive(value: Boolean) {
        active = value
        if (canPlay) resume() else output.pause()
    }

    fun setOtherAudioPlaying(value: Boolean) {
        otherAudio = value
        if (canPlay) resume() else output.pause()
    }

    /** The caller identifies the night whose chime was started, so opening a save at midnight does not hush it. */
    fun updateNight(
        minuteOfDay: Double,
        chimeForCurrentNight: Boolean,
    ) {
        val next = chimeForCurrentNight && NightPulse.isMusicHushed(minuteOfDay)
        if (next == hushed || closed) return
        hushed = next
        output.setVolume(volume, 2.0)
    }

    fun skip() {
        if (!canPlay) return
        playlists[season] = (playlists[season] ?: return).advance()
        discardTrack()
        resume()
    }

    private fun resume() {
        if (!canPlay) return
        if (loaded) {
            if (ready) output.start()
            return
        }
        val track = playlists[season]?.current ?: return
        val token = ++generation
        loaded = true
        trackName = track.path.substringAfterLast('/').substringBeforeLast('.')
        output.load(
            track.path.removePrefix("/"),
            ready = {
                if (token == generation && !closed) {
                    ready = true
                    output.setVolume(volume)
                    onTrackChange?.invoke(trackName)
                    if (canPlay) output.start()
                }
            },
            completed = { if (token == generation && !closed) finished() },
            failed = { if (token == generation && !closed) discardTrack() },
        )
    }

    private fun finished() {
        // Completion can arrive just after a pause. Remember the next track without starting it in the background.
        playlists[season] = (playlists[season] ?: return).advance()
        discardTrack()
        resume()
    }

    private fun discardTrack() {
        generation++
        loaded = false
        ready = false
        output.stop()
        if (trackName != null) {
            trackName = null
            onTrackChange?.invoke(null)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        discardTrack()
        output.close()
    }

    companion object {
        fun playlists(assetExists: (String) -> Boolean): Map<Season, Playlist> =
            Season.entries.associateWith { season ->
                Playlist(
                    SeasonalSoundtrack
                        .tracks(season)
                        .map { "audio/Music/$it.m4a" }
                        .filter(assetExists)
                        .map { URI(null, null, "/$it", null) },
                )
            }
    }
}
