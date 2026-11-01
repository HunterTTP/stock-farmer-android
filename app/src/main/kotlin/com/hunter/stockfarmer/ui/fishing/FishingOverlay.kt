package com.hunter.stockfarmer.ui.fishing

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hunter.stockfarmer.core.catalog.Rarity
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.FishingContest
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.firstRejection
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.game.GameSession
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.Locale

/** Draw above the farm and HUD. The owner removes it only after onFinish, after the closing reveal. */
@Composable
fun FishingOverlay(
    session: GameSession,
    cast: FishingCast,
    onFinish: (List<GameEvent>) -> Unit,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val safe = WindowInsets.safeDrawing
    val tablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val area =
            remember(session, cast) {
                FishingArea(
                    maxWidth.value.toDouble(),
                    maxHeight.value.toDouble(),
                    safe.getLeft(density, direction) / density.density.toDouble(),
                    safe.getTop(density) / density.density.toDouble(),
                    safe.getRight(density, direction) / density.density.toDouble(),
                    safe.getBottom(density) / density.density.toDouble(),
                    if (tablet) 1.3 else 1.0,
                )
            }
        val fishing = remember(session, cast) { FishingSession(cast, area.bounds, SystemRandomSource) }
        val reveal = remember(fishing) { Animatable(0f) }
        var closingEvents by remember(fishing) { mutableStateOf<List<GameEvent>?>(null) }
        var error by remember(fishing) { mutableStateOf<String?>(null) }
        var seconds by remember(fishing) { mutableDoubleStateOf(0.0) }
        var caughtAt by remember(fishing) { mutableDoubleStateOf(0.0) }
        val finish by rememberUpdatedState(onFinish)
        val owner = LocalLifecycleOwner.current
        val haptics = LocalHapticFeedback.current
        val caught = fishing.progress >= 1
        val closing = closingEvents != null

        fun complete(events: List<GameEvent>) {
            error = events.firstRejection?.message
            if (fishing.isFinished && closingEvents == null) closingEvents = events
        }

        fun leave() {
            if (closingEvents == null) complete(session.act { fishing.giveUp(it, Instant.now()) })
        }

        fun land(selling: Boolean) {
            if (closingEvents == null) complete(session.act { fishing.land(it, Instant.now(), selling) })
        }

        BackHandler(onBack = ::leave)
        DisposableEffect(fishing, owner) {
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
        LaunchedEffect(fishing, closing) {
            if (closing) {
                reveal.animateTo(0f, fishingReveal())
                finish(closingEvents.orEmpty())
            } else {
                delay(40)
                reveal.animateTo(1f, fishingReveal())
            }
        }
        LaunchedEffect(fishing, owner, closing) {
            if (closing) return@LaunchedEffect
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var last: Long? = null
                var lastPulse = Double.NEGATIVE_INFINITY
                while (true) {
                    withFrameNanos { now ->
                        val elapsed = last?.let { (now - it) / 1_000_000_000.0 } ?: 0.0
                        last = now
                        seconds += elapsed.coerceIn(0.0, 0.05)
                        val before = fishing.progress
                        if (fishing.advance(elapsed)) {
                            caughtAt = seconds
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (fishing.progress > before && seconds - lastPulse >= 0.14 - 0.10 * fishing.progress) {
                            lastPulse = seconds
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
            }
        }

        FishingCanvas(
            session,
            fishing,
            area,
            seconds,
            caughtAt,
            reveal.value,
            Modifier.fillMaxSize().pointerInput(fishing, closing, caught) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    if (!closing && !caught) {
                        fun move(
                            x: Float,
                            y: Float,
                        ) {
                            val position =
                                FishingContest.Position(
                                    x / size.width * area.width,
                                    y / size.height * area.height,
                                )
                            fishing.moveRod(position)
                        }
                        try {
                            move(down.position.x, down.position.y)
                            do {
                                val event = awaitPointerEvent()
                                val finger = event.changes.firstOrNull { it.id == down.id } ?: break
                                finger.consume()
                                if (finger.pressed) move(finger.position.x, finger.position.y)
                            } while (finger.pressed)
                        } finally {
                            fishing.moveRod(null)
                        }
                    }
                }
            },
        )

        if (!closing) {
            if (caught) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(0.4f), contentAlignment = Alignment.Center) {
                    CatchDetails(cast)
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.48f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        error?.let { Text(it, color = Color(0xFFFFAAAA), textAlign = TextAlign.Center) }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Button(onClick = { land(selling = true) }) {
                                Text("Sell · ${cast.sellPrice.dollars}")
                            }
                            Button(onClick = { land(selling = false) }) {
                                Text("Store")
                            }
                        }
                    }
                }
            } else {
                CatchMeter(
                    cast,
                    fishing.progress,
                    Modifier.align(Alignment.TopCenter).padding(top = (area.top + 8).dp),
                )
            }
            TextButton(
                onClick = ::leave,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = (area.bottom + 24).dp),
            ) {
                Text(if (caught) "Store & close" else "Give up", color = Color.White)
            }
        }
    }
}

private fun fishingReveal() = tween<Float>(550, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f))

@Composable
private fun CatchMeter(
    cast: FishingCast,
    progress: Double,
    modifier: Modifier,
) {
    FishingPanel(modifier) {
        Text("Catch Meter", color = Color.White, fontWeight = FontWeight.Bold)
        Canvas(
            Modifier.size(200.dp, 12.dp).semantics {
                contentDescription = "${cast.kind.rarity.displayName} catch meter"
                progressBarRangeInfo = ProgressBarRangeInfo(progress.toFloat(), 0f..1f)
            },
        ) {
            val amount = progress.toFloat().coerceIn(0f, 1f)
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(Color.Black.copy(alpha = 0.45f), cornerRadius = radius)
            if (amount > 0) {
                drawRoundRect(
                    Color.hsv(0.332f * (1 - amount) * 360, 0.48f + 0.4f * amount, 1f),
                    size = Size(size.width * amount, size.height),
                    cornerRadius = radius,
                )
            }
        }
        Text(cast.kind.rarity.displayName, color = cast.kind.rarity.fishingColor(), fontWeight = FontWeight.Bold)
        if (cast.showsHint) Text("Hold the rod over the fish", color = Color.LightGray, fontSize = 12.sp)
    }
}

@Composable
private fun CatchDetails(cast: FishingCast) {
    val article = if (cast.kind.name.first() in "AEIOU") "an" else "a"
    FishingPanel(Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp)) {
        Text(
            "You caught $article ${cast.kind.name}!",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Text(cast.kind.rarity.displayName, color = cast.kind.rarity.fishingColor(), fontWeight = FontWeight.Bold)
        Text(String.format(Locale.US, "%.1f lb", cast.weight), color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            String.format(
                Locale.US,
                "Heavier than %.1f%% of this species",
                cast.kind.weightPercentile(cast.weight) * 100,
            ),
            color = cast.kind.weightRarity(cast.weight).fishingColor(),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FishingPanel(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xEE303030),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

private fun Rarity.fishingColor() = Color(color.red.toFloat(), color.green.toFloat(), color.blue.toFloat())
