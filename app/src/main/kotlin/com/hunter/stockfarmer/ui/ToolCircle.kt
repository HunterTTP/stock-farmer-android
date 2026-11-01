package com.hunter.stockfarmer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.farm.FarmRenderer
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.ToolDrag

/** The tool in hand as a circle to drag out onto the farm, as on iOS: the farm pans with a plain swipe. */
@Composable
fun ToolCircle(
    session: GameSession,
    label: String,
    modifier: Modifier = Modifier,
) {
    val drag = remember(session) { ToolDrag(session) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    fun tileAt(local: Offset) =
        session.view.tileAt(origin.x + local.x, origin.y + local.y, FarmRenderer.TILE, GameState.GRID_SIZE)

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(CIRCLE_SIZE)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .onGloballyPositioned { origin = it.positionInWindow() }
                .pointerInput(drag) {
                    detectDragGestures(
                        onDragStart = { drag.move(tileAt(it)) },
                        onDrag = { change, _ -> drag.move(tileAt(change.position)) },
                        onDragEnd = { drag.end() },
                        onDragCancel = { drag.end() },
                    )
                },
    ) {
        Text(label, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
    }
}

private val CIRCLE_SIZE = 72.dp
