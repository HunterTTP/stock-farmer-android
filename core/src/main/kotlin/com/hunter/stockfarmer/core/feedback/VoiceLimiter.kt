package com.hunter.stockfarmer.core.feedback

/** A sound starts only on a finished voice; when every voice is busy the newcomer is dropped. */
@ConsistentCopyVisibility
data class VoiceLimiter<Effect> private constructor(
    private val voices: List<Voice<Effect>?>,
    private val lastStarted: Map<Effect, Double>,
    private val cooldown: Double,
    private val maxCopiesOfOneEffect: Int,
) {
    private data class Voice<Effect>(
        val effect: Effect,
        val endsAt: Double,
    )
    constructor(voiceCount: Int, cooldown: Double, maxCopiesOfOneEffect: Int) :
        this(List(voiceCount) { null }, emptyMap(), cooldown, maxCopiesOfOneEffect)

    fun request(
        effect: Effect,
        duration: Double,
        at: Double,
    ): Pair<VoiceLimiter<Effect>, Int?> {
        if (!(at - (lastStarted[effect] ?: Double.NEGATIVE_INFINITY) >= cooldown) ||
            voices.count { it != null && it.effect == effect && it.endsAt > at } >= maxCopiesOfOneEffect
        ) {
            return this to null
        }
        val index = voices.indexOfFirst { it == null || it.endsAt <= at }
        if (index < 0) return this to null
        return copy(
            voices = voices.mapIndexed { i, voice -> if (i == index) Voice(effect, at + duration) else voice },
            lastStarted = lastStarted + (effect to at),
        ) to index
    }

    fun reset(): VoiceLimiter<Effect> = copy(voices = List(voices.size) { null }, lastStarted = emptyMap())
}
