package com.hunter.stockfarmer.ui.rewards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.farm.FarmRenderer
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.screenPosition
import com.hunter.stockfarmer.ui.menus.MenuAssetLoader
import com.hunter.stockfarmer.ui.menus.MenuImage
import com.hunter.stockfarmer.ui.menus.MenuPictures
import com.hunter.stockfarmer.ui.menus.rememberMenuAssetLoader
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.hudPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RewardFlightsState internal constructor() {
    val tracker = RewardFlightTracker()
    internal val frames = mutableStateMapOf<HUDTarget, Rect>()
    internal var revision by mutableIntStateOf(0)
    internal var now by mutableStateOf(0.0)

    fun displayedMoney(money: Double): Double {
        revision
        return tracker.displayedMoney(money)
    }

    fun displayedXP(xp: Int): Int {
        revision
        return tracker.displayedXP(xp)
    }

    fun clear() {
        tracker.clear()
        revision++
    }
}

val LocalRewardFlights = staticCompositionLocalOf<RewardFlightsState?> { null }

/** Share this scope between Hud and RewardFlights so targets, pulses and delayed totals use the same tracker. */
@Composable
fun RewardFlightsHost(
    session: GameSession,
    content: @Composable () -> Unit,
) {
    val state = remember(session) { RewardFlightsState() }
    CompositionLocalProvider(LocalRewardFlights provides state, content = content)
}

/** Marks the actual HUD panel, in the same Compose root as the overlay, and throbs when a flight lands. */
@Composable
fun Modifier.rewardTarget(target: HUDTarget): Modifier {
    val state = LocalRewardFlights.current ?: return this
    state.revision
    val pulse =
        when (target) {
            HUDTarget.Money -> state.tracker.moneyPulse
            HUDTarget.Level -> state.tracker.xpPulse
            HUDTarget.Storage -> state.tracker.storagePulse
        }
    var expanded by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (expanded) 1.2f else 1f, if (expanded) tween(100) else spring(0.7f, 439f))
    LaunchedEffect(pulse) {
        if (pulse > 0) {
            expanded = true
            delay(100)
            expanded = false
        }
    }
    DisposableEffect(state, target) { onDispose { state.frames.remove(target) } }
    return onGloballyPositioned { state.frames[target] = it.boundsInRoot() }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/** Draw above the full-screen farm and Hud. It observes batches without consuming touches. */
@Composable
fun RewardFlights(
    session: GameSession,
    modifier: Modifier = Modifier,
) {
    val shared = LocalRewardFlights.current
    val state = shared ?: remember(session) { RewardFlightsState() }
    val density = LocalDensity.current.density
    val images = rememberMenuAssetLoader()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var reduceMotion by remember { mutableStateOf(false) }
    LaunchedEffect(session, state, density) {
        reduceMotion = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f
        launch {
            session.events.collect { events ->
                val now = System.nanoTime() / 1_000_000_000.0
                val view = session.view.snapshot
                state.tracker.launch(events, now) { tile ->
                    view?.screenPosition(tile, FarmRenderer.TILE, GameState.GRID_SIZE)?.let {
                        RewardPoint(
                            (it.x - bounds.left) / density.toDouble(),
                            (it.y - bounds.top) / density.toDouble(),
                        )
                    }
                }
                state.now = now
                state.revision++
            }
        }
        snapshotFlow {
            state.revision
            state.tracker.flights.isNotEmpty() || state.tracker.tally != null
        }.collectLatest { busy ->
            if (busy) {
                while (isActive) {
                    withFrameNanos {
                        state.now = System.nanoTime() / 1_000_000_000.0
                        state.tracker.tick(state.now)
                        state.revision++
                    }
                }
            }
        }
    }
    DisposableEffect(state) { onDispose { state.clear() } }
    state.revision
    val tallyPoint = RewardPoint(bounds.width / density / 2.0, 185.0)
    val bannerPoint = RewardPoint(tallyPoint.x, bounds.height / density / 2.0)
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .semantics { hideFromAccessibility() },
    ) {
        state.tracker.tally?.let { tally ->
            Column(
                Modifier
                    .at(
                        RewardPoint(tallyPoint.x, tallyPoint.y + (tally.rows.size - 1) * 20.0),
                        density,
                    ).hudPanel(16.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                tally.rows.forEach { row ->
                    Row(Modifier.align(Alignment.CenterHorizontally)) {
                        row.forEach { kind ->
                            Box(Modifier.size(92.dp, 40.dp), contentAlignment = Alignment.Center) {
                                RewardLabel(kind, images, compact = true)
                            }
                        }
                    }
                }
            }
        }
        if (!reduceMotion) {
            state.tracker.flights.forEach { flight ->
                val target =
                    state.frames[flight.kind.target]?.center?.let {
                        RewardPoint(
                            (it.x - bounds.left) / density.toDouble(),
                            (it.y - bounds.top) / density.toDouble(),
                        )
                    }
                if (flight.route != RewardRoute.Direct || target != null) {
                    val pose = flight.pose(state.now, tallyPoint, bannerPoint, target)
                    Box(
                        Modifier.at(pose.position, density).graphicsLayer {
                            alpha = pose.alpha
                            scaleX = pose.scale
                            scaleY = pose.scale
                        },
                    ) { RewardLabel(flight.kind, images) }
                }
            }
        }
    }
}

private fun Modifier.at(
    point: RewardPoint,
    density: Float,
): Modifier =
    graphicsLayer {
        translationX = (point.x * density).toFloat() - size.width / 2
        translationY = (point.y * density).toFloat() - size.height / 2
    }

val RewardKind.color: Color
    get() =
        when (this) {
            is RewardKind.Spend -> Theme.spend
            is RewardKind.Earn -> Theme.money
            is RewardKind.XP -> Theme.xp
            is RewardKind.Item -> Color.White
        }

@Composable
private fun RewardLabel(
    kind: RewardKind,
    images: MenuAssetLoader,
    compact: Boolean = false,
) {
    val density = LocalDensity.current.density
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (kind is RewardKind.Item) {
            MenuImage(MenuPictures.stored(kind.item), images, Modifier.size(if (compact) 30.dp else 34.dp))
        }
        val text =
            when (kind) {
                is RewardKind.Spend -> "−${kind.value.dollars}"
                is RewardKind.Earn -> "+${kind.value.dollars}"
                is RewardKind.XP -> "+${kind.amount} XP"
                is RewardKind.Item -> "${kind.count}x"
            }
        Text(
            text,
            color = kind.color,
            fontSize = if (compact) 18.sp else 20.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            style =
                TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontFeatureSettings = "tnum",
                    shadow = Shadow(Color.Black.copy(alpha = 0.65f), Offset(0f, 2 * density), 2 * density),
                ),
        )
    }
}
