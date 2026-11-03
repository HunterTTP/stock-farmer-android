package com.hunter.stockfarmer.farm.animals

import com.hunter.stockfarmer.core.util.secondsSince
import java.time.Instant

internal data class Heart(
    val rise: Float,
    val alpha: Float,
)

/** Petting is independent of the body animation; repeating it replaces the pending heart sequence. */
internal class AnimalHearts {
    private val births = mutableListOf<Instant>()

    fun pet(now: Instant) {
        // Replacing the keyed action cancels future emissions, but its existing child hearts finish floating.
        births.removeAll { it > now || now.secondsSince(it) >= 1 }
        repeat(3) { index -> births += now.plusMillis(600L + index * 800L) }
    }

    fun hearts(now: Instant): List<Heart> {
        births.removeAll { now.secondsSince(it) >= 1 }
        return births.mapNotNull { birth ->
            val life = now.secondsSince(birth)
            if (life < 0) {
                null
            } else {
                Heart(
                    (life.coerceAtLeast(0.0) * 40).toFloat(),
                    (if (life <= 0.5) 1.0 else (1 - life) / 0.5).toFloat(),
                )
            }
        }
    }
}
