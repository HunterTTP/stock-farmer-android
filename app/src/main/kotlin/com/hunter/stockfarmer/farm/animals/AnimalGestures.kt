package com.hunter.stockfarmer.farm.animals

import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.util.secondsSince
import java.time.Instant
import kotlin.math.abs

enum class AnimalGesture { PET, NUDGE }

data class AnimalHitTarget(
    val animal: AnimalRef,
    val x: Float,
    val y: Float,
    val inside: Boolean = false,
    /** Seconds until the current step ends. A nudge must wait until then. */
    val stepRemaining: Double = 0.0,
)

data class AnimalReaction(
    val animal: AnimalRef,
    val gesture: AnimalGesture,
    val delay: Double,
)

/** World coordinates, Y up. The nearest visible tile center wins, including during a step. */
fun animalAtTouch(
    x: Float,
    y: Float,
    animals: List<AnimalHitTarget>,
    tileSize: Float = 100f,
): AnimalRef? =
    animals
        .filter { !it.inside && abs(it.x - x) <= tileSize / 2 && abs(it.y - y) <= tileSize / 2 }
        .minByOrNull { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) }
        ?.animal

fun animalReaction(
    animal: AnimalRef,
    gesture: AnimalGesture,
    animals: List<AnimalHitTarget>,
): AnimalReaction? =
    animals.firstOrNull { it.animal == animal }?.let {
        AnimalReaction(animal, gesture, if (gesture == AnimalGesture.PET) 0.0 else it.stepRemaining.coerceAtLeast(0.0))
    }

fun reactionAtTouch(
    x: Float,
    y: Float,
    gesture: AnimalGesture,
    animals: List<AnimalHitTarget>,
): AnimalReaction? = animalAtTouch(x, y, animals)?.let { animalReaction(it, gesture, animals) }

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
