package com.hunter.stockfarmer.ui.fishing

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hunter.stockfarmer.core.rules.FishingContest
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.containsRejection
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.game.FishingCast
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.theme.LocalHudScale
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Fishing mode while a line is out: the water opens over the farm, the controls of the game stay over it, and the rod tool
 * in the tool circle is held over the fish. The owner removes it only after `onFinish`, after the water has closed again.
 */
internal class FishingMode(
    private val session: GameSession,
    val fishing: FishingSession,
    val area: FishingArea,
) {
    /** How much of the screen the water has opened over, from 0 to 1. */
    val reveal = Animatable(0f)

    var seconds by mutableDoubleStateOf(0.0)
        private set
    var caughtAt by mutableDoubleStateOf(0.0)
        private set

    /** The bar is full: the fish is shown, to be sold or stored. */
    var isCaught by mutableStateOf(false)
        private set
    var closingEvents by mutableStateOf<List<GameEvent>?>(null)
        private set

    /** The game is in fishing mode: a line is out, and it is not being reeled in. */
    val isFishing: Boolean get() = closingEvents == null

    /** The rod tool is held at a place on the screen, or let go. Once the fish is caught it does nothing. */
    fun moveRod(point: Offset?) {
        if (isCaught) return
        fishing.moveRod(point?.let { FishingContest.Position(it.x.toDouble(), it.y.toDouble()) })
    }

    /** The player leaves fishing mode: a fish not yet caught swims away, one already caught is stored. */
    fun leave() {
        if (isFishing) complete(session.act { fishing.giveUp(it, Instant.now()) })
    }

    fun land(selling: Boolean) {
        if (isFishing) complete(session.act { fishing.land(it, Instant.now(), selling) })
    }

    /** A refusal, such as storage being full, keeps the fish on show for the other choice. */
    private fun complete(events: List<GameEvent>) {
        if (events.containsRejection) session.publish(events)
        if (fishing.isFinished && closingEvents == null) closingEvents = events
    }

    internal fun step(
        elapsed: Double,
        onCaught: () -> Unit,
    ) {
        seconds += elapsed.coerceIn(0.0, 0.05)
        if (fishing.advance(elapsed)) {
            caughtAt = seconds
            isCaught = true
            onCaught()
        }
    }
}

private fun fishingReveal() = tween<Float>(550, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f))

/** Starts fishing mode for [cast] over a window of [width] by [height] points, and runs it until it has closed. */
@Composable
internal fun rememberFishingMode(
    session: GameSession,
    cast: FishingCast,
    width: Double,
    height: Double,
    onCaught: () -> Unit,
    onFinish: (List<GameEvent>) -> Unit,
): FishingMode {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val safe = WindowInsets.safeDrawing
    val scale = LocalHudScale.current.toDouble()
    val mode =
        remember(session, cast) {
            val points = density.density.toDouble()
            val area =
                FishingArea(
                    width,
                    height,
                    safe.getLeft(density, direction) / points,
                    safe.getTop(density) / points,
                    safe.getRight(density, direction) / points,
                    safe.getBottom(density) / points,
                    scale,
                )
            val fishing = FishingSession(cast, area.bounds, SystemRandomSource, session::haptic, session::reel)
            FishingMode(session, fishing, area)
        }
    val finish by rememberUpdatedState(onFinish)
    val caughtSound by rememberUpdatedState(onCaught)
    val owner = LocalLifecycleOwner.current
    val fishing = mode.fishing
    val closing = !mode.isFishing

    BackHandler(onBack = mode::leave)
    DisposableEffect(mode, owner) {
        val observer =
            LifecycleEventObserver { _, _ ->
                fishing.setActive(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            }
        fishing.setActive(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            fishing.setActive(false)
        }
    }
    // The water starts closed and opens a moment later, so that the change is seen.
    LaunchedEffect(mode, closing) {
        if (closing) {
            mode.reveal.animateTo(0f, fishingReveal())
            finish(mode.closingEvents.orEmpty())
        } else {
            delay(40)
            mode.reveal.animateTo(1f, fishingReveal())
        }
    }
    LaunchedEffect(mode, owner, closing) {
        if (closing) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last: Long? = null
            while (true) {
                withFrameNanos { now ->
                    val elapsed = last?.let { (now - it) / 1_000_000_000.0 } ?: 0.0
                    last = now
                    mode.step(elapsed, caughtSound)
                }
            }
        }
    }
    return mode
}
