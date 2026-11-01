package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.Rejection
import com.hunter.stockfarmer.core.rules.advanceTractor
import com.hunter.stockfarmer.core.util.advancedBy
import java.time.Instant
import java.util.ArrayDeque

/** Frame pacing and presentation only: movement, harvesting and cargo remain core actions. */
class TractorDriver {
    private val notices = ArrayDeque<Rejection>()
    private var nextNotice = Instant.MIN

    fun advance(
        engine: GameEngine,
        elapsed: Double,
        active: Boolean,
        saveBlocked: Boolean,
        now: Instant,
    ): List<GameEvent> {
        if (!active || saveBlocked) return emptyList()
        val events =
            if (engine.state.tractorRun != null && elapsed.isFinite() && elapsed > 0) {
                engine.advanceTractor(elapsed.coerceAtMost(0.1), true, now)
            } else {
                emptyList()
            }
        return present(events, now)
    }

    internal fun present(
        events: List<GameEvent>,
        now: Instant,
    ): List<GameEvent> {
        events.filterIsInstance<GameEvent.Rejected>().forEach { notices.addLast(it.rejection) }
        val ready = events.filterNot { it is GameEvent.Rejected }.toMutableList()
        if (notices.isNotEmpty() && now >= nextNotice) {
            ready += GameEvent.Rejected(notices.removeFirst())
            nextNotice = now.advancedBy(2.3)
        }
        return ready
    }

    fun clear() {
        notices.clear()
        nextNotice = Instant.MIN
    }
}
