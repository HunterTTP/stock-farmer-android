package com.hunter.stockfarmer.ui.fishing

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.FishingContest
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.firstRejection
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.PillButton
import com.hunter.stockfarmer.ui.theme.PillRole
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.glassPanel
import com.hunter.stockfarmer.ui.theme.hudColor
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.Locale

/** Draw above the farm and HUD. The owner removes it only after onFinish, after the closing reveal. */
@Composable
fun FishingOverlay(
    session: GameSession,
    cast: FishingCast,
    onCaught: () -> Unit = {},
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
        val fishing =
            remember(session, cast) {
                FishingSession(cast, area.bounds, SystemRandomSource, session::haptic, session::reel)
            }
        val reveal = remember(fishing) { Animatable(0f) }
        var closingEvents by remember(fishing) { mutableStateOf<List<GameEvent>?>(null) }
        var error by remember(fishing) { mutableStateOf<String?>(null) }
        var seconds by remember(fishing) { mutableDoubleStateOf(0.0) }
        var caughtAt by remember(fishing) { mutableDoubleStateOf(0.0) }
        val finish by rememberUpdatedState(onFinish)
        val caughtSound by rememberUpdatedState(onCaught)
        val owner = LocalLifecycleOwner.current
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
                while (true) {
                    withFrameNanos { now ->
                        val elapsed = last?.let { (now - it) / 1_000_000_000.0 } ?: 0.0
                        last = now
                        seconds += elapsed.coerceIn(0.0, 0.05)
                        if (fishing.advance(elapsed)) {
                            caughtAt = seconds
                            caughtSound()
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
                        val message = error
                        if (message != null) {
                            BasicText(
                                message,
                                style = HudType.subheadline.copy(color = Theme.spend, textAlign = TextAlign.Center),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            PillButton(
                                title = "Sell · ${cast.sellPrice.dollars}",
                                onClick = { land(selling = true) },
                                role = PillRole.earn,
                            )
                            PillButton(
                                title = "Store",
                                onClick = { land(selling = false) },
                                role = PillRole.neutral,
                            )
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
            PillButton(
                title = if (caught) "Store & close" else "Give up",
                onClick = ::leave,
                role = PillRole.neutral,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = (area.bottom + 24).dp),
            )
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
        BasicText(
            "Catch Meter",
            style = HudType.subheadline.copy(fontWeight = FontWeight.Bold, color = Color.White),
        )
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
            val inset = 0.5.dp.toPx()
            drawRoundRect(
                Theme.panelBorder,
                topLeft = Offset(inset, inset),
                size = Size(size.width - 2 * inset, size.height - 2 * inset),
                cornerRadius = radius,
                style = Stroke(1.dp.toPx()),
            )
        }
        BasicText(
            cast.kind.rarity.displayName,
            style = HudType.subheadline.copy(fontWeight = FontWeight.Bold, color = cast.kind.rarity.hudColor),
        )
        if (cast.showsHint) {
            BasicText(
                "Hold the rod over the fish",
                style = HudType.caption.copy(fontWeight = FontWeight.SemiBold, color = Theme.textSecondary),
            )
        }
    }
}

@Composable
private fun CatchDetails(cast: FishingCast) {
    val article = if (cast.kind.name.first() in "AEIOU") "an" else "a"
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 560.dp)
            .glassPanel(18.dp)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            "You caught $article ${cast.kind.name}!",
            style =
                HudType.title2.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                ),
        )
        BasicText(
            cast.kind.rarity.displayName,
            style = HudType.caption.copy(fontWeight = FontWeight.Bold, color = cast.kind.rarity.hudColor),
        )
        BasicText(
            String.format(Locale.US, "%.1f lb", cast.weight),
            style = HudType.headline.copy(color = Color.White),
        )
        BasicText(
            buildAnnotatedString {
                append("Heavier than ")
                withStyle(SpanStyle(color = cast.kind.weightRarity(cast.weight).hudColor)) {
                    append(String.format(Locale.US, "%.1f%%", cast.kind.weightPercentile(cast.weight) * 100))
                }
                append(" of this species")
            },
            style = HudType.caption.copy(color = Color.White, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun FishingPanel(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .glassPanel(16.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
