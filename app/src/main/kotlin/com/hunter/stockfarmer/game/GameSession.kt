package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.DaySummary
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.newGame
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.ui.fishing.FishingCast
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant

/**
 * The game being played. The farm draws it on the render thread and the screens read and change it on the main thread,
 * so every access goes through [read] or [act], which take turns.
 */
class GameSession(
    private val engine: GameEngine = GameEngine(newGame(SystemRandomSource)),
) {
    /** Looks at the game without changing it. */
    fun <T> read(look: (GameState) -> T): T = synchronized(engine) { look(engine.state) }

    /** Changes the game, handing back whatever the change returns. */
    fun <T> act(change: (GameEngine) -> T): T = synchronized(engine) { change(engine) }

    private val published = MutableSharedFlow<List<GameEvent>>(extraBufferCapacity = EVENT_BUFFER)

    /** What happened in the game, in batches, for sound and for the screens that show news. */
    val events: SharedFlow<List<GameEvent>> = published.asSharedFlow()

    private val days = MutableSharedFlow<DaySummary>(extraBufferCapacity = EVENT_BUFFER)

    /** The day just over, with what sold in it, each time a new day begins. */
    val newDays: SharedFlow<DaySummary> = days.asSharedFlow()

    fun announce(summary: DaySummary) {
        days.tryEmit(summary)
    }

    /** The fish on the line while the fishing game is open; null otherwise. */
    val casts = MutableStateFlow<FishingCast?>(null)

    /** Asks the farm for a picture of the whole map. */
    val photos = PhotoRequests()

    /** Where the farm camera looks, so sounds can be placed to one side or made quieter when far away. */
    val view = FarmView()

    /** Tells everyone listening what just happened; nothing is sent for an empty batch. */
    fun publish(batch: List<GameEvent>): List<GameEvent> = batch.also { if (it.isNotEmpty()) published.tryEmit(it) }

    /** A tap on the farm uses the selected tool on that tile. */
    fun tap(point: GridPoint): List<GameEvent> = publish(act { it.tap(point, Instant.now()) })

    private companion object {
        const val EVENT_BUFFER = 64
    }
}
