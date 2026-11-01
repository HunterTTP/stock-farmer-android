package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.UnlockOrder
import com.hunter.stockfarmer.core.model.DaySummary
import com.hunter.stockfarmer.core.model.ItemSale
import com.hunter.stockfarmer.core.model.roundedToCents
import com.hunter.stockfarmer.core.util.secondsSince
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

internal fun GameEngine.recordSale(
    itemID: String,
    name: String,
    quantity: Int,
    earnings: Double,
) {
    state.career.addGrossIncome(earnings)
    val sale = state.salesToday[itemID] ?: ItemSale(itemID, name, 0, 0.0)
    state.salesToday[itemID] =
        ItemSale(itemID, name, sale.quantity + quantity, (sale.earnings + earnings).roundedToCents)
}

internal fun GameEngine.recordSale(
    item: StorageItem,
    quantity: Int,
    earnings: Double,
) = recordSale(item.id, item.name, quantity, earnings)

/** Ends the day as the midnight wave sets off, once for each night however often the clock is read inside the wave. */
fun GameEngine.beginNewDayIfDue(now: Instant): DaySummary? {
    if (!NightPulse.isChimeDue(minuteOfDay(now))) return null
    val night = NightPulse.day(now, state.clockOffset)
    if (state.lastNewDayNight == night) return null
    state.lastNewDayNight = night
    return beginNextDay()
}

/** A game opened in the middle of the wave doesn't begin its day: that midnight was not passed while playing. */
fun GameEngine.skipNewDayInProgress(now: Instant) {
    if (!NightPulse.isChimeDue(minuteOfDay(now))) return
    state.lastNewDayNight = NightPulse.day(now, state.clockOffset)
}

/** Ends the day: counts the next one and hands back what sold in the one just over, starting the tally afresh. */
fun GameEngine.beginNextDay(): DaySummary {
    val sales = state.salesToday.values.sortedWith(compareByDescending<ItemSale> { it.earnings }.thenBy { it.itemID })
    state.salesToday = mutableMapOf()
    state.career.closeDay()
    state.day += 1
    return DaySummary(state.day, sales)
}

/** The minute of the day on the farm's clock. */
fun GameEngine.minuteOfDay(now: Instant): Double = DayNightCycle.minuteOfDay(now, state.clockOffset)

/**
 * Sets the clock to a minute of the day, which then carries on from there. It moves within the farm's day and never to
 * another one, so setting the time can't make a night begin or be missed: the nights are counted in days of this clock.
 */
fun GameEngine.setTimeOfDay(
    minute: Double,
    now: Instant,
) {
    val target = min(max(0.0, minute), DayNightCycle.minutesPerDay - 1)
    state.clockOffset += (target - minuteOfDay(now)) * (DayNightCycle.secondsPerDay / DayNightCycle.minutesPerDay)
}

/** The minute the Midnight preset sets: just after midnight, when the wave of the new day has begun to roll. */
const val MIDNIGHT_PRESET_MINUTE = 1.0

/**
 * Sets the clock to a minute after midnight and lets that night begin again, as it does when the clock reaches midnight:
 * the next [beginNewDayIfDue] counts a day, and the chime and wave follow.
 */
fun GameEngine.setTimeToMidnight(now: Instant) {
    setTimeOfDay(MIDNIGHT_PRESET_MINUTE, now)
    state.lastNewDayNight = null
}

/**
 * The farm's days and nights only pass while the game is open: the clock is wound back by the time spent away, so it
 * reads what it read when the player left. Crops, animals and everything else timed carry on in real time.
 */
fun GameEngine.holdClockWhileAway(now: Instant) {
    val leftAt = state.lastSavedAt ?: return
    holdClockWhileAway(leftAt, now)
}

/**
 * For a game that was left while open, when the moment it was left is known better than its last save: the app is saved
 * again each time it goes inactive, even on its way back from the background.
 */
fun GameEngine.holdClockWhileAway(
    leftAt: Instant,
    now: Instant,
) {
    state.clockOffset -= max(0.0, now.secondsSince(leftAt))
}

data class CropGrowthSummary(
    val crop: Crop,
    val count: Int,
    /** How many of the plants are ready to harvest now. */
    val readyCount: Int,
    val remaining: Double,
) {
    val id: String get() = crop.id
}

/** Only planted types, in unlock order, with the wait until their last plant is ready. */
fun GameEngine.cropGrowthOverview(now: Instant): List<CropGrowthSummary> {
    val conditions = GrowthConditions(state)
    val groups =
        state.plots
            .filter { (tile, plot) -> plot.isAnchor(tile) }
            .entries
            .groupBy { it.value.cropID }
    return UnlockOrder.sorted(Crop.all) { it.unlockLevel }.mapNotNull { crop ->
        val plants = groups[crop.id] ?: return@mapNotNull null
        val remaining =
            plants.maxOfOrNull { (point, plot) -> conditions.remainingTime(plot, crop, plot.origin ?: point, now) }
                ?: 0.0
        val readyCount = plants.count { (point, plot) -> conditions.isReady(plot, crop, point, now) }
        CropGrowthSummary(crop, plants.size, readyCount, remaining)
    }
}
