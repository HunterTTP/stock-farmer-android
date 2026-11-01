package com.hunter.stockfarmer.audio

import com.hunter.stockfarmer.core.feedback.AnimalChatter
import com.hunter.stockfarmer.core.feedback.SoundCue
import com.hunter.stockfarmer.core.feedback.SoundPlacement
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.core.util.randomElement

data class AnimalVoice(
    val cue: SoundCue,
    val placement: SoundPlacement,
    val isVisible: Boolean = true,
)

/** Each audible kind gets one random moment per 180 seconds, with a 15 second gap across kinds. */
class AnimalVoices(
    private val random: RandomSource = SystemRandomSource,
) {
    private var chatter = AnimalChatter<SoundCue>(180.0, 15.0)

    fun clear() {
        chatter = AnimalChatter(180.0, 15.0)
    }

    fun next(
        now: Double,
        animals: List<AnimalVoice>,
    ): AnimalVoice? {
        val heard = animals.filter { it.isVisible && it.placement.isCloseUp }.groupBy { it.cue }
        val (next, kind) = chatter.nextSpeaker(now, heard.keys.sortedBy { it.name }, random)
        chatter = next
        return heard[kind]?.randomElement(random)
    }
}
