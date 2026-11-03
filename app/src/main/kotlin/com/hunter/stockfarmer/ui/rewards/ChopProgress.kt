package com.hunter.stockfarmer.ui.rewards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.farm.FarmRenderer
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.TILE
import com.hunter.stockfarmer.game.screenPosition
import kotlinx.coroutines.delay

/** How far the axe or pickaxe has got with the tree or rock at [tile]; the pass that fells or breaks it counts to its own total. */
data class ChopProgress(
    val tile: GridPoint,
    val hits: Int,
    val total: Int,
) {
    val lingerMillis: Long get() = if (hits >= total) FINAL_LINGER_MILLIS else LINGER_MILLIS

    companion object {
        const val LINGER_MILLIS = 1000L
        const val FINAL_LINGER_MILLIS = 700L
        const val FADE_MILLIS = 250

        /** The count of the last new hit in a batch, shown on that one tree or rock alone. */
        fun of(events: List<GameEvent>): ChopProgress? =
            events
                .mapNotNull { event ->
                    when {
                        event is GameEvent.Chopped && event.isNewHit -> {
                            ChopProgress(
                                event.treeOrigin,
                                event.hits,
                                event.total,
                            )
                        }

                        event is GameEvent.Mined && event.isNewHit -> {
                            ChopProgress(event.point, event.hits, event.total)
                        }

                        else -> {
                            null
                        }
                    }
                }.lastOrNull()
    }
}

/** A small "hits/total" label with a bar above the tree or rock being hit, fading after a moment. */
@Composable
fun ChopProgressOverlay(
    session: GameSession,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableStateOf<ChopProgress?>(null) }
    var shownAt by remember { mutableStateOf(0) }
    var alpha by remember { mutableStateOf(0f) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val shownAlpha by animateFloatAsState(
        alpha,
        if (alpha ==
            1f
        ) {
            snap()
        } else {
            tween(ChopProgress.FADE_MILLIS)
        },
        label = "chopProgress",
    )
    LaunchedEffect(session) {
        session.events.collect { events ->
            ChopProgress.of(events)?.let {
                progress = it
                shownAt++
            }
        }
    }
    LaunchedEffect(shownAt) {
        val shown = progress ?: return@LaunchedEffect
        alpha = 1f
        delay(shown.lingerMillis)
        alpha = 0f
        delay(ChopProgress.FADE_MILLIS.toLong())
    }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .semantics { hideFromAccessibility() },
    ) {
        val shown = progress
        val view = session.view.snapshot
        val here = shown?.let { view?.screenPosition(it.tile, TILE, GameState.GRID_SIZE) }
        val above =
            shown?.let {
                view?.screenPosition(GridPoint(it.tile.row - 1, it.tile.col), TILE, GameState.GRID_SIZE)
            }
        if (shown != null && here != null && above != null && shownAlpha > 0f) {
            val lift = (here.y - above.y) * 0.9f
            ChopProgressLabel(
                shown,
                Modifier.graphicsLayer {
                    translationX = here.x - bounds.left - size.width / 2
                    translationY = here.y - bounds.top - lift - size.height / 2
                    this.alpha = shownAlpha
                },
            )
        }
    }
}

@Composable
private fun ChopProgressLabel(
    progress: ChopProgress,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .size(58.dp, 31.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${progress.hits}/${progress.total}",
            style =
                TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(
                    7.dp,
                ).clip(RoundedCornerShape(3.5.dp))
                .background(Color.White.copy(alpha = 0.25f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((progress.hits.toFloat() / maxOf(progress.total, 1)).coerceIn(0f, 1f))
                    .height(7.dp)
                    .background(Color(0.45f, 0.87f, 0.45f)),
            )
        }
    }
}
