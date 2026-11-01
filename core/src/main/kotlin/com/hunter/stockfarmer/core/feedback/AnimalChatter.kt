package com.hunter.stockfarmer.core.feedback

import com.hunter.stockfarmer.core.util.RandomSource

/** Each kind of animal speaks once in a while, at a random moment, and never two kinds together. */
@ConsistentCopyVisibility
data class AnimalChatter<Species> private constructor(
    private val period: Double,
    private val minimumGap: Double,
    private val triggers: Map<Species, RandomMomentTrigger>,
    private val lastSpoke: Double,
) {
    constructor(period: Double, minimumGap: Double) : this(period, minimumGap, emptyMap(), Double.NEGATIVE_INFINITY)

    fun nextSpeaker(
        at: Double,
        among: List<Species>,
        using: RandomSource,
    ): Pair<AnimalChatter<Species>, Species?> {
        if (!(at - lastSpoke >= minimumGap)) return this to null
        var next = this
        for (kind in among) {
            val (trigger, due) = (next.triggers[kind] ?: RandomMomentTrigger(period)).isDue(at, using)
            next = next.copy(triggers = next.triggers + (kind to trigger))
            if (due) return next.copy(lastSpoke = at) to kind
        }
        return next to null
    }
}
