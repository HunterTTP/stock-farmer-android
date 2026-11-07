package com.hunter.stockfarmer.audio

import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.feedback.SoundCue
import com.hunter.stockfarmer.core.feedback.SoundPlacement
import com.hunter.stockfarmer.core.feedback.VariantPicker
import com.hunter.stockfarmer.core.feedback.VoiceLimiter
import com.hunter.stockfarmer.core.feedback.cuesFor
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PetSpecies
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.SystemRandomSource

data class EffectClip(
    val id: Int,
    val duration: Double,
)

data class StereoVolume(
    val left: Float,
    val right: Float,
) {
    operator fun times(gain: Double): StereoVolume = StereoVolume((left * gain).toFloat(), (right * gain).toFloat())

    companion object {
        /** A sound quieter than this is not worth a voice. */
        private const val AUDIBLE_GAIN = 0.02

        /** The volume a cue plays at: its own, then its channel's and the master's. */
        fun forCue(
            cue: SoundCue,
            placement: SoundPlacement,
            settings: SoundSettings,
        ): StereoVolume? =
            beforeSliders(cue, placement, settings)?.times(settings.mixedGain(AudioChannel.of(cue.category)))

        /**
         * The cue's own volume, split by its pan: its trim and, for an animal, how far away it is. It is null when the
         * sliders leave it unheard, or leave an animal too far away to be worth a voice.
         */
        fun beforeSliders(
            cue: SoundCue,
            placement: SoundPlacement,
            settings: SoundSettings,
        ): StereoVolume? {
            val channel = AudioChannel.of(cue.category)
            val heard = cue.trimGain * (if (cue.fadesWithDistance) placement.gain else 1.0)
            if (!heard.isFinite() || !settings.isAudible(channel)) return null
            if (cue.fadesWithDistance && heard * settings.mixedGain(channel) < AUDIBLE_GAIN) return null
            val volume = heard.coerceIn(0.0, 1.0)
            val pan = if (placement.pan.isFinite()) placement.pan.coerceIn(-1.0, 1.0) else 0.0
            // SoundPool has independent channel gains rather than AVAudioPlayerNode's pan control.
            return StereoVolume(
                (volume * (1.0 - maxOf(0.0, pan))).toFloat(),
                (volume * (1.0 + minOf(0.0, pan))).toFloat(),
            )
        }
    }
}

interface EffectOutput : AutoCloseable {
    fun clips(cue: SoundCue): List<EffectClip>

    fun play(
        clip: EffectClip,
        volume: StereoVolume,
    ): Int

    /** Changes the volume of a stream that may still be playing. */
    fun setVolume(
        stream: Int,
        volume: StereoVolume,
    )

    fun stop(stream: Int)
}

/**
 * Main-thread controller; the clock is monotonic seconds, independent of the farm's clock. Each channel has its own voices,
 * so a busy farm can never take the ambience's, and a volume change is heard at once on what is playing.
 */
class SoundEffectsPlayer(
    private val output: EffectOutput,
    private val seconds: () -> Double,
    private val random: RandomSource = SystemRandomSource,
) : AutoCloseable {
    private data class Voice(
        val cue: SoundCue,
        val stream: Int,
        /** Its own volume, before the sliders. */
        val volume: StereoVolume,
    )

    /** A channel's voices, which are [firstVoice] onwards in [voices]. */
    private data class Bus(
        val firstVoice: Int,
        val limiter: VoiceLimiter<SoundCue>,
    )

    private var buses =
        mapOf(
            AudioChannel.effects to Bus(0, limiter(EFFECT_VOICES)),
            AudioChannel.ambience to Bus(EFFECT_VOICES, limiter(AMBIENCE_VOICES)),
        )
    private var variants = VariantPicker<SoundCue>()
    private val voices = arrayOfNulls<Voice>(VOICE_COUNT)
    private val lastStreams = mutableMapOf<SoundCue, Int>()
    private var suspended = true
    private var closed = false

    /** Told how long a sound that turns the music down will play, in seconds, as it starts. */
    var onMusicDuck: ((Double) -> Unit)? = null

    var settings = SoundSettings()
        set(value) {
            field = value
            voices.forEachIndexed { index, voice ->
                if (voice == null) return@forEachIndexed
                val channel = AudioChannel.of(voice.cue.category)
                if (value.isAudible(channel)) {
                    output.setVolume(voice.stream, voice.volume * value.mixedGain(channel))
                } else {
                    cutShort(index)
                }
            }
            if (AudioChannel.effectChannels.none(value::isAudible)) silence()
        }

    fun play(
        cue: SoundCue,
        placement: SoundPlacement = SoundPlacement.centered,
    ): Boolean {
        if (closed || suspended) return false
        val channel = AudioChannel.of(cue.category)
        val own = StereoVolume.beforeSliders(cue, placement, settings) ?: return false
        val choices = output.clips(cue)
        if (choices.isEmpty()) return false
        val (nextVariants, choice) = variants.pick(cue, choices.size, random)
        variants = nextVariants
        val clip = choices[choice]
        val bus = buses.getValue(channel)
        val (nextLimiter, slot) = bus.limiter.request(cue, clip.duration, seconds())
        if (slot == null) return false
        val stream = output.play(clip, own * settings.mixedGain(channel))
        if (stream == 0) return false
        buses = buses + (channel to bus.copy(limiter = nextLimiter))
        voices[bus.firstVoice + slot] = Voice(cue, stream, own)
        lastStreams[cue] = stream
        if (cue.ducksMusic) onMusicDuck?.invoke(clip.duration)
        return true
    }

    fun react(
        events: List<GameEvent>,
        landscapeTool: LandscapeTool,
        petSpecies: (GridPoint) -> PetSpecies?,
        placement: (GridPoint) -> SoundPlacement,
    ) {
        for (sound in cuesFor(events, landscapeTool, petSpecies)) {
            play(sound.cue, sound.tile?.let(placement) ?: SoundPlacement.centered)
        }
        for (event in events) {
            if (event is GameEvent.TractorGathered) {
                play(SoundCue.cropPickup, placement(event.point))
            }
        }
    }

    fun stop(cue: SoundCue) {
        val stream = lastStreams[cue] ?: return
        val index = voices.indexOfFirst { it?.cue == cue && it.stream == stream }
        if (index >= 0) cutShort(index)
    }

    fun suspend() {
        suspended = true
        silence()
    }

    fun resume() {
        if (!closed) suspended = false
    }

    override fun close() {
        if (closed) return
        suspend()
        closed = true
        output.close()
    }

    private fun cutShort(index: Int) {
        voices[index]?.let { output.stop(it.stream) }
        voices[index] = null
    }

    private fun silence() {
        voices.indices.forEach(::cutShort)
        lastStreams.clear()
        buses = buses.mapValues { (_, bus) -> bus.copy(limiter = bus.limiter.reset()) }
    }

    companion object {
        /** How soon one sound may start again, and how many of it may sound at once. */
        private const val COOLDOWN = 0.03
        private const val MAX_COPIES_OF_ONE_EFFECT = 6
        private const val EFFECT_VOICES = 12
        private const val AMBIENCE_VOICES = 4
        const val VOICE_COUNT = EFFECT_VOICES + AMBIENCE_VOICES

        private fun limiter(voiceCount: Int) = VoiceLimiter<SoundCue>(voiceCount, COOLDOWN, MAX_COPIES_OF_ONE_EFFECT)
    }
}
