package com.hunter.stockfarmer.notifications

import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.wholeFieldReadyDate
import java.time.Duration
import java.time.Instant

interface FieldReadyScheduler {
    fun replace(delay: Duration)

    fun cancel()
}

/** Call on the game state's owning thread, when the app enters the background or becomes active. */
class FieldReadyNotifier(
    private val scheduler: FieldReadyScheduler,
) {
    fun onBackground(
        engine: GameEngine,
        now: Instant,
    ) {
        val ready = engine.wholeFieldReadyDate()
        if (ready == null || ready <= now) {
            scheduler.cancel()
            return
        }
        scheduler.replace(maxOf(Duration.ofSeconds(1), Duration.between(now, ready)))
    }

    fun onActive() {
        scheduler.cancel()
    }
}
