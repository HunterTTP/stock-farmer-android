package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.NightPulse
import com.hunter.stockfarmer.core.rules.WildGrowthPlan
import com.hunter.stockfarmer.core.rules.WildGrowthSweep
import com.hunter.stockfarmer.core.rules.growWild
import com.hunter.stockfarmer.core.rules.planWildGrowth
import com.hunter.stockfarmer.core.util.RandomSource
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

/** Owns one night's existing core plan. Only origins actually grown are handed to GrowthReveal. */
class WildGrowthDriver {
    data class Growth(
        val trees: Set<GridPoint>,
        val rocks: Set<GridPoint>,
        val at: Instant,
    )

    private class Pending(
        val night: Int,
        val sweep: WildGrowthSweep,
        val plan: WildGrowthPlan,
    )

    private var pending: Pending? = null
    private val reveals = ConcurrentLinkedQueue<Growth>()

    fun begin(
        engine: GameEngine,
        night: Int,
        row: Double,
        column: Double,
        random: RandomSource,
    ) {
        pending = Pending(night, WildGrowthSweep(row, column), engine.planWildGrowth(random))
    }

    fun advance(
        engine: GameEngine,
        now: Instant,
        finishing: Boolean = false,
    ) {
        val growth = pending ?: return
        val wave = NightPulse.wave(now, engine.state.clockOffset)
        val progress = if (finishing) 1.0 else wave?.takeIf { it.day == growth.night }?.progress ?: 1.0
        val reached = growth.sweep.tilesReached(progress)
        if (progress >= 1) pending = null
        val grown = engine.growWild(reached, growth.plan, now)
        if (grown.isEmpty()) return
        reveals.add(
            Growth(
                grown.filter { it in engine.state.trees }.toSet(),
                grown.filter { it in engine.state.rocks }.toSet(),
                now,
            ),
        )
    }

    fun drain(): List<Growth> =
        buildList {
            while (true) add(reveals.poll() ?: break)
        }

    fun clear() {
        pending = null
        reveals.clear()
    }
}
