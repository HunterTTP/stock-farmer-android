package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.rules.activateSprinklers
import com.hunter.stockfarmer.core.rules.isSoilWateringChanging
import java.time.Instant

/** How often the farm checks on the land its sprinklers water, as iOS does: a few times a second. */
const val WATERING_INTERVAL_SECONDS = 0.25f

/**
 * A sprinkler set down waters its land tile by tile through its first spray, and one moved or destroyed lets its old
 * land dry out the same way. Says whether anything changed; it is saved by the autosave once nothing is changing.
 */
fun GameSession.waterWithSprinklers(now: Instant): Boolean {
    val changed = act { it.isSoilWateringChanging && it.activateSprinklers(now) }
    if (changed) commit()
    return changed
}
