package com.hunter.stockfarmer.ui.fishing

import com.hunter.stockfarmer.core.feedback.HapticCue
import com.hunter.stockfarmer.core.rules.FishingContest
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.containsRejection
import com.hunter.stockfarmer.core.rules.landFish
import com.hunter.stockfarmer.core.rules.loseFish
import com.hunter.stockfarmer.core.util.RandomSource
import java.time.Instant

/** UI-independent contest lifecycle. Coordinates are density-independent screen points, as on iOS. */
class FishingSession(
    val cast: FishingCast,
    bounds: FishingContest.Bounds,
    private val random: RandomSource,
    private val onHaptic: (HapticCue) -> Unit = {},
    private val onReeling: (Double) -> Unit = {},
) {
    private val contest = FishingContest(cast.kind.rarity, bounds, cast.reachFactor, random)
    private var active = true
    private var phase = Phase.PLAYING

    var rod: FishingContest.Position? = null
        private set

    val fish: FishingContest.Position get() = contest.fish
    val heading: Double get() = contest.heading
    val progress: Double get() = contest.progress
    val reach: Double get() = contest.reach
    val isOverFish: Boolean get() = rod?.let(contest::isOverFish) == true
    val isCaught: Boolean get() = phase == Phase.CAUGHT
    val isFinished: Boolean get() = phase == Phase.FINISHED

    fun moveRod(position: FishingContest.Position?) {
        if (active && phase == Phase.PLAYING) rod = position
    }

    fun setActive(value: Boolean) {
        active = value
        if (!active) rod = null
    }

    /** Returns true only on the frame the fish is caught. Stalls never make it jump across the water. */
    fun advance(seconds: Double): Boolean {
        if (!active || phase != Phase.PLAYING || !seconds.isFinite() || seconds <= 0) return false
        val before = contest.progress
        val caught = contest.advance(seconds.coerceAtMost(0.05), rod, random)
        if (contest.progress > before) onReeling(contest.progress)
        if (!caught) return false
        phase = Phase.CAUGHT
        rod = null
        onHaptic(HapticCue.Success)
        return true
    }

    /** Call inside GameSession.act. Refusals keep the caught fish on show for another choice. */
    fun land(
        engine: GameEngine,
        now: Instant,
        selling: Boolean,
    ): List<GameEvent> {
        if (phase != Phase.CAUGHT) return emptyList()
        val events = engine.landFish(cast.pond, cast.point, now, selling)
        if (!events.containsRejection) phase = Phase.FINISHED
        return events
    }

    /** Leaving with an already caught fish stores it, just as the reference's Back action does. */
    fun giveUp(
        engine: GameEngine,
        now: Instant,
    ): List<GameEvent> {
        if (phase == Phase.CAUGHT) return land(engine, now, selling = false)
        if (phase == Phase.FINISHED) return emptyList()
        engine.loseFish(cast.pond, now)
        rod = null
        phase = Phase.FINISHED
        return emptyList()
    }

    private enum class Phase { PLAYING, CAUGHT, FINISHED }
}
