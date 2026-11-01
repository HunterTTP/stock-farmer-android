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
    companion object {
        fun forCue(
            cue: SoundCue,
            placement: SoundPlacement,
            settings: SoundSettings,
        ): StereoVolume? {
            val heard = if (cue.fadesWithDistance) placement.gain else 1.0
            val level = settings[AudioChannel.of(cue.category)]
            if (!heard.isFinite() || heard < 0.02 || level <= 0.0) return null
            val volume = (level * cue.trimGain * heard).coerceIn(0.0, 1.0)
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

    fun stop(stream: Int)
}

/** Main-thread controller; the clock is monotonic seconds, independent of the farm's clock. */
class SoundEffectsPlayer(
    private val output: EffectOutput,
    private val seconds: () -> Double,
    private val random: RandomSource = SystemRandomSource,
) : AutoCloseable {
    private data class Voice(
        val cue: SoundCue,
        val stream: Int,
    )

    private var limiter = VoiceLimiter<SoundCue>(VOICE_COUNT, 0.03, 6)
    private var variants = VariantPicker<SoundCue>()
    private val voices = arrayOfNulls<Voice>(VOICE_COUNT)
    private val lastStreams = mutableMapOf<SoundCue, Int>()
    private var suspended = true
    private var closed = false

    var settings = SoundSettings()
        set(value) {
            field = value
            voices.indices
                .filter { index ->
                    voices[index]?.let { value[AudioChannel.of(it.cue.category)] == 0.0 } == true
                }.forEach(::cutShort)
            if (AudioChannel.entries.filter { it != AudioChannel.music }.all { value[it] == 0.0 }) silence()
        }

    fun play(
        cue: SoundCue,
        placement: SoundPlacement = SoundPlacement.centered,
    ): Boolean {
        if (closed || suspended) return false
        val volume = StereoVolume.forCue(cue, placement, settings) ?: return false
        val choices = output.clips(cue)
        if (choices.isEmpty()) return false
        val (nextVariants, choice) = variants.pick(cue, choices.size, random)
        variants = nextVariants
        val clip = choices[choice]
        val (nextLimiter, voice) = limiter.request(cue, clip.duration, seconds())
        if (voice == null) return false
        val stream = output.play(clip, volume)
        if (stream == 0) return false
        limiter = nextLimiter
        voices[voice] = Voice(cue, stream)
        lastStreams[cue] = stream
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
        limiter = limiter.reset()
    }

    companion object {
        const val VOICE_COUNT = 16
    }
}
