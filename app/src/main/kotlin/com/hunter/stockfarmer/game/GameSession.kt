package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.feedback.HapticCue
import com.hunter.stockfarmer.core.model.DaySummary
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.newGame
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.ui.fishing.FishingCast
import com.hunter.stockfarmer.ui.menus.countCurrentUnlocksAsSeen
import com.hunter.stockfarmer.ui.sheets.SheetTarget
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
    private val engine: GameEngine = GameEngine(newGame(SystemRandomSource, Instant.now())),
) {
    init {
        engine.countCurrentUnlocksAsSeen()
    }

    /** Looks at the game without changing it. */
    fun <T> read(look: (GameState) -> T): T = synchronized(engine) { look(engine.state) }

    /** Changes the game, handing back whatever the change returns. */
    fun <T> act(change: (GameEngine) -> T): T = synchronized(engine) { change(engine) }

    private val published = MutableSharedFlow<List<GameEvent>>(extraBufferCapacity = EVENT_BUFFER)

    /** What happened in the game, in batches, for sound and for the screens that show news. */
    val events: SharedFlow<List<GameEvent>> = published.asSharedFlow()

    private val days = MutableSharedFlow<DaySummary>(extraBufferCapacity = EVENT_BUFFER)

    /** The day just over, with what sold in it, a little after each new day begins (the banner fades in). */
    val newDays: SharedFlow<DaySummary> = days.asSharedFlow()

    fun announce(summary: DaySummary) {
        days.tryEmit(summary)
    }

    private val nights = MutableSharedFlow<Int?>(extraBufferCapacity = EVENT_BUFFER)

    /** The night when a new day begins, or null to end the chime and its retries when the clock is set by hand. */
    val dayBegins: SharedFlow<Int?> = nights.asSharedFlow()

    var nightPulse: Int? = null
        private set

    var nightPulseRevision = 0L
        private set

    /** The activity's sound driver cuts short the chime as soon as the player changes the time. */
    var onNightEnded: () -> Unit = {}

    fun beginDay(night: Int?) =
        act {
            nightPulse = night
            nightPulseRevision++
            nights.tryEmit(night)
            Unit
        }

    /** Finishes the old night's growth before moving the clock, and ends its visual and audio playback. */
    fun endNight(now: Instant) =
        act {
            wildGrowth.advance(it, now, finishing = true)
            beginDay(null)
            onNightEnded()
        }

    private val toastsFlow = MutableSharedFlow<ToastMessage>(extraBufferCapacity = EVENT_BUFFER)

    /** Short messages shown over the farm or inside sheets. */
    val toasts: SharedFlow<ToastMessage> = toastsFlow.asSharedFlow()

    fun showToast(
        message: String,
        tool: HarvestTool? = null,
        durationMillis: Long = 4_000L,
    ) {
        toastsFlow.tryEmit(ToastMessage(message, tool, durationMillis))
    }

    /** The fish on the line while the fishing game is open; null otherwise. */
    val casts = MutableStateFlow<FishingCast?>(null)

    /** Something the player is asked to confirm before it happens; null when nothing is. */
    val confirmations = MutableStateFlow<Confirmation?>(null)

    private val hapticRequests = MutableSharedFlow<HapticRequest>(extraBufferCapacity = EVENT_BUFFER)
    val haptics: SharedFlow<HapticRequest> = hapticRequests.asSharedFlow()

    fun haptic(cue: HapticCue) {
        hapticRequests.tryEmit(HapticRequest.Cue(cue))
    }

    fun reel(progress: Double) {
        hapticRequests.tryEmit(HapticRequest.Reeling(progress))
    }

    fun ask(confirmation: Confirmation) {
        haptic(HapticCue.Medium)
        confirmations.value = confirmation
    }

    /** The building, animal or fish whose sheet is open; null when none is. */
    val sheets = MutableStateFlow<SheetTarget?>(null)

    /** Counts the touches that land on the farm, which close the floating tool menus. */
    val farmTouches = MutableStateFlow(0)

    /** Asks the farm for a picture of the whole map. */
    val photos = PhotoRequests()

    /** Pauses the farm's frame loop while something opaque hides it. */
    val farmCover = FarmCover()

    /** Where the farm camera looks, so sounds can be placed to one side or made quieter when far away. */
    val view = FarmView()

    /** Fireworks waiting for the farm scene to draw them. */
    val celebrations = FarmCelebrations()

    val sceneFeedback = FarmSceneFeedback()
    val wildGrowth = WildGrowthDriver()
    val tractor = TractorDriver()

    @Volatile var isActive = true

    @Volatile var isTimelapseViewerOpen = false

    @Volatile var isSaveBlocked = false

    /** Tells everyone listening what just happened; nothing is sent for an empty batch. */
    fun publish(batch: List<GameEvent>): List<GameEvent> =
        batch.also {
            if (it.any { event -> event is GameEvent.LeveledUp }) celebrations.celebrateAcross()
            if (it.isNotEmpty()) published.tryEmit(it)
        }

    /** A tap on the farm uses the selected tool on that tile. */
    fun tap(point: GridPoint): List<GameEvent> = publish(act { it.tap(point, Instant.now()) })

    private companion object {
        const val EVENT_BUFFER = 64
    }
}
