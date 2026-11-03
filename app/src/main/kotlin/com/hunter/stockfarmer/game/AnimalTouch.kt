package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.rules.AnimalRef
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
