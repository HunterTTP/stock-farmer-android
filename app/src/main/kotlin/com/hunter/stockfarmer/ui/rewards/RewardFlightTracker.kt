package com.hunter.stockfarmer.ui.rewards

import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEvent

/** Main-thread presentation state, driven by monotonic seconds without an Android or coroutine scheduler. */
class RewardFlightTracker {
    var flights: List<RewardFlight> = emptyList()
        private set
    var tally: HarvestTally? = null
        private set
    var moneyPulse = 0
        private set
    var xpPulse = 0
        private set
    var storagePulse = 0
        private set

    /**
     * Counts every change to what the HUD shows from this tracker: the flights and tally, the held totals and the pulses.
     * A tick that only moves time leaves it alone, so a screen can recompose on a change and not on every frame.
     */
    var version = 0
        private set

    private var heldSpending = 0.0
    private var heldEarnings = 0.0
    private var heldXP = 0
    private var flushAt: Double? = null
    private var nextID = 0L

    fun displayedMoney(money: Double): Double = maxOf(0.0, money + heldSpending - heldEarnings)

    fun displayedXP(xp: Int): Int = maxOf(0, xp - heldXP)

    fun clear() {
        version++
        flights = emptyList()
        tally = null
        flushAt = null
        heldSpending = 0.0
        heldEarnings = 0.0
        heldXP = 0
    }

    fun launch(
        events: List<GameEvent>,
        now: Double,
        position: (GridPoint) -> RewardPoint?,
    ) {
        tick(now)
        launchSpending(events, now, position)
        launchHarvest(events, now, position)
        val xp = events.filterIsInstance<GameEvent.QuestCompleted>().sumOf { it.xp }
        if (xp > 0) {
            heldXP += xp
            launch(RewardKind.XP(xp), RewardPoint(0.0, 0.0), now, delay = 0.8, startsAtBanner = true)
        }
    }

    /** Process deadlines in order, including when a slow frame spans both tally arrival and departure. */
    fun tick(now: Double) {
        while (true) {
            val landing = flights.minByOrNull { it.landingAt }
            val flush = flushAt ?: Double.POSITIVE_INFINITY
            val deadline = minOf(landing?.landingAt ?: Double.POSITIVE_INFINITY, flush)
            if (deadline > now || !deadline.isFinite()) return
            if (landing != null && landing.landingAt <= flush) {
                flights = flights.filterNot { it.id == landing.id }
                land(landing)
            } else {
                version++
                val finished = tally
                tally = null
                flushAt = null
                finished?.placedParts?.forEach { (kind, offset) ->
                    launch(kind, RewardPoint(0.0, 0.0), deadline, route = RewardRoute.FromTally(offset))
                }
            }
        }
    }

    private fun launchSpending(
        events: List<GameEvent>,
        now: Double,
        position: (GridPoint) -> RewardPoint?,
    ) {
        val spending =
            events.mapNotNull { event ->
                val (tile, cost) =
                    when (event) {
                        is GameEvent.Planted -> event.point to event.cost
                        is GameEvent.Fertilized -> event.point to event.cost
                        is GameEvent.Built -> event.point to event.cost
                        else -> return@mapNotNull null
                    }
                if (cost <= 0) return@mapNotNull null
                position(tile)?.let { it to cost }
            }
        val start = spending.map { it.first }.middle() ?: return
        val cost = spending.sumOf { it.second }
        heldSpending += cost
        launch(RewardKind.Spend(cost), start, now)
    }

    private data class Gathering(
        val source: HarvestSource,
        var count: Int = 0,
        var xp: Int = 0,
        var earned: Double = 0.0,
        val positions: MutableList<RewardPoint> = mutableListOf(),
    )

    private fun launchHarvest(
        events: List<GameEvent>,
        now: Double,
        position: (GridPoint) -> RewardPoint?,
    ) {
        val gatherings = linkedMapOf<HarvestSource, Gathering>()

        fun gather(
            id: String,
            sold: Boolean,
            xp: Int,
            earned: Double,
            tile: GridPoint?,
            count: Int = 1,
        ) {
            val spot = tile?.let(position) ?: return
            val item = StorageItem.of(id) ?: return
            val source = HarvestSource(item, sold)
            val gathering = gatherings.getOrPut(source) { Gathering(source) }
            gathering.count += count
            gathering.xp += xp
            gathering.earned += earned
            gathering.positions += spot
        }
        for (event in events) {
            when (event) {
                is GameEvent.Stored -> {
                    gather(event.itemID, false, event.xp, 0.0, event.point)
                }

                is GameEvent.Sold -> {
                    gather(event.itemID, true, event.xp, event.value, event.point)
                }

                is GameEvent.TractorCargoStored -> {
                    gather(event.itemID, false, event.xp, 0.0, event.at, event.quantity)
                }

                is GameEvent.TractorCargoSold -> {
                    gather(
                        event.itemID,
                        true,
                        event.xp,
                        event.value,
                        event.at,
                        event.quantity,
                    )
                }

                else -> {
                    continue
                }
            }
        }
        gatherings.values.forEachIndexed { index, gathering ->
            val spot = requireNotNull(gathering.positions.middle()).shifted(y = index * 34.0)
            val delay = index * 0.05
            if (gathering.earned > 0) {
                heldEarnings += gathering.earned
                gatherIntoTally(
                    RewardKind.Earn(gathering.earned),
                    gathering.source,
                    spot.shifted(x = -85.0),
                    now,
                    delay + 0.04,
                )
            }
            if (gathering.xp > 0) {
                heldXP += gathering.xp
                gatherIntoTally(RewardKind.XP(gathering.xp), gathering.source, spot, now, delay + 0.08)
            }
            val kind = RewardKind.Item(gathering.source.item, gathering.count, gathering.source.isSold)
            gatherIntoTally(kind, gathering.source, spot.shifted(x = 85.0), now, delay)
        }
    }

    private fun gatherIntoTally(
        kind: RewardKind,
        source: HarvestSource,
        start: RewardPoint,
        now: Double,
        delay: Double,
    ) {
        if (flights.size >= 12) {
            addToTally(kind, source, now)
        } else {
            launch(kind, start, now, delay, RewardRoute.ToTally, source)
        }
    }

    private fun addToTally(
        kind: RewardKind,
        source: HarvestSource,
        now: Double,
    ) {
        version++
        tally = (tally ?: HarvestTally()).add(kind, source)
        flushAt = now + 0.9
    }

    private fun launch(
        kind: RewardKind,
        start: RewardPoint,
        now: Double,
        delay: Double = 0.0,
        route: RewardRoute = RewardRoute.Direct,
        source: HarvestSource? = null,
        startsAtBanner: Boolean = false,
    ) {
        version++
        flights = flights + RewardFlight(nextID++, kind, start, now, startsAtBanner, delay, route, source)
    }

    private fun land(flight: RewardFlight) {
        version++
        if (flight.route == RewardRoute.ToTally && flight.source != null) {
            addToTally(flight.kind, flight.source, flight.landingAt)
            return
        }
        when (val kind = flight.kind) {
            is RewardKind.Spend -> {
                heldSpending = maxOf(0.0, heldSpending - kind.value)
                moneyPulse++
            }

            is RewardKind.Earn -> {
                heldEarnings = maxOf(0.0, heldEarnings - kind.value)
                moneyPulse++
            }

            is RewardKind.XP -> {
                heldXP = maxOf(0, heldXP - kind.amount)
                xpPulse++
            }

            is RewardKind.Item -> {
                if (!kind.isSold) storagePulse++
            }
        }
    }
}

private fun List<RewardPoint>.middle(): RewardPoint? =
    if (isEmpty()) null else RewardPoint(sumOf { it.x } / size, sumOf { it.y } / size)
