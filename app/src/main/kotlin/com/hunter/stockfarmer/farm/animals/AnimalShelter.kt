package com.hunter.stockfarmer.farm.animals

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.util.secondsSince
import java.time.Instant

internal data class AnimalEntrance(
    val front: GridPoint,
    val door: GridPoint,
)

internal data class ShelteredPose(
    val pose: AnimalPose,
    val alpha: Float,
    val inside: Boolean,
)

/** Only animates the doorway. The game owns the path to the front tile and the decision to shelter. */
internal class AnimalShelter {
    private var initialized = false
    private var inside = false
    private var changedAt = Instant.EPOCH
    private var door: GridPoint? = null

    fun update(
        pose: AnimalPose,
        entrance: AnimalEntrance?,
        sheltering: Boolean,
        now: Instant,
    ): ShelteredPose {
        val entering =
            sheltering && entrance != null && pose.row == entrance.front.row.toFloat() &&
                pose.col == entrance.front.col.toFloat() && !pose.walking
        if (entering && !inside) {
            inside = true
            door = entrance?.door
            changedAt = if (initialized) now else now.minusSeconds(1)
        } else if (inside && (!sheltering || entrance == null)) {
            inside = false
            changedAt = now
        }
        initialized = true
        val destination = door ?: return ShelteredPose(pose, 1f, false)
        if (inside && entrance != null) door = entrance.door
        val elapsed = now.secondsSince(changedAt).coerceAtLeast(0.0)
        if (!inside && elapsed >= 0.5) return ShelteredPose(pose, 1f, false)
        val progress = (elapsed / 0.5).coerceIn(0.0, 1.0).toFloat()
        val weight = if (inside) progress else 1 - progress
        val visible =
            if (inside) {
                (1 - (elapsed - 0.15) / 0.35).coerceIn(0.0, 1.0)
            } else {
                (elapsed / 0.35).coerceIn(0.0, 1.0)
            }
        return ShelteredPose(
            pose.copy(
                row = pose.row + (destination.row - pose.row) * weight,
                col = pose.col + (destination.col - pose.col) * weight,
                facing = if (inside) Facing.UP else Facing.DOWN,
                walking = elapsed < 0.5,
                elapsed = elapsed,
            ),
            visible.toFloat(),
            inside,
        )
    }
}
