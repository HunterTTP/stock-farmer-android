package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.util.secondsSince
import java.time.Instant

/** One-shot scene animation timings from BuildingLayer and DecorationLayer. */
class SceneInteractions {
    data class Motion(
        val name: String,
        val count: Int,
        val frame: Int,
        val width: Int,
        val height: Int,
    )

    private val buildings = mutableMapOf<GridPoint, Instant>()
    private val decorations = mutableMapOf<GridPoint, Instant>()

    fun interact(request: FarmSceneFeedback.Interaction) {
        (if (request.building) buildings else decorations)[request.origin] = request.at
    }

    fun door(
        origin: GridPoint,
        now: Instant,
    ): Int? = frame(buildings, origin, now, 14, 0.07)

    fun decoration(
        origin: GridPoint,
        placed: PlacedDecoration,
        season: Season,
        now: Instant,
    ): Motion? {
        if (season == Season.winter) return null
        val name =
            when (placed.decorationID) {
                "well" -> "well"
                "trunk" -> "chest2"
                "decorativeChest" -> if (placed.size == 2) "chest2" else "chest1"
                else -> return null
            }
        val count = if (name == "well") 16 else 20
        val index = frame(decorations, origin, now, count, 0.06) ?: return null
        return Motion(name, count, index, if (name == "well") 144 else 96, if (name == "well") 96 else 144)
    }

    fun winterRotation(
        origin: GridPoint,
        placed: PlacedDecoration,
        now: Instant,
    ): Float {
        if (placed.decorationID !in listOf("well", "trunk", "decorativeChest")) return 0f
        val age = decorations[origin]?.let { now.secondsSince(it) } ?: return 0f
        val radians =
            when {
                age < 0 -> {
                    0.0
                }

                age < 0.1 -> {
                    0.02 * age / 0.1
                }

                age < 0.2 -> {
                    0.02 - 0.04 * (age - 0.1) / 0.1
                }

                age < 0.3 -> {
                    -0.02 + 0.02 * (age - 0.2) / 0.1
                }

                else -> {
                    decorations.remove(origin)
                    0.0
                }
            }
        return Math.toDegrees(radians).toFloat()
    }

    private fun frame(
        starts: MutableMap<GridPoint, Instant>,
        origin: GridPoint,
        now: Instant,
        count: Int,
        duration: Double,
    ): Int? {
        val age = starts[origin]?.let { now.secondsSince(it) } ?: return null
        if (age >= count * duration) {
            starts.remove(origin)
            return null
        }
        return (age.coerceAtLeast(0.0) / duration).toInt().coerceAtMost(count - 1)
    }
}
