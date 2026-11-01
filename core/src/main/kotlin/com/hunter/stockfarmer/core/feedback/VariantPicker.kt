package com.hunter.stockfarmer.core.feedback

import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.nextInt

/** Chooses a recording so a repeated action does not sound the same each time. */
@ConsistentCopyVisibility
data class VariantPicker<Effect> private constructor(
    private val previous: Map<Effect, Int>,
) {
    constructor() : this(emptyMap())

    fun pick(
        effect: Effect,
        among: Int,
        using: RandomSource,
    ): Pair<VariantPicker<Effect>, Int> {
        if (among <= 1) return this to 0
        var choice = using.nextInt(0..among - 2)
        val last = previous[effect]
        if (last != null && choice >= last) choice += 1
        return copy(previous = previous + (effect to choice)) to choice
    }
}
